package dev.sfg.numa.mobile.social

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RoomPolicyTest {
    @Test fun onlyTheHostCanPublishPlaybackToGuests() {
        assertTrue(roomPeerCanPublishState("host", "host", false))
        assertFalse(roomPeerCanPublishState("guest", "host", false))
        assertFalse(roomPeerCanPublishState("host", "host", true))
        assertFalse(roomPeerCanPublishState("", "", false))
    }
    @Test fun guestsCannotMutatePlaybackOrQueue() {
        listOf("play", "pause", "next", "previous", "seek", "state", "play-track", "add-queue",
            "clear-queue", "play-next", "remove-queue", "move-queue", "toggle-shuffle", "cycle-repeat", "")
            .forEach { assertFalse(it, guestRoomRequestAllowed(it)) }
        assertTrue(guestRoomRequestAllowed("sync-clock"))
    }
    @Test fun onlyTheCurrentHostLeavingClosesTheRoom() {
        assertTrue(roomHostDeparted("host", "host"))
        assertFalse(roomHostDeparted("guest", "host"))
        assertFalse(roomHostDeparted("", ""))
        assertFalse(roomHostDeparted("old-host", "new-host"))
    }
}
