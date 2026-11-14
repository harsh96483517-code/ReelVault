package com.reelvault.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * A popup drawn as a liquid-glass card instead of a flat Material surface.
 *
 * Dialogs live in their own window, so the page backdrop cannot be sampled here (a Haze child in
 * another window would blur that window, not the app behind it). The glass look therefore comes from
 * [glass]: the same tint, diagonal sheen, inner edge and specular rim the buttons use, with Haze
 * switched off through the local so it takes the painted branch rather than the blurred one.
 */
@Composable
fun GlassDialog(
    onDismiss: () -> Unit,
    buttons: @Composable RowScope.() -> Unit,
    modifier: Modifier = Modifier,
    title: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = remember { RoundedCornerShape(28.dp) }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        CompositionLocalProvider(LocalHaze provides null) {
            Column(
                modifier.fillMaxWidth().padding(horizontal = 24.dp).glass(shape, strong = true).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                title?.invoke()
                content()
                Row(
                    Modifier.fillMaxWidth().padding(top = 2.dp),
                    horizontalArrangement = Arrangement.End,
                    content = buttons,
                )
            }
        }
    }
}
