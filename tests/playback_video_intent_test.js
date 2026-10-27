import { readFileSync } from 'node:fs';
import { runInNewContext } from 'node:vm';
import { test } from 'node:test';
import assert from 'node:assert/strict';

const source = readFileSync(new URL('../app/src/playback/playback_video_source.cpp', import.meta.url), 'utf8');
const expression = source.match(/const bool paused = (.*);/)[1];

for (const [name, loading, resumePaused, playing, expected] of [
    ['next video starts while its audio is still loading', true, false, false, false],
    ['paused restored track stays paused during loading', true, true, false, true],
    ['playing song continues when switching to video', false, false, true, false],
    ['user-paused song stays paused when switching to video', false, false, false, true],
]) {
    test(name, () => {
        const paused = runInNewContext(expression, {
            m_loading: loading, m_resumePaused: resumePaused, playing: () => playing,
        });
        assert.equal(paused, expected);
    });
}

const theater = readFileSync(new URL('../app/qml/components/player/MusicVideoTheater.qml', import.meta.url), 'utf8');
const positionHandler = theater.match(/function onPositionChanged\(\) \{([\s\S]*?)\n        \}/)[1];
for (const [name, at, seeks] of [
    ['seek immediately moves the music video', 45, true],
    ['regular ticks leave the video clock alone', 10.25, false],
]) {
    test(name, () => {
        let syncs = 0;
        const root = {
            previousAudioPosition: 10,
            video: { seekable: true, position: 10000, playbackRate: 0.98 },
            syncVideo() { syncs++; },
        };
        runInNewContext(positionHandler, { root, NumaPlayback: { position: at, audiblePosition: at - 0.1 } });
        assert.equal(root.previousAudioPosition, at);
        assert.equal(root.video.position, seeks ? (at - 0.1) * 1000 : 10000);
        assert.equal(syncs, seeks ? 1 : 0);
        assert.equal(root.video.playbackRate, seeks ? 1 : 0.98);
    });
}
