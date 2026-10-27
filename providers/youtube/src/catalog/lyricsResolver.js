/*
 * Copyright (C) 2026 SFG545
 *
 * This file is part of Numa.
 *
 * Numa is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Affero General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option) any
 * later version.
 *
 * Numa is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU Affero General Public License for more
 * details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Numa. If not, see <https://www.gnu.org/licenses/>.
 */

// Coordinates lyrics providers and converts their formats to the renderer-facing result shape.
// Runs in bare QuickJS: no URL, URLSearchParams, or abortable fetch.
import { parseAmLyricsTtml, parseAmLyricsPayload, normalizeAmLyricsLines } from './lyricsFormats.js';
import { rankedLyricsCandidates, matchesRecording, candidateFitsReference } from './lyricsTiming.js';
import { queryString } from '../shared/queryString.js';
import { resolveBetterLyrics } from './betterLyricsProvider.js';
import { resolveLrclibLyrics } from './lrclibProvider.js';

const amLyricsFetchTimeoutMs = 8000;
const amLyricsServers = [
  'https://lyricsplus.binimum.org',
  'https://lyricsplus-seven.vercel.app',
  'https://lyricsplus.prjktla.workers.dev',
  'https://lyrics-plus-backend.vercel.app'
];

function lyricsLookupText(value = '') {
  return String(value || '')
    .replace(/\([^)]*(official|video|visualizer|lyrics?|audio|hd|4k)[^)]*\)/gi, '')
    .replace(/\[[^\]]*(official|video|visualizer|lyrics?|audio|hd|4k)[^\]]*\]/gi, '')
    .replace(/\s+-\s+Topic$/i, '')
    .replace(/\s+/g, ' ')
    .trim();
}

function lyricsDurationMs(track = {}) {
  let seconds = Number(track.durationSeconds || 0);
  if (!(seconds > 0) && /^\d+(:\d{1,2})+$/.test(String(track.duration || '').trim())) {
    seconds = String(track.duration).trim().split(':').reduce((total, part) => total * 60 + Number(part), 0);
  }
  return seconds > 0 ? Math.round(seconds * 1000) : undefined;
}

function lyricsMetadata(track = {}) {
  const title = lyricsLookupText(track.title);
  const artist = lyricsLookupText(String(track.artist || track.artists?.[0] || '').split(',')[0]);
  if (!title || !artist) return null;

  return {
    title,
    artist,
    album: lyricsLookupText(track.album) || '',
    durationMs: lyricsDurationMs(track),
    videoId: String(track.id || '').trim()
  };
}

// The host fetch cannot abort, so a slow mirror is abandoned rather than cancelled.
async function fetchWithTimeout(url, options = {}, timeoutMs = amLyricsFetchTimeoutMs) {
  let timeout;
  const expired = new Promise((_, reject) => {
    timeout = setTimeout(() => reject(new Error('Lyrics request timed out.')), timeoutMs);
  });

  try {
    return await Promise.race([fetch(url, options), expired]);
  } finally {
    clearTimeout(timeout);
  }
}

async function resolveBiniLyrics(metadata, reference) {
  const query = queryString({
    track: metadata.title,
    artist: metadata.artist,
    album: metadata.album,
    duration: metadata.durationMs ? Math.round(metadata.durationMs / 1000) : undefined
  });

  const response = await fetchWithTimeout(`https://lyrics-api.binimum.org/?${query}`);
  if (!response.ok) return null;

  const payload = await response.json();
  for (const candidate of rankedLyricsCandidates(payload?.results || [], metadata)) {
    if (!candidate.lyricsUrl) continue;
    try {
      const response = await fetchWithTimeout(candidate.lyricsUrl);
      if (!response.ok) continue;
      const data = normalizeAmLyricsLines(parseAmLyricsTtml(await response.text()));
      data.recordingMatched = matchesRecording(candidate, metadata);
      if (data.lines.length && candidateFitsReference(reference, data)) return data;
    } catch {}
  }
  return null;
}

