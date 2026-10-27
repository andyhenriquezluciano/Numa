/*
 * Copyright (C) 2026 SFG545
 *
 * This file is part of Numa.
 *
 * Numa is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Affero General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option) any
 * later version.
 *
 * Numa is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU Affero General Public License for more
 * details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Numa. If not, see <https://www.gnu.org/licenses/>.
 */

package dev.sfg.numa.mobile.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import dev.sfg.numa.mobile.model.GlassBorders
import dev.sfg.numa.mobile.model.GlassTheme
import dev.sfg.numa.mobile.ui.theme.RosePineColors
import dev.sfg.numa.mobile.ui.glass.GlassTone
import dev.sfg.numa.mobile.ui.glass.LocalGlass
import dev.sfg.numa.mobile.ui.glass.glassPane
import dev.sfg.numa.mobile.ui.motion.popOnChange
import dev.sfg.numa.mobile.ui.navigation.Routes
import dev.sfg.numa.mobile.ui.theme.CanopyColors
import dev.sfg.numa.mobile.ui.theme.LocalAccent

data class BottomDestination(val route: String, val label: String, val icon: ImageVector)

private val destinations = listOf(
    BottomDestination(Routes.HOME, "Inicio", Icons.Rounded.Home),
    BottomDestination(Routes.LIBRARY, "Biblioteca", Icons.Rounded.LibraryMusic),
    BottomDestination(Routes.ROOMS, "Salas", Icons.Rounded.Groups),
)

private val settingsDestination = BottomDestination(Routes.SETTINGS, "Ajustes", Icons.Rounded.Person)

/**
 * Height the mini player and nav bar occupy together. Screens reserve this much bottom content
 * padding so their last row can still be scrolled clear of the floating chrome.
 */
val NumaChromeHeight = 122.dp

