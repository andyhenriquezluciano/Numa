import { countersAfterReset } from "./metrics_reset.js";

const LISTS = ["likedTracks", "savedAlbums", "savedArtists", "savedPlaylists", "playedTracks", "recentlyPlayed"];
const MAPS = ["automaticCovers", "coverStyles"];
const safe = value => value && typeof value === "object" && !Array.isArray(value) ? value : {};
const number = value => Number.isSafeInteger(value) && value >= 0 ? value : 0;
export function sameLibraryValue(a, b) {
  if (a === b || (a == null && b == null)) return true;
  if (a == null || b == null || typeof a !== "object" || typeof b !== "object") return false;
  if (Array.isArray(a) || Array.isArray(b)) return Array.isArray(a) && Array.isArray(b)
    && a.length === b.length && a.every((value, index) => sameLibraryValue(value, b[index]));
  const keys = Object.keys(a);
  return keys.length === Object.keys(b).length
    && keys.every(key => Object.hasOwn(b, key) && sameLibraryValue(a[key], b[key]));
}

export function mergeMap(base, local, remote) {
  const output = Object.assign(Object.create(null), safe(remote));
  for (const key of new Set([...Object.keys(safe(base)), ...Object.keys(safe(local))])) {
    if (sameLibraryValue(base?.[key], local?.[key])) continue;
    if (Object.hasOwn(safe(local), key)) output[key] = local[key]; else delete output[key];
  }
  return output;
}
const LIKED_COVER_KEYS = ["3d50f01a47b7493dd29f7310d6a9b79b30de551bc5f07a3fcccf5d47c8b081fb", "eadede9d5c7ef2070ac8fb231dea0d84c4e11cd6d8f7ca801c687cf73c5ff5ca"];
export function mergeCoverStyles(base, local, remote) {
  const output = mergeMap(base, local, remote);
  const changed = LIKED_COVER_KEYS.filter(key => !sameLibraryValue(base?.[key], local?.[key]));
  const key = changed[0] || LIKED_COVER_KEYS.find(key => Object.hasOwn(safe(remote), key));
  if (!key) return output;
  const value = changed.length ? local?.[key] : remote?.[key];
  for (const alias of LIKED_COVER_KEYS) {
    if (value === undefined) delete output[alias]; else output[alias] = value;
  }
  return output;
}
function rows(values, playlists = false) {
  return Object.fromEntries((Array.isArray(values) ? values : []).filter(v => typeof v?.id === "string" && v.id).map(v => {
    const id = playlists ? v.id.replace(/^VL/, "").replace(/^LM$/, "FEmusic_liked_videos") : v.id;
    return [id, playlists ? { ...v, id } : v];
  }));
}
function maxMap(a, b) {
  return Object.fromEntries([...new Set([...Object.keys(safe(a)), ...Object.keys(safe(b))])]
    .map(k => [k, Math.max(number(a?.[k]), number(b?.[k]))]));
}
function addMaps(values) {
  const output = Object.create(null);
  for (const value of values) for (const [key, count] of Object.entries(safe(value))) output[key] = Math.min(2147483647, (output[key] || 0) + number(count));
  return output;
}
function dayMap(days) {
  return Object.fromEntries((Array.isArray(days) ? days : []).filter(d => /^\d{4}-\d{2}-\d{2}$/.test(d?.date) && typeof d.trackId === "string")
    .map(d => [JSON.stringify([d.date, d.trackId]), { date: d.date, trackId: d.trackId, listens: number(d.listens), heardMs: number(d.heardMs) }]));
}
function combineDays(parts, maxima = false) {
  const output = {};
  for (const part of parts) for (const [key, day] of Object.entries(safe(part))) {
    const old = output[key] || { ...day, listens: 0, heardMs: 0 };
    output[key] = { ...day, listens: maxima ? Math.max(old.listens, number(day.listens)) : old.listens + number(day.listens),
      heardMs: maxima ? Math.max(old.heardMs, number(day.heardMs)) : old.heardMs + number(day.heardMs) };
  }
  return output;
}

