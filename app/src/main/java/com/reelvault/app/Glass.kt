package com.reelvault.app

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeChild

/** The active haze source. Null => cheap translucent fallback (used over video / in dialogs). */
val LocalHaze = staticCompositionLocalOf<HazeState?> { null }

/**
 * Liquid-glass surface. With a Haze source: real background blur + light frosted tint.
 * Without one (over video / inside the blurred area): a white "glass" gradient fill.
 * On top: diagonal sheen, top-edge lens highlight, pink/cyan edge dispersion and a bright specular rim.
 */
@Composable
fun Modifier.glass(shape: Shape = RoundedCornerShape(24.dp), strong: Boolean = false): Modifier {
    val dark = LocalDark.current
    val haze = LocalHaze.current
    val fill = if (dark) Color.White.copy(alpha = if (strong) 0.16f else 0.10f)
    else Color.White.copy(alpha = if (strong) 0.62f else 0.45f)
    val base = if (haze != null)
        this.hazeChild(state = haze, shape = shape, style = HazeStyle(
            backgroundColor = if (dark) Color(0xFF07050F) else Color(0xFFF3F0FF),
            tint = HazeTint(fill), blurRadius = 28.dp, noiseFactor = 0.03f,
        ))
    else this.clip(shape)
        .background(if (dark) Color.Black.copy(alpha = 0.16f) else Color.Transparent)
        .background(Brush.linearGradient(listOf(fill.copy(alpha = fill.alpha * 1.7f), fill.copy(alpha = fill.alpha * 0.7f))))
    return base.drawWithContent {
        drawContent()
        val outline = shape.createOutline(size, layoutDirection, this)
        val w = size.width
        val h = size.height
        val rim = 1.6.dp.toPx()
        // diagonal sheen
        drawOutline(
            outline,
            Brush.linearGradient(
                0f to Color.White.copy(alpha = if (dark) 0.26f else 0.55f),
                0.40f to Color.Transparent,
                1f to Blue.copy(alpha = 0.14f),
                start = Offset(0f, 0f), end = Offset(w * 0.8f, h),
            ),
        )
        // top lens highlight (light pooling at the top edge)
        drawOutline(
            outline,
            Brush.verticalGradient(0f to Color.White.copy(alpha = if (dark) 0.22f else 0.5f), 0.28f to Color.Transparent, startY = 0f, endY = h),
        )
        // chromatic dispersion
        translate(left = 1.1f, top = 1.1f) { drawOutline(outline, Pink.copy(alpha = 0.30f), style = Stroke(rim)) }
        translate(left = -1.1f, top = -1.1f) { drawOutline(outline, Color(0xFF38E8FF).copy(alpha = 0.30f), style = Stroke(rim)) }
        // specular rim
        drawOutline(
            outline,
            Brush.linearGradient(
                0f to Color.White.copy(alpha = 0.95f),
                0.30f to Color.White.copy(alpha = 0.10f),
                0.70f to Color.White.copy(alpha = 0.06f),
                1f to Violet.copy(alpha = 0.85f),
                start = Offset(0f, 0f), end = Offset(w, h),
            ),
            style = Stroke(rim),
        )
    }
}

/** Jelly press: x and y springs differ, so the glass squishes and wobbles back like liquid. */
fun Modifier.bounceClick(enabled: Boolean = true, onClick: () -> Unit): Modifier = composed {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val sx by animateFloatAsState(
        if (pressed) 0.93f else 1f, spring(Spring.DampingRatioHighBouncy, Spring.StiffnessMedium), label = "sx"
    )
    val sy by animateFloatAsState(
        if (pressed) 0.88f else 1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow), label = "sy"
    )
    this.graphicsLayer { scaleX = sx; scaleY = sy }.clickable(src, null, enabled, onClick = onClick)
}

