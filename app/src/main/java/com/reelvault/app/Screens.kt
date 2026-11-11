package com.reelvault.app

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import coil.annotation.ExperimentalCoilApi
import coil.imageLoader
import coil.compose.AsyncImage
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze

private val sortNames = listOf("Newest", "Oldest", "Name")

// ───────────────────────── HOME ─────────────────────────
@Composable
fun HomeScreen(
    vm: MainVm, perm: Boolean, askPerm: () -> Unit, searchOpen: Boolean, topPad: Dp, bottomPad: Dp,
    gridState: LazyGridState, listState: LazyListState, onPlay: (List<String>, Int) -> Unit,
) {
    val list by vm.visible.collectAsState()
    val prefs by vm.prefs.collectAsState()
    val q by vm.query.collectAsState()
    val folder by vm.folderFilter.collectAsState()
    val scanning by vm.scanning.collectAsState()
    var sortMenu by remember { mutableStateOf(false) }
    val onSurface = MaterialTheme.colorScheme.onSurface

    Column(Modifier.fillMaxSize().padding(top = topPad)) {
        AnimatedVisibility(searchOpen) {
            Box(Modifier.padding(horizontal = 20.dp, vertical = 6.dp).fillMaxWidth().glass(RoundedCornerShape(20.dp))) {
                TextField(
                    value = q, onValueChange = { vm.query.value = it }, singleLine = true,
                    placeholder = { Text("Search videos") },
                    leadingIcon = { Icon(Icons.Rounded.Search, null) },
                    trailingIcon = { if (q.isNotEmpty()) Icon(Icons.Rounded.Close, "Clear", Modifier.clickable { vm.query.value = "" }) },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        if (scanning) LinearProgressIndicator(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).clip(CircleShape), color = Violet, trackColor = Color.Transparent
        )
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text("Downloaded Videos", style = MaterialTheme.typography.titleMedium, color = onSurface)
                Text("${list.size} videos", style = MaterialTheme.typography.bodySmall, color = onSurface.copy(alpha = 0.65f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box {
                    GlassChip(sortNames[prefs.sort], { sortMenu = true }, icon = Icons.Rounded.Sort)
                    DropdownMenu(sortMenu, { sortMenu = false }) {
                        sortNames.forEachIndexed { i, n ->
                            DropdownMenuItem(text = { Text(n) }, onClick = { vm.setPref { it[K.sort] = i }; sortMenu = false })
                        }
                    }
                }
                GlassIconButton(
                    if (prefs.grid) Icons.Rounded.ViewList else Icons.Rounded.GridView, "Toggle view",
                    { vm.setPref { it[K.grid] = !prefs.grid } }, size = 38.dp,
                )
            }
        }
        folder?.let {
            GlassChip("$it   ✕", { vm.folderFilter.value = null }, Modifier.padding(horizontal = 20.dp, vertical = 4.dp), Icons.Rounded.FolderOpen, true)
        }
        val resumeIdx = if (prefs.remember) list.indexOfFirst { it.uri == prefs.last } else -1
        if (resumeIdx >= 0) {
            val v = list[resumeIdx]
            Row(
                Modifier.padding(horizontal = 20.dp, vertical = 6.dp).fillMaxWidth().bounceClick { onPlay(list.map { it.uri }, resumeIdx) }
                    .glass(RoundedCornerShape(18.dp)).padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(Icons.Rounded.PlayArrow, null, tint = Violet)
                Column(Modifier.weight(1f)) {
                    Text("Continue watching", style = MaterialTheme.typography.labelSmall, color = onSurface.copy(alpha = 0.65f))
                    Text(v.name, maxLines = 1, overflow = TextOverflow.Ellipsis, color = onSurface, fontWeight = FontWeight.Medium)
                }
                if (v.positionMs > 0) Text(fmtDur(v.positionMs), color = onSurface.copy(alpha = 0.65f), fontSize = 12.sp)
            }
        }
        when {
            !perm -> EmptyState(Icons.Rounded.Lock, "Allow video access",
                "ReelVault only reads videos on this phone. Nothing leaves your device.") {
                GradientButton("Allow access", Icons.Rounded.Check, askPerm)
            }
            list.isEmpty() -> EmptyState(Icons.Rounded.VideoLibrary,
                if (q.isNotBlank() || folder != null) "No matches" else "No videos yet",
                if (q.isNotBlank() || folder != null) "Try a different search or folder." else "Open Library to pick a folder or refresh the scan.") {
                if (q.isBlank() && folder == null) GradientButton("Scan now", Icons.Rounded.Refresh, { vm.refresh() })
            }
            prefs.grid -> VideoGrid(list, gridState, bottomPad) { onPlay(list.map { v -> v.uri }, it) }
            else -> VideoList(list, listState, bottomPad, { onPlay(list.map { v -> v.uri }, it) }, { vm.toggleFavorite(it) })
        }
    }
}

// ───────────────────────── LIBRARY ─────────────────────────
@Composable
fun LibraryScreen(
    vm: MainVm, perm: Boolean, askPerm: () -> Unit, topPad: Dp, bottomPad: Dp, onOpenFolder: (String) -> Unit,
) {
    val folders by vm.folders.collectAsState()
    val prefs by vm.prefs.collectAsState()
    val scanning by vm.scanning.collectAsState()
    val onSurface = MaterialTheme.colorScheme.onSurface
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) vm.addFolder(uri)
    }
    LazyColumn(
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = topPad, bottom = bottomPad),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Text("Library", style = MaterialTheme.typography.titleLarge, color = onSurface) }
        item {
            GlassCard {
                Text("Scan your phone for videos. Folders you add are read-only; originals are never changed.",
                    style = MaterialTheme.typography.bodyMedium, color = onSurface.copy(alpha = 0.7f))
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    GradientButton("Select Folder", Icons.Rounded.CreateNewFolder, { picker.launch(null) }, Modifier.weight(1f))
                    GlassIconButton(Icons.Rounded.Refresh, "Refresh library", { if (perm) vm.refresh() else askPerm() }, size = 48.dp)
                }
                if (scanning) {
                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator(Modifier.fillMaxWidth().clip(CircleShape), color = Violet)
                }
            }
        }
        if (!perm) item {
            GlassCard {
                Text("Video access is off", color = onSurface, fontWeight = FontWeight.SemiBold)
                Text("Allow it to detect Download, Movies, Instagram, WhatsApp Video and more.",
                    style = MaterialTheme.typography.bodySmall, color = onSurface.copy(alpha = 0.65f))
                Spacer(Modifier.height(10.dp))
                GradientButton("Allow access", Icons.Rounded.Check, askPerm)
            }
        }
        item { SectionTitle("Detected folders") }
        if (folders.isEmpty()) item {
            Text("Nothing found yet. Tap refresh.", color = onSurface.copy(alpha = 0.6f), style = MaterialTheme.typography.bodyMedium)
        }
        items(folders, key = { it.first }) { (name, count) ->
            Row(
                Modifier.fillMaxWidth().bounceClick { onOpenFolder(name) }.glass(RoundedCornerShape(18.dp)).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(Icons.Rounded.FolderOpen, null, tint = Violet)
                Text(name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, color = onSurface)
                Text("$count", color = onSurface.copy(alpha = 0.65f))
                Icon(Icons.Rounded.ChevronRight, null, tint = onSurface.copy(alpha = 0.5f))
            }
        }
        if (prefs.folders.isNotEmpty()) {
            item { SectionTitle("Folders you added") }
            items(prefs.folders.toList(), key = { it }) { f ->
                Row(
                    Modifier.fillMaxWidth().glass(RoundedCornerShape(18.dp)).padding(start = 14.dp, top = 6.dp, bottom = 6.dp, end = 6.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(Icons.Rounded.Folder, null, tint = Blue)
                    Text(Uri.decode(f.toUri().lastPathSegment ?: f), Modifier.weight(1f), maxLines = 1,
                        overflow = TextOverflow.Ellipsis, color = onSurface)
                    GlassIconButton(Icons.Rounded.Close, "Remove folder", { vm.removeFolder(f) }, size = 38.dp)
                }
            }
        }
    }
}

