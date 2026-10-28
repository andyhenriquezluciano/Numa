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

import dev.sfg.numa.mobile.ui.text.uiText



import dev.sfg.numa.mobile.ui.glass.glassPane

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.sfg.numa.mobile.NumaGraph
import dev.sfg.numa.mobile.lyrics.translation.LyricTranslationState
import dev.sfg.numa.mobile.lyrics.translation.LyricTranslationState.Status
import dev.sfg.numa.mobile.model.LyricLine

/** Registers [lines] as on screen, which is what lets the translator spend CPU on them. */
@Composable
internal fun rememberLyricTranslation(lines: List<LyricLine>, songId: String): LyricTranslationState {
    val translator = NumaGraph.from(LocalContext.current).lyricTranslation
    DisposableEffect(translator, lines, songId) {
        translator.show(lines, songId)
        onDispose { translator.hide(lines, songId) }
    }
    val state by translator.state.collectAsStateWithLifecycle()
    return state
}

/** The translation as the main line, with the original small beneath it like an adlib. */
@Composable
internal fun TranslatedLyricLine(
    line: LyricLine,
    translation: String,
    synced: Boolean,
    active: Boolean,
    isAlternate: Boolean,
    accent: Color,
    positionMs: State<Float>,
) {
    val align = if (isAlternate) TextAlign.End else TextAlign.Start
    Text(
        text = translation,
        color = Color.White,
        textAlign = align,
        modifier = Modifier.fillMaxWidth(),
        style = (if (synced) lyricTextStyle() else unsyncedLyricStyle()).copy(
            shadow = if (active) Shadow(accent.copy(alpha = 0.4f), blurRadius = 24f) else null,
        ),
    )
    if (line.words.isNotEmpty()) {
        TimedWords(
            words = line.words,
            positionMs = positionMs,
            lineActive = active,
            adlib = true,
            isAlternate = isAlternate,
            accent = accent,
        )
    } else {
        Text(
            text = line.text,
            color = Color.White.copy(alpha = 0.72f),
            textAlign = align,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            style = if (synced) lyricTextStyle(adlib = true) else unsyncedLyricStyle().copy(fontSize = 14.sp, lineHeight = 20.sp),
        )
    }
}

@Composable
internal fun LyricTranslateChip(modifier: Modifier = Modifier) {
    val graph = NumaGraph.from(LocalContext.current)
    val settings by graph.settings.settings.collectAsStateWithLifecycle()
    val enabled = settings.translateLyrics
    val state by graph.lyricTranslation.state.collectAsStateWithLifecycle()
    val loading = enabled && state.status in setOf(Status.TRANSLATING, Status.DOWNLOADING)
    val tint = Color.White.copy(alpha = if (enabled) 0.95f else 0.6f)
    val shape = RoundedCornerShape(12.dp)
    val label = when {
        loading && state.status == Status.DOWNLOADING -> uiText("Preparing translation…", "Preparando traducción…")
        loading -> if (state.progress > 0) uiText("Translating ${(state.progress * 100).toInt()} %", "Traduciendo ${(state.progress * 100).toInt()} %") else uiText("Translating…", "Traduciendo…")
        enabled && state.status == Status.FAILED -> translationStatusMessage(state).ifBlank { uiText("Translation failed", "No se pudo traducir") } + uiText(" · Retry", " · Reintentar")
        enabled && state.status == Status.UNSUPPORTED -> uiText("Translation unavailable", "Traducción no disponible")
        enabled && state.status == Status.WAITING -> uiText("Connect to prepare the translator", "Conéctate para preparar el traductor")
        enabled && state.status == Status.READY -> if (settings.lyricTranslationProvider == dev.sfg.numa.mobile.model.LyricTranslationProvider.LOCAL)
            uiText("Ready offline", "Lista sin conexión") else uiText("Translation ready", "Traducción lista")
        else -> null
    }
    Box(
        modifier = modifier
            .size(48.dp)
            .clip(RoundedCornerShape(24.dp))
            .semantics {
                stateDescription = translationStatusMessage(state).ifBlank { label ?: if (enabled) uiText("Translation on", "Traducción activada") else uiText("Translation off", "Traducción desactivada") }
                liveRegion = LiveRegionMode.Polite
            }
            .clickable(role = androidx.compose.ui.semantics.Role.Button) {
                if (enabled && state.status in setOf(Status.FAILED, Status.WAITING)) graph.lyricTranslation.retry()
                else graph.settings.updateSettings(settings.copy(translateLyrics = !enabled))
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(34.dp)
            .glassPane(shape, dev.sfg.numa.mobile.ui.glass.GlassTone.CONTROL)
            .then(if (enabled) Modifier.background(Color.White.copy(alpha = 0.06f), shape) else Modifier),
            contentAlignment = Alignment.Center) {
        if (loading) {
            androidx.compose.material3.CircularProgressIndicator(
                modifier = Modifier.size(16.dp), color = tint, strokeWidth = 1.5.dp,
            )
        } else {
            Icon(Icons.Rounded.Translate,
                contentDescription = if (enabled) uiText("Turn off translation", "Desactivar traducción") else uiText("Translate lyrics into English", "Traducir letras al español"),
                tint = tint, modifier = Modifier.size(18.dp))
        }
        }
    }
}
