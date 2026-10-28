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

package dev.sfg.numa.mobile.ui.foldable

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.sfg.numa.mobile.ui.components.NumaMark
import dev.sfg.numa.mobile.ui.glass.LocalGlass
import dev.sfg.numa.mobile.ui.glass.glassPane
import dev.sfg.numa.mobile.ui.navigation.Routes
import dev.sfg.numa.mobile.ui.theme.LocalAccent
import dev.sfg.numa.mobile.ui.theme.RosePineColors
import dev.sfg.numa.mobile.model.GlassTheme
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected

data class RailDestination(val route: String, val label: String, val icon: ImageVector)

private val foldableDestinations = listOf(
    RailDestination(Routes.HOME, "Inicio", Icons.Rounded.Home),
    RailDestination(Routes.LIBRARY, "Biblioteca", Icons.Rounded.LibraryMusic),
    RailDestination(Routes.ROOMS, "Salas", Icons.Rounded.Groups),
    RailDestination(Routes.SETTINGS, "Ajustes", Icons.Rounded.Person),
)

/**
 * Vertical navigation rail designed for foldable devices (unfolded) and tablets.
 * Sits elegantly along the left edge, replacing the bottom bar and freeing up
 * valuable vertical screen real estate.
 */
@Composable
fun NumaNavigationRail(
    currentRoute: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val glassTint = LocalGlass.current.tint
    val tintColor = if (glassTint != Color.Unspecified && glassTint.alpha > 0f) glassTint else LocalAccent.current
    val isRosePine = LocalGlass.current.theme == GlassTheme.ROSE_PINE
    val selectionColor = if (isRosePine) RosePineColors.Rose else tintColor

    val selectedIndex = remember(currentRoute) {
        val idx = foldableDestinations.indexOfFirst { it.route == currentRoute }
        if (idx != -1) idx
        else if (currentRoute == Routes.SETTINGS_HOME_LAYOUT) {
            foldableDestinations.indexOfFirst { it.route == Routes.SETTINGS }.takeIf { it != -1 }
        } else {
            null
        }
    }
    val showBorders = dev.sfg.numa.mobile.ui.glass.LocalGlass.current.borders != dev.sfg.numa.mobile.model.GlassBorders.LIMPIO
    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(FoldableNavRailWidth)
            .glassPane(RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp))
            .drawWithContent {
                drawContent()
                if (showBorders) {
                    drawLine(
                        color = Color.White.copy(alpha = 0.08f),
                    start = androidx.compose.ui.geometry.Offset(size.width, 0f),
                    end = androidx.compose.ui.geometry.Offset(size.width, size.height),
                    strokeWidth = 1.dp.toPx(),
                )
                }
            },
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .width(FoldableNavRailWidth)
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Top: Numa Logo
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(bounded = true, radius = 24.dp),
                        onClick = { onSelect(Routes.HOME) },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                NumaMark(Modifier.size(34.dp))
            }

            Spacer(Modifier.height(8.dp))

            // Nav destinations evenly spaced across the rail height
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.SpaceEvenly,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                foldableDestinations.forEachIndexed { index, destination ->
                    val isSelected = selectedIndex == index
                    val activeProgress by animateFloatAsState(
                        targetValue = if (isSelected) 1f else 0f,
                        animationSpec = spring(dampingRatio = 0.75f, stiffness = 350f),
                        label = "RailItemSelect_${destination.route}",
                    )

                    val unselectedColor = if (isRosePine) RosePineColors.Muted else Color.White.copy(alpha = 0.72f)
                    val itemColor by animateColorAsState(
                        targetValue = if (isSelected) selectionColor else unselectedColor,
                        animationSpec = tween(180),
                        label = "RailItemColor_${destination.route}",
                    )

                    val textColor by animateColorAsState(
                        targetValue = if (isSelected) {
                            if (isRosePine) RosePineColors.Text else Color.White
                        } else unselectedColor,
                        animationSpec = tween(180),
                        label = "RailItemText_${destination.route}",
                    )

                    Surface(
                        onClick = { onSelect(destination.route) },
                        shape = RoundedCornerShape(16.dp),
                        color = Color.Transparent,
                        modifier = Modifier
                            .size(width = 62.dp, height = 54.dp)
                            .semantics { selected = isSelected },
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Icon(
                                destination.icon,
                                contentDescription = dev.sfg.numa.mobile.ui.text.navigationTitle(destination.route),
                                tint = itemColor,
                                modifier = Modifier.size(22.dp).scale(1f + 0.08f * activeProgress),
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = dev.sfg.numa.mobile.ui.text.navigationTitle(destination.route),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = textColor,
                                maxLines = 1,
                            )
                            Spacer(Modifier.height(4.dp))
                            Box(Modifier.size(width = 28.dp, height = 3.dp)
                                .alpha(activeProgress.coerceIn(0f, 1f))
                                .background(selectionColor, RoundedCornerShape(16.dp)))
                        }
                    }
                }
            }
        }
    }
}
