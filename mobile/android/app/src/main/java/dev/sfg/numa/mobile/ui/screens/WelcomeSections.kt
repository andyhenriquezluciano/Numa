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

package dev.sfg.numa.mobile.ui.screens

import dev.sfg.numa.mobile.ui.glass.glassPane

import dev.sfg.numa.mobile.ui.text.uiText

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.graphicsLayer
import dev.sfg.numa.mobile.ui.motion.riseIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Gradient
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.MultipleStop
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import dev.sfg.numa.mobile.ui.components.ThinGlassSlider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import dev.sfg.numa.mobile.auth.AuthState
import dev.sfg.numa.mobile.model.AudioQuality
import dev.sfg.numa.mobile.model.LocalQobuzLinked
import dev.sfg.numa.mobile.model.NumaSettings
import dev.sfg.numa.mobile.ui.theme.CanopyColors
import dev.sfg.numa.mobile.ui.theme.LocalAccent
import kotlin.math.roundToInt

@Composable
internal fun WelcomeHeroHeader() {
    // The badge breathes while the walkthrough is open; first impressions deserve a pulse.
    val breath = rememberInfiniteTransition(label = "WelcomeBreath").animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(1600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "BadgeScale",
    )
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .riseIn(fromScale = 0.4f)
                .graphicsLayer {
                    scaleX = breath.value
                    scaleY = breath.value
                }
                .size(72.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            LocalAccent.current.copy(alpha = 0.35f),
                            LocalAccent.current.copy(alpha = 0.08f),
                        ),
                    ),
                )
                .border(1.5.dp, LocalAccent.current.copy(alpha = 0.4f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.MusicNote,
                contentDescription = null,
                tint = LocalAccent.current,
                modifier = Modifier.size(38.dp),
            )
        }

        Spacer(Modifier.height(16.dp))

        Text(
            text = uiText("Welcome to NÜMA", "Bienvenido a NÜMA"),
            style = MaterialTheme.typography.headlineLarge.copy(
                fontWeight = FontWeight.ExtraBold,
                fontSize = 28.sp,
            ),
            color = CanopyColors.Text,
            textAlign = TextAlign.Center,
            modifier = Modifier.riseIn(1),
        )

        Spacer(Modifier.height(6.dp))

        Text(
            text = uiText("Personalize your sound, visuals, and music experience", "Personaliza el sonido, la apariencia y tu experiencia musical"),
            style = MaterialTheme.typography.bodyLarge,
            color = CanopyColors.Muted,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp).riseIn(2),
        )
    }
}

