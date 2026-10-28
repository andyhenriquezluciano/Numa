import { readFileSync } from 'node:fs';
import { runInNewContext } from 'node:vm';
import { test } from 'node:test';
import assert from 'node:assert/strict';

const source = readFileSync(new URL('../app/qml/components/PopupBloomSurface.qml', import.meta.url), 'utf8');
const method = source.match(/function backdropBehind\(overlay, item, scene\) \{[\s\S]*?\n    \}/)[0];
const backdrop = runInNewContext(`(${method})`);

test('a help card samples the settings surface directly behind it', () => {
    const scene = {};
    const overlay = { children: [] };
    const settings = { parent: overlay, visible: true, contentItem: {} };
    const dimmer = { parent: overlay, visible: true };
    const help = { parent: overlay, visible: true, contentItem: {} };
    overlay.children = [settings, dimmer, help];
    assert.equal(backdrop(overlay, { parent: help }, scene), settings);
    assert.equal(backdrop(overlay, settings, scene), scene);
});

test('hidden popups and dimmers cannot replace the actual backdrop', () => {
    const scene = {};
    const overlay = { children: [] };
    const hidden = { parent: overlay, visible: false, contentItem: {} };
    const dimmer = { parent: overlay, visible: true };
    const help = { parent: overlay, visible: true, contentItem: {} };
    overlay.children = [hidden, dimmer, help];
    assert.equal(backdrop(overlay, help, scene), scene);
    assert.equal(backdrop(null, help, scene), scene);
});
