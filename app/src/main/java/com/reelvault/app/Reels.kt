package com.reelvault.app

import android.content.ClipData
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Color as AColor
import androidx.activity.compose.BackHandler
import androidx.activity.ComponentActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

private class PlayerState(val player: ExoPlayer) {
    var pos by mutableLongStateOf(0L)
    var dur by mutableLongStateOf(0L)
    var playing by mutableStateOf(true)
    var paused by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var speed by mutableFloatStateOf(1f)
    var volume by mutableFloatStateOf(1f)
    var loop by mutableStateOf(true)
    var full by mutableStateOf(false)
    var showVol by mutableStateOf(false)
    var fill by mutableStateOf(true)      // fill the screen (zoom) for portrait videos
    var portrait by mutableStateOf(true)
    var onEnded: () -> Unit = {}

    fun toggle() {
        if (player.isPlaying) { player.pause(); paused = true } else { player.play(); paused = false }
    }
}

@OptIn(ExperimentalFoundationApi::class, UnstableApi::class)
@Composable
fun ReelsScreen(vm: MainVm, req: PlayRequest, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val activity = ctx as ComponentActivity
    val view = LocalView.current
    val prefs by vm.prefs.collectAsState()
    val all by vm.all.collectAsState()
    val byUri = remember(all) { all.associateBy { it.uri } }
    val uris = req.uris
    if (uris.isEmpty()) { LaunchedEffect(Unit) { onClose() }; return }
    val scope = rememberCoroutineScope()

    val player = remember {
        ExoPlayer.Builder(ctx)
            .setLoadControl(DefaultLoadControl.Builder().setBufferDurationsMs(2000, 8000, 500, 1000).build())
            .setHandleAudioBecomingNoisy(true)
            .build().apply {
                setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), true)
            }
    }
    val st = remember { PlayerState(player) }
    val startPage = req.start.coerceIn(0, uris.lastIndex)
    val pager = rememberPagerState(initialPage = startPage) { uris.size }
    var cur by remember { mutableIntStateOf(startPage) }

    fun resumePos(uri: String): Long {
        val e = byUri[uri] ?: return 0L
        return if (prefs.remember && e.positionMs > 2000 && e.positionMs < e.durationMs - 3000) e.positionMs else 0L
    }

    // Player setup / teardown + immersive mode
    DisposableEffect(Unit) {
        st.speed = prefs.speed
        st.loop = prefs.loop && !req.autoNext
        player.repeatMode = if (st.loop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
        player.setMediaItems(uris.map { MediaItem.fromUri(it) }, startPage, resumePos(uris[startPage]))
        player.setPlaybackSpeed(st.speed)
        player.prepare()
        player.playWhenReady = true
        vm.setLast(uris[startPage])

        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { st.playing = isPlaying }
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) st.portrait = videoSize.width <= videoSize.height
            }
            override fun onPlayerError(error: PlaybackException) { st.error = "This video can't be played on this device." }
            override fun onPlaybackStateChanged(state: Int) { if (state == Player.STATE_ENDED) st.onEnded() }
        }
        player.addListener(listener)

        val controller = WindowCompat.getInsetsController(activity.window, view)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            vm.savePosition(uris[cur], player.currentPosition, player.duration)
            player.removeListener(listener)
            player.release()
            controller.show(WindowInsetsCompat.Type.systemBars())
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    st.onEnded = { if (cur + 1 < uris.size) scope.launch { pager.animateScrollToPage(cur + 1) } }

    // Swipe navigation
    LaunchedEffect(pager) {
        snapshotFlow { pager.settledPage }.drop(1).collect { page ->
            if (page != cur) {
                vm.savePosition(uris[cur], player.currentPosition, player.duration)
                cur = page
                st.error = null
                st.paused = false
                player.seekTo(page, resumePos(uris[page]))
                if (player.playbackState == Player.STATE_IDLE) player.prepare()
                player.play()
                vm.setLast(uris[page])
            }
        }
    }
    // Progress ticker
    LaunchedEffect(player) {
        while (true) {
            st.pos = player.currentPosition
            st.dur = player.duration.takeIf { it > 0 } ?: 0L
            delay(if (st.playing) 250 else 600)
        }
    }
    LaunchedEffect(st.full) {
        activity.requestedOrientation =
            if (st.full) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }
    BackHandler { if (st.full) st.full = false else onClose() }

    CompositionLocalProvider(LocalHaze provides null) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            VerticalPager(pager, Modifier.fillMaxSize(), beyondViewportPageCount = 0, key = { uris[it] }) { page ->
                ReelPage(uris[page], byUri[uris[page]], page == pager.settledPage, st, vm, prefs, onClose)
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun ReelPage(
    uri: String, e: VideoEntity?, isCur: Boolean, st: PlayerState, vm: MainVm, prefs: Prefs, onBack: () -> Unit,
) {
    val ctx = LocalContext.current
    var heart by remember { mutableStateOf(false) }
    var speedMenu by remember { mutableStateOf(false) }
    var moreMenu by remember { mutableStateOf(false) }
    var info by remember { mutableStateOf(false) }
    var editCaption by remember { mutableStateOf(false) }
    var seeking by remember { mutableStateOf(false) }
    var seekV by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(heart) { if (heart) { delay(650); heart = false } }

    Box(
        Modifier.fillMaxSize().pointerInput(isCur, uri) {
            detectTapGestures(
                onTap = { if (isCur) st.toggle() },
                onDoubleTap = { vm.like(uri); heart = true },
            )
        }
    ) {
        Thumb(uri, Modifier.fillMaxSize(), if (st.fill && (!isCur || st.portrait)) ContentScale.Crop else ContentScale.Fit)
        if (isCur) AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { c ->
                PlayerView(c).apply {
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    setShutterBackgroundColor(AColor.TRANSPARENT)
                    setKeepContentOnPlayerReset(true)
                }
            },
            update = {
                it.player = st.player
                it.resizeMode = if (st.fill && st.portrait) AspectRatioFrameLayout.RESIZE_MODE_ZOOM else AspectRatioFrameLayout.RESIZE_MODE_FIT
            },
            onRelease = { it.player = null },
        )

        // scrims
        Box(Modifier.fillMaxWidth().height(170.dp).align(Alignment.TopCenter)
            .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent))))
        Box(Modifier.fillMaxWidth().height(340.dp).align(Alignment.BottomCenter)
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.82f)))))

        if (isCur && st.paused && st.error == null) Box(
            Modifier.align(Alignment.Center).size(76.dp).glass(CircleShape, strong = true), contentAlignment = Alignment.Center
        ) { Icon(Icons.Rounded.PlayArrow, "Play", tint = Color.White, modifier = Modifier.size(42.dp)) }

        if (isCur) st.error?.let { msg ->
            Column(Modifier.align(Alignment.Center).padding(32.dp).glass(RoundedCornerShape(24.dp), true).padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Rounded.ErrorOutline, null, tint = Pink, modifier = Modifier.size(36.dp))
                Text(msg, color = Color.White, fontSize = 14.sp)
                Text("Swipe up for the next video.", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
            }
        }

        AnimatedVisibility(
            heart, Modifier.align(Alignment.Center),
            enter = scaleIn(spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow)) + fadeIn(),
            exit = fadeOut() + scaleOut(),
        ) { Icon(Icons.Rounded.Favorite, null, tint = Pink, modifier = Modifier.size(120.dp)) }

        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = 14.dp, vertical = 10.dp)) {
            // Top bar
            Row(Modifier.align(Alignment.TopCenter).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GlassIconButton(Icons.Rounded.ArrowBack, "Back", onBack, size = 42.dp, tint = Color.White)
                Text("ReelVault", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.weight(1f))
                Box {
                    GlassChip("${st.speed}x".replace(".0x", "x"), { speedMenu = true }, icon = Icons.Rounded.Speed)
                    DropdownMenu(speedMenu, { speedMenu = false }) {
                        listOf(0.5f, 1f, 1.5f, 2f).forEach { s ->
                            DropdownMenuItem(
                                text = { Text("${s}x".replace(".0x", "x")) },
                                onClick = { st.speed = s; st.player.setPlaybackSpeed(s); speedMenu = false },
                            )
                        }
                    }
                }
                GlassIconButton(if (st.volume == 0f) Icons.Rounded.VolumeOff else Icons.Rounded.VolumeUp, "Volume",
                    { st.showVol = !st.showVol }, size = 42.dp, tint = Color.White)
            }
            if (isCur) AnimatedVisibility(st.showVol, Modifier.align(Alignment.TopEnd).padding(top = 54.dp), enter = fadeIn(), exit = fadeOut()) {
                Row(Modifier.width(230.dp).glass(RoundedCornerShape(24.dp), true).padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.VolumeUp, null, tint = Color.White, modifier = Modifier.size(20.dp))
                    Slider(st.volume, { st.volume = it; st.player.volume = it }, Modifier.weight(1f),
                        colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Violet, inactiveTrackColor = Color.White.copy(alpha = 0.3f)))
                }
            }

            // Bottom block: info + action rail side by side, glass controls underneath (nothing overlaps)
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                    Column(Modifier.weight(1f).padding(end = 10.dp, bottom = 2.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Avatar(prefs.avatar, prefs.name, 38.dp)
                            Text(prefs.handle.ifBlank { prefs.name.ifBlank { "reelvault" } }, color = Color.White,
                                fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Text(e?.name ?: "", color = Color.White, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (!e?.caption.isNullOrBlank()) Text(e!!.caption, color = Color.White.copy(alpha = 0.9f), maxLines = 2,
                            overflow = TextOverflow.Ellipsis, fontSize = 14.sp)
                    }
                    // Right action rail
                    Column(horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        RailButton(if (e?.liked == true) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, "Like",
                            if (e?.liked == true) Pink else Color.White) { vm.toggleLike(uri) }
                        RailButton(if (e?.favorite == true) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder, "Save",
                            if (e?.favorite == true) Color(0xFFB794FF) else Color.White) { vm.toggleFavorite(uri) }
                        RailButton(Icons.Rounded.Share, "Share", Color.White) {
                            val i = Intent(Intent.ACTION_SEND).apply {
                                type = "video/*"; putExtra(Intent.EXTRA_STREAM, uri.toUri())
                                clipData = ClipData.newRawUri("", uri.toUri()); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            ctx.startActivity(Intent.createChooser(i, "Share video"))
                        }
                        Box {
                            RailButton(Icons.Rounded.MoreVert, "More", Color.White) { moreMenu = true }
                            DropdownMenu(moreMenu, { moreMenu = false }) {
                                DropdownMenuItem({ Text("Edit caption") }, { moreMenu = false; editCaption = true }, leadingIcon = { Icon(Icons.Rounded.Edit, null) })
                                DropdownMenuItem({ Text("Video info") }, { moreMenu = false; info = true }, leadingIcon = { Icon(Icons.Rounded.Info, null) })
                                DropdownMenuItem({ Text("Open with…") }, {
                                    moreMenu = false
                                    val i = Intent(Intent.ACTION_VIEW).apply { setDataAndType(uri.toUri(), "video/*"); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                                    runCatching { ctx.startActivity(Intent.createChooser(i, "Open with")) }
                                }, leadingIcon = { Icon(Icons.Rounded.OpenInNew, null) })
                                DropdownMenuItem({ Text(if (st.fill) "Fill screen: on" else "Fill screen: off") }, {
                                    st.fill = !st.fill; moreMenu = false
                                }, leadingIcon = { Icon(Icons.Rounded.Fullscreen, null) })
                                DropdownMenuItem({ Text(if (st.loop) "Loop: on" else "Loop: off") }, {
                                    st.loop = !st.loop
                                    st.player.repeatMode = if (st.loop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
                                    moreMenu = false
                                }, leadingIcon = { Icon(Icons.Rounded.Repeat, null) })
                            }
                        }
                    }

                }
                Row(
                    Modifier.fillMaxWidth().glass(RoundedCornerShape(28.dp), true).padding(horizontal = 6.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.bounceClick { if (isCur) st.toggle() }.size(40.dp), contentAlignment = Alignment.Center) {
                        Icon(if (st.paused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause, "Play/pause", tint = Color.White)
                    }
                    val frac = if (isCur && st.dur > 0) (st.pos.toFloat() / st.dur).coerceIn(0f, 1f) else 0f
                    SeekBar(
                        value = if (seeking) seekV else frac,
                        onChange = { seeking = true; seekV = it },
                        onDone = { if (isCur) st.player.seekTo((seekV * st.dur).toLong()); seeking = false },
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                    )
                    Text("${fmtDur(if (isCur) st.pos else 0)} / ${fmtDur(if (isCur) st.dur else e?.durationMs ?: 0)}",
                        color = Color.White, fontSize = 11.sp, maxLines = 1)
                    Box(Modifier.bounceClick { st.full = !st.full }.size(40.dp), contentAlignment = Alignment.Center) {
                        Icon(if (st.full) Icons.Rounded.FullscreenExit else Icons.Rounded.Fullscreen, "Full screen", tint = Color.White)
                    }
                }
            }
        }
    }

    if (info && e != null) AlertDialog(
        onDismissRequest = { info = false }, confirmButton = { TextButton({ info = false }) { Text("Close") } },
        title = { Text("Video info") },
        text = {
            Text("Name: ${e.name}\nFolder: ${e.folder}\nDuration: ${fmtDur(e.durationMs)}\nSize: ${fmtSize(e.size)}\nAdded: ${fmtDate(e.dateAdded)}")
        },
    )
    if (editCaption && e != null) {
        var text by remember { mutableStateOf(e.caption) }
        AlertDialog(
            onDismissRequest = { editCaption = false },
            title = { Text("Caption") },
            text = { OutlinedTextField(text, { text = it.take(200) }, maxLines = 4, placeholder = { Text("Good vibes only #reels") }) },
            confirmButton = { TextButton({ vm.setCaption(uri, text); editCaption = false }) { Text("Save") } },
            dismissButton = { TextButton({ editCaption = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun RailButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, tint: Color, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        GlassIconButton(icon, label, onClick, size = 50.dp, tint = tint)
        Text(label, color = Color.White, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
    }
}

/** Slim seek bar: thin track, gradient progress, small thumb (replaces the chunky Material slider). */
@Composable
private fun SeekBar(value: Float, onChange: (Float) -> Unit, onDone: () -> Unit, modifier: Modifier = Modifier) {
    var w by remember { mutableFloatStateOf(1f) }
    var dragging by remember { mutableStateOf(false) }
    Canvas(
        modifier.height(28.dp).onSizeChanged { w = it.width.toFloat().coerceAtLeast(1f) }
            .pointerInput(Unit) { detectTapGestures { o -> onChange((o.x / w).coerceIn(0f, 1f)); onDone() } }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { dragging = true; onChange((it.x / w).coerceIn(0f, 1f)) },
                    onDragEnd = { dragging = false; onDone() },
                    onDragCancel = { dragging = false; onDone() },
                ) { change, _ -> onChange((change.position.x / w).coerceIn(0f, 1f)) }
            }
    ) {
        val cy = size.height / 2
        val th = 3.dp.toPx()
        val x = value.coerceIn(0f, 1f) * size.width
        drawRoundRect(Color.White.copy(alpha = 0.30f), Offset(0f, cy - th / 2), Size(size.width, th), CornerRadius(th))
        drawRoundRect(Brush.horizontalGradient(listOf(Violet, Blue)), Offset(0f, cy - th / 2), Size(x.coerceAtLeast(th), th), CornerRadius(th))
        drawCircle(Color.White, if (dragging) 8.dp.toPx() else 5.5f.dp.toPx(), Offset(x, cy))
    }
}