@Composable
internal fun WelcomeAuthSection(
    auth: AuthState,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
) {
    WelcomeSectionCard(
        title = uiText("YouTube Music Account", "Cuenta de YouTube Music"),
        subtitle = uiText("Connect to sync your library, likes, and recommendations", "Conecta tu cuenta para sincronizar la biblioteca, tus Me gusta y recomendaciones"),
    ) {
        val isSignedIn = auth is AuthState.SignedIn
        val avatarUrl = (auth as? AuthState.SignedIn)?.avatarUrl.orEmpty()
        val displayName = (auth as? AuthState.SignedIn)?.displayName.orEmpty().ifBlank { "YouTube Music" }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(LocalAccent.current.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                if (avatarUrl.isNotBlank()) {
                    AsyncImage(
                        model = avatarUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(
                        if (isSignedIn) Icons.Rounded.CheckCircle else Icons.Rounded.Person,
                        contentDescription = null,
                        tint = LocalAccent.current,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }

            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isSignedIn) displayName else uiText("Not Connected", "Sin conexión"),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = CanopyColors.Text,
                )
                Text(
                    text = if (isSignedIn) uiText("Signed in and ready to stream", "Sesión iniciada y lista para reproducir") else uiText("Guest mode active", "Modo invitado activo"),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isSignedIn) LocalAccent.current else CanopyColors.Muted,
                )
            }
        }

        Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            if (isSignedIn) {
                OutlinedButton(
                    onClick = onSignOut,
                    shape = CircleShape,
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                ) {
                    Text(uiText("Sign out / Switch account", "Cerrar sesión o cambiar de cuenta"), color = CanopyColors.Danger)
                }
            } else {
                Button(
                    onClick = onSignIn,
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = LocalAccent.current,
                        contentColor = Color.Black,
                    ),
                    modifier = Modifier.fillMaxWidth().height(46.dp),
                ) {
                    Text(uiText("Sign in with YouTube Music", "Iniciar sesión con YouTube Music"), fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
internal fun WelcomeVisualsSection(
    settings: NumaSettings,
    onUpdateSettings: (NumaSettings) -> Unit,
) {
    WelcomeSectionCard(
        title = "Visuals & Appearance",
        subtitle = uiText("Customize ambient lighting and animated artwork", "Personaliza la iluminación y las carátulas animadas"),
    ) {
        WelcomeToggleRow(
            icon = Icons.Rounded.Gradient,
            title = uiText("Animated Background", "Fondo animado"),
            subtitle = uiText("Drifting cover colors behind the app", "Los colores de la carátula se mueven detrás de la aplicación"),
            checked = settings.animatedBackground,
            onChecked = { onUpdateSettings(settings.copy(animatedBackground = it)) },
        )
        WelcomeDivider()
        WelcomeToggleRow(
            icon = Icons.Rounded.Image,
            title = uiText("Animated Artwork", "Carátulas animadas"),
            subtitle = uiText("Play dynamic motion artwork for supported tracks", "Reproduce carátulas animadas en las canciones compatibles"),
            checked = settings.animatedArtwork,
            onChecked = { onUpdateSettings(settings.copy(animatedArtwork = it)) },
        )
    }
}

@Composable
internal fun WelcomeCrossfadeSection(
    settings: NumaSettings,
    onUpdateSettings: (NumaSettings) -> Unit,
) {
    WelcomeSectionCard(
        title = "Crossfade & Transitions",
        subtitle = uiText("Seamless blending between tracks", "Transiciones suaves entre canciones"),
    ) {
        WelcomeToggleRow(
            icon = Icons.Rounded.MultipleStop,
            title = uiText("Crossfade Playback", "Fundido cruzado"),
            subtitle = if (settings.crossfadeEnabled) "Tracks blend for ${settings.crossfadeSeconds}s" else uiText("Smooth transition between songs", "Transición suave entre canciones"),
            checked = settings.crossfadeEnabled,
            onChecked = { onUpdateSettings(settings.copy(crossfadeEnabled = it)) },
        )

        AnimatedVisibility(visible = settings.crossfadeEnabled) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        uiText("Duration", "Duración"),
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        color = CanopyColors.Text,
                    )
                    Text(
                        "${settings.crossfadeSeconds} seconds",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = LocalAccent.current,
                    )
                }

                ThinGlassSlider(
                    value = settings.crossfadeSeconds.toFloat(),
                    onValueChange = { onUpdateSettings(settings.copy(crossfadeSeconds = it.roundToInt())) },
                    valueRange = NumaSettings.MIN_CROSSFADE_SECONDS.toFloat()..NumaSettings.MAX_CROSSFADE_SECONDS.toFloat(),
                    steps = NumaSettings.MAX_CROSSFADE_SECONDS - NumaSettings.MIN_CROSSFADE_SECONDS - 1,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(8.dp))

                Text(
                    uiText("Transition Type", "Tipo de transición"),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    color = CanopyColors.Text,
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .glassPane(RoundedCornerShape(12.dp))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    val smart = settings.smartCrossfade
                    WelcomeOptionChip(
                        title = "Adaptive mix",
                        subtitle = uiText("Beat-matched overlap", "Mezcla sincronizada con el ritmo"),
                        icon = Icons.Rounded.AutoAwesome,
                        selected = smart,
                        modifier = Modifier.weight(1f),
                        onClick = { onUpdateSettings(settings.copy(smartCrossfade = true)) },
                    )
                    WelcomeOptionChip(
                        title = uiText("Standard", "Estándar"),
                        subtitle = uiText("Linear volume ramp", "Cambio de volumen lineal"),
                        icon = Icons.Rounded.GraphicEq,
                        selected = !smart,
                        modifier = Modifier.weight(1f),
                        onClick = { onUpdateSettings(settings.copy(smartCrossfade = false)) },
                    )
                }
            }
        }
    }
}

