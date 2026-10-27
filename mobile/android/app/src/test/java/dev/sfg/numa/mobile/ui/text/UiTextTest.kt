package dev.sfg.numa.mobile.ui.text

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class UiTextTest {
    @Test fun followsTheActiveInterfaceLanguage() {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("es-DO"))
            assertEquals("Descargas", uiText("Downloads", "Descargas"))
            assertEquals("Lista automática", catalogCaption("Auto playlist"))
            Locale.setDefault(Locale.ENGLISH)
            assertEquals("Downloads", uiText("Downloads", "Descargas"))
            assertEquals("Auto playlist", catalogCaption("Auto playlist"))
        } finally { Locale.setDefault(original) }
    }

    @Test fun neverTranslatesArtistNamesOrUnknownCatalogContent() {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("es"))
            assertEquals("Eladio Carrión", catalogCaption("Eladio Carrión"))
            assertEquals("The Weeknd", catalogCaption("The Weeknd"))
            assertEquals("Subscribed on YouTube", uiText("Subscribed on YouTube", "Subscribed on YouTube"))
            assertEquals("Siguiendo en YouTube", catalogCaption("Subscribed on YouTube"))
        } finally { Locale.setDefault(original) }
    }
}
