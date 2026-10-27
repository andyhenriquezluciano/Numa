package dev.sfg.numa.mobile.lyrics

import dev.sfg.numa.mobile.model.LyricLine
import java.text.Normalizer

/** Reject provider album notes rather than presenting them as a lyric transcript. */
internal fun List<LyricLine>.containsEditorialNotes(): Boolean {
    val transcript = Normalizer.normalize(joinToString(" ") { it.text }, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "").lowercase(java.util.Locale.ROOT)
    return editorialPhrases.any(transcript::contains)
}

internal fun List<LyricLine>.withoutEditorialNotes(): List<LyricLine> =
    takeUnless { it.containsEditorialNotes() }.orEmpty()

private val editorialPhrases = listOf(
    "la idea de este album", "la idea de ese album", "sobre la creacion del album",
    "idea for this album", "idea of this album", "concept for this album",
    "acerca de esta cancion", "notas del album", "comentario del artista",
    "about the making of", "about this album", "album commentary", "artist commentary",
    "editor's notes", "editor’s notes", "track-by-track commentary",
)
