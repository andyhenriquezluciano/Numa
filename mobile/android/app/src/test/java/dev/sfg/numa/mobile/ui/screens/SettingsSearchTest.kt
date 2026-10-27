package dev.sfg.numa.mobile.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsSearchTest {
    @Test fun settingsModulesHaveDistinctResponsibilities() {
        assertEquals(listOf("Cuentas", "Letras y traducción", "Reproducción", "Descargas", "Apariencia", "Integraciones"),
            listOf(SettingsPage.Accounts, SettingsPage.Lyrics, SettingsPage.Audio, SettingsPage.Storage, SettingsPage.Appearance, SettingsPage.Connections).map { it.title })
    }

    @Test fun settingsMoveToTheirDesktopModules() {
        assertTrue(searchSettings("historial youtube").all { it.page == SettingsPage.Accounts })
        assertTrue(searchSettings("tasa bits").any { it.page == SettingsPage.Appearance })
        assertTrue(searchSettings("descargar caratula").all { it.page == SettingsPage.Storage })
        assertTrue(searchSettings("notas version").isEmpty())
    }

    @Test fun downloadQualityDoesNotChangePlaybackQuality() {
        val original = dev.sfg.numa.mobile.model.NumaSettings()
        val changed = original.copy(downloadQuality = dev.sfg.numa.mobile.model.AudioQuality.DATA_SAVER)
        assertEquals(original.audioQuality, changed.audioQuality)
        assertEquals(original.cellularAudioQuality, changed.cellularAudioQuality)
    }
    @Test fun accentsAndWhitespaceDoNotHideSettings() {
        assertEquals(searchSettings("normalización volumen"), searchSettings("  NORMALIZACION\tvolumen  "))
        assertTrue(searchSettings("normalizacion volumen").isNotEmpty())
    }

    @Test fun searchKeepsTheRealDestination() {
        assertTrue(searchSettings("ecualizador").all { it.page == SettingsPage.Audio })
        assertTrue(searchSettings("fondo animado").any { it.page == SettingsPage.Appearance })
        assertTrue(searchSettings("discord").any { it.page == SettingsPage.Connections })
        assertTrue(searchSettings("borrar cache").any { it.page == SettingsPage.Storage })
    }

    @Test fun blankAndUnknownQueriesHaveNoResults() {
        assertTrue(searchSettings("  \n ").isEmpty())
        assertTrue(searchSettings("zzzznonexistent").isEmpty())
    }
}
