package dev.sfg.numa.mobile.app

import dev.sfg.numa.mobile.playback.ListeningPartyManager

internal fun finishListeningParty(party: ListeningPartyManager, notice: SuccessNotice) {
    val host = party.state.value.isHost
    party.leaveParty()
    notice.show(if (host) "La sala compartida ha finalizado." else "Has salido de la sala compartida.", atTop = true)
}
