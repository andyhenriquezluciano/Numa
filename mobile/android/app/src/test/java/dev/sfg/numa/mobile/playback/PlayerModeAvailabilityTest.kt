package dev.sfg.numa.mobile.playback

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerModeAvailabilityTest {
    private fun modes(video: Boolean = true, playing: Boolean = false, checking: Boolean = false,
        local: Boolean = true, control: Boolean = true, cast: Boolean = false) =
        playerModeAvailability(video, playing, checking, local, control, cast)

    @Test fun separateAudioAndVideoAreAvailableForSupportedTracks() {
        assertTrue(modes().song)
        assertTrue(modes().video)
    }
    @Test fun tracksWithoutVideoKeepAudioEnabled() {
        assertTrue(modes(video = false).song)
        assertFalse(modes(video = false).video)
    }
    @Test fun lookingUpVideoDoesNotBlockAudio() {
        assertTrue(modes(checking = true).song)
        assertFalse(modes(checking = true).video)
    }
    @Test fun anActiveVideoCanAlwaysReturnToAudioWithControl() {
        assertTrue(modes(video = false, playing = true, checking = true).song)
        assertTrue(modes(video = false, playing = true, checking = true).video)
    }
    @Test fun roomGuestsCannotChangeEitherMode() {
        assertFalse(modes(control = false).song)
        assertFalse(modes(control = false, playing = true).video)
    }
    @Test fun castAndRemoteTargetsDoNotOfferLocalVideo() {
        assertTrue(modes(cast = true).song)
        assertFalse(modes(cast = true).video)
        assertFalse(modes(local = false).video)
    }
}
