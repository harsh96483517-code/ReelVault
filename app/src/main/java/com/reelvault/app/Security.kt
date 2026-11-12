package com.reelvault.app

import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.delay
import java.security.MessageDigest
import java.util.UUID
import kotlin.math.hypot

/** Pattern + fingerprint app lock. Kept in SharedPreferences so it is known before the UI draws. */
object AppLock {
    private lateinit var sp: SharedPreferences
    var locked by mutableStateOf(false)
    var vaultOpen by mutableStateOf(false)
    private var bypassUntil = 0L
    private var failCount = 0
    private var blockedUntil = 0L

    fun init(ctx: Context) {
        sp = ctx.getSharedPreferences("rv_lock", Context.MODE_PRIVATE)
        locked = isSet
    }

    val isSet: Boolean get() = sp.getString("hash", null) != null
    var fingerprint: Boolean
        get() = sp.getBoolean("fp", false)
        set(v) { sp.edit().putBoolean("fp", v).apply() }
    /** Milliseconds the app may stay in the background before it locks again (0 = instantly). */
    var timeoutMs: Long
        get() = sp.getLong("timeout", 0L)
        set(v) { sp.edit().putLong("timeout", v).apply() }

    private fun hash(p: String, salt: String) =
        MessageDigest.getInstance("SHA-256").digest((salt + p).toByteArray()).joinToString("") { "%02x".format(it) }

    fun set(pattern: String) {
        val salt = UUID.randomUUID().toString()
        sp.edit().putString("salt", salt).putString("hash", hash(pattern, salt)).apply()
    }

    fun clear() { sp.edit().clear().apply(); locked = false }

    /** Call right before opening a picker / share sheet / installer so coming back doesn't re-lock. */
    fun bypass() { bypassUntil = SystemClock.elapsedRealtime() + 120_000 }
    fun consumeBypass(): Boolean {
        val ok = SystemClock.elapsedRealtime() < bypassUntil
        bypassUntil = 0L
        return ok
    }

    sealed interface Result { data object Ok : Result; data object Wrong : Result; data class Blocked(val seconds: Int) : Result }

    fun verify(pattern: String): Result {
        val now = SystemClock.elapsedRealtime()
        if (now < blockedUntil) return Result.Blocked(((blockedUntil - now) / 1000 + 1).toInt())
        val h = sp.getString("hash", null) ?: return Result.Wrong
        if (h == hash(pattern, sp.getString("salt", "") ?: "")) { failCount = 0; return Result.Ok }
        failCount++
        if (failCount >= 5) { failCount = 0; blockedUntil = now + 30_000; return Result.Blocked(30) }
        return Result.Wrong
    }
}

fun canUseBiometric(ctx: Context): Boolean =
    BiometricManager.from(ctx).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS

fun promptBiometric(activity: FragmentActivity, title: String, onSuccess: () -> Unit) {
    val prompt = BiometricPrompt(
        activity, ContextCompat.getMainExecutor(activity),
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { onSuccess() }
        },
    )
    prompt.authenticate(
        BiometricPrompt.PromptInfo.Builder().setTitle(title).setSubtitle("Touch the fingerprint sensor")
            .setNegativeButtonText("Use pattern")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG).build()
    )
}

