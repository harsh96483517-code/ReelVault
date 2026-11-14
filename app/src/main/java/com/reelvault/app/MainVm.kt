package com.reelvault.app

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.datastore.preferences.core.MutablePreferences
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

class MainVm(app: Application) : AndroidViewModel(app) {
    private val db = AppDb.get(app)
    private val dao = db.dao()
    private val repo = SettingsRepo(app)
    private val scanner = Scanner(app, db)

    val prefs: StateFlow<Prefs> = repo.prefs.stateIn(viewModelScope, SharingStarted.Eagerly, Prefs())
    /** Every row, including hidden ones (only the player and the Hidden vault use this). */
    val allRaw: StateFlow<List<VideoEntity>> = dao.observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    /** Visible videos: hidden ones never appear in Home, Library, Favorites, search, stats or random reels. */
    val all: StateFlow<List<VideoEntity>> = allRaw.map { l -> l.filter { !it.hidden } }
        .flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val hidden: StateFlow<List<VideoEntity>> = allRaw.map { l -> l.filter { it.hidden }.sortedByDescending { it.dateAdded } }
        .flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val query = MutableStateFlow("")
    val folderFilter = MutableStateFlow<String?>(null)
    val scanning = MutableStateFlow(false)

    val visible: StateFlow<List<VideoEntity>> = combine(
        all, query, folderFilter, prefs.map { it.sort }.distinctUntilChanged()
    ) { list, q, folder, sort ->
        var l = list
        if (folder != null) l = l.filter { it.folder == folder }
        if (q.isNotBlank()) l = l.filter { it.name.contains(q.trim(), ignoreCase = true) }
        when (sort) {
            1 -> l.sortedBy { it.dateAdded }
            2 -> l.sortedBy { it.name.lowercase() }
            else -> l.sortedByDescending { it.dateAdded }
        }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val favorites: StateFlow<List<VideoEntity>> = all
        .map { l -> l.filter { it.favorite }.sortedByDescending { it.dateAdded } }
        .flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val folders: StateFlow<List<Pair<String, Int>>> = all
        .map { l -> l.groupingBy { it.folder }.eachCount().toList().sortedByDescending { it.second } }
        .flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // ---- in-app updates (GitHub Releases) ----
    val update = MutableStateFlow<UpdateInfo?>(null)
    val updateStatus = MutableStateFlow<String?>(null)
    val downloadProgress = MutableStateFlow<Float?>(null)
    private var autoChecked = false

    fun updateRepo(): String = prefs.value.updateRepo.trim().ifBlank { BuildConfig.UPDATE_REPO }

    fun checkUpdate(manual: Boolean) {
        val repo = updateRepo()
        if (repo.isBlank()) { if (manual) updateStatus.value = "Add your GitHub repo (owner/name) first."; return }
        if (!manual) { if (autoChecked || !prefs.value.autoUpdate) return; autoChecked = true }
        viewModelScope.launch {
            if (manual) updateStatus.value = "Checking…"
            try {
                val info = Updater.check(getApplication<Application>(), repo)
                update.value = info
                if (manual) updateStatus.value = if (info == null) "You're on the latest version." else "Version ${info.version} is available."
            } catch (e: Exception) {
                if (manual) updateStatus.value = "Couldn't check (offline or repo not found)."
            }
        }
    }

    fun downloadAndInstall() {
        val info = update.value ?: return
        if (downloadProgress.value != null) return
        viewModelScope.launch {
            downloadProgress.value = 0f
            try {
                val f = Updater.download(getApplication<Application>(), info) { downloadProgress.value = it }
                downloadProgress.value = null
                if (Updater.install(getApplication<Application>(), f)) update.value = null
                else updateStatus.value = "Allow 'install unknown apps' for ReelVault, then tap Update again."
            } catch (e: Exception) {
                downloadProgress.value = null
                updateStatus.value = "Download failed. Check your internet and try again."
            }
        }
    }

    fun dismissUpdate() { update.value = null }

    fun refresh() {
        if (scanning.value) return
        viewModelScope.launch {
            scanning.value = true
            try { scanner.scan(prefs.value.folders) } finally { scanning.value = false }
        }
    }

    fun setPref(block: (MutablePreferences) -> Unit) { viewModelScope.launch { repo.edit(block) } }

    fun toggleFavorite(uri: String) = io { allRaw.value.firstOrNull { it.uri == uri }?.let { dao.setFavorite(uri, !it.favorite) } }
    fun toggleLike(uri: String) = io { allRaw.value.firstOrNull { it.uri == uri }?.let { dao.setLiked(uri, !it.liked) } }
    fun like(uri: String) = io { dao.setLiked(uri, true) }
    fun setCaption(uri: String, c: String) = io { dao.setCaption(uri, c.trim()) }
    fun savePosition(uri: String, pos: Long, dur: Long) = io {
        dao.setPosition(uri, if (dur > 0 && pos > dur - 2000) 0 else pos.coerceAtLeast(0))
    }
    fun removeRows(uris: List<String>) = io { uris.chunked(500).forEach { dao.delete(it) } }
    fun hide(uris: List<String>) = io { uris.chunked(500).forEach { dao.setHidden(it, true) } }
    fun unhide(uris: List<String>) = io { uris.chunked(500).forEach { dao.setHidden(it, false) } }
    fun favoriteMany(uris: List<String>, v: Boolean) = io { uris.chunked(500).forEach { dao.setFavoriteMany(it, v) } }
    fun setLast(uri: String) { setPref { it[K.last] = uri } }

    fun addFolder(uri: Uri) {
        runCatching {
            getApplication<Application>().contentResolver
                .takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }
        setPref { it[K.folders] = (it[K.folders] ?: emptySet()) + uri.toString() }
        viewModelScope.launch { kotlinx.coroutines.delay(200); refresh() }
    }

    fun removeFolder(uri: String) {
        runCatching {
            getApplication<Application>().contentResolver
                .releasePersistableUriPermission(Uri.parse(uri), Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        setPref { it[K.folders] = (it[K.folders] ?: emptySet()) - uri }
        viewModelScope.launch { kotlinx.coroutines.delay(200); refresh() }
    }

    fun saveProfile(name: String, handle: String, bio: String, instagram: String, avatar: Uri?) = io {
        val app = getApplication<Application>()
        var path: String? = null
        if (avatar != null) {
            runCatching {
                val f = File(app.filesDir, "avatar_${System.currentTimeMillis()}.jpg")
                app.contentResolver.openInputStream(avatar)?.use { i -> f.outputStream().use { o -> i.copyTo(o) } }
                File(prefs.value.avatar).takeIf { it.isFile }?.delete()
                path = f.absolutePath
            }
        }
        repo.edit {
            it[K.name] = name.trim()
            it[K.handle] = handle.trim().removePrefix("@").replace(" ", "_")
            it[K.bio] = bio.trim()
            it[K.instagram] = instagram.trim().removePrefix("@")
            path?.let { p -> it[K.avatar] = p }
        }
    }

    private fun io(block: suspend () -> Unit) = viewModelScope.launch(Dispatchers.IO) { block() }
}