/** Three-way metadata merge, monotonic per-install counters, and a max-only historical migration. */
export function mergeLibrary(previous, request) {
  const remote = safe(previous?.library), base = safe(request.base), local = safe(request.local);
  const clocks = { ...safe(previous?.clocks) }, known = Object.hasOwn(clocks, request.client);
  const own = safe(clocks[request.client]), incoming = safe(request.counters);
  if (known && Number.isSafeInteger(request.seq) && number(own.seq) >= request.seq) return previous;
  clocks[request.client] = { counts: maxMap(own.counts, incoming.counts), heard: maxMap(own.heard, incoming.heard),
    days: combineDays([safe(own.days), dayMap(incoming.days)], true), seq: number(request.seq) };
  const reset = number(previous?.metricsResetAt);
  const floors = { ...safe(previous?.counterFloors) };
  if (reset && !known) floors[request.client] = structuredClone(clocks[request.client]);
  const effective = Object.entries(clocks).map(([id, clock]) => reset ? countersAfterReset(clock, floors[id]) : clock);
  const countSum = addMaps(effective.map(c => c.counts));
  let baselineCounts = previous?.version === 2 ? safe(previous.baselineCounts) : safe(remote.playCounts);
  let baselineDays = previous?.version === 2 ? safe(previous.baselineDays) : dayMap(remote.listeningDays);
  let baselineHeard = previous?.version === 2 ? number(previous.baselineHeard) :
    number(remote.listeningTotalMs) || Object.values(baselineDays).reduce((sum, d) => sum + d.heardMs, 0);
  if (!reset && (!known || request.migrateLegacy === true)) {
    baselineCounts = { ...baselineCounts };
    for (const [id, count] of Object.entries(safe(local.playCounts))) baselineCounts[id] =
      Math.max(number(baselineCounts[id]), number(count) - number(countSum[id]), 0);
    const others = combineDays(Object.values(clocks).map(c => c.days));
    baselineDays = { ...baselineDays };
    for (const [key, day] of Object.entries(dayMap(local.listeningDays))) {
      const old = baselineDays[key]; const counted = others[key];
      baselineDays[key] = { ...day, listens: Math.max(number(old?.listens), day.listens - number(counted?.listens), 0),
        heardMs: Math.max(number(old?.heardMs), day.heardMs - number(counted?.heardMs), 0) };
    }
    const totalHeard = Object.values(clocks).flatMap(c => Object.values(c.heard)).reduce((a, b) => a + number(b), 0);
    const localHeard = number(local.listeningTotalMs) || Object.values(dayMap(local.listeningDays)).reduce((n, d) => n + d.heardMs, 0);
    baselineHeard = Math.max(baselineHeard, localHeard - totalHeard, 0);
  }
  const library = { ...remote };
  for (const key of LISTS) library[key] = Object.values(mergeMap(rows(base[key], key === "savedPlaylists"), rows(local[key], key === "savedPlaylists"), rows(remote[key], key === "savedPlaylists")));
  library.recentlyPlayed = library.recentlyPlayed.slice(-50);
  for (const key of MAPS) library[key] = key === "coverStyles" ? mergeCoverStyles(base[key], local[key], remote[key]) : mergeMap(base[key], local[key], remote[key]);
  const set = values => Object.fromEntries((Array.isArray(values) ? values : []).map(v => [v, true]));
  library.hiddenPlaylistIds = Object.keys(mergeMap(set(base.hiddenPlaylistIds), set(local.hiddenPlaylistIds), set(remote.hiddenPlaylistIds)));
  library.playCounts = addMaps([baselineCounts, countSum]);
  library.listeningDays = Object.values(combineDays([baselineDays, ...effective.map(c => c.days)]))
    .filter(d => d.listens > 0 || d.heardMs > 0);
  library.listeningTotalMs = baselineHeard + effective.flatMap(c => Object.values(c.heard)).reduce((a, b) => a + number(b), 0);
  return { version: 2, library, clocks, baselineCounts, baselineDays, baselineHeard,
    ...(reset ? { metricsResetAt: reset, counterFloors: floors } : {}) };
}