private val BottomBarShape = RoundedCornerShape(24.dp)
/** Bottom navigation bar: a frosted floating pane, inset from the sides and fully rounded. */
@Composable
fun NumaBottomBar(currentRoute: String?, onSelect: (String) -> Unit, showSettings: Boolean = false) {
    val glassStyle = LocalGlass.current
    val glassTint = glassStyle.tint
    val theme = glassStyle.theme
    val showBorders = glassStyle.borders != GlassBorders.LIMPIO
    val isRosePine = theme == GlassTheme.ROSE_PINE
    val isDarkGlass = theme == GlassTheme.CRISTAL_OSCURO
    // 0 to 1 as the Settings tab grows in and out of the row.
    val settingsReveal by animateFloatAsState(
        targetValue = if (showSettings) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 300f),
        label = "BottomBarSettingsReveal",
    )
    val settingsVisible = showSettings || settingsReveal > 0.001f
    val allDestinations = if (settingsVisible) destinations + settingsDestination else destinations
    val selectedIndex = remember(currentRoute, allDestinations) {
        allDestinations.indexOfFirst { it.route == currentRoute }.takeIf { it != -1 }
            ?: if (currentRoute == Routes.SETTINGS_HOME_LAYOUT && settingsVisible) allDestinations.lastIndex else null
    }
    var lastKnownIndex by remember { mutableIntStateOf(selectedIndex ?: 0) }
    LaunchedEffect(selectedIndex) {
        if (selectedIndex != null) {
            lastKnownIndex = selectedIndex
        }
    }
    val activeIndex = selectedIndex ?: lastKnownIndex

    val tintColor = if (glassTint != Color.Unspecified && glassTint.alpha > 0f) glassTint else LocalAccent.current

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .then(
                if (isRosePine) {
                    Modifier
                        .clip(BottomBarShape)
                        .background(RosePineColors.Surface, BottomBarShape)
                        .then(if (showBorders) Modifier.border(1.dp, RosePineColors.Rose, BottomBarShape) else Modifier)
                } else {
                    Modifier
                        .clip(BottomBarShape)
                        .background(
                            if (isDarkGlass) Color(0xD80C0E11) else Color(0x9914171A),
                            BottomBarShape,
                        )
                        .glassPane(BottomBarShape, GlassTone.CHROME)
                        .then(
                            if (showBorders) {
                                Modifier.border(
                                    1.dp,
                                    if (isDarkGlass) Color(0x28FFFFFF) else Color(0x30FFFFFF),
                                    BottomBarShape,
                                )
                            } else Modifier
                        )
                }
            ),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
        ) {
            val slotWidth = maxWidth / (destinations.size + settingsReveal)
            val indicatorWidth = 28.dp
            val indicatorHeight = 3.dp
            val targetOffset = (slotWidth * (activeIndex.coerceAtMost(allDestinations.lastIndex) + 0.5f)) - (indicatorWidth / 2)

            val animatedOffset by animateDpAsState(
                targetValue = targetOffset,
                animationSpec = spring(
                    dampingRatio = 0.78f,
                    stiffness = 380f,
                ),
                label = "BottomBarIndicatorX",
            )
            val indicatorAlpha by animateFloatAsState(
                targetValue = if (selectedIndex != null) 1f else 0f,
                animationSpec = tween(180),
                label = "BottomBarIndicatorAlpha",
            )

            if (indicatorAlpha > 0.001f) {
                val indicatorColor = if (isRosePine) RosePineColors.Rose else tintColor

                Box(
                    modifier = Modifier
                        .offset { IntOffset(animatedOffset.roundToPx(), 59.dp.roundToPx()) }
                        .size(width = indicatorWidth, height = indicatorHeight)
                        .alpha(indicatorAlpha)
                        .background(
                            color = indicatorColor,
                            shape = RoundedCornerShape(16.dp),
                        ),
                )
            }

            NavigationBar(
                containerColor = Color.Transparent,
                tonalElevation = 0.dp,
                // The Scaffold already insets the whole chrome column above the system navigation bar.
                // Letting the bar apply that inset a second time would eat into its fixed 64dp height,
                // which squashed the icons and labels under 3-button navigation.
                windowInsets = WindowInsets(0),
                modifier = Modifier.height(64.dp),
            ) {
                allDestinations.forEach { destination ->
                    val isSettings = destination === settingsDestination
                    val isSelected = selectedIndex == allDestinations.indexOf(destination)
                    val item: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {
                        NavigationBarItem(
                            selected = isSelected,
                            onClick = { onSelect(destination.route) },
                            icon = {
                                val iconScale by animateFloatAsState(
                                    targetValue = if (isSelected) 1.08f else 1.0f,
                                    animationSpec = spring(dampingRatio = 0.65f, stiffness = 400f),
                                    label = "NavIconScale",
                                )
                                Icon(
                                    destination.icon,
                                    contentDescription = destination.label,
                                    modifier = Modifier
                                        .size(20.dp)
                                        .scale(iconScale)
                                        .popOnChange(isSelected, peak = 1.35f, onlyOn = true),
                                )
                            },
                            label = {
                                Text(
                                    destination.label,
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = if (isRosePine) RosePineColors.Rose else tintColor,
                                selectedTextColor = if (isRosePine) RosePineColors.Text else Color.White,
                                indicatorColor = Color.Transparent,
                                unselectedIconColor = if (isRosePine) RosePineColors.Muted else Color.White.copy(alpha = 0.72f),
                                unselectedTextColor = if (isRosePine) RosePineColors.Muted else Color.White.copy(alpha = 0.72f),
                            ),
                        )
                    }
                    if (isSettings) {
                        Box(
                            Modifier
                                .weight(settingsReveal.coerceAtLeast(0.001f))
                                .fillMaxHeight()
                                .alpha(settingsReveal)
                                .clipToBounds(),
                        ) { Row(Modifier.fillMaxSize()) { item() } }
                    } else {
                        item()
                    }
                }
            }
        }
    }
}
