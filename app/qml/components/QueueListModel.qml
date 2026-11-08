import QtQuick

ListModel {
    id: root
    property var items: []
    signal updated()

    function reconcile() {
        const occurrences = new Map();
        const keys = [];
        for (let index = 0; index < root.count; ++index)
            keys.push(root.get(index).entryKey);
        for (let index = 0; index < root.items.length; ++index) {
            const item = root.items[index];
            const identity = String(item.id || item.videoId || JSON.stringify([item.title, item.artist, item.thumbnail]));
            const occurrence = occurrences.get(identity) || 0;
            occurrences.set(identity, occurrence + 1);
            const key = JSON.stringify([identity, occurrence]);
            const data = JSON.stringify(item);
            const found = keys.indexOf(key, index);
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
        if (root.count > root.items.length)
            root.remove(root.items.length, root.count - root.items.length);
        root.updated();
    }

    onItemsChanged: Qt.callLater(root.reconcile)
    Component.onCompleted: root.reconcile()
}
