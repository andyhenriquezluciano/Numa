package dev.sfg.numa.mobile.ui.components

import dev.sfg.numa.mobile.model.GlassBorders
import dev.sfg.numa.mobile.model.NumaSettings
import dev.sfg.numa.mobile.ui.screens.equalizerPresetLabel
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class AppearanceDefaultsTest {
    @Test fun standardBordersAreTheDefault() {
        assertEquals(GlassBorders.PREDETERMINADO, NumaSettings().glassBorders)
    }

    @Test fun equalizerProfilesUseSpanishLabels() {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("es-DO"))
            assertEquals("Plano", equalizerPresetLabel("flat"))
            assertEquals("Personalizado", equalizerPresetLabel("custom"))
            assertEquals("Refuerzo de graves", equalizerPresetLabel("bass_boost"))
        } finally {
            Locale.setDefault(original)
        }
    }
}
