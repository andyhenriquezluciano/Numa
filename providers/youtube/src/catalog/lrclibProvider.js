/* Copyright (C) 2026 SFG545. SPDX-License-Identifier: AGPL-3.0-or-later */
import { queryString } from '../shared/queryString.js';
import { matchesLyricsTrack, rankedLyricsCandidates } from './lyricsTiming.js';
export { matchesLyricsTrack } from './lyricsTiming.js';

const lrcTimestamp = /\[(\d+):([0-5]\d)(?:[.:](\d{1,3}))?\]/g;
const lrcMetadataLine = /^\[[a-z][a-z0-9_-]*:.*\]$/i;

function lrcSeconds(minutes, seconds, fraction = '') {
  return Number(minutes) * 60 + Number(seconds) + (fraction ? Number(fraction) / 10 ** fraction.length : 0);
}

function parseLocalLyrics(lyrics = '') {
  const offset = -Number(String(lyrics).match(/\[offset:\s*([+-]?\d+)\s*\]/i)?.[1] || 0) / 1000;
  const synced = [];
  const unsynced = [];
  for (const rawLine of String(lyrics).replace(/\r\n?/g, '\n').split('\n')) {
    const line = rawLine.trim();
    if (!line || lrcMetadataLine.test(line)) continue;
    const matches = [...line.matchAll(lrcTimestamp)];
    if (!matches.length) {
      unsynced.push({ text: line });
      continue;
    }
    const text = line.replace(lrcTimestamp, '').trim();
    if (!text) continue;
    // Repeated choruses share one line with several stamps.
    for (const match of matches) synced.push({ text, startTime: Math.max(0, lrcSeconds(match[1], match[2], match[3]) + offset) });
  }
  synced.sort((a, b) => a.startTime - b.startTime);
  return { synced: synced.length ? synced : null, unsynced };
}

function normalizeLyricsResult(result) {
  if (!result) return { status: 'unavailable', mode: '', lines: [], source: 'lrclib' };

  if (result.instrumental) {
    return {
      status: 'ready',
      mode: 'unsynced',
      lines: [{ text: 'Instrumental' }],
      source: 'lrclib'
    };
  }

  if (result.syncedLyrics) {
    const parsed = parseLocalLyrics(result.syncedLyrics);
    if (parsed.synced?.length) {
      return {
        status: 'ready',
        mode: 'synced',
        lines: parsed.synced,
        source: 'lrclib'
      };
    }
  }

  if (result.plainLyrics) {
    const parsed = parseLocalLyrics(result.plainLyrics);
    if (parsed.unsynced.length) {
      return {
        status: 'ready',
        mode: 'unsynced',
        lines: parsed.unsynced,
        source: 'lrclib'
      };
    }
  }

  return { status: 'unavailable', mode: '', lines: [], source: 'lrclib' };
}


function identity(value = '') {
  return String(value).normalize('NFD').replace(/\p{M}+/gu, '').toLowerCase()
    .replace(/[^\p{L}\p{N}]+/gu, ' ').trim();
}

const unavailable = () => ({ status: 'unavailable', mode: '', lines: [], source: 'lrclib' });
function usable(candidate) {
  const result = normalizeLyricsResult(candidate);
  const transcript = identity(result.lines.map(line => line.text).join(' '));
  const editorial = ['la idea de este album', 'la idea de ese album', 'sobre la creacion del album',
    'idea for this album', 'idea of this album', 'concept for this album', 'acerca de esta cancion',
    'notas del album', 'comentario del artista', 'about the making of', 'about this album',
    'album commentary', 'artist commentary', 'editor s notes', 'track by track commentary'];
  return editorial.some(phrase => transcript.includes(phrase)) ? unavailable() : result;
}

export async function resolveLrclibLyrics(metadata, fetchWithTimeout) {
  if (!metadata) return unavailable();
  const request = async (path, params) => {
    const response = await fetchWithTimeout(`https://lrclib.net/api/${path}?${queryString(params)}`, {
      headers: { 'Lrclib-Client': 'Numa' }
    });
    if (!response.ok) throw new Error(`LRCLIB HTTP ${response.status}`);
    return response.json();
  };
  let exact = unavailable();
  try {
    const candidate = await request('get', {
      track_name: metadata.title, artist_name: metadata.artist,
      album_name: /\b(?:plays?|views?|listeners?|subscribers?)\b/i.test(metadata.album) ? undefined : metadata.album || undefined,
      duration: metadata.durationMs > 0 ? Math.floor(metadata.durationMs / 1000) : undefined
    });
    if (candidate && matchesLyricsTrack(candidate, metadata)) exact = usable(candidate);
    if (exact.mode === 'synced') return exact;
  } catch {}
  try {
    const results = await request('search', { track_name: metadata.title, artist_name: metadata.artist });
    const matches = rankedLyricsCandidates(results, metadata).map(usable)
      .filter(result => result.lines.length > 0);
    return matches.find(result => result.mode === 'synced')
      || (exact.lines.length ? exact : matches[0]) || unavailable();
  } catch { return exact; }
}
