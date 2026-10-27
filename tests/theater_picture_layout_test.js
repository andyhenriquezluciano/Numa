import { readFileSync } from 'node:fs';
import { runInNewContext } from 'node:vm';
import { test } from 'node:test';
import assert from 'node:assert/strict';

const qml = readFileSync(new URL('../app/qml/components/player/TheaterPicture.qml', import.meta.url), 'utf8').replaceAll('\r\n', '\n');
const zoomExpression = qml.match(/readonly property real zoom: ([\s\S]*?)\n    readonly property/)[1];
const marginsExpression = qml.match(/readonly property bool hasMargins: ([\s\S]*?)\n\n/)[1];

for (const [name, frameWidth, frameHeight, cropWidth, cropHeight, expected] of [
    ['matching aspect needs no ambient texture', 1920, 1080, 1, 1, false],
    ['4:3 picture gets ambient sides', 1440, 1080, 1, 1, true],
    ['portrait picture gets ambient sides', 1080, 1920, 1, 1, true],
    ['wide picture gets ambient above and below', 2560, 1080, 1, 1, true],
    ['embedded side bars are excluded from the ambient source', 1920, 1080, 0.75, 1, true],
]) {
    test(name, () => {
        const context = { width: 1920, height: 1080, known: true,
            frameWidth, frameHeight, cropWidth, cropHeight };
        context.zoom = runInNewContext(zoomExpression, context);
        assert.equal(runInNewContext(marginsExpression, context), expected);
        assert.ok(frameWidth * cropWidth * context.zoom <= context.width + 0.001);
        assert.ok(frameHeight * cropHeight * context.zoom <= context.height + 0.001);
    });
}

test('ambient reuses one video output without adding a decoder', () => {
    assert.equal((qml.match(/VideoOutput\s*\{/g) || []).length, 1);
    assert.equal((qml.match(/MediaPlayer\s*\{/g) || []).length, 0);
    assert.match(qml, /sourceItem: output/);
    assert.match(qml, /active: root.visible && root.hasMargins/);
});

test('asymmetric embedded bars cannot cover the centered ambient margins', () => {
    const viewport = qml.slice(qml.indexOf('id: pictureViewport'), qml.indexOf('VideoOutput {'));
    assert.match(viewport, /anchors.centerIn: parent/);
    assert.match(viewport, /clip: true/);
    const root = { known: true, width: 1920, height: 1080,
        frameWidth: 1920, frameHeight: 1080, cropX: 0.05, cropY: 0,
        cropWidth: 0.8, cropHeight: 1, zoom: 1 };
    const viewportWidth = runInNewContext(viewport.match(/width: (.*)/)[1], { root });
    const viewportHeight = runInNewContext(viewport.match(/height: (.*)/)[1], { root });
    const left = (root.width - viewportWidth) / 2;
    const top = (root.height - viewportHeight) / 2;
    assert.equal(left, root.width - left - viewportWidth);
    assert.equal(top, root.height - top - viewportHeight);
    const output = qml.slice(qml.indexOf('id: output'));
    const offsetX = runInNewContext(output.match(/x: (.*)/)[1], { root });
    const contentLeft = offsetX + root.frameWidth * root.cropX * root.zoom;
    assert.equal(contentLeft, 0);
    assert.equal(contentLeft + root.frameWidth * root.cropWidth * root.zoom, viewportWidth);
});