@Composable
fun AppBackground() {
    val dark = LocalDark.current
    val bg = MaterialTheme.colorScheme.background
    Box(
        Modifier.fillMaxSize().background(bg).drawBehind {
            val a = if (dark) 0.50f else 0.28f
            drawRect(Brush.radialGradient(listOf(Violet.copy(alpha = a), Color.Transparent),
                center = Offset(size.width * 0.05f, size.height * 0.04f), radius = size.width * 1.0f))
            drawRect(Brush.radialGradient(listOf(Blue.copy(alpha = a * 0.8f), Color.Transparent),
                center = Offset(size.width * 0.98f, size.height * 0.92f), radius = size.width * 1.1f))
        }
    )
}

@Composable
fun LogoMark(size: Dp) {
    Canvas(Modifier.size(size).clip(RoundedCornerShape(size * 0.28f))) {
        val w = this.size.width
        val k = w / 108f
        drawRect(Brush.linearGradient(listOf(Color(0xFF1E1250), Color(0xFF07050F)), Offset.Zero, Offset(w, w)))
        drawCircle(Violet.copy(alpha = 0.20f), 40f * k, Offset(54f * k, 54f * k))
        // vault-dial ring with a gap at 12 o'clock
        drawArc(
            Brush.linearGradient(listOf(Color(0xFFC4B5FD), Violet, Blue), Offset(20f * k, 20f * k), Offset(88f * k, 88f * k)),
            startAngle = -70f, sweepAngle = 320f, useCenter = false,
            topLeft = Offset(25f * k, 25f * k), size = Size(58f * k, 58f * k),
            style = Stroke(4.5f * k, cap = StrokeCap.Round),
        )
        drawCircle(Pink, 2.8f * k, Offset(54f * k, 22f * k))
        // V monogram = play button turned downward
        val v = Path().apply {
            moveTo(39f * k, 39.5f * k); lineTo(69f * k, 39.5f * k)
            quadraticBezierTo(72.6f * k, 39.5f * k, 70.8f * k, 42.6f * k)
            lineTo(56f * k, 69f * k)
            quadraticBezierTo(54f * k, 72.4f * k, 52f * k, 69f * k)
            lineTo(37.2f * k, 42.6f * k)
            quadraticBezierTo(35.4f * k, 39.5f * k, 39f * k, 39.5f * k); close()
        }
        drawPath(v, Brush.linearGradient(listOf(Color(0xFFDDD6FE), Violet, Blue), Offset(38f * k, 40f * k), Offset(70f * k, 72f * k)))
        val play = Path().apply { moveTo(48f * k, 42.5f * k); lineTo(48f * k, 54f * k); lineTo(59f * k, 48.2f * k); close() }
        drawPath(play, Color(0xFF0B0714))
    }
}

@Composable
fun GlassIconButton(
    icon: ImageVector, desc: String, onClick: () -> Unit, modifier: Modifier = Modifier,
    size: Dp = 44.dp, tint: Color = MaterialTheme.colorScheme.onSurface,
) {
    Box(
        modifier.bounceClick(onClick = onClick).size(size).glass(CircleShape),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, desc, tint = tint, modifier = Modifier.size(size * 0.5f)) }
}

@Composable
fun GlassChip(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null, selected: Boolean = false) {
    val shape = RoundedCornerShape(50)
    val content = if (selected) Color.White else MaterialTheme.colorScheme.onSurface
    Row(
        modifier.bounceClick(onClick = onClick).height(38.dp)
            .then(if (selected) Modifier.clip(shape).background(AccentBrush) else Modifier.glass(shape))
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (icon != null) Icon(icon, null, tint = content, modifier = Modifier.size(18.dp))
        Text(text, color = content, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

@Composable
fun GradientButton(text: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.bounceClick(onClick = onClick).height(48.dp).clip(RoundedCornerShape(16.dp)).background(AccentBrush)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(20.dp))
        Text(text, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    }
}

@Composable
fun GlassCard(modifier: Modifier = Modifier, padding: Dp = 16.dp, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().glass(RoundedCornerShape(24.dp)).padding(padding), content = content)
}
