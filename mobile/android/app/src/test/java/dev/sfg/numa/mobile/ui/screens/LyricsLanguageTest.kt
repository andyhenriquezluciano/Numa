package dev.sfg.numa.mobile.ui.screens

import dev.sfg.numa.mobile.lyrics.translation.LyricTranslationState
import java.util.Locale
import org.junit.Assert.*
import org.junit.Test

class LyricsLanguageTest {
    @Test fun anExistingTranslationStateFollowsTheNewInterfaceLanguage() {
        val old = Locale.getDefault()
        try {
            val pending = LyricTranslationState(LyricTranslationState.Status.WAITING,
                message = "Preparación pausada por ahorro de batería")
            Locale.setDefault(Locale.ENGLISH)
            assertEquals("Preparation paused by battery saver", translationStatusMessage(pending))
            Locale.setDefault(Locale.forLanguageTag("es"))
            assertEquals("Preparación pausada por ahorro de batería", translationStatusMessage(pending))
        } finally { Locale.setDefault(old) }
    }

    @Test fun sdkFailuresDoNotLeakUntranslatedTechnicalMessages() {
        val old = Locale.getDefault()
        try {
            val failure = LyricTranslationState(LyricTranslationState.Status.FAILED, "java.net.UnknownHostException: secret.internal")
            Locale.setDefault(Locale.forLanguageTag("es"))
            val spanish = translationStatusMessage(failure)
            assertTrue(spanish.contains("Comprueba la conexión"))
            assertFalse(spanish.contains("secret.internal"))
            Locale.setDefault(Locale.ENGLISH)
            assertTrue(translationStatusMessage(failure).contains("Check your connection"))
            assertEquals("Lyrics are not available for this track.", lyricNoticeMessage("La letra no está disponible para esta canción."))
        } finally { Locale.setDefault(old) }
    }

    @Test fun savedSourceLanguageNamesAreLocalizedInsteadOfTranslatingSongContent() {
        val old = Locale.getDefault()
        try {
            val ready = LyricTranslationState(LyricTranslationState.Status.READY,
                message = "Traducido de inglés", sourceName = "inglés", translations = listOf("Contenido de la canción"))
            Locale.setDefault(Locale.ENGLISH)
            assertEquals("Translated from English", translationStatusMessage(ready))
            assertEquals(listOf("Contenido de la canción"), ready.translations)
        } finally { Locale.setDefault(old) }
    }
}
