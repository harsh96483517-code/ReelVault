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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
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

/** Liquid-glass surface: real blur via Haze (API 31+), frosted tint, specular border. */
@Composable
fun Modifier.glass(shape: Shape = RoundedCornerShape(24.dp), strong: Boolean = false): Modifier {
    val dark = LocalDark.current
    val haze = LocalHaze.current
    val tint = if (dark) Color(0xFF1B1433).copy(alpha = if (strong) 0.55f else 0.34f)
    else Color.White.copy(alpha = if (strong) 0.62f else 0.42f)
    val border = Brush.linearGradient(
        listOf(Color.White.copy(alpha = if (dark) 0.38f else 0.95f), Color.White.copy(alpha = 0.05f), Violet.copy(alpha = 0.40f))
    )
    val base = if (haze != null)
        this.hazeChild(state = haze, shape = shape, style = HazeStyle(tint = HazeTint(tint), blurRadius = 20.dp, noiseFactor = 0.04f))
    else this.clip(shape).background(if (dark) tint.copy(alpha = tint.alpha + 0.2f) else tint)
    return base.border(BorderStroke(1.dp, border), shape)
}

/** Spring press animation + click. Put it FIRST in the chain so everything after it scales. */
fun Modifier.bounceClick(enabled: Boolean = true, onClick: () -> Unit): Modifier = composed {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val s by animateFloatAsState(
        if (pressed) 0.90f else 1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium), label = "press"
    )
    this.scale(s).clickable(src, null, enabled, onClick = onClick)
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
    Box(Modifier.size(size).clip(RoundedCornerShape(size * 0.3f)).background(AccentBrush), contentAlignment = Alignment.Center) {
        Icon(Icons.Rounded.PlayArrow, null, tint = Color.White, modifier = Modifier.size(size * 0.65f))
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
