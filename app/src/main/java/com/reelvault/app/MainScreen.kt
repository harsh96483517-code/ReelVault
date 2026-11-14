package com.reelvault.app

import android.Manifest
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze

private val permName = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_VIDEO
else Manifest.permission.READ_EXTERNAL_STORAGE

private data class Tab(val label: String, val icon: ImageVector)
private val tabs = listOf(
    Tab("Home", Icons.Rounded.Home), Tab("Library", Icons.Rounded.VideoLibrary),
    Tab("Favorites", Icons.Rounded.Favorite), Tab("Profile", Icons.Rounded.Person),
)

@Composable
fun MainScreen(vm: MainVm) {
    val ctx = LocalContext.current
    val activity = ctx as ComponentActivity
    val dark = LocalDark.current
    val prefs by vm.prefs.collectAsState()

    var tab by rememberSaveable { mutableIntStateOf(0) }
    var settings by rememberSaveable { mutableStateOf(false) }
    var search by rememberSaveable { mutableStateOf(false) }
    var req by remember { mutableStateOf<PlayRequest?>(null) }
    var perm by remember { mutableStateOf(ContextCompat.checkSelfPermission(ctx, permName) == android.content.pm.PackageManager.PERMISSION_GRANTED) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        perm = granted; if (granted) vm.refresh()
    }
    val askPerm = { launcher.launch(permName) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        perm = ContextCompat.checkSelfPermission(ctx, permName) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }
    LaunchedEffect(dark) {
        val style = if (dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
        activity.enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    }
    var scanned by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(perm) { if (perm && !scanned && prefs.auto) { scanned = true; vm.refresh() } }

    val allVideos by vm.all.collectAsState()
    val update by vm.update.collectAsState()
    val progress by vm.downloadProgress.collectAsState()
    val status by vm.updateStatus.collectAsState()
    LaunchedEffect(Unit) { vm.checkUpdate(manual = false) }

    // Dock "Reels" button: straight into the player, random or in order (Settings).
    val startReels: () -> Unit = {
        if (allVideos.isEmpty()) {
            android.widget.Toast.makeText(ctx, "No videos yet. Open Library and scan.", android.widget.Toast.LENGTH_SHORT).show()
        } else {
            val ordered = allVideos.sortedByDescending { it.dateAdded }.map { it.uri }
            req = if (prefs.reelsRandom) PlayRequest(ordered.shuffled(), 0)
            else PlayRequest(ordered, if (prefs.remember) ordered.indexOf(prefs.last).coerceAtLeast(0) else 0)
        }
    }

    // multi-select (long-press a video) + hidden vault
    val selected = remember { mutableStateListOf<String>() }
    val toggle: (String) -> Unit = { u -> if (u in selected) selected.remove(u) else selected.add(u) }
    var vault by remember { mutableStateOf(false) }
    var vaultAuth by remember { mutableStateOf(false) }
    var bin by remember { mutableStateOf(false) }
    var deleteDialog by remember { mutableStateOf(false) }
    val actions = rememberMediaActions(vm)
    LaunchedEffect(tab) { selected.clear() }
    DisposableEffect(vault) {
        AppLock.vaultOpen = vault
        (ctx as? MainActivity)?.refreshSecure()
        onDispose { AppLock.vaultOpen = false; (ctx as? MainActivity)?.refreshSecure() }
    }
    val openVault: () -> Unit = {
        if (!AppLock.isSet) {
            android.widget.Toast.makeText(ctx, "Set a pattern lock first to protect hidden videos.", android.widget.Toast.LENGTH_LONG).show()
            settings = true
        } else vaultAuth = true
    }

    val homeGrid = rememberLazyGridState()
    val homeList = rememberLazyListState()
    val hazeState = remember { HazeState() }

    update?.let { UpdateDialog(vm, it, progress, status) }
    if (deleteDialog) androidx.compose.material3.AlertDialog(
        onDismissRequest = { deleteDialog = false },
        title = { Text("Delete ${selected.size} video(s)?") },
        text = { Text("Move to bin keeps them for about 30 days so you can restore them. Delete permanently erases them from your phone for good.") },
        confirmButton = {
            androidx.compose.material3.TextButton({ val l = selected.toList(); deleteDialog = false; selected.clear(); actions.trash(l) }) { Text("Move to bin") }
        },
        dismissButton = {
            androidx.compose.material3.TextButton({ val l = selected.toList(); deleteDialog = false; selected.clear(); actions.deleteForever(l) }) { Text("Delete permanently") }
        },
    )

    when {
        req != null -> ReelsScreen(vm, req!!) { req = null }
        vaultAuth -> UnlockPanel("Hidden vault", "Verify it's you", onSuccess = { vaultAuth = false; vault = true }, onCancel = { vaultAuth = false })
        vault -> VaultScreen(vm, onBack = { vault = false }, onPlay = { req = it })
        bin -> BinScreen(vm, actions) { bin = false }
        settings -> SettingsScreen(vm) { settings = false }
        else -> {
            BackHandler(enabled = selected.isNotEmpty() || tab != 0 || search) {
                if (selected.isNotEmpty()) selected.clear()
                else if (search) { search = false; vm.query.value = "" } else tab = 0
            }
            val topPad = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 80.dp
            val bottomPad = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 110.dp

            CompositionLocalProvider(LocalHaze provides (if (SafeMode.on) null else hazeState)) {
                Box(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize().then(if (SafeMode.on) Modifier else Modifier.haze(hazeState))) {
                      // Content INSIDE the haze source must not use hazeChild itself (it would blur itself and crash).
                      CompositionLocalProvider(LocalHaze provides null) {
                        AppBackground()
                        // elastic, water-like switch between tabs
                        AnimatedContent(
                            targetState = tab,
                            transitionSpec = {
                                (fadeIn(tween(220)) + scaleIn(initialScale = 0.92f, animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow)))
                                    .togetherWith(fadeOut(tween(140)))
                            },
                            label = "tabs",
                        ) { t ->
                            when (t) {
                                0 -> HomeScreen(vm, perm, { askPerm() }, search, topPad, bottomPad, homeGrid, homeList, selected, toggle) { uris, i -> req = PlayRequest(uris, i) }
                                1 -> LibraryScreen(vm, perm, { askPerm() }, topPad, bottomPad, { bin = true }) { folder -> vm.folderFilter.value = folder; tab = 0 }
                                2 -> FavoritesScreen(vm, topPad, bottomPad, selected, toggle) { req = it }
                                else -> ProfileScreen(vm, topPad, bottomPad, openVault) { settings = true }
                            }
                        }
                      }
                    }
                    if (selected.isEmpty()) TopHeader(tab == 0, search, { search = !search; if (!search) vm.query.value = "" }, { settings = true },
                        Modifier.align(Alignment.TopCenter))
                    else SelectionBar(
                        selected.size, { selected.clear() },
                        { val pool = if (tab == 2) vm.favorites.value else vm.visible.value; selected.clear(); selected.addAll(pool.map { it.uri }) },
                        { vm.favoriteMany(selected.toList(), true); selected.clear() },
                        {
                            val n = selected.size
                            vm.hide(selected.toList()); selected.clear()
                            android.widget.Toast.makeText(ctx, "$n hidden. Find them in Profile > Hidden vault.", android.widget.Toast.LENGTH_LONG).show()
                        },
                        { deleteDialog = true },
                        Modifier.align(Alignment.TopCenter),
                    )
                    BottomBar(tab, { tab = it }, startReels, Modifier.align(Alignment.BottomCenter))
                }
            }
        }
    }
}

