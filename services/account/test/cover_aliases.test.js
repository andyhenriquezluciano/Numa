import assert from "node:assert/strict";
import { createHash } from "node:crypto";
import { test } from "node:test";
import { mergeCoverStyles, mergeLibrary } from "../src/library_merge.js";
import { packet } from "./sync_fixture.js";

const key = id => createHash("sha256").update(id).digest("hex");
const canonical = key("FEmusic_liked_videos"), legacy = key("LM");
const oldCover = { kind: "custom", asset: "a".repeat(64) };
const newCover = { kind: "custom", asset: "b".repeat(64) };

test("liked playlist aliases identify one collection across Android and Windows", () => {
  const initial = mergeLibrary(null, packet("android", 1, { savedPlaylists: [{ id: "LM", title: "Liked", tracks: [{ id: "song" }] }] }));
  const local = { ...initial.library, savedPlaylists: [{ ...initial.library.savedPlaylists[0], id: "VLFEmusic_liked_videos", title: "Updated" }] };
  const next = mergeLibrary(initial, packet("windows", 1, local, initial.library));
  assert.equal(next.library.savedPlaylists.length, 1);
  assert.equal(next.library.savedPlaylists[0].id, "FEmusic_liked_videos");
  assert.equal(next.library.savedPlaylists[0].title, "Updated");
  assert.deepEqual(next.library.savedPlaylists[0].tracks, [{ id: "song" }]);
});
test("Android and desktop liked cover aliases converge in both directions", () => {
  const base = { [canonical]: oldCover, [legacy]: oldCover };
  const android = mergeCoverStyles(base, { ...base, [legacy]: newCover }, base);
  assert.deepEqual(android[canonical], newCover);
  assert.deepEqual(android[legacy], newCover);
  const desktop = mergeCoverStyles(base, { ...base, [canonical]: newCover }, base);
  assert.deepEqual(desktop[canonical], newCover);
  assert.deepEqual(desktop[legacy], newCover);
});
test("resetting either liked cover alias clears both without affecting other lists", () => {
  const base = { [canonical]: oldCover, [legacy]: oldCover, other: oldCover };
  const reset = mergeCoverStyles(base, { ...base, [legacy]: { kind: "default" } }, base);
  assert.deepEqual(reset[canonical], { kind: "default" });
  assert.deepEqual(reset[legacy], { kind: "default" });
  assert.deepEqual(reset.other, oldCover);
});
test("unchanged client accepts remote cover and does not restore an outdated alias", () => {
  const base = { [canonical]: oldCover, [legacy]: oldCover };
  const result = mergeCoverStyles(base, base, { [legacy]: newCover });
  assert.deepEqual(result[canonical], newCover);
  assert.deepEqual(result[legacy], newCover);
});
