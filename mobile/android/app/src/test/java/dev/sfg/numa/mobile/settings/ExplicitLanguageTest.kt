package dev.sfg.numa.mobile.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class ExplicitLanguageTest {
    @Test fun systemAndUnsupportedChoicesResolveToSpanishRatherThanFollowTheDevice() {
        assertEquals("es", explicitInterfaceLanguage(""))
        assertEquals("es", explicitInterfaceLanguage("fr"))
        assertEquals("es", explicitInterfaceLanguage("es-DO"))
        assertEquals("en", explicitInterfaceLanguage("en-US"))
    }
}
