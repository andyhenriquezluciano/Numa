package dev.sfg.numa.mobile.ui.screens

import dev.sfg.numa.mobile.auth.AuthState
import org.junit.Assert.*
import org.junit.Test

class HomeLoadFailureTest {
    @Test fun signedOutUsersGetGoogleSignIn() {
        assertTrue(requiresHomeSignIn(AuthState.SignedOut, "Could not load Home"))
    }
    @Test fun expiredAuthenticationOffersSignInAgain() {
        assertTrue(requiresHomeSignIn(AuthState.SignedIn(), "Authenticated YouTube cookies are required."))
    }
    @Test fun ordinaryServiceErrorsStillOfferRetry() {
        assertFalse(requiresHomeSignIn(AuthState.SignedIn(), "Service unavailable"))
    }
}
