package dev.sfg.numa.mobile.ui.screens

import org.junit.Assert.*
import org.junit.Test

class LoginPageVisibilityTest {
    @Test fun hidesMusicAfterGoogleRedirect() {
        assertTrue(hideYouTubeLandingPage("https://music.youtube.com/", false))
        assertTrue(hideYouTubeLandingPage("https://music.youtube.com/browse/LM", false))
    }
    @Test fun keepsGoogleAndAccountChoicesVisible() {
        assertFalse(hideYouTubeLandingPage("https://accounts.google.com/ServiceLogin", false))
        assertFalse(hideYouTubeLandingPage("https://m.youtube.com/#channel_switcher", true))
        assertFalse(hideYouTubeLandingPage("https://www.youtube.com/signin/selectchannel", true))
    }
    @Test fun ignoresOtherDomains() {
        assertFalse(hideYouTubeLandingPage("https://youtube.com.example.org/", false))
        assertFalse(hideYouTubeLandingPage(null, false))
    }
}
