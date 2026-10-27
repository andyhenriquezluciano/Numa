import assert from "node:assert/strict";
import { test } from "node:test";
import { syncFixture, packet } from "./sync_fixture.js";

test("library commits notify the account hub and both clients retain edits and covers", async () => {
  const { call, env } = await syncFixture();
  const notices = [];
  env.CONNECT_HUB = { idFromName: name => name, get: user => ({ fetch: async request => {
    notices.push({ user, ...await request.json() }); return new Response(null, { status: 204 });
  } }) };
  const playlist = { id: "PL-shared", title: "Compartida", tracks: [{ id: "song", title: "Song" }] };
  const styles = { cover: { kind: "custom", asset: "a".repeat(64) } };
  let response = await call("/sync/library", "POST", packet("windows", 1,
    { savedPlaylists: [playlist], coverStyles: styles }));
  const pc = (await response.json()).library;
  const phone = (await (await call("/sync/library")).json()).library;
  assert.deepEqual(phone.savedPlaylists, [playlist]);
  assert.deepEqual(phone.coverStyles, styles);
  response = await call("/sync/library", "POST", packet("android", 1,
    { ...phone, savedPlaylists: [{ ...playlist, title: "Renombrada" }], coverStyles: { cover: { kind: "default" } } }, phone));
  const updated = (await response.json()).library;
  await call("/sync/library", "POST", packet("windows", 2, { ...pc, savedArtists: [{ id: "artist", name: "Artist" }] }, pc));
  const final = (await (await call("/sync/library")).json()).library;
  assert.equal(final.savedPlaylists[0].title, "Renombrada");
  assert.deepEqual(final.savedPlaylists[0].tracks, playlist.tracks);
  assert.deepEqual(final.coverStyles, updated.coverStyles);
  assert.equal(final.savedArtists[0].id, "artist");
  assert.equal(notices.length, 3);
  assert.ok(notices.every(notice => notice.user === "a" && notice.device === "device-a"));
  assert.deepEqual(notices.map(notice => notice.revision), [1, 2, 3]);
  assert.deepEqual((await (await call("/sync/library", "GET", undefined, "b")).json()).library, {});
});

test("notification outages preserve committed library changes", async () => {
  const { call, env } = await syncFixture();
  env.CONNECT_HUB = { idFromName: name => name, get: () => ({ fetch: async () => { throw new Error("offline"); } }) };
  assert.equal((await call("/sync/library", "POST", packet("windows", 1, { savedPlaylists: [{ id: "list" }] }))).status, 200);
  assert.equal((await (await call("/sync/library")).json()).library.savedPlaylists[0].id, "list");
});

test("two installs converge, retries and stale packets do not duplicate listens", async () => {
  const { call } = await syncFixture();
  const first = packet("android", 1, { likedTracks: [{ id: "one" }] }, {}, { one: 1 });
  let response = await call("/sync/library", "POST", first);
  assert.equal(response.status, 200);
  const base = (await response.json()).library;
  response = await call("/sync/library", "POST", packet("windows", 1, { ...base, savedArtists: [{ id: "artist", name: "Artist" }] }, base, { one: 1 }));
  assert.equal(response.status, 200);
  assert.equal((await response.json()).library.playCounts.one, 2);
  await call("/sync/library", "POST", first);
  await call("/sync/library", "POST", packet("android", 2, { likedTracks: [] }, base, { one: 1 }));
  await call("/sync/library", "POST", first);
  const remote = (await (await call("/sync/library")).json()).library;
  assert.equal(remote.playCounts.one, 2);
  assert.deepEqual(remote.likedTracks, []);
  assert.equal(remote.savedArtists[0].id, "artist");
  assert.deepEqual((await (await call("/sync/library", "GET", undefined, "b")).json()).library, {});
  assert.equal((await call("/sync/library", "GET", undefined, "unknown")).status, 401);
});

test("legacy writes cannot discard synchronization clocks", async () => {
  const { call } = await syncFixture();
  await call("/sync/library", "POST", packet("android", 1, {}, {}, { song: 3 }));
  const result = await call("/library/state", "PUT", { version: 1, library: {} }, "a", { "if-match": '"1"' });
  assert.equal(result.status, 409);
  assert.equal((await (await call("/sync/library")).json()).library.playCounts.song, 3);
});

test("invalid counter and private fields are rejected without writing", async () => {
  const { call } = await syncFixture();
  for (const body of [packet("android", 0), packet("android", 1, { sessions: [] }), packet("android", 1, {}, {}, { song: -1 }),
    { ...packet("android", 1), counters: { days: [{ date: "bad", trackId: "song", listens: -1, heardMs: 0 }] } }])
    assert.equal((await call("/sync/library", "POST", body)).status, 400);
  assert.equal((await (await call("/sync/library")).json()).revision, 0);
});

test("private cover upload is idempotent, validated and isolated by account", async () => {
  const { call, env } = await syncFixture();
  const bytes = new Uint8Array([82,73,70,70,18,0,0,0,87,69,66,80,86,80,56,76,5,0,0,0,47,0,0,0,0,0]);
  const digest = [...new Uint8Array(await crypto.subtle.digest("SHA-256", bytes))].map(v => v.toString(16).padStart(2, "0")).join("");
  const path = `/sync/covers/${digest}`;
  assert.equal((await call(path, "PUT", bytes)).status, 204);
  assert.equal((await call(path, "PUT", bytes)).status, 204);
  const image = await call(path);
  assert.equal(image.status, 200);
  assert.deepEqual(new Uint8Array(await image.arrayBuffer()), bytes);
  assert.equal((await call(path, "GET", undefined, "b")).status, 404);
  assert.equal((await call(path, "GET", undefined, "unknown")).status, 401);
  assert.equal((await call(path, "PUT", new Uint8Array([1, 2]))).status, 400);
  assert.equal((await env.DB.prepare("SELECT count(*) AS n FROM library_covers").first()).n, 1);
  await env.DB.prepare("DELETE FROM devices WHERE id = ?").bind("device-a").run();
  assert.equal((await call(path)).status, 401);
});

test("cover quota is enforced per account without blocking existing assets", async () => {
  const { env } = await syncFixture();
  for (let i = 0; i < 20; i++) await env.DB.prepare("INSERT INTO library_covers (user_id,digest,bytes,size) VALUES (?,?,?,?)")
    .bind("a", String(i), new Uint8Array([0]), 1048576).run();
  await assert.rejects(env.DB.prepare("INSERT INTO library_covers (user_id,digest,bytes,size) VALUES (?,?,?,?)")
    .bind("a", "overflow", new Uint8Array([0]), 1).run(), /cover_quota/);
  await env.DB.prepare("INSERT INTO library_covers (user_id,digest,bytes,size) VALUES (?,?,?,?)")
    .bind("b", "own", new Uint8Array([0]), 1).run();
});
