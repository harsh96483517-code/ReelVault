package com.reelvault.app

import android.app.Activity
import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Real deletion. On Android 11+ the system shows its own confirmation, because apps can't silently
 * delete other apps' media. "Bin" = Android's trash (kept ~30 days), "permanently" = gone for good.
 */
class MediaActions(
    private val ctx: Context,
    private val vm: MainVm,
    private val request: (IntentSender, () -> Unit) -> Unit,
) {
    private fun isMedia(u: String) = Uri.parse(u).authority == MediaStore.AUTHORITY
    private fun toast(t: String) = Toast.makeText(ctx, t, Toast.LENGTH_LONG).show()

    fun trash(uris: List<String>, onDone: () -> Unit = {}) {
        val media = uris.filter { isMedia(it) }
        if (media.size != uris.size) toast("Videos in added folders can't go to the bin. Use Delete permanently.")
        if (media.isEmpty()) return
        if (Build.VERSION.SDK_INT < 30) { toast("The bin needs Android 11 or newer."); return }
        AppLock.bypass()
        val pi = MediaStore.createTrashRequest(ctx.contentResolver, media.map { Uri.parse(it) }, true)
        request(pi.intentSender) { vm.removeRows(media); toast("Moved to bin"); onDone() }
    }

    fun deleteForever(uris: List<String>, onDone: () -> Unit = {}) {
        val media = uris.filter { isMedia(it) }
        val other = uris.filter { !isMedia(it) }
        val gone = other.filter { runCatching { DocumentsContract.deleteDocument(ctx.contentResolver, Uri.parse(it)) }.getOrDefault(false) }
        if (gone.isNotEmpty()) vm.removeRows(gone)
        if (gone.size != other.size) toast("Couldn't delete ${other.size - gone.size} video(s) from added folders. Remove and re-add the folder to allow it.")
        if (media.isEmpty()) { if (gone.isNotEmpty()) onDone(); return }
        if (Build.VERSION.SDK_INT >= 30) {
            AppLock.bypass()
            val pi = MediaStore.createDeleteRequest(ctx.contentResolver, media.map { Uri.parse(it) })
            request(pi.intentSender) { vm.removeRows(media); toast("Deleted permanently"); onDone() }
        } else {
            val ok = media.filter { runCatching { ctx.contentResolver.delete(Uri.parse(it), null, null) > 0 }.getOrDefault(false) }
            if (ok.isNotEmpty()) { vm.removeRows(ok); toast("Deleted permanently"); onDone() }
            else toast("Couldn't delete this video on this Android version.")
        }
    }

    fun restore(uris: List<String>, onDone: () -> Unit = {}) {
        if (Build.VERSION.SDK_INT < 30) return
        AppLock.bypass()
        val pi = MediaStore.createTrashRequest(ctx.contentResolver, uris.map { Uri.parse(it) }, false)
        request(pi.intentSender) { vm.refresh(); toast("Restored"); onDone() }
    }
}

@Composable
fun rememberMediaActions(vm: MainVm): MediaActions {
    val ctx = LocalContext.current
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) pending?.invoke()
        pending = null
    }
    return remember {
        MediaActions(ctx, vm) { sender, after ->
            pending = after
            launcher.launch(IntentSenderRequest.Builder(sender).build())
        }
    }
}

data class BinItem(val uri: String, val name: String, val size: Long, val durationMs: Long, val daysLeft: Int)

object Bin {
    /** Videos currently in Android's trash (Android 11+). */
    fun list(ctx: Context): List<BinItem> {
        if (Build.VERSION.SDK_INT < 30) return emptyList()
        val coll = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        val args = Bundle().apply { putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_ONLY) }
        val proj = arrayOf(
            MediaStore.Video.Media._ID, MediaStore.Video.Media.DISPLAY_NAME, MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DURATION, MediaStore.MediaColumns.DATE_EXPIRES,
        )
        val out = ArrayList<BinItem>()
        runCatching {
            ctx.contentResolver.query(coll, proj, args, null)?.use { c ->
                val now = System.currentTimeMillis() / 1000
                while (c.moveToNext()) {
                    val left = (((c.getLong(4) - now) / 86400).toInt() + 1).coerceAtLeast(0)
                    out.add(BinItem(android.content.ContentUris.withAppendedId(coll, c.getLong(0)).toString(),
                        c.getString(1) ?: "video", c.getLong(2), c.getLong(3), left))
                }
            }
        }
        return out
    }
}

@Composable
fun BinScreen(vm: MainVm, actions: MediaActions, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val ink = MaterialTheme.colorScheme.onSurface
    var binItems by remember { mutableStateOf<List<BinItem>>(emptyList()) }
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(tick) { binItems = withContext(Dispatchers.IO) { Bin.list(ctx) } }
    BackHandler(onBack = onBack)

    Box(Modifier.fillMaxSize()) {
        AppBackground()
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth().height(60.dp)
                    .glass(RoundedCornerShape(30.dp)).padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                GlassIconButton(Icons.Rounded.ArrowBack, "Back", onBack, size = 42.dp)
                Column(Modifier.weight(1f)) {
                    Text("Bin", style = MaterialTheme.typography.titleMedium, color = ink)
                    Text("${binItems.size} video(s) • removed after ~30 days", style = MaterialTheme.typography.bodySmall, color = ink.copy(alpha = 0.6f))
                }
                if (binItems.isNotEmpty()) GlassChip("Empty bin", { actions.deleteForever(binItems.map { it.uri }) { tick++ } }, icon = Icons.Rounded.DeleteForever)
            }
            if (binItems.isEmpty()) EmptyState(Icons.Rounded.Delete, "Bin is empty",
                "Videos you move to the bin wait here, and you can restore them any time before they're removed.")
            else LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 32.dp),
            ) {
                items(binItems, key = { it.uri }) { v ->
                    Row(
                        Modifier.fillMaxWidth().glass(RoundedCornerShape(18.dp)).padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Box(Modifier.width(52.dp).aspectRatio(9f / 14f).clip(RoundedCornerShape(10.dp)).background(if (Mono.dark) androidx.compose.ui.graphics.Color(0xFF1E1E1E) else androidx.compose.ui.graphics.Color(0xFFE4E4E4))) {
                            Thumb(v.uri, Modifier.fillMaxSize())
                        }
                        Column(Modifier.weight(1f)) {
                            Text(v.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold, color = ink)
                            Text("${fmtSize(v.size)}  •  ${v.daysLeft} days left", style = MaterialTheme.typography.bodySmall, color = ink.copy(alpha = 0.6f))
                        }
                        GlassIconButton(Icons.Rounded.Restore, "Restore", { actions.restore(listOf(v.uri)) { tick++ } }, size = 40.dp)
                        GlassIconButton(Icons.Rounded.DeleteForever, "Delete permanently", { actions.deleteForever(listOf(v.uri)) { tick++ } }, size = 40.dp)
                    }
                }
            }
        }
    }
}
