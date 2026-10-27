package dev.sfg.numa.mobile.social

/** Guests can measure the clock; playback belongs to the current host. */
fun guestRoomRequestAllowed(action: String): Boolean = action == "sync-clock"

fun roomHostDeparted(participantId: String, hostId: String): Boolean =
    hostId.isNotBlank() && participantId == hostId

fun roomPeerCanPublishState(peerId: String, hostId: String, recipientIsHost: Boolean): Boolean =
    !recipientIsHost && hostId.isNotBlank() && peerId == hostId
