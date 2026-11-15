/*
 * Liquid-glass integration for the dock.
 *
 * The glass rendering itself is Kyant0/backdrop (Apache-2.0), vendored in
 * [com.reelvault.app.backdrop]. This file is the integration glue, adapted from
 * BitChord's LiquidGlass.kt (GPL-3.0, kushagrasinghx/BitChord), which in turn
 * adapts EchoMusicApp/Echo-Music's GlassEffectConfig (GPL-3.0). The per-app
 * settings were removed: glass is always on where the device supports it.
 */
package com.reelvault.app

import android.os.Build
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.reelvault.app.backdrop.Backdrop
import com.reelvault.app.backdrop.backdrops.LayerBackdrop
import com.reelvault.app.backdrop.drawBackdrop
import com.reelvault.app.backdrop.effects.blur
import com.reelvault.app.backdrop.effects.colorControls
import com.reelvault.app.backdrop.effects.lens
import com.reelvault.app.backdrop.highlight.Highlight
import com.reelvault.app.backdrop.shadow.Shadow

/** The backdrop content (app UI) that a liquid glass surface samples from. */
val LocalAppBackdrop = staticCompositionLocalOf<Backdrop> { error("No AppBackdrop provided") }

/** Where a glass surface also records the glass it draws, so the selection lens can refract it. */
internal val LocalGlassExport = compositionLocalOf<LayerBackdrop?> { null }

/** The backdrop blur pipeline needs RenderEffect on a RenderNode: Android 12 (API 31). */
fun isGlassSupported(sdkInt: Int = Build.VERSION.SDK_INT): Boolean = sdkInt >= Build.VERSION_CODES.S

/** The spring the selection pill and glyphs travel on. */
internal val GlassSpring = spring<Float>(dampingRatio = 0.72f, stiffness = 320f)

private const val VIBRANCY = 1f
private const val BLUR_RADIUS_DP = 8f
private const val LENS_HEIGHT = 0.5f
private const val LENS_AMOUNT = 0.5f
private const val LENS_MAX_DP = 48f
private const val SURFACE_OPACITY = 0.4f
private const val GLASS_RESOLUTION_SCALE = 0.33f

internal val GLASS_EDGE_WIDTH = 0.5.dp
internal val GLASS_EDGE_COLOR = Color.White.copy(alpha = 0.10f)

@Composable
fun glassContentColor(): Color =
    if (MaterialTheme.colorScheme.surface.luminance() > 0.5f) Color.Black else Color.White

@Composable
fun glassIndicatorColor(): Color =
    if (MaterialTheme.colorScheme.surface.luminance() > 0.5f) Color.White else Color.Black

/**
 * Renders this composable as a liquid glass surface sampling [LocalAppBackdrop]: vibrancy, blur and lens
 * refraction, then a theme-adaptive tint. [shape] must be a [CornerBasedShape] (the lens effect requires it).
 */
@Composable
fun Modifier.liquidGlass(shape: CornerBasedShape): Modifier {
    if (!isGlassSupported()) return this
    val backdrop = LocalAppBackdrop.current
    val exportedBackdrop = LocalGlassExport.current
    val density = LocalDensity.current
    val blurPx = with(density) { BLUR_RADIUS_DP.dp.toPx() } * GLASS_RESOLUTION_SCALE
    val lensHeightPx = with(density) { (LENS_HEIGHT * LENS_MAX_DP).dp.toPx() } * GLASS_RESOLUTION_SCALE
    val lensAmountPx = with(density) { (LENS_AMOUNT * LENS_MAX_DP).dp.toPx() } * GLASS_RESOLUTION_SCALE
    val surfaceTintColor = if (MaterialTheme.colorScheme.surface.luminance() > 0.5f) Color(0xFFFAFAFA) else Color(0xFF121212)

    return drawBackdrop(
        backdrop = backdrop,
        shape = { shape },
        effects = {
            colorControls(saturation = 1f + 0.5f * VIBRANCY)
            blur(blurPx)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                lens(
                    refractionHeight = lensHeightPx,
                    refractionAmount = lensAmountPx,
                    depthEffect = true,
                    chromaticAberration = true,
                )
            }
        },
        highlight = { Highlight.Default },
        shadow = { Shadow.Default },
        onDrawSurface = { drawRect(color = surfaceTintColor.copy(alpha = SURFACE_OPACITY), size = size) },
        exportedBackdrop = exportedBackdrop,
        backdropScale = GLASS_RESOLUTION_SCALE,
    )
}

// ---- dock sizing constants (from BitChord's FloatingBottomBar.kt) ----
data class BottomTab(val label: String, val icon: ImageVector)

internal val PILL_INSET = 6.dp
internal val TAB_VERTICAL_PADDING = 9.dp
internal val TAB_ICON_LABEL_GAP = 2.dp
internal val BAR_GUTTER = 16.dp
private val GESTURE_BAR_FLOOR = 15.dp

internal val floatingBarInsets: WindowInsets
    @Composable get() = WindowInsets.navigationBars.union(WindowInsets(bottom = GESTURE_BAR_FLOOR))
