import assert from 'node:assert/strict';
import test from 'node:test';
import { lyricsProviders, resolveLyricsChain } from '../src/catalog/lyricsResolver.js';
import { matchesLyricsTrack, resolveLrclibLyrics } from '../src/catalog/lrclibProvider.js';

const metadata = { title: 'Test Song', artist: 'Artist', album: 'Album', durationMs: 200000 };
const result = (source, mode) => ({ status: 'ready', source, mode, lines: [{ text: source, ...(mode === 'synced' ? { startTime: 0 } : {}) }] });

test('desktop validates rich lyrics against the selected reference', async () => {
  assert.deepEqual(lyricsProviders, ['lrclib', 'amlyrics', 'youtube']);
  const calls = [];
  const lyrics = await resolveLyricsChain(async (_, provider) => { calls.push(provider); return result(provider, 'synced'); });
  assert.equal(lyrics.source, 'lrclib');
  assert.equal(lyrics.policyVersion, 'recording-timing-v3');
  assert.deepEqual(calls, ['lrclib', 'amlyrics']);
});

test('synchronized fallback wins over plain LRCLIB', async () => {
  const lyrics = await resolveLyricsChain(async (_, provider) => result(provider, provider === 'lrclib' ? 'unsynced' : 'synced'));
  assert.equal(lyrics.source, 'amlyrics');
});

test('plain LRCLIB wins when neither lyric source has timing', async () => {
  const calls = [];
  const lyrics = await resolveLyricsChain(async (_, provider) => { calls.push(provider); return result(provider, 'unsynced'); });
  assert.equal(lyrics.source, 'lrclib');
  assert.deepEqual(calls, ['lrclib', 'amlyrics']);
});

test('YouTube remains the final fallback on provider errors', async () => {
  const lyrics = await resolveLyricsChain(async (_, provider) => { if (provider !== 'youtube') throw Error('offline'); return result(provider, 'unsynced'); });
  assert.equal(lyrics.source, 'youtube');
});

test('track matching rejects another version or duration', () => {
  const track = { trackName: metadata.title, artistName: metadata.artist, duration: 200 };
  assert.ok(matchesLyricsTrack(track, metadata));
  assert.ok(!matchesLyricsTrack({ ...track, trackName: 'Test Song Remix' }, metadata));
  assert.ok(!matchesLyricsTrack({ ...track, duration: 240 }, metadata));
  assert.ok(!matchesLyricsTrack({ ...track, artistName: 'Other Singer' }, metadata));
});

test('search checks identity instead of accepting its first result', async () => {
  const lyrics = await resolveLrclibLyrics(metadata, async url => url.includes('/get?')
    ? { ok: false, status: 404 }
    : { ok: true, json: async () => [
      { trackName: 'Test Song Remix', artistName: 'Artist', duration: 240, syncedLyrics: '[00:01]Wrong version' },
      { trackName: 'Test Song', artistName: 'Artist', duration: 200, syncedLyrics: '[00:02]Matched version' }
    ] });
  assert.equal(lyrics.lines[0].text, 'Matched version');
  assert.equal(lyrics.lines[0].startTime, 2);
});

test('plain exact lyrics can be replaced by a synchronized search match', async () => {
  const track = { trackName: 'Test Song', artistName: 'Artist', duration: 200 };
  const lyrics = await resolveLrclibLyrics(metadata, async url => ({ ok: true, json: async () => url.includes('/get?')
    ? { ...track, plainLyrics: 'Plain transcript' } : [{ ...track, syncedLyrics: '[00:01]Timed transcript' }] }));
  assert.equal(lyrics.mode, 'synced');
});

test('plain exact lyrics remain usable if search fails', async () => {
  const lyrics = await resolveLrclibLyrics(metadata, async url => url.includes('/get?')
    ? { ok: true, json: async () => ({ trackName: 'Test Song', artistName: 'Artist', duration: 200, plainLyrics: 'Plain transcript' }) }
    : { ok: false, status: 503 });
  assert.equal(lyrics.mode, 'unsynced');
});
