import assert from 'node:assert/strict';
import test from 'node:test';
import { matchesLyricsTrack, rankedLyricsCandidates, timingComparison } from '../src/catalog/lyricsTiming.js';
import { createLyricsResolver, resolveLyricsChain } from '../src/catalog/lyricsResolver.js';
import { parseAmLyricsPayload } from '../src/catalog/lyricsFormats.js';
import { resolveLrclibLyrics } from '../src/catalog/lrclibProvider.js';

const metadata = { title: 'Test Song', artist: 'Artist', album: 'Album', durationMs: 211000 };
const record = { trackName: 'Test Song', artistName: 'Artist', albumName: 'Album', duration: 211, isrc: 'GBAYE0001456' };
const lines = shift => ['First synthetic verse', 'Another synthetic line', 'Final synthetic phrase']
  .map((text, index) => ({ text, startTime: 20 + index * 4 + shift }));
const data = (source, shift = 0, recordingMatched = false) => ({ status: 'ready', mode: 'synced', source, lines: lines(shift), recordingMatched });

test('matching excludes shorter recordings and unknown durations', () => {
  assert.equal(matchesLyricsTrack({ ...record, duration: 208 }, metadata), false);
  assert.equal(matchesLyricsTrack({ ...record, duration: 0 }, metadata), false);
  assert.equal(matchesLyricsTrack({ ...record, duration: 210.9 }, metadata), true);
  assert.equal(matchesLyricsTrack({ ...record, trackName: 'Test Song (Remastered)' }, metadata), false);
});

test('duration proximity precedes album and response order', () => {
  const exact = { ...record, albumName: 'Compilation' };
  assert.equal(rankedLyricsCandidates([{ ...record, duration: 210.2 }, exact], metadata)[0], exact);
});

test('constant advance and accumulating drift are incompatible', () => {
  assert.equal(timingComparison(lines(0), lines(-2.7)).compatible, false);
  assert.equal(timingComparison(lines(0), lines(0).map((line, i) => ({ ...line, startTime: line.startTime + i * 2 }))).compatible, false);
});

test('an ISRC matched recording can correct a generic reference with the same transcript', async () => {
  const result = await resolveLyricsChain(async (_, provider) => provider === 'lrclib'
    ? data('lrclib') : data('amlyrics', 2.7, true));
  assert.equal(result.source, 'amlyrics');
  assert.equal(result.lines[0].startTime, 22.7);
});

test('unverified mismatched timing cannot replace a synchronized reference', async () => {
  const result = await resolveLyricsChain(async (_, provider) => provider === 'lrclib'
    ? data('lrclib') : data('amlyrics', -2.7));
  assert.equal(result.source, 'lrclib');
});

test('compatible rich lyrics preserve word timings', async () => {
  const rich = data('amlyrics');
  rich.lines.forEach(line => { line.words = [{ text: line.text, startTime: line.startTime, endTime: line.startTime + 2 }]; });
  const result = await resolveLyricsChain(async (_, provider) => provider === 'lrclib' ? data('lrclib') : rich);
  assert.ok(result.lines[0].words);
});

test('LyricsPlus decimal and integer values both mean milliseconds', () => {
  const result = parseAmLyricsPayload({ lyrics: [{ text: 'Hello', time: 1250.5, duration: 2000.5 }, { text: 'World', time: 4000, duration: 1000 }] });
  assert.equal(result[0].startTime, 1.251);
  assert.equal(result[0].endTime, 3.252);
  assert.equal(result[1].startTime, 4);
});

test('LRC offsets apply even when the tag follows repeated timestamps', async () => {
  const result = await resolveLrclibLyrics(metadata, async () => ({ ok: true, json: async () => ({
    ...record, syncedLyrics: '[00:10.00][00:20.00]Synthetic verse\n[offset:500]' }) }));
  assert.deepEqual(result.lines.map(line => line.startTime), [9.5, 19.5]);
});

test('Bini selects the matching recording instead of the first URL', async () => {
  const previous = globalThis.fetch;
  const called = [];
  globalThis.fetch = async url => {
    called.push(url);
    return url.includes('lyrics-api.binimum.org') ? { ok: true, json: async () => ({ results: [
      { ...record, duration: 208, lyricsUrl: 'https://fixture.invalid/short' },
      { ...record, lyricsUrl: 'https://fixture.invalid/matched' } ] }) }
      : { ok: true, text: async () => '<tt><body><p begin="00:22.70" end="00:24.00">Synthetic verse</p></body></tt>' };
  };
  try {
    const result = await createLyricsResolver({})({ id: 'source-id', ...metadata, durationSeconds: 211 }, 'amlyrics');
    assert.equal(result.recordingMatched, true);
    assert.equal(result.lines[0].startTime, 22.7);
    assert.equal(called.some(url => url.endsWith('/short')), false);
  } finally { globalThis.fetch = previous; }
});
