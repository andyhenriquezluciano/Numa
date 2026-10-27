/*
 * Copyright (C) 2026 SFG545
 *
 * This file is part of Numa.
 *
 * Numa is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Affero General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option) any
 * later version.
 *
 * Numa is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU Affero General Public License for more
 * details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Numa. If not, see <https://www.gnu.org/licenses/>.
 */

package dev.sfg.numa.mobile.app

import dev.sfg.numa.mobile.model.PlaybackSnapshot
import dev.sfg.numa.mobile.model.Track

internal data class BestMixQueueRequest(
    private val currentTrackId: String?,
    private val upcomingTrackIds: List<String>,
) {
    /** Keeps unplayed results and appended songs; queue edits invalidate the captured prefix. */
    fun reconcile(snapshot: PlaybackSnapshot, sortedUpcoming: List<Track>): List<Track>? {
        if (sortedUpcoming.map(Track::id).toSet() != upcomingTrackIds.toSet()) return null
        val consumed = if (snapshot.currentTrack?.id == currentTrackId) 0
            else upcomingTrackIds.indexOf(snapshot.currentTrack?.id.orEmpty()).takeIf { it >= 0 }?.plus(1) ?: return null
        val remaining = upcomingTrackIds.drop(consumed)
        val latest = snapshot.upcoming
        if (latest.size < remaining.size) return null
        if (latest.take(remaining.size).map(Track::id) != remaining) return null

        return sortedUpcoming.filter { it.id in remaining } + latest.drop(remaining.size)
    }

    companion object {
        fun capture(snapshot: PlaybackSnapshot): BestMixQueueRequest = BestMixQueueRequest(
            currentTrackId = snapshot.currentTrack?.id,
            upcomingTrackIds = snapshot.upcoming.map { it.id },
        )
    }
}
