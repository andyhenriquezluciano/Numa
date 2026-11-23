pragma Singleton

import QtQuick

QtObject {
    readonly property int maxConcurrent: 6
    property var waiting: []
    property var loading: []

    function request(artwork) {
        waiting.push(artwork);
        wake();
    }

    function release(artwork) {
        const waitingIndex = waiting.indexOf(artwork);
        if (waitingIndex >= 0)
            waiting.splice(waitingIndex, 1);
        const loadingIndex = loading.indexOf(artwork);
        if (loadingIndex >= 0) {
            loading.splice(loadingIndex, 1);
            wake();
        }
    }

    function wake() {
        Qt.callLater(dispatch);
    }

    function dispatch() {
        const available = maxConcurrent - loading.length;
        if (available <= 0 || waiting.length === 0)
            return;

        const ranked = [];
        for (const artwork of waiting) {
            if (!artwork || !artwork.visible)
                continue;
            const distance = artwork.thumbnailDistance();
            let rank = ranked.length;
            while (rank > 0 && distance < ranked[rank - 1].distance)
                --rank;
            if (rank >= available)
                continue;
            if (ranked.length === available)
                ranked.pop();
            ranked.splice(rank, 0, { artwork, distance });
            // All slots are filled by visible covers. Later covers cannot outrank them.
            if (ranked.length === available && ranked[available - 1].distance === 0)
                break;
        }
        for (const candidate of ranked) {
            const index = waiting.indexOf(candidate.artwork);
            if (index < 0)
                continue;
            waiting.splice(index, 1);
            loading.push(candidate.artwork);
            candidate.artwork.grantedThumbnail = candidate.artwork.thumbnailCandidate;
        }
    }
}