@Composable
internal fun WelcomeQualityAndStorageSection(
    settings: NumaSettings,
    onUpdateSettings: (NumaSettings) -> Unit,
) {
    WelcomeSectionCard(
        title = uiText("Audio Quality & Cache", "Calidad de audio y caché"),
        subtitle = uiText("Streaming fidelity and offline cache limit", "Calidad de reproducción y límite de caché sin conexión"),
    ) {
        WelcomeToggleRow(
            icon = Icons.AutoMirrored.Rounded.VolumeUp,
            title = uiText("Exponential volume", "Volumen exponencial"),
            subtitle = uiText("Finer control at low volumes with the media-volume buttons", "Control más preciso a volumen bajo con los botones multimedia"),
            checked = settings.exponentialVolumeEnabled,
            onChecked = { onUpdateSettings(settings.copy(exponentialVolumeEnabled = it)) },
        )
        WelcomeDivider()
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RowIcon(Icons.Rounded.GraphicEq)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        uiText("Streaming Quality", "Calidad de transmisión"),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = CanopyColors.Text,
                    )
                    Text(
                        settings.audioQuality.name.lowercase().replace('_', ' ').replaceFirstChar(Char::titlecase),
                        style = MaterialTheme.typography.bodySmall,
                        color = LocalAccent.current,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .glassPane(CircleShape)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                val linked = LocalQobuzLinked.current
                AudioQuality.entries.forEach { quality ->
                    val choosable = quality != AudioQuality.MAX || linked
                    val isSelected = quality == settings.audioQuality && choosable
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(36.dp)
                            .background(
                                if (isSelected) LocalAccent.current else Color.Transparent,
                                CircleShape,
                            )
                            .clickable(enabled = choosable) { onUpdateSettings(settings.copy(audioQuality = quality)) }
                            .alpha(if (choosable) 1f else 0.4f),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = when (quality) {
                                AudioQuality.DATA_SAVER -> uiText("Saver", "Ahorro")
                                AudioQuality.NORMAL -> "Normal"
                                AudioQuality.HIGH -> "High"
                                AudioQuality.MAX -> "Max"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) Color.Black else CanopyColors.Muted,
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            WelcomeDivider()
            Spacer(Modifier.height(12.dp))

            val steps = NumaSettings.CACHE_SIZE_STEPS_MB
            val index = steps.indexOfFirst { it >= settings.cacheSizeMb }.takeIf { it >= 0 } ?: steps.lastIndex

            Row(verticalAlignment = Alignment.CenterVertically) {
                RowIcon(Icons.Rounded.Storage)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        uiText("Audio Cache Size", "Tamaño de la caché de audio"),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = CanopyColors.Text,
                    )
                    Text(
                        uiText("Store up to ${formatCacheSize(steps[index])} of played music", "Guarda hasta ${formatCacheSize(steps[index])} de música reproducida"),
                        style = MaterialTheme.typography.bodySmall,
                        color = CanopyColors.Muted,
                    )
                }
                Text(
                    formatCacheSize(steps[index]),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = LocalAccent.current,
                )
            }

            ThinGlassSlider(
                value = index.toFloat(),
                onValueChange = { onUpdateSettings(settings.copy(cacheSizeMb = steps[it.roundToInt()])) },
                valueRange = 0f..steps.lastIndex.toFloat(),
                steps = steps.size - 2,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
