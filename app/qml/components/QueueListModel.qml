import QtQuick

ListModel {
    id: root
    property var items: []
    property bool suspended: false
    signal updated()

    function reconcile() {
        if (suspended)
            return;

        const occurrences = new Map();
        const wantedKeys = new Set();
        const entries = root.items.map(item => {
            const identity = String(item.id || item.videoId || JSON.stringify([item.title, item.artist, item.thumbnail]));
            const occurrence = occurrences.get(identity) || 0;
            occurrences.set(identity, occurrence + 1);
            const key = JSON.stringify([identity, occurrence]);
            wantedKeys.add(key);
            return { key, item };
        });
        const keys = [];
        let end = root.count;
        for (let index = 0; index < end; ++index)
            keys.push(root.get(index).entryKey);

        // Remove obsolete runs before moving rows. Advancing to the next song
        // usually drops one row from the front while leaving the rest in order.
        while (end > 0) {
            if (wantedKeys.has(keys[end - 1])) {
                --end;
                continue;
            }
            let start = end - 1;
            while (start > 0 && !wantedKeys.has(keys[start - 1]))
                --start;
            root.remove(start, end - start);
            keys.splice(start, end - start);
            end = start;
        }

        for (let index = 0; index < entries.length; ++index) {
            const { key, item } = entries[index];
            const data = JSON.stringify(item);
            const found = keys[index] === key ? index : keys.indexOf(key, index);
            if (found < 0) {
                root.insert(index, {entryKey: key, entryJson: data});
                keys.splice(index, 0, key);
            } else {
                if (found !== index) {
                    root.move(found, index, 1);
                    keys.splice(index, 0, keys.splice(found, 1)[0]);
                }
                if (root.get(index).entryJson !== data)
                    root.setProperty(index, "entryJson", data);
            }
        }
        root.updated();
    }

    onItemsChanged: if (!suspended) Qt.callLater(root.reconcile)
    onSuspendedChanged: if (!suspended) Qt.callLater(root.reconcile)
    Component.onCompleted: root.reconcile()
}
