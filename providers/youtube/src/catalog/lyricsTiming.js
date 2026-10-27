/* Copyright (C) 2026 SFG545. SPDX-License-Identifier: AGPL-3.0-or-later */
export function lyricsIdentity(value = '') {
  return String(value).normalize('NFD').replace(/\p{M}+/gu, '').toLowerCase()
    .replace(/[^\p{L}\p{N}]+/gu, ' ').trim();
}

function durationMs(candidate) {
  if (Number(candidate.duration) > 0) return Number(candidate.duration) * 1000;
  const parts = String(candidate.totalDuration || '').split(':').map(Number);
  return parts.length >= 2 && parts.every(Number.isFinite)
    ? parts.reduce((total, part) => total * 60 + part, 0) * 1000 : 0;
}

export function matchesLyricsTrack(candidate, metadata) {
  if (!candidate || !metadata) return false;
  const title = lyricsIdentity(metadata.title);
  const artist = lyricsIdentity(String(metadata.artist || '').split(',')[0].split(' feat.')[0]);
  const foundTitle = lyricsIdentity(candidate.trackName || candidate.track_name || candidate.title || candidate.name);
  const foundArtist = lyricsIdentity(candidate.artistName || candidate.artist_name || candidate.artist);
  if (!title || title !== foundTitle || !artist || !(` ${foundArtist} `).includes(` ${artist} `)) return false;
  return !(metadata.durationMs > 0) || (durationMs(candidate) > 0 && Math.abs(durationMs(candidate) - metadata.durationMs) <= 1000);
}

export function matchesRecording(candidate, metadata) {
  return metadata.durationMs > 0 && matchesLyricsTrack(candidate, metadata)
    && /^[A-Z]{2}[A-Z0-9]{3}[0-9]{7}$/.test(String(candidate.isrc || '').toUpperCase());
}

export function rankedLyricsCandidates(results, metadata) {
  return (Array.isArray(results) ? results : []).filter(item => matchesLyricsTrack(item, metadata))
    .sort((a, b) => (metadata.durationMs > 0 ? Math.abs(durationMs(a) - metadata.durationMs)
      - Math.abs(durationMs(b) - metadata.durationMs) : 0)
      || Number(lyricsIdentity(a.albumName || a.album) !== lyricsIdentity(metadata.album))
      - Number(lyricsIdentity(b.albumName || b.album) !== lyricsIdentity(metadata.album)));
}

export function timingComparison(reference = [], candidate = []) {
  const tokens = candidate.flatMap(line => (line.words?.length ? line.words : [line])
    .flatMap(word => lyricsIdentity(word.text).split(' ').filter(Boolean)
      .map(text => ({ text, time: word.startTime }))));
  let cursor = 0;
  const differences = [];
  const timed = reference.filter(line => Number.isFinite(line.startTime) && line.text?.trim());
  for (const line of timed) {
    const phrase = lyricsIdentity(line.text).split(' ').filter(Boolean).slice(0, 4);
    if (!phrase.length) continue;
    for (let i = cursor; i <= tokens.length - phrase.length; i++) {
      if (phrase.every((text, j) => tokens[i + j].text === text) && Number.isFinite(tokens[i].time)) {
        differences.push(Math.abs(tokens[i].time - line.startTime));
        cursor = i + phrase.length;
        break;
      }
    }
  }
  const sameText = timed.length > 0 && differences.length >= Math.min(3, timed.length)
    && differences.length >= timed.length * 0.5;
  return { sameText, compatible: sameText && differences.filter(delta => delta <= 0.75).length >= differences.length * 0.8 };
}

export function candidateFitsReference(reference, candidate) {
  if (!reference?.lines?.some(line => Number.isFinite(line.startTime))) return true;
  const comparison = timingComparison(reference.lines, candidate.lines);
  return comparison.compatible || (candidate.recordingMatched === true && comparison.sameText);
}
