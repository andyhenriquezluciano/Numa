package dev.sfg.numa.mobile.ui.glass

import dev.sfg.numa.mobile.model.GlassBorders
import dev.sfg.numa.mobile.model.GlassTheme
import org.junit.Assert.*
import org.junit.Test

class GlassBackdropPolicyTest {
    @Test fun `chrome inside navigation cannot sample its enclosing scene`() {
        assertEquals(GlassCapture.WASH, glassBackdropFor(GlassTone.CHROME, GlassCapture.SCENE))
        assertEquals(GlassCapture.WASH, glassBackdropFor(GlassTone.OVERLAY, GlassCapture.SCENE))
    }

    @Test fun `background capture cannot sample any captured layer`() {
        GlassTone.entries.forEach { assertNull(glassBackdropFor(it, GlassCapture.WASH)) }
    }

    @Test fun `external chrome can still sample the screen for actual blur`() {
        assertEquals(GlassCapture.SCENE, glassBackdropFor(GlassTone.CHROME, GlassCapture.NONE))
        assertEquals(GlassCapture.SCENE, glassBackdropFor(GlassTone.OVERLAY, GlassCapture.NONE))
    }

    @Test fun `buttons always use independent background layers`() {
        assertEquals(GlassCapture.WASH, glassBackdropFor(GlassTone.CONTROL, GlassCapture.NONE))
        assertEquals(GlassCapture.WASH, glassBackdropFor(GlassTone.CONTROL, GlassCapture.SCENE))
    }

    @Test fun `buttons and menus have identical translucent material in both glass themes`() {
        for (theme in listOf(GlassTheme.CRISTAL, GlassTheme.CRISTAL_OSCURO)) {
            for (borders in GlassBorders.entries) {
                val button = GlassTone.CONTROL.spec(theme, borders)
                val menu = GlassTone.OVERLAY.spec(theme, borders)
                assertEquals(button.base, menu.base)
                assertEquals(button.film, menu.film, 0f)
                assertEquals(button.tintMix, menu.tintMix, 0f)
                assertEquals(button.contrastUndercoat, menu.contrastUndercoat, 0f)
                assertEquals(button.solidFilm, menu.solidFilm, 0f)
                assertEquals(button.solidUndercoat, menu.solidUndercoat, 0f)
                assertTrue(button.film < 0.5f)
            }
        }
    }

    @Test fun `borderless material never draws a rim`() {
        for (theme in GlassTheme.entries) {
            assertEquals(0f, GlassTone.CHROME.spec(theme, GlassBorders.LIMPIO).borderAlpha, 0f)
        }
    }
}
