/*
 * The bar's structure and its inline/expanded behaviour are EchoMusicApp/Echo-Music's AppFloatingNavBar
 * (GPL-3.0), as adapted by BitChord's GlassNavBar (GPL-3.0, kushagrasinghx/BitChord), over the
 * FloatingTabBar vendored in [com.reelvault.app.floatingtabbar] (compose-floating-tab-bar by Elyes Mansour,
 * Apache-2.0). The now-playing accessory was removed; the last tab is the standalone circle.
 */
@file:OptIn(ExperimentalSharedTransitionApi::class)

package com.reelvault.app

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.reelvault.app.floatingtabbar.FloatingTabBar
import com.reelvault.app.floatingtabbar.FloatingTabBarDefaults
import com.reelvault.app.floatingtabbar.FloatingTabBarScrollConnection

/**
 * The liquid-glass navigation bar: a pill of tabs plus a separate circular tab (iOS 26 shape). Scrolling down
 * folds the tabs to just the selected one; scrolling up expands them. Dragging the selection pill picks up a
 * glass lens that refracts the page behind it.
 *
 * [tabs] are the grouped tabs, with the LAST one shown as the standalone circle. Selecting the standalone one
 * should not change [selectedIndex] (ReelVault uses it as the "play reels" action).
 */
@Composable
fun GlassNavBar(
    tabs: List<BottomTab>,
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit,
    scrollConnection: FloatingTabBarScrollConnection,
    modifier: Modifier = Modifier,
) {
    val pillShape = remember { RoundedCornerShape(percent = 50) }
    val contentColor = glassContentColor()
    val selectedColor = contentColor
    val unselectedColor = contentColor.copy(alpha = 0.65f)
    val haptics = rememberHaptics()
    val currentOnTabSelected by rememberUpdatedState(onTabSelected)
    val standaloneIndex = tabs.lastIndex
    val glassSurface: @Composable () -> Modifier = { Modifier.liquidGlass(shape = pillShape) }

    FloatingTabBar(
        selectedTabKey = selectedIndex,
        scrollConnection = scrollConnection,
        modifier = modifier
            .windowInsetsPadding(floatingBarInsets)
            .padding(horizontal = BAR_GUTTER)
            .padding(bottom = 2.dp)
            .fillMaxWidth(),
        tabBarContentModifier = glassSurface,
        colors = FloatingTabBarDefaults.colors(
            backgroundColor = Color.Transparent,
            accessoryBackgroundColor = Color.Transparent,
            indicatorColor = glassIndicatorColor().copy(alpha = 0.5f),
        ),
        elevations = FloatingTabBarDefaults.elevations(inlineElevation = 0.dp, expandedElevation = 0.dp),
        sizes = FloatingTabBarDefaults.sizes(
            tabBarContentPadding = PaddingValues(PILL_INSET),
            tabExpandedContentPadding = PaddingValues(vertical = TAB_VERTICAL_PADDING),
        ),
        contentKey = remember(selectedIndex, tabs, contentColor) { listOf(selectedIndex, tabs, contentColor) },
    ) {
        tabs.forEachIndexed { index, tab ->
            val isSelected = index == selectedIndex
            val tint = if (isSelected) selectedColor else unselectedColor
            val onClick = {
                if (!isSelected) haptics.play(Haptic.Select)
                currentOnTabSelected(index)
            }
            if (index == standaloneIndex) {
                standaloneTab(
                    key = index,
                    icon = { Icon(tab.icon, tab.label, tint = tint, modifier = Modifier.size(25.dp)) },
                    onClick = onClick,
                )
            } else {
                tab(
                    key = index,
                    icon = { Icon(tab.icon, tab.label, tint = tint, modifier = Modifier.size(25.dp)) },
                    title = {
                        Text(
                            text = tab.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = tint,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = TAB_ICON_LABEL_GAP),
                        )
                    },
                    onClick = onClick,
                )
            }
        }
    }
}
