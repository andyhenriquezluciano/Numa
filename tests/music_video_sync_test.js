import { readFileSync } from 'node:fs';
import { runInNewContext } from 'node:vm';
import { test } from 'node:test';
import assert from 'node:assert/strict';

const qml = readFileSync(new URL('../app/qml/components/player/MusicVideoTheater.qml', import.meta.url), 'utf8');
const sync = qml.slice(qml.indexOf('function syncVideo()'), qml.indexOf('function checkPicture()'));

function fixture(overrides = {}) {
    const video = {
        mediaStatus: 4, seekable: true, position: 10000, playbackRate: 1,
        playbackState: 1,
        play() { this.playbackState = 1; },
        pause() { this.playbackState = 2; },
        ...overrides,
    };
    const context = {
        video, root: { pictureReceived: true },
        NumaPlayback: { audiblePosition: 10, playing: true },
        NumaMusicVideo: { presentedPosition: () => 10 },
        MediaPlayer: { NoMedia: 0, LoadingMedia: 1, InvalidMedia: 2,
            BufferingMedia: 3, StalledMedia: 5, PlayingState: 1 },
        initialPictureSeek: false, lastSeekAt: 0,
        pictureResume: { running: false, restart() { this.running = true; } },
    };
    return { video, context, sync: () => runInNewContext(`${sync}; syncVideo();`, context) };
}

test('uses the presented frame, not a decoder position ahead of the picture', () => {
    const f = fixture({ position: 11000 });
    f.sync();
    assert.equal(f.video.playbackRate, 1);
    assert.equal(f.video.position, 11000);
});

test('gently catches up a small picture delay without seeking', () => {
    const f = fixture();
    f.context.NumaMusicVideo.presentedPosition = () => 9.8;
    f.sync();
    assert.ok(f.video.playbackRate > 1 && f.video.playbackRate <= 1.06);
    assert.equal(f.video.position, 10000);
});

test('corrects a large drift to the audible position', () => {
    const f = fixture({ position: 8000 });
    f.context.NumaMusicVideo.presentedPosition = () => 8;
    f.sync();
    assert.equal(f.video.position, 10000);
    assert.equal(f.video.playbackRate, 1);
});

test('does not seek repeatedly while buffering', () => {
    const f = fixture({ position: 8000, mediaStatus: 3 });
    f.context.NumaMusicVideo.presentedPosition = () => 8;
    f.sync();
    assert.equal(f.video.position, 8000);
});

test('pauses the picture with the soundtrack', () => {
    const f = fixture();
    f.context.NumaPlayback.playing = false;
    f.sync();
    assert.equal(f.video.playbackState, 2);
    assert.equal(f.video.playbackRate, 1);
});

test('initial alignment waits for the resume workaround', () => {
    const f = fixture({ position: 0 });
    f.context.initialPictureSeek = true;
    f.sync();
    assert.equal(f.video.position, 10000);
    assert.equal(f.video.playbackState, 2);
    assert.equal(f.context.pictureResume.running, true);
});

test('loading indicator stays hidden while paused, including initial loading', () => {
    const expression = qml.match(/readonly property bool waiting: ([\s\S]*?)\n        visible:/)[1];
    for (const status of ['checking', 'loading', 'ready']) {
        const f = fixture({ mediaStatus: 5 });
        f.context.root.pictureReceived = false;
        f.context.root.video = f.video;
        f.context.NumaPlayback.playing = false;
        f.context.NumaMusicVideo.status = status;
        assert.equal(runInNewContext(expression, f.context), false);
        f.context.NumaPlayback.playing = true;
        assert.equal(runInNewContext(expression, f.context), true);
    }
});
