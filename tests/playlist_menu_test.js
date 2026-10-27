import { readFileSync } from 'node:fs';
import { runInNewContext } from 'node:vm';
import { test } from 'node:test';
import assert from 'node:assert/strict';

const read = path => readFileSync(new URL(`../${path}`, import.meta.url), 'utf8').replaceAll('\r\n', '\n');
const picker = read('app/qml/components/PlaylistPicker.qml');
const titleFunction = picker.match(/function targetTitle\(target\) \{[\s\S]*?\n    \}/)[0];
const localFunction = picker.match(/function targetIsLocal\(target\) \{[\s\S]*?\n    \}/)[0];

test('confirmation actions inherit the shared glass button style', () => {
    const body = read('app/qml/components/settings/ConfirmDialogBody.ui.qml');
    assert.match(body, /SettingsButton\s*\{\s*id: confirm\s*text: body.confirmText\s*\}/);
    assert.doesNotMatch(body, /#c9705f|#d98273/);
});

test('system playlist titles are translated without translating custom titles', () => {
    for (const title of ['Liked Music', 'Liked songs', 'Your Likes', 'Prueba']) {
        const value = runInNewContext(`${titleFunction}; targetTitle(target)`, {
            target: { id: 'PLcustom', title }, qsTr: () => 'Música que te gusta',
        });
        assert.equal(value, title === 'Prueba' ? title : 'Música que te gusta');
    }
});

test('playlist origin comes from its identity, never from its display title', () => {
    for (const [target, local] of [
        [{ id: 'local-playlist:123', title: 'Prueba' }, true],
        [{ id: 'PL123', title: 'Prueba' }, false],
        [{ id: '123', source: 'local', title: 'Prueba' }, true],
    ]) {
        assert.equal(runInNewContext(`${localFunction}; targetIsLocal(target)`, {
            root: { localTrack: false }, target,
        }), local);
    }
});

test('contextual removal targets the selected row, not the currently playing song', () => {
    const menu = read('app/qml/components/MediaMenu.qml');
    assert.match(menu, /onTriggered: NumaLibrary.removeLike\(root.media\)/);
    assert.match(menu, /root.isSong && root.likedPlaylist && !root.isLocalSong/);
    assert.match(menu, /NumaLibrary.removeFromPlaylist\(root.media, root.removablePlaylistId/);
    const view = read('app/qml/views/playlist/PlaylistView.qml');
    assert.match(view, /shareVisible: false/);
    assert.match(view, /likedPlaylist: root.isLikedPlaylist/);
});

test('sidebar sorts local playlists first and saved episodes last', () => {
    const source = read('app/qml/components/home/NavigationSidebar.qml');
    const rankFunction = source.match(/function playlistRank\(playlist\) \{[\s\S]*?\n    \}/)[0];
    const rank = playlist => runInNewContext(`${rankFunction}; playlistRank(playlist)`, {
        playlist, playlistIdentity: p => p.id,
    });
    const entries = [{ id: 'SE' }, { id: 'PLone' }, { id: 'local-playlist:1' }, { id: 'LM' }, { id: 'PLtwo' }];
    entries.sort((a, b) => rank(a) - rank(b));
    assert.deepEqual(entries.map(p => p.id), ['local-playlist:1', 'LM', 'PLone', 'PLtwo', 'SE']);
});

test('deletion protects system lists and requires online sign-in for YouTube', () => {
    const source = read('app/qml/components/PlaylistActions.qml');
    const expression = source.match(/readonly property bool canDelete: ([\s\S]*?)\n    width:/)[1];
    for (const [id, local, signedIn, offline, expected] of [
        ['LM', false, true, false, false], ['SE', false, true, false, false],
        ['PLone', false, true, false, true], ['PLone', false, false, false, false],
        ['PLone', false, true, true, false], ['local-playlist:1', true, false, true, true],
    ]) {
        assert.equal(runInNewContext(expression, {
            playlistId: id, local, playlist: {}, NumaAuth: { isSignedIn: signedIn }, NumaNetwork: { offline },
        }), expected);
    }
    assert.match(source, /onTriggered: root.confirmDelete\(\)/);
    assert.match(source, /onAccepted:/);
    assert.equal(runInNewContext(expression, {
        playlistId: 'PLowned', local: false, playlist: { editable: false },
        NumaAuth: { isSignedIn: true }, NumaNetwork: { offline: false },
    }), true);
});

test('only a local custom cover replaces its full-view gradient', () => {
    const source = read('app/qml/views/playlist/PlaylistView.qml');
    const expression = source.match(/gradientCover: ([\s\S]*?)\n            localGradientCover:/)[1];
    for (const [isLocal, hasCustomCover, expected] of [
        [true, false, true], [true, true, false], [false, false, true],
    ]) {
        assert.equal(runInNewContext(expression, { root: {
            isLocal, isLikedPlaylist: false, detail: { playlistId: 'PLcustom', title: 'Prueba', hasCustomCover },
        } }), expected);
    }
    const sidebar = read('app/qml/components/home/NavigationSidebar.qml');
    assert.match(sidebar, /source: shortcut.isLiked \|\| root.playlistRank\(shortcut.modelData\) === 3 \? shortcut.artworkUrl : ""/);
});

test('home cards use playlist icons without changing system or recommended artwork', () => {
    const card = read('app/qml/components/home/MediaCard.qml');
    const identity = card.match(/function playlistIdentity\(item\) \{[\s\S]*?\n    \}/)[0];
    const expression = card.match(/readonly property bool playlistBadge: ([\s\S]*?)\n\n/)[1];
    for (const [id, title, localPlaylist, expected] of [
        ['PLowned', 'Prueba', false, true], ['local-playlist:1', 'Hola', true, true],
        ['LM', 'Liked Music', false, false], ['SE', 'Episodes for Later', false, false],
        ['PLrecommendation', 'Mix', false, false],
    ]) {
        assert.equal(runInNewContext(`${identity}; ${expression}`, {
            media: { id, title, type: 'playlist' }, presentation: 'playlist', localPlaylist,
            NumaHome: { playlists: [{ browseId: 'VLPLowned' }, { id: 'LM' }, { id: 'SE' }] },
        }), expected);
    }
});