/** 3x3 pattern pad. Calls onComplete with the dot order, e.g. "01258". */
@Composable
fun PatternPad(modifier: Modifier = Modifier, error: Boolean = false, onComplete: (String) -> Unit) {
    val haptic = LocalHapticFeedback.current
    val ink = MaterialTheme.colorScheme.onSurface
    var size by remember { mutableStateOf(IntSize.Zero) }
    val path = remember { mutableStateListOf<Int>() }
    var pointer by remember { mutableStateOf<Offset?>(null) }
    val shake = remember { Animatable(0f) }
    LaunchedEffect(error) {
        if (error) { repeat(3) { shake.animateTo(16f, tween(45)); shake.animateTo(-16f, tween(45)) }; shake.animateTo(0f, tween(45)) }
    }

    fun center(i: Int): Offset { val cell = size.width / 3f; return Offset(cell * (i % 3 + 0.5f), cell * (i / 3 + 0.5f)) }
    fun hit(o: Offset) {
        val cell = size.width / 3f
        for (i in 0..8) {
            if (i in path) continue
            if (hypot(o.x - center(i).x, o.y - center(i).y) < cell * 0.30f) {
                if (path.isNotEmpty()) { // include the dot in the middle of a straight jump (0 -> 2 passes 1)
                    val l = path.last(); val r1 = l / 3; val c1 = l % 3; val r2 = i / 3; val c2 = i % 3
                    if ((r1 + r2) % 2 == 0 && (c1 + c2) % 2 == 0) {
                        val mid = ((r1 + r2) / 2) * 3 + (c1 + c2) / 2
                        if (mid !in path) path.add(mid)
                    }
                }
                path.add(i)
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                break
            }
        }
    }

    Canvas(
        modifier.aspectRatio(1f).onSizeChanged { size = it }.graphicsLayer { translationX = shake.value }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { path.clear(); pointer = it; hit(it) },
                    onDrag = { c, _ -> pointer = c.position; hit(c.position) },
                    onDragEnd = { val p = path.joinToString(""); pointer = null; path.clear(); if (p.isNotEmpty()) onComplete(p) },
                    onDragCancel = { pointer = null; path.clear() },
                )
            }
    ) {
        val lineColor = ink.copy(alpha = if (error) 0.35f else 0.85f)
        for (k in 0 until path.size - 1) drawLine(lineColor, center(path[k]), center(path[k + 1]), 5.dp.toPx(), StrokeCap.Round)
        if (path.isNotEmpty() && pointer != null) drawLine(lineColor.copy(alpha = 0.5f), center(path.last()), pointer!!, 5.dp.toPx(), StrokeCap.Round)
        for (i in 0..8) {
            val c = center(i)
            if (i in path) {
                drawCircle(ink.copy(alpha = 0.10f), 34.dp.toPx(), c)       // glow
                drawCircle(ink.copy(alpha = 0.22f), 24.dp.toPx(), c)
                drawCircle(ink, 9.dp.toPx(), c)
            } else {
                drawCircle(ink.copy(alpha = 0.35f), 7.dp.toPx(), c)
                drawCircle(ink.copy(alpha = 0.18f), 18.dp.toPx(), c, style = Stroke(1.5.dp.toPx()))
            }
        }
    }
}

/** Full-screen unlock: pattern pad + fingerprint button. Used at launch and before opening the vault. */
@Composable
fun UnlockPanel(title: String, subtitle: String, onSuccess: () -> Unit, onCancel: (() -> Unit)? = null) {
    val ctx = LocalContext.current
    val activity = ctx as? FragmentActivity
    val ink = MaterialTheme.colorScheme.onSurface
    var msg by remember { mutableStateOf(subtitle) }
    var error by remember { mutableStateOf(false) }
    val bio = AppLock.fingerprint && canUseBiometric(ctx)
    LaunchedEffect(Unit) { if (bio && activity != null) promptBiometric(activity, title, onSuccess) }
    LaunchedEffect(error) { if (error) { delay(900); error = false; msg = subtitle } }
    if (onCancel != null) BackHandler(onBack = onCancel)

    Box(Modifier.fillMaxSize()) {
        AppBackground()
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
        ) {
            LogoMark(64.dp)
            Spacer(Modifier.height(18.dp))
            Text(title, style = MaterialTheme.typography.titleLarge, color = ink)
            Text(msg, color = ink.copy(alpha = if (error) 1f else 0.65f), textAlign = TextAlign.Center,
                fontWeight = if (error) FontWeight.SemiBold else FontWeight.Normal, modifier = Modifier.padding(top = 6.dp, bottom = 28.dp))
            PatternPad(Modifier.fillMaxWidth(0.82f), error) { p ->
                when (val r = AppLock.verify(p)) {
                    AppLock.Result.Ok -> onSuccess()
                    AppLock.Result.Wrong -> { msg = "Wrong pattern"; error = true }
                    is AppLock.Result.Blocked -> { msg = "Too many tries. Wait ${r.seconds}s"; error = true }
                }
            }
            Spacer(Modifier.height(24.dp))
            if (bio && activity != null) GlassIconButton(Icons.Rounded.Fingerprint, "Fingerprint",
                { promptBiometric(activity, title, onSuccess) }, size = 60.dp)
            if (onCancel != null) TextButton(onCancel) { Text("Cancel", color = ink.copy(alpha = 0.7f)) }
        }
    }
}

