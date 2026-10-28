package dev.sfg.numa.mobile.lyrics.translation

import org.junit.Assert.*
import org.junit.Test

class TranslationPreparationPolicyTest {
    @Test fun visibleLyricsCanPrepareDespiteBatterySaverOrMeteredNetwork() {
        assertTrue(TranslationConditions(true, false, true).allowModelDownload(true))
        assertTrue(TranslationConditions(true, true, true).allowModelDownload(true))
        assertFalse(TranslationConditions(false, false, false).allowModelDownload(true))
        assertFalse(TranslationConditions(true, true, true).allowModelDownload(false))
    }
    @Test fun doesNotPrepareOnMobileDataOfflineOrBatterySaver() {
        assertFalse(TranslationConditions(true, true, false).allowPreparation)
        assertFalse(TranslationConditions(false, false, false).allowPreparation)
        assertFalse(TranslationConditions(true, false, true).allowPreparation)
        assertTrue(TranslationConditions(true, false, false).allowPreparation)
    }

    @Test fun installsOnlyWhatTheNextLanguageNeeds() {
        assertEquals(setOf("es"), missingTranslationModels("en", emptySet()))
        assertEquals(setOf("ko"), missingTranslationModels("ko", setOf("es")))
        assertTrue(missingTranslationModels("ko", setOf("ko", "es")).isEmpty())
        assertTrue(missingTranslationModels("es", emptySet()).isEmpty())
    }
    @org.junit.Test fun modelsAndCachesAreIndependentForEachTargetLanguage() {
        assertEquals(setOf("es"), missingTranslationModels("es", emptySet(), "en"))
        assertEquals(setOf("ko"), missingTranslationModels("ko", setOf("es"), "en"))
        assertTrue(missingTranslationModels("en", emptySet(), "en").isEmpty())
        org.junit.Assert.assertNotEquals(OnDeviceLyricTranslation().revision("spa", "en"), OnDeviceLyricTranslation().revision("spa", "es"))
        org.junit.Assert.assertNotEquals(
            RemoteLyricTranslation.cacheRevision(dev.sfg.numa.mobile.model.LyricTranslationProvider.GOOGLE, "", "", "en"),
            RemoteLyricTranslation.cacheRevision(dev.sfg.numa.mobile.model.LyricTranslationProvider.GOOGLE, "", "", "es"))
    }

}