@Composable
private fun UpdateDialog(vm: MainVm, info: UpdateInfo, progress: Float?, status: String?) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = { if (progress == null) vm.dismissUpdate() },
        title = { Text("Update available • v${info.version}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(info.notes.trim().ifBlank { "A new version of ReelVault is ready." }.take(500))
                if (info.size > 0) Text("Size: ${fmtSize(info.size)}", style = MaterialTheme.typography.bodySmall)
                if (progress != null) androidx.compose.material3.LinearProgressIndicator({ progress }, Modifier.fillMaxWidth(), color = Violet)
                if (status != null && progress == null) Text(status, style = MaterialTheme.typography.bodySmall, color = Pink)
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton({ vm.downloadAndInstall() }, enabled = progress == null) {
                Text(if (progress == null) "Update now" else "Downloading…")
            }
        },
        dismissButton = { androidx.compose.material3.TextButton({ vm.dismissUpdate() }, enabled = progress == null) { Text("Later") } },
    )
}

@Composable
private fun SelectionBar(
    count: Int, onClose: () -> Unit, onAll: () -> Unit, onFav: () -> Unit, onHide: () -> Unit, onDelete: () -> Unit, modifier: Modifier,
) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    Row(
        modifier.statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth().height(60.dp)
            .glass(RoundedCornerShape(30.dp)).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        GlassIconButton(Icons.Rounded.Close, "Cancel", onClose, size = 42.dp)
        Text("$count selected", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, color = onSurface)
        GlassIconButton(Icons.Rounded.SelectAll, "Select all", onAll, size = 40.dp)
        GlassIconButton(Icons.Rounded.FavoriteBorder, "Add to favorites", onFav, size = 40.dp)
        GlassIconButton(Icons.Rounded.VisibilityOff, "Hide", onHide, size = 40.dp)
        GlassIconButton(Icons.Rounded.Delete, "Delete", onDelete, size = 40.dp)
    }
}

