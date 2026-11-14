/*
 * Liquid glass, ported from Harsh Musix (LiquidGlass.kt). The glass rendering itself is Kyant0/backdrop
 * (Apache-2.0), vendored in the `backdrop` package next to this file.
 *
 * Works in dark AND light theme: the glass is tinted near-white in light and near-black in dark, so nothing
 * turns into a black slab in light mode.
 */
package com.reelvault.app

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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

/** The recorded app content that liquid glass surfaces sample. Null => no glass (old phone / safe mode). */
val LocalAppBackdrop = staticCompositionLocalOf<Backdrop?> { null }

/**
 * Where a [liquidGlass] surface composed under it also records the glass it draws, so the dock's travelling
 * selection lens can refract the dock itself rather than only the page behind it.
 */
internal val LocalGlassExport = compositionLocalOf<LayerBackdrop?> { null }

/** The backdrop blur pipeline needs RenderEffect on a RenderNode: Android 12 (API 31)+. */
fun isGlassSupported(sdkInt: Int = Build.VERSION.SDK_INT): Boolean = sdkInt >= Build.VERSION_CODES.S && !SafeMode.on

/** True when a liquid glass surface can actually be drawn here. */
@Composable
fun glassAvailable(): Boolean = isGlassSupported() && LocalAppBackdrop.current != null

private const val VIBRANCY = 1f
private const val BLUR_RADIUS_DP = 8f
private const val LENS_HEIGHT = 0.5f
private const val LENS_AMOUNT = 0.5f
private const val LENS_MAX_DP = 48f
private const val SURFACE_OPACITY = 0.4f

/** Resolution fraction the glass records/processes its backdrop at (cheap; the blur hides the upscale). */
private const val GLASS_RESOLUTION_SCALE = 0.33f

internal val GLASS_EDGE_WIDTH = 0.5.dp

/** Hairline along a glass bar's edge, readable on both themes. */
internal val glassEdgeColor: Color
    get() = if (Mono.dark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.10f)

/** Glass body tint: light glass in light theme, dark glass in dark theme. */
internal val glassTintColor: Color
    get() = if (Mono.dark) Color(0xFF121212) else Color(0xFFFAFAFA)

/** Selected-pill fill on the glass dock: a soft white wash in dark theme, a soft black wash in light theme. */
internal val glassIndicatorColor: Color
    get() = if (Mono.dark) Color.White.copy(alpha = 0.16f) else Color.Black.copy(alpha = 0.10f)

/**
 * Renders this composable as a liquid glass surface sampling [LocalAppBackdrop]: vibrancy, blur and lens
 * refraction, then a theme-adaptive surface tint. Caller should check [glassAvailable] first.
 * [shape] must be a [CornerBasedShape] (the lens effect throws for other shapes).
 */
@Composable
fun Modifier.liquidGlass(shape: CornerBasedShape): Modifier {
    val backdrop = LocalAppBackdrop.current ?: return this
    if (!isGlassSupported()) return this
    val exported = LocalGlassExport.current
    val density = LocalDensity.current
    val blurPx = with(density) { BLUR_RADIUS_DP.dp.toPx() } * GLASS_RESOLUTION_SCALE
    val lensHeightPx = with(density) { (LENS_HEIGHT * LENS_MAX_DP).dp.toPx() } * GLASS_RESOLUTION_SCALE
    val lensAmountPx = with(density) { (LENS_AMOUNT * LENS_MAX_DP).dp.toPx() } * GLASS_RESOLUTION_SCALE
    val tint = glassTintColor

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
        onDrawSurface = { drawRect(color = tint.copy(alpha = SURFACE_OPACITY), size = size) },
        exportedBackdrop = exported,
        backdropScale = GLASS_RESOLUTION_SCALE,
    )
}

/** Cheap stand-in used where real glass is unavailable: same tint + hairline, no sampling. */
@Composable
fun Modifier.flatGlass(shape: CornerBasedShape): Modifier =
    this.background(glassTintColor.copy(alpha = if (Mono.dark) 0.62f else 0.78f), shape)
        .border(GLASS_EDGE_WIDTH, glassEdgeColor, shape)
