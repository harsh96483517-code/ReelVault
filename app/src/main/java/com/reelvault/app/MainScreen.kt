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
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.math.abs
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.layout
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import com.reelvault.app.backdrop.backdrops.layerBackdrop
import com.reelvault.app.backdrop.backdrops.rememberLayerBackdrop
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
        // The window sits behind every screen change: keep it the theme's own colour, not always black.
        activity.window.setBackgroundDrawable(
            android.graphics.drawable.ColorDrawable(if (dark) android.graphics.Color.BLACK else 0xFFF2F2F2.toInt())
        )
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
    if (deleteDialog) GlassDialog(
        onDismiss = { deleteDialog = false },
        title = { Text("Delete ${selected.size} video(s)?") },
        content = { Text("Move to bin keeps them for about 30 days so you can restore them. Delete permanently erases them from your phone for good.") },
        buttons = {
            androidx.compose.material3.TextButton({ val l = selected.toList(); deleteDialog = false; selected.clear(); actions.deleteForever(l) }) { Text("Delete permanently") }
            androidx.compose.material3.TextButton({ val l = selected.toList(); deleteDialog = false; selected.clear(); actions.trash(l) }) { Text("Move to bin") }
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

            // Liquid glass (dock, header, selection bar) samples this recording of the page. It is painted over the
            // window colour first so the glass has something opaque to blur in light AND dark theme.
            val glassSupported = isGlassSupported()
            val windowBg = MaterialTheme.colorScheme.background
            val paintBackdrop: ContentDrawScope.() -> Unit = remember(windowBg) { { drawRect(windowBg); drawContent() } }
            val appBackdrop = rememberLayerBackdrop(onDraw = paintBackdrop)
            CompositionLocalProvider(
                LocalHaze provides (if (SafeMode.on) null else hazeState),
                LocalAppBackdrop provides (if (glassSupported) appBackdrop else null),
            ) {
                Box(Modifier.fillMaxSize()) {
                    Box(
                        Modifier.fillMaxSize()
                            .then(if (SafeMode.on) Modifier else Modifier.haze(hazeState))
                            .then(if (glassSupported) Modifier.layerBackdrop(appBackdrop) else Modifier)
                    ) {
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
    GlassDialog(
        onDismiss = { if (progress == null) vm.dismissUpdate() },
        title = { Text("Update available • v${info.version}") },
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(info.notes.trim().ifBlank { "A new version of ReelVault is ready." }.take(500))
                if (info.size > 0) Text("Size: ${fmtSize(info.size)}", style = MaterialTheme.typography.bodySmall)
                if (progress != null) androidx.compose.material3.LinearProgressIndicator({ progress }, Modifier.fillMaxWidth(), color = Violet)
                if (status != null && progress == null) Text(status, style = MaterialTheme.typography.bodySmall, color = Pink)
            }
        },
        buttons = {
            androidx.compose.material3.TextButton({ vm.dismissUpdate() }, enabled = progress == null) { Text("Later") }
            androidx.compose.material3.TextButton({ vm.downloadAndInstall() }, enabled = progress == null) {
                Text(if (progress == null) "Update now" else "Downloading…")
            }
        },
    )
}

@Composable
private fun SelectionBar(
    count: Int, onClose: () -> Unit, onAll: () -> Unit, onFav: () -> Unit, onHide: () -> Unit, onDelete: () -> Unit, modifier: Modifier,
) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    val shape = remember { RoundedCornerShape(30.dp) }
    val glassOn = glassAvailable()
    Row(
        modifier.statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth().height(60.dp)
            .then(
                if (glassOn) Modifier.clip(shape).liquidGlass(shape).border(GLASS_EDGE_WIDTH, glassEdgeColor, shape)
                else Modifier.glass(shape, strong = true)
            )
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        GlassIconButton(Icons.Rounded.Close, "Cancel", onClose, size = 42.dp, flat = true)
        Text("$count selected", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, color = onSurface)
        GlassIconButton(Icons.Rounded.SelectAll, "Select all", onAll, size = 40.dp, flat = true)
        GlassIconButton(Icons.Rounded.FavoriteBorder, "Add to favorites", onFav, size = 40.dp, flat = true)
        GlassIconButton(Icons.Rounded.VisibilityOff, "Hide", onHide, size = 40.dp, flat = true)
        GlassIconButton(Icons.Rounded.Delete, "Delete", onDelete, size = 40.dp, flat = true)
    }
}

@Composable
private fun TopHeader(showSearch: Boolean, searchOn: Boolean, onSearch: () -> Unit, onSettings: () -> Unit, modifier: Modifier) {
    val shape = remember { RoundedCornerShape(30.dp) }
    val glassOn = glassAvailable()
    // Liquid glass that follows the theme: dark glass in dark mode, light glass in light mode (never a black slab).
    Row(
        modifier.statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth().height(60.dp)
            .then(
                if (glassOn) Modifier.clip(shape).liquidGlass(shape).border(GLASS_EDGE_WIDTH, glassEdgeColor, shape)
                else Modifier.glass(shape, strong = true)
            )
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Wordmark(Modifier.weight(1f).height(44.dp).padding(start = 8.dp), dark = Mono.dark)
        if (showSearch) GlassIconButton(if (searchOn) Icons.Rounded.Close else Icons.Rounded.Search, "Search", onSearch, size = 40.dp, flat = true)
        GlassIconButton(Icons.Rounded.Settings, "Settings", onSettings, size = 40.dp, flat = true)
    }
}

private const val CENTER_SLOT = 2
private fun slotOf(t: Int) = if (t >= CENTER_SLOT) t + 1 else t
private fun tabOf(s: Int) = if (s > CENTER_SLOT) s - 1 else s

/** Gap between the dock's glass edge and the icons inside it (same as Harsh Musix's PILL_INSET). */
private val PILL_INSET = 6.dp

/**
 * Liquid-glass icon dock, ported 1:1 from Harsh Musix's floating tab bar.
 *
 * The selection pill is a real glass lens: a tap (or a finger) lifts it past the dock edges on springs, it
 * refracts the dock and the icons under it, stretches/squashes with its own acceleration while travelling,
 * resists at the ends, ticks across tab boundaries and sheds the glass back into a flat fill when it lands.
 * The centre ▶ button plays reels directly; the pill never rests on it (dragging over it just skips across).
 */
@Composable
private fun BottomBar(selected: Int, onSelect: (Int) -> Unit, onReels: () -> Unit, modifier: Modifier) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val shape = remember { RoundedCornerShape(percent = 50) }
    val glassOn = glassAvailable()

    val selectedSlot = slotOf(selected)
    val currentSlot by rememberUpdatedState(selectedSlot)
    val currentOnSelect by rememberUpdatedState(onSelect)

    val pill = remember { GlassPillMotion(scope, selectedSlot) }
    val barGlass = rememberLayerBackdrop()
    val tabRow = rememberLayerBackdrop()
    var rowSize by remember { mutableStateOf(IntSize.Zero) }
    var lastHapticSlot by remember { mutableIntStateOf(selectedSlot) }

    val padPx = with(density) { PILL_INSET.toPx() }
    val slotW = if (rowSize.width > 0) rowSize.width / 5f else 0f
    pill.stepDp = slotW / density.density
    val rowH = rowSize.height.toFloat()
    val restSize = Size(slotW, rowH)
    // The pill rests in its slot and lifts to stand PILL_GROW_HEIGHT taller than the whole dock.
    val liftedH = rowH + padPx * 2f + with(density) { PILL_GROW_HEIGHT.toPx() }
    val liftedSize = if (rowH > 0f) Size(liftedH * slotW / rowH, liftedH) else restSize
    val centerInRow: (Float) -> Offset = { pos -> Offset(pos * slotW + slotW / 2f, rowH / 2f) }
    val showPill = slotW > 0f && rowH > 0f

    LaunchedEffect(selectedSlot) {
        pill.animateTo(selectedSlot)
        lastHapticSlot = selectedSlot
    }

    Box(modifier.navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp).fillMaxWidth()) {
        // The dock's own glass is exported so the lens can refract it.
        CompositionLocalProvider(LocalGlassExport provides barGlass) {
            Box(
                Modifier.fillMaxWidth()
                    .then(
                        if (glassOn) Modifier.clip(shape).liquidGlass(shape).border(GLASS_EDGE_WIDTH, glassEdgeColor, shape)
                        else Modifier.glass(shape, strong = true)
                    )
                    .padding(PILL_INSET),
            ) {
                // Flat pill: what is drawn at rest (and everywhere when real glass is unavailable).
                // Always composed (never added/removed mid-frame, which crashed measure); it just fades out
                // while the glass lens is lifted.
                FlatSelectionPill(
                    color = glassIndicatorColor,
                    modifier = Modifier.matchParentSize()
                        .graphicsLayer { alpha = if (glassOn && !pill.isFlat) 0f else 1f }
                        .pillPlacement(
                            center = { centerInRow(pill.position) },
                            size = { pill.liveSize(restSize, liftedSize) },
                        ),
                )

                Row(
                    Modifier.fillMaxWidth().height(56.dp)
                        .onSizeChanged { rowSize = it }
                        .layerBackdrop(tabRow)
                        .pointerInput(slotW, currentSlot) {
                            if (slotW <= 0f) return@pointerInput
                            var totalDrag = 0f
                            detectHorizontalDragGestures(
                                onDragStart = { totalDrag = 0f; pill.startDrag() },
                                onDragCancel = { pill.release(currentSlot) },
                                onDragEnd = {
                                    val ratio = totalDrag / slotW
                                    val shift = when {
                                        ratio > 0.35f -> maxOf(1, ratio.roundToInt())
                                        ratio < -0.35f -> minOf(-1, ratio.roundToInt())
                                        else -> 0
                                    }
                                    var target = (currentSlot + shift).coerceIn(0, 4)
                                    if (target == CENTER_SLOT) target = if (shift > 0) 3 else 1   // never rest on ▶
                                    pill.release(target)
                                    if (target != currentSlot) currentOnSelect(tabOf(target))
                                },
                                onHorizontalDrag = { _, delta ->
                                    totalDrag += delta
                                    val dragOffset = when {
                                        totalDrag > 0 && currentSlot == 4 -> totalDrag * 0.25f   // rubber-band at the ends
                                        totalDrag < 0 && currentSlot == 0 -> totalDrag * 0.25f
                                        else -> totalDrag
                                    }
                                    pill.dragTo(currentSlot + dragOffset / slotW)
                                    val approx = (currentSlot + dragOffset / slotW).coerceIn(0f, 4f).roundToInt()
                                    if (approx != lastHapticSlot) {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        lastHapticSlot = approx
                                    }
                                },
                            )
                        },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    for (i in 0..4) {
                        if (i == CENTER_SLOT) {
                            Box(Modifier.weight(1f).fillMaxHeight().bounceClick(onClick = onReels), contentAlignment = Alignment.Center) {
                                Box(Modifier.size(50.dp).clip(CircleShape).background(AccentBrush), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Rounded.PlayArrow, "Play reels", tint = OnAccent, modifier = Modifier.size(30.dp))
                                }
                            }
                        } else {
                            val t = tabOf(i)
                            // On the glass dock the tint comes from the surface luminance (Musix's nav bar rule);
                            // the haze fallback keeps the theme accents.
                            val glassContent = glassContentColor()
                            val c = when {
                                glassOn && t == selected -> glassContent
                                glassOn -> glassContent.copy(alpha = 0.65f)
                                t == selected -> Violet
                                else -> onSurface.copy(alpha = 0.72f)
                            }
                            Box(Modifier.weight(1f).fillMaxHeight().bounceClick { onSelect(t) }, contentAlignment = Alignment.Center) {
                                Icon(
                                    tabs[t].icon, tabs[t].label, tint = c,
                                    modifier = Modifier.size(27.dp).graphicsLayer {
                                        val d = abs(i - pill.position)
                                        val sc = 1f + 0.16f * (1f - d).coerceIn(0f, 1f)   // icon under the pill grows a little
                                        scaleX = sc; scaleY = sc
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }

        // The lifted glass lens: drawn over the dock, free to stand past its edges. Always composed;
        // it skips drawing while the pill is flat.
        if (glassOn) {
            GlassSelectionPill(
                motion = pill,
                fillColor = glassIndicatorColor,
                barGlass = barGlass,
                tabRow = tabRow,
                modifier = Modifier.matchParentSize()
                    .drawWithContent { if (!pill.isFlat) drawContent() }
                    .pillPlacement(
                        center = { centerInRow(pill.position) + Offset(padPx, padPx) },
                        size = { pill.liveSize(restSize, liftedSize) },
                    ),
            )
        }
    }
}
