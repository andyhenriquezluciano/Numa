import assert from "node:assert/strict";
import { test } from "node:test";
import { mergeLibrary, sameLibraryValue } from "../src/library_merge.js";

const client = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";
const other = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb";
const song = { id: "song", title: "Song", artist: "Artist" };
let seq = 0;
function request(id = client, local = {}, base = {}, counters = {}) { return { client: id, local, base, counters, seq: ++seq }; }
test("fresh install restores saved metadata and counters without duplicating history", () => {
  const old = mergeLibrary(null, request(client, { likedTracks: [song], playCounts: { song: 8 } }));
  const next = mergeLibrary(old, request(other, { playCounts: { song: 8 } }));
  assert.equal(next.library.playCounts.song, 8);
  assert.deepEqual(next.library.likedTracks, [song]);
});
test("independent device counters add, retries and out-of-order requests do not add twice", () => {
  const initial = mergeLibrary(null, request(client, { playCounts: { song: 8 } }));
  const a = request(client, initial.library, initial.library, { counts: { song: 2 } });
  const b = request(other, initial.library, initial.library, { counts: { song: 3 } });
  const merged = mergeLibrary(mergeLibrary(initial, a), b);
  assert.equal(merged.library.playCounts.song, 13);
  assert.equal(mergeLibrary(mergeLibrary(merged, a), request(client, initial.library, initial.library, { counts: { song: 1 } })).library.playCounts.song, 13);
});
test("deletions propagate while a stale reader cannot resurrect deleted metadata", () => {
  const initial = mergeLibrary(null, request(client, { savedArtists: [{ id: "artist", name: "Artist" }] }));
  const deleted = mergeLibrary(initial, request(client, { savedArtists: [] }, initial.library));
  const stale = mergeLibrary(deleted, request(other, initial.library, initial.library));
  assert.deepEqual(stale.library.savedArtists, []);
});
test("concurrent additions are preserved and local edits are retained", () => {
  const first = mergeLibrary(null, request(client, { likedTracks: [song] }));
  const second = mergeLibrary(first, request(other, { likedTracks: [{ ...song, id: "other" }] }));
  assert.deepEqual(new Set(second.library.likedTracks.map(t => t.id)), new Set(["song", "other"]));
  const changed = mergeLibrary(second, request(client, { likedTracks: [{ ...song, title: "Updated" }] }, first.library));
  assert.deepEqual(changed.library.likedTracks.map(t => t.title), ["Updated", "Song"]);
});
test("daily audible time and counts merge independently of the playhead", () => {
  const day = { date: "2026-10-25", trackId: "song", listens: 1, heardMs: 15_000 };
  const a = mergeLibrary(null, request(client, {}, {}, { counts: { song: 1 }, heard: { song: 15_000 }, days: [day] }));
  const b = mergeLibrary(a, request(other, {}, {}, { counts: { song: 1 }, heard: { song: 20_000 }, days: [{ ...day, heardMs: 20_000 }] }));
  assert.equal(b.library.playCounts.song, 2);
  assert.equal(b.library.listeningTotalMs, 35_000);
  assert.equal(b.library.listeningDays[0].listens, 2);
  assert.equal(b.library.listeningDays[0].heardMs, 35_000);
});
test("historical migration uses maxima, including date-specific statistics", () => {
  const old = { playCounts: { song: 8 }, listeningTotalMs: 30_000,
    listeningDays: [{ date: "2026-10-25", trackId: "song", listens: 8, heardMs: 30_000 }] };
  const first = mergeLibrary(null, request(client, old));
  const restored = mergeLibrary(first, request(other, old));
  assert.equal(restored.library.listeningTotalMs, 30_000);
  assert.equal(restored.library.listeningDays[0].listens, 8);
});
test("cover changes and disabling automatic covers propagate", () => {
  const initial = mergeLibrary(null, request(client, { automaticCovers: { LM: { enabled: true } }, coverStyles: { key: { kind: "custom", asset: "a" } } }));
  const changed = mergeLibrary(initial, request(client, { automaticCovers: { LM: { enabled: false } }, coverStyles: { key: { kind: "default" } } }, initial.library));
  assert.equal(changed.library.automaticCovers.LM.enabled, false);
  assert.equal(changed.library.coverStyles.key.kind, "default");
});

test("metadata equality ignores object order and preserves array order", () => {
  assert.equal(sameLibraryValue({ id: "one", tags: ["a", "b"], meta: { x: 1, y: 2 } },
    { meta: { y: 2, x: 1 }, tags: ["a", "b"], id: "one" }), true);
  assert.equal(sameLibraryValue({ tags: ["a", "b"] }, { tags: ["b", "a"] }), false);
  assert.equal(sameLibraryValue({ title: null }, {}), false);
  assert.equal(sameLibraryValue(null, undefined), true);
});

test("large unchanged collections preserve remote additions without serializing metadata recursively", () => {
  const tracks = Array.from({ length: 500 }, (_, i) => ({ id: `track-${i}`, title: `Song ${i}`, artist: "Artist" }));
  const local = { savedPlaylists: [{ id: "playlist", tracks }] };
  const remote = { ...local, savedArtists: [{ id: "remote-artist", name: "Other device" }] };
  const state = mergeLibrary({ version: 2, library: remote }, request(client, structuredClone(local), structuredClone(local)));
  assert.deepEqual(state.library.savedPlaylists[0].tracks, tracks);
  assert.equal(state.library.savedArtists[0].id, "remote-artist");
});
