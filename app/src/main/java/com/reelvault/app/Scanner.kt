package com.reelvault.app

import android.content.ContentUris
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Read-only library scanner. Uses MediaStore for everything on the device and the
 * Storage Access Framework for extra folders the user picked. Never writes to videos.
 */
class Scanner(private val ctx: Context, private val db: AppDb) {

    suspend fun scan(treeUris: Set<String>) = withContext(Dispatchers.IO) {
        val dao = db.dao()
        val existing = dao.getAll().associateBy { it.uri }
        val found = LinkedHashMap<String, VideoEntity>()
        val seen = HashSet<String>()

        queryMediaStore(found, seen)
        for (t in treeUris) runCatching { scanTree(Uri.parse(t), found, seen, existing) }

        db.withTransaction {
            val fresh = ArrayList<VideoEntity>()
            for (v in found.values) {
                val o = existing[v.uri]
                if (o == null) fresh.add(v)
                else if (o.name != v.name || o.durationMs != v.durationMs || o.size != v.size ||
                    o.dateAdded != v.dateAdded || o.folder != v.folder
                ) dao.updateMeta(v.uri, v.name, v.durationMs, v.size, v.dateAdded, v.folder)
            }
            if (fresh.isNotEmpty()) dao.insert(fresh)
            (existing.keys - found.keys).chunked(500).forEach { dao.delete(it) } // DB rows only
        }
    }

    private fun queryMediaStore(out: MutableMap<String, VideoEntity>, seen: MutableSet<String>) {
        val collection = if (Build.VERSION.SDK_INT >= 29)
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        else MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val pathCol = if (Build.VERSION.SDK_INT >= 29) MediaStore.Video.Media.RELATIVE_PATH else MediaStore.Video.Media.DATA
        val projection = arrayOf(MediaStore.Video.Media._ID, MediaStore.Video.Media.DISPLAY_NAME, MediaStore.Video.Media.DURATION, MediaStore.Video.Media.SIZE, MediaStore.Video.Media.DATE_ADDED, pathCol)
        ctx.contentResolver.query(collection, projection, null, null, "${MediaStore.Video.Media.DATE_ADDED} DESC")?.use { c ->
            val iId = c.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val iName = c.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val iDur = c.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
            val iSize = c.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
            val iDate = c.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
            val iPath = c.getColumnIndexOrThrow(pathCol)
            while (c.moveToNext()) {
                val name = c.getString(iName) ?: continue
                val uri = ContentUris.withAppendedId(collection, c.getLong(iId)).toString()
                val raw = c.getString(iPath) ?: ""
                val folder = if (Build.VERSION.SDK_INT >= 29) raw.trimEnd('/').ifBlank { "Internal storage" }
                else File(raw).parentFile?.name ?: "Internal storage"
                val size = c.getLong(iSize)
                seen.add("$name|$size")
                out[uri] = VideoEntity(uri, name, c.getLong(iDur), size, c.getLong(iDate), folder)
            }
        }
    }

    private fun scanTree(
        tree: Uri, out: MutableMap<String, VideoEntity>, seen: Set<String>, existing: Map<String, VideoEntity>,
    ) {
        val root = DocumentFile.fromTreeUri(ctx, tree) ?: return
        val stack = ArrayDeque<DocumentFile>()
        stack.add(root)
        while (stack.isNotEmpty()) {
            val dir = stack.removeLast()
            for (f in dir.listFiles()) {
                if (f.isDirectory) { stack.add(f); continue }
                if (f.type?.startsWith("video/") != true) continue
                val name = f.name ?: continue
                val size = f.length()
                if ("$name|$size" in seen) continue // already found through MediaStore
                val key = f.uri.toString()
                val dur = existing[key]?.durationMs ?: duration(f.uri)
                out[key] = VideoEntity(key, name, dur, size, f.lastModified() / 1000, dir.name ?: "Folder")
            }
        }
    }

    private fun duration(uri: Uri): Long {
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(ctx, uri)
            r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        } catch (e: Exception) { 0L } finally { runCatching { r.release() } }
    }
}