// ───────────────────────── FAVORITES ─────────────────────────
@Composable
fun FavoritesScreen(vm: MainVm, topPad: Dp, bottomPad: Dp, onPlay: (PlayRequest) -> Unit) {
    val favs by vm.favorites.collectAsState()
    val state = rememberLazyGridState()
    val onSurface = MaterialTheme.colorScheme.onSurface
    Column(Modifier.fillMaxSize().padding(top = topPad)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("Favorites", style = MaterialTheme.typography.titleLarge, color = onSurface)
                Text("${favs.size} saved", style = MaterialTheme.typography.bodySmall, color = onSurface.copy(alpha = 0.65f))
            }
            if (favs.isNotEmpty()) GradientButton("Play all", Icons.Rounded.PlayArrow,
                { onPlay(PlayRequest(favs.map { it.uri }, 0, autoNext = true)) })
        }
        if (favs.isEmpty()) EmptyState(Icons.Rounded.FavoriteBorder, "No favorites yet",
            "Tap the bookmark on any reel to save it here.")
        else VideoGrid(favs, state, bottomPad) { onPlay(PlayRequest(favs.map { v -> v.uri }, it, autoNext = true)) }
    }
}

// ───────────────────────── PROFILE ─────────────────────────
@Composable
fun ProfileScreen(vm: MainVm, topPad: Dp, bottomPad: Dp, onSettings: () -> Unit) {
    val prefs by vm.prefs.collectAsState()
    val all by vm.all.collectAsState()
    val onSurface = MaterialTheme.colorScheme.onSurface
    var editing by remember { mutableStateOf(false) }
    val favCount = all.count { it.favorite }
    val likeCount = all.count { it.liked }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = topPad, bottom = bottomPad),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Avatar(prefs.avatar, prefs.name, 104.dp)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(prefs.name.ifBlank { "Your name" }, style = MaterialTheme.typography.titleLarge, color = onSurface)
            Text("@" + prefs.handle.ifBlank { "yourhandle" }, color = Violet, fontWeight = FontWeight.Medium)
            if (prefs.bio.isNotBlank()) Text(prefs.bio, textAlign = TextAlign.Center, color = onSurface.copy(alpha = 0.75f),
                modifier = Modifier.padding(top = 6.dp, start = 12.dp, end = 12.dp))
        }
        GradientButton(if (prefs.name.isBlank()) "Create profile" else "Edit profile", Icons.Rounded.Edit, { editing = true })
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Stat("Videos", "${all.size}", Modifier.weight(1f))
            Stat("Favorites", "$favCount", Modifier.weight(1f))
            Stat("Liked", "$likeCount", Modifier.weight(1f))
        }
        GlassCard {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Rounded.Storage, null, tint = Blue)
                Column {
                    Text("Library size", color = onSurface.copy(alpha = 0.65f), style = MaterialTheme.typography.bodySmall)
                    Text(fmtSize(all.sumOf { it.size }), color = onSurface, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().bounceClick(onClick = onSettings).glass(RoundedCornerShape(20.dp)).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.Rounded.Settings, null, tint = Violet)
            Text("Settings", Modifier.weight(1f), color = onSurface, fontWeight = FontWeight.Medium)
            Icon(Icons.Rounded.ChevronRight, null, tint = onSurface.copy(alpha = 0.5f))
        }
    }
    if (editing) EditProfileDialog(prefs, { editing = false }) { n, h, b, a -> vm.saveProfile(n, h, b, a); editing = false }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier) {
    Column(modifier.glass(RoundedCornerShape(20.dp)).padding(vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f))
    }
}

