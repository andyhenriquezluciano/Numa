// SPDX-License-Identifier: AGPL-3.0-or-later

.pragma library

function albumId(media) {
    if (media.type === "album" || media.kind === "album")
        return (media.browsePayload || {}).browseId || media.browseId || media.albumId || "";
    return media.albumId || "";
}

function artistIds(media) {
    if (media.type === "artist" || media.kind === "artist") {
        const id = media.browseId || (media.browsePayload || {}).browseId;
        return id ? [id] : [];
    }
    if (media.artistBrowseIds && media.artistBrowseIds.length)
        return media.artistBrowseIds;
    const id = media.artistBrowseId || media.artistId;
    return id ? [id] : [];
}