/** Create a new pattern: draw, then draw again to confirm. */
@Composable
fun PatternSetup(onDone: () -> Unit, onCancel: () -> Unit) {
    val ink = MaterialTheme.colorScheme.onSurface
    var first by remember { mutableStateOf<String?>(null) }
    var msg by remember { mutableStateOf("Connect at least 4 dots") }
    var error by remember { mutableStateOf(false) }
    LaunchedEffect(error) { if (error) { delay(900); error = false } }
    BackHandler(onBack = onCancel)
    Box(Modifier.fillMaxSize()) {
        AppBackground()
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
        ) {
            LogoMark(64.dp)
            Spacer(Modifier.height(18.dp))
            Text(if (first == null) "Create pattern" else "Confirm pattern", style = MaterialTheme.typography.titleLarge, color = ink)
            Text(msg, color = ink.copy(alpha = 0.65f), textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp, bottom = 28.dp))
            PatternPad(Modifier.fillMaxWidth(0.82f), error) { p ->
                val f = first
                when {
                    p.length < 4 -> { msg = "Too short. Connect at least 4 dots"; error = true }
                    f == null -> { first = p; msg = "Draw it again to confirm" }
                    f == p -> { AppLock.set(p); onDone() }
                    else -> { first = null; msg = "Didn't match. Start again"; error = true }
                }
            }
            Spacer(Modifier.height(24.dp))
            TextButton(onCancel) { Text("Cancel", color = ink.copy(alpha = 0.7f)) }
        }
    }
}

/** Hidden videos. Opened only after the pattern/fingerprint check. Nothing here shows anywhere else in the app. */
@Composable
fun VaultScreen(vm: MainVm, onBack: () -> Unit, onPlay: (PlayRequest) -> Unit) {
    val hidden by vm.hidden.collectAsState()
    val ink = MaterialTheme.colorScheme.onSurface
    val selected = remember { mutableStateListOf<String>() }
    val grid = rememberLazyGridState()
    BackHandler { if (selected.isNotEmpty()) selected.clear() else onBack() }
    fun toggle(u: String) { if (u in selected) selected.remove(u) else selected.add(u) }

    Box(Modifier.fillMaxSize()) {
        AppBackground()
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth().height(60.dp)
                    .glass(RoundedCornerShape(30.dp)).padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                GlassIconButton(Icons.Rounded.ArrowBack, "Back", { if (selected.isNotEmpty()) selected.clear() else onBack() }, size = 42.dp)
                Column(Modifier.weight(1f)) {
                    Text(if (selected.isEmpty()) "Hidden vault" else "${selected.size} selected",
                        style = MaterialTheme.typography.titleMedium, color = ink)
                    Text("${hidden.size} hidden", style = MaterialTheme.typography.bodySmall, color = ink.copy(alpha = 0.6f))
                }
                if (hidden.isNotEmpty()) GlassIconButton(Icons.Rounded.SelectAll, "Select all",
                    { selected.clear(); selected.addAll(hidden.map { it.uri }) }, size = 40.dp)
            }
            if (hidden.isEmpty()) EmptyState(Icons.Rounded.VisibilityOff, "Nothing hidden",
                "Long-press videos on Home, then tap the hide button. They disappear everywhere and live here.")
            else Box(Modifier.weight(1f)) {
                VideoGrid(hidden, grid, if (selected.isEmpty()) 24.dp else 110.dp, selected, { toggle(it) }) { i ->
                    if (selected.isNotEmpty()) toggle(hidden[i].uri) else onPlay(PlayRequest(hidden.map { it.uri }, i))
                }
                if (selected.isNotEmpty()) GradientButton(
                    "Unhide ${selected.size}", Icons.Rounded.Visibility,
                    { vm.unhide(selected.toList()); selected.clear() },
                    Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 18.dp),
                )
            }
        }
    }
}
