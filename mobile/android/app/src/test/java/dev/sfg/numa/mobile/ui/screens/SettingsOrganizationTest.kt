package dev.sfg.numa.mobile.ui.screens

import org.junit.Assert.*
import org.junit.Test

class SettingsOrganizationTest {
    @Test fun movedOptionsRemainSearchableAtTheirActualDestination() {
        for ((query, page) in listOf("traducir letras" to SettingsPage.Lyrics,
            "fuente traduccion" to SettingsPage.Lyrics, "cuenta numa" to SettingsPage.Accounts,
            "sincronizar biblioteca" to SettingsPage.Accounts, "chromecast" to SettingsPage.Connections,
            "gestos" to SettingsPage.Audio, "mejor mezcla" to SettingsPage.Audio)) {
            val matches = searchSettings(query)
            assertTrue(query, matches.isNotEmpty())
            assertTrue(query, matches.all { it.page == page })
        }
    }

    @Test fun removedManualBackupOptionsAreNotSearchable() {
        for (query in listOf("copia", "exportar", "importar")) {
            assertTrue(query, searchSettings(query).isEmpty())
        }
    }

    @Test fun languageIsOutsideSettingsModules() {
        assertEquals(SettingsPage.Home, searchSettings("idioma interfaz").single().page)
    }

    @Test fun obviousOptionsAndCategoryNamesDoNotHaveInfoButtons() {
        for (title in SettingsPage.entries.map { it.title } + listOf("Idioma de la interfaz",
            "Reproductor de NÜMA al bloquear", "Mostrar tasa de bits del audio", "Temas",
            "Estilo de bordes", "Activar carátulas animadas", "Descargar carátulas animadas")) {
            assertNull(title, settingsHelp(title))
        }
    }

    @Test fun playbackChoicesExplainTheirEffectAndPreparation() {
        for (title in listOf("Orden de reproducción", "Mejor mezcla", "Tipo de transición",
            "Duración de la transición", "Volumen exponencial", "Fuente de traducción")) {
            assertFalse(title, settingsHelp(title).isNullOrBlank())
        }
        assertTrue(settingsHelp("Mejor mezcla")!!.contains("datos"))
        assertTrue(settingsHelp("Tipo de transición")!!.contains("No cambia el orden"))
    }
}