async function resolveLyricsPlusLyrics(metadata, reference) {
  const query = queryString({
    title: metadata.title,
    artist: metadata.artist,
    album: metadata.album,
    duration: metadata.durationMs ? Math.round(metadata.durationMs / 1000) : undefined
  });

  for (const base of amLyricsServers) {
    try {
      const response = await fetchWithTimeout(`${base}/v2/lyrics/get?${query}`);
      if (!response.ok) continue;

      const payload = await response.json();
      if (!rankedLyricsCandidates([payload?.metadata], metadata).length) continue;
      const data = normalizeAmLyricsLines(parseAmLyricsPayload(payload));
      data.recordingMatched = matchesRecording(payload.metadata, metadata);
      if (data.lines.length && candidateFitsReference(reference, data)) return data;
    } catch {
      // Try the next LyricsPlus mirror.
    }
  }

  return [];
}

async function resolveAmLyrics(track = {}, reference = null) {
  const metadata = lyricsMetadata(track);
  if (!metadata) return { status: 'unavailable', mode: '', lines: [], source: 'amlyrics' };

  try {
    const bini = await resolveBiniLyrics(metadata, reference);
    if (bini) return bini;
  } catch {
    // BiniLyrics is the fastest Apple/TTML path when available.
  }

  try {
    const betterLines = await resolveBetterLyrics(metadata, { fetchWithTimeout, parseTtml: parseAmLyricsTtml });
    if (betterLines?.length) {
      const data = normalizeAmLyricsLines(betterLines);
      if (candidateFitsReference(reference, data)) return data;
    }
  } catch {
    // BetterLyrics is another TTML source credited under the am-lyrics provider.
  }

  try {
    return await resolveLyricsPlusLyrics(metadata, reference) || normalizeAmLyricsLines([]);
  } catch {
    return { status: 'unavailable', mode: '', lines: [], source: 'amlyrics' };
  }
}

// Lyrics: the only part of a song everyone is confident about until the chorus.
export function createLyricsResolver({ loadYouTubeLyricsText }) {
  async function resolveYoutubeLyrics(track = {}) {
    if (!track.id) return { status: 'unavailable', mode: '', lines: [], source: 'youtube' };

    try {
      const lines = String(await loadYouTubeLyricsText(track.id) || '')
        .split(/\r?\n/)
        .map((text) => ({ text: text.trim() }))
        .filter((line) => line.text);

      if (lines.length) {
        return {
          status: 'ready',
          mode: 'unsynced',
          lines,
          source: 'youtube'
        };
      }
    } catch {
      // Missing lyrics tabs and region-restricted lyrics are normal for this fallback.
    }

    return { status: 'unavailable', mode: '', lines: [], source: 'youtube' };
  }

  return async function resolveLyrics(track = {}, provider = 'lrclib', reference = null) {
    if (provider === 'amlyrics') return resolveAmLyrics(track, reference);
    if (provider === 'youtube') return resolveYoutubeLyrics(track);
    return resolveLrclibLyrics(lyricsMetadata(track), fetchWithTimeout);
  };
}

export const lyricsProviders = Object.freeze(['lrclib', 'amlyrics', 'youtube']);
export const lyricsPolicyVersion = 'recording-timing-v3';

function ready(data) { return data?.status === 'ready' && data.lines?.length > 0; }

export async function resolveLyricsChain(resolveLyrics, track = {}) {
  const attempt = async (provider, reference) => {
    try { return await resolveLyrics(track, provider, reference); } catch { return null; }
  };
  const reference = await attempt('lrclib');
  const candidate = await attempt('amlyrics', reference);
  const synced = data => ready(data) && data.mode === 'synced';
  const selected = synced(candidate) && candidateFitsReference(reference, candidate) ? candidate
    : synced(reference) ? reference : ready(reference) ? reference
    : ready(candidate) ? candidate : await attempt('youtube');
  return { ...(ready(selected) ? selected : { status: 'unavailable', mode: '', lines: [], source: '' }),
    policyVersion: lyricsPolicyVersion };
}