@Composable
private fun TopHeader(showSearch: Boolean, searchOn: Boolean, onSearch: () -> Unit, onSettings: () -> Unit, modifier: Modifier) {
    val shape = RoundedCornerShape(30.dp)
    // The logo bar is always black (both themes) so the chrome wordmark looks its best.
    CompositionLocalProvider(LocalDark provides true, LocalHaze provides null) {
        Row(
            modifier.statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth().height(60.dp)
                .clip(shape).background(Color.Black).border(1.dp, Color.White.copy(alpha = 0.22f), shape).padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Wordmark(Modifier.weight(1f).height(40.dp).padding(start = 8.dp), dark = true)
            if (showSearch) GlassIconButton(if (searchOn) Icons.Rounded.Close else Icons.Rounded.Search, "Search", onSearch, size = 40.dp, tint = Color.White)
            GlassIconButton(Icons.Rounded.Settings, "Settings", onSettings, size = 40.dp, tint = Color.White)
        }
    }
}

@Composable
private fun BottomBar(selected: Int, onSelect: (Int) -> Unit, onReels: () -> Unit, modifier: Modifier) {
    val dark = LocalDark.current
    val onSurface = MaterialTheme.colorScheme.onSurface
    BoxWithConstraints(
        modifier.navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp).fillMaxWidth().height(68.dp)
            .glass(RoundedCornerShape(34.dp), strong = true).padding(6.dp),
    ) {
        val slotW = maxWidth / 5
        val slot = if (selected >= 2) selected + 1 else selected   // slot 2 is the Reels button
        // liquid lens that glides to the selected icon with a springy overshoot
        val targetX = slotW * slot
        val x by animateDpAsState(targetX, spring(Spring.DampingRatioLowBouncy, Spring.StiffnessMediumLow), label = "lens")
        val stretch = (kotlin.math.abs((targetX - x).value) / slotW.value).coerceIn(0f, 1f)   // water-drop stretch while moving
        Box(
            Modifier.offset(x = x).width(slotW).fillMaxHeight()
                .graphicsLayer { scaleX = 1f + 0.40f * stretch; scaleY = 1f - 0.12f * stretch }
                .clip(RoundedCornerShape(28.dp))
                .background(if (dark) Color.White.copy(alpha = 0.16f) else Color.Black.copy(alpha = 0.07f))
                .border(1.dp, if (dark) Color.White.copy(alpha = 0.35f) else Color.Black.copy(alpha = 0.18f), RoundedCornerShape(28.dp))
        )
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            for (i in 0..4) {
                if (i == 2) {
                    Box(Modifier.weight(1f).fillMaxHeight().bounceClick(onClick = onReels), contentAlignment = Alignment.Center) {
                        Box(Modifier.size(50.dp).clip(CircleShape).background(AccentBrush), contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.PlayArrow, "Play reels", tint = OnAccent, modifier = Modifier.size(30.dp))
                        }
                    }
                } else {
                    val t = if (i > 2) i - 1 else i
                    val sel = t == selected
                    val grow by animateFloatAsState(if (sel) 1f else 0f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium), label = "tab")
                    val c = if (sel) Violet else onSurface.copy(alpha = 0.72f)
                    Box(Modifier.weight(1f).fillMaxHeight().bounceClick { onSelect(t) }, contentAlignment = Alignment.Center) {
                        Icon(tabs[t].icon, tabs[t].label, tint = c, modifier = Modifier.size(27.dp).scale(1f + 0.15f * grow))
                    }
                }
            }
        }
    }
}
