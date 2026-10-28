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
    @Test fun cachedSpanishCaptionsAndDefaultListsFollowEnglishWithoutRenamingPersonalLists() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.ENGLISH)
            assertEquals("Home", navigationTitle(dev.sfg.numa.mobile.ui.navigation.Routes.HOME))
            assertEquals("Your daily discovery", catalogCaption("Tu descubrimiento diario"))
            assertEquals("Personalized mixes", catalogCaption("Mixes personalizados"))
            assertEquals("Listen together", catalogCaption("Escuchar juntos"))
            assertEquals("Liked music", playlistDisplayTitle("VLLM", "Música que me gusta"))
            assertEquals("Mi lista personal", playlistDisplayTitle("VLPLpersonal", "Mi lista personal"))
            Locale.setDefault(Locale.forLanguageTag("es"))
            assertEquals("Música que me gusta", playlistDisplayTitle("VLLM", "Liked music"))
            assertEquals("Mixes personalizados", catalogCaption("Personalized mixes"))
        } finally { Locale.setDefault(previous) }
    }

}
