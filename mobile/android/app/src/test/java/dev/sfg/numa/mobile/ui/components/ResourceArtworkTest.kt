package dev.sfg.numa.mobile.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ResourceArtworkTest {
    @Test fun defaultPlaylistCoverResolvesToItsDrawable() {
        assertEquals(123, artworkResourceId("android.resource://dev.sfg.numa.mobile/123", "dev.sfg.numa.mobile"))
    }

    @Test fun remoteCoversStillUseTheImageLoader() {
        assertNull(artworkResourceId("https://example.com/123", "dev.sfg.numa.mobile"))
    }

    @Test fun foreignAndInvalidResourcesAreNotLoaded() {
        assertNull(artworkResourceId("android.resource://other.app/123", "dev.sfg.numa.mobile"))
        assertNull(artworkResourceId("android.resource://dev.sfg.numa.mobile/0", "dev.sfg.numa.mobile"))
    }
}
