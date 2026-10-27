import assert from "node:assert/strict";
import { test } from "node:test";
import { mergeLibrary } from "../src/library_merge.js";
import { resetLibraryMetrics } from "../src/metrics_reset.js";

const client = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";
const song = { id: "s", title: "Song" };
const counters = (n = 3) => ({ counts: { s: n }, heard: { s: n * 15_000 },
  days: [{ date: "2026-10-25", trackId: "s", listens: n, heardMs: n * 15_000 }] });
const request = (seq, n = 3, id = client) => ({ client: id, seq, base: {}, local: { playCounts: { s: 30 },
  likedTracks: [song], savedArtists: [{ id: "a" }], coverStyles: { p: { kind: "custom" } } }, counters: counters(n) });

test("reset clears only metrics, preserves metadata and rejects stale retry resurrection", () => {
  const first = mergeLibrary(null, request(1));
  const reset = resetLibraryMetrics(first, 123);
  assert.deepEqual(reset.library.playCounts, {});
  assert.equal(reset.library.listeningTotalMs, 0);
  assert.deepEqual(reset.library.listeningDays, []);
  assert.deepEqual(reset.library.likedTracks, first.library.likedTracks);
  assert.deepEqual(reset.library.coverStyles, first.library.coverStyles);
  const stale = mergeLibrary(reset, { ...request(2), migrateLegacy: true });
  assert.equal(stale.library.playCounts.s || 0, 0);
  assert.equal(stale.library.listeningTotalMs, 0);
  assert.deepEqual(stale.library.listeningDays, []);
});
test("new listens and audible time count from zero without double counting retries", () => {
  const reset = resetLibraryMetrics(mergeLibrary(null, request(1)), 123);
  const next = mergeLibrary(reset, request(2, 4));
  assert.equal(next.library.playCounts.s, 1);
  assert.equal(next.library.listeningTotalMs, 15_000);
  assert.equal(next.library.listeningDays[0].listens, 1);
  assert.deepEqual(mergeLibrary(next, request(2, 4)), next);
});
test("restored or previously unseen device cannot import erased history", () => {
  const reset = resetLibraryMetrics(mergeLibrary(null, request(1)), 123);
  const id = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb";
  const old = mergeLibrary(reset, request(1, 8, id));
  assert.equal(old.library.playCounts.s || 0, 0);
  assert.equal(old.library.listeningTotalMs, 0);
  const played = mergeLibrary(old, request(2, 9, id));
  assert.equal(played.library.playCounts.s, 1);
});
test("a second reset clears subsequent listens and keeps counter floors", () => {
  const reset = resetLibraryMetrics(mergeLibrary(null, request(1)), 123);
  const played = mergeLibrary(reset, request(2, 4));
  const again = resetLibraryMetrics(played, 456);
  assert.equal(mergeLibrary(again, request(3, 4)).library.playCounts.s || 0, 0);
  assert.equal(mergeLibrary(again, request(4, 5)).library.playCounts.s, 1);
});