@Composable
private fun EditProfileDialog(prefs: Prefs, onDismiss: () -> Unit, onSave: (String, String, String, Uri?) -> Unit) {
    var name by remember { mutableStateOf(prefs.name) }
    var handle by remember { mutableStateOf(prefs.handle) }
    var bio by remember { mutableStateOf(prefs.bio) }
    var photo by remember { mutableStateOf<Uri?>(null) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { if (it != null) photo = it }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Your profile") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.clickable { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                    if (photo != null) Box(Modifier.size(96.dp).clip(CircleShape)) {
                        AsyncImage(photo, null, Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                    } else Avatar(prefs.avatar, name, 96.dp)
                    Icon(Icons.Rounded.CameraAlt, "Change photo", tint = Color.White,
                        modifier = Modifier.align(Alignment.BottomEnd).size(30.dp).clip(CircleShape).background(Violet).padding(6.dp))
                }
                OutlinedTextField(name, { name = it.take(30) }, label = { Text("Name") }, singleLine = true)
                OutlinedTextField(handle, { handle = it.take(24) }, label = { Text("Username") }, prefix = { Text("@") }, singleLine = true)
                OutlinedTextField(bio, { bio = it.take(120) }, label = { Text("Bio") }, maxLines = 3)
            }
        },
        confirmButton = { TextButton({ onSave(name, handle, bio, photo) }) { Text("Save") } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
    )
}

