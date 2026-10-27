import { readFileSync } from 'node:fs';
import { runInNewContext } from 'node:vm';
import { test } from 'node:test';
import assert from 'node:assert/strict';

const source = readFileSync(new URL('../app/qml/components/player/FullscreenPlayer.qml', import.meta.url), 'utf8').replaceAll('\r\n', '\n');
const restore = source.match(/function restoreWindowVisibility\(\) \{[\s\S]*?\n    \}/)[0];
const Window = { Windowed: 2, Maximized: 4, FullScreen: 5 };

test('closing the song view restores native fullscreen to the previous window state', () => {
    assert.match(source, /if \(!open\) \{\s*root.restoreWindowVisibility\(\);/);
    for (const previous of [Window.Windowed, Window.Maximized]) {
        let restored;
        const window = { visibility: Window.FullScreen, showNormal: () => restored = Window.Windowed,
            showMaximized: () => restored = Window.Maximized };
        runInNewContext(`${restore}; restoreWindowVisibility();`, {
            root: { Window: { window }, visibilityBeforeNativeFullscreen: previous }, Window,
        });
        assert.equal(restored, previous);
    }
});

test('closing an already windowed view leaves its window untouched', () => {
    const window = { visibility: Window.Windowed, showNormal: () => assert.fail(), showMaximized: () => assert.fail() };
    runInNewContext(`${restore}; restoreWindowVisibility();`, { root: { Window: { window } }, Window });
});
