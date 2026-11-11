package com.reelvault.app

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.res.painterResource
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
 * Liquid-glass surface: Haze blur (API 31+) + frosted tint, then painted "liquid" layers:
 * a diagonal sheen, a specular rim light (bright top-left, accent bottom-right) and a faint
 * chromatic-dispersion edge (pink / cyan offset strokes) so the glass looks thick and wet.
 */
@Composable
fun Modifier.glass(shape: Shape = RoundedCornerShape(24.dp), strong: Boolean = false): Modifier {
    val dark = LocalDark.current
    val haze = LocalHaze.current
    val tint = if (dark) Color(0xFF1B1433).copy(alpha = if (strong) 0.55f else 0.34f)
    else Color.White.copy(alpha = if (strong) 0.62f else 0.42f)
    val base = if (haze != null)
        this.hazeChild(state = haze, shape = shape, style = HazeStyle(tint = HazeTint(tint), blurRadius = 20.dp, noiseFactor = 0.04f))
    else this.clip(shape).background(if (dark) tint.copy(alpha = tint.alpha + 0.2f) else tint)
    return base.drawWithContent {
        drawContent()
        val outline = shape.createOutline(size, layoutDirection, this)
        val w = size.width
        val h = size.height
        val rim = 1.4.dp.toPx()
        // sheen
        drawOutline(
            outline,
            Brush.linearGradient(
                0f to Color.White.copy(alpha = if (dark) 0.20f else 0.55f),
                0.45f to Color.Transparent,
                1f to Blue.copy(alpha = 0.10f),
                start = Offset(0f, 0f), end = Offset(w * 0.8f, h),
            ),
        )
        // chromatic dispersion
        translate(left = 0.9f, top = 0.9f) { drawOutline(outline, Pink.copy(alpha = 0.22f), style = Stroke(rim)) }
        translate(left = -0.9f, top = -0.9f) { drawOutline(outline, Color(0xFF38E8FF).copy(alpha = 0.22f), style = Stroke(rim)) }
        // specular rim
        drawOutline(
            outline,
            Brush.linearGradient(
                0f to Color.White.copy(alpha = if (dark) 0.85f else 1f),
                0.35f to Color.White.copy(alpha = 0.06f),
                0.65f to Color.White.copy(alpha = 0.04f),
                1f to Violet.copy(alpha = 0.75f),
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
    Image(painterResource(R.drawable.ic_logo_mark), "ReelVault", Modifier.size(size).clip(RoundedCornerShape(size * 0.28f)))
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