// ───────────────────────── SETTINGS ─────────────────────────
@OptIn(ExperimentalCoilApi::class)
@Composable
fun SettingsScreen(vm: MainVm, onBack: () -> Unit) {
    val prefs by vm.prefs.collectAsState()
    val ctx = LocalContext.current
    val onSurface = MaterialTheme.colorScheme.onSurface
    val haze = remember { HazeState() }
    var cleared by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { if (it != null) vm.addFolder(it) }
    BackHandler(onBack = onBack)

    CompositionLocalProvider(LocalHaze provides haze) {
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().haze(haze)) {
                AppBackground()
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding()
                        .padding(top = 84.dp, start = 20.dp, end = 20.dp).navigationBarsPadding().padding(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    GlassCard {
                        Text("Theme", color = onSurface, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("System", "Dark", "Light").forEachIndexed { i, n ->
                                GlassChip(n, { vm.setPref { it[K.theme] = i } }, selected = prefs.theme == i)
                            }
                        }
                    }
                    GlassCard {
                        SwitchRow("Auto-scan on launch", "Look for new videos when the app opens", prefs.auto) { v -> vm.setPref { it[K.auto] = v } }
                        SwitchRow("Remember last played", "Resume where you left off", prefs.remember) { v -> vm.setPref { it[K.remember] = v } }
                        SwitchRow("Loop video", "Off = auto-play the next reel", prefs.loop) { v -> vm.setPref { it[K.loop] = v } }
                    }
                    GlassCard {
                        Text("Default playback speed", color = onSurface, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(0.5f, 1f, 1.5f, 2f).forEach { s ->
                                GlassChip("${s}x".replace(".0x", "x"), { vm.setPref { it[K.speed] = s } }, selected = prefs.speed == s)
                            }
                        }
                    }
                    GlassCard {
                        Text("Video folders", color = onSurface, fontWeight = FontWeight.SemiBold)
                        Text("${prefs.folders.size} custom folder(s) added. Manage them in Library.",
                            style = MaterialTheme.typography.bodySmall, color = onSurface.copy(alpha = 0.65f))
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            GlassChip("Add folder", { picker.launch(null) }, icon = Icons.Rounded.CreateNewFolder)
                            GlassChip("Rescan", { vm.refresh() }, icon = Icons.Rounded.Refresh)
                        }
                    }
                    GlassCard {
                        Text("Storage", color = onSurface, fontWeight = FontWeight.SemiBold)
                        Text("Clears thumbnails and temporary files. Your videos and favorites are untouched.",
                            style = MaterialTheme.typography.bodySmall, color = onSurface.copy(alpha = 0.65f))
                        Spacer(Modifier.height(10.dp))
                        GlassChip(if (cleared) "Cache cleared" else "Clear app cache", {
                            ctx.imageLoader.memoryCache?.clear()
                            ctx.imageLoader.diskCache?.clear()
                            ctx.cacheDir.listFiles()?.forEach { runCatching { it.deleteRecursively() } }
                            cleared = true
                        }, icon = Icons.Rounded.Delete)
                    }
                    Text("ReelVault 1.0 • 100% offline • no ads • no login", Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall, color = onSurface.copy(alpha = 0.5f))
                }
            }
            Row(
                Modifier.statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth().height(60.dp)
                    .glass(RoundedCornerShape(30.dp)).padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                GlassIconButton(Icons.Rounded.ArrowBack, "Back", onBack, size = 42.dp)
                Text("Settings", style = MaterialTheme.typography.titleMedium, color = onSurface)
            }
        }
    }
}

@Composable
private fun SwitchRow(title: String, sub: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = onSurface, fontWeight = FontWeight.Medium)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = onSurface.copy(alpha = 0.6f))
        }
        Switch(checked, onChange, colors = SwitchDefaults.colors(checkedTrackColor = Violet))
    }
}
