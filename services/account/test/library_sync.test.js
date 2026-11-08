import assert from "node:assert/strict";
import { test } from "node:test";
import { syncFixture, packet } from "./sync_fixture.js";
import { ConnectHub } from "../src/connect.js";

test("library commits notify the account hub and both clients retain edits and covers", async () => {
  const { call, env } = await syncFixture();
  const notices = [];
  const hub = new ConnectHub({getWebSockets: () => []}, env);
  hub.notifyLibraryChanged = change => notices.push(change);
  env.CONNECT_HUB = { idFromName: name => name, get: user => ({ fetch: async request => {
    if (new URL(request.url).pathname === "/internal/library-sync") return hub.fetch(request);
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
  const hub = new ConnectHub({getWebSockets: () => []}, env);
  hub.notifyLibraryChanged = () => { throw new Error("offline"); };
  env.CONNECT_HUB = { idFromName: name => name, get: () => ({ fetch: async request => {
    if (new URL(request.url).pathname === "/internal/library-sync") return hub.fetch(request);
    throw new Error("offline");
  } }) };
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

test("sync delegation rejects forged claims and preserves validation errors", async () => {
  const {call, env} = await syncFixture();
  const objects = new Map();
  env.CONNECT_HUB = {idFromName: name => name, get: user => ({fetch: request => {
    if (!objects.has(user)) objects.set(user, new ConnectHub({getWebSockets: () => [], waitUntil: promise => promise}, env));
    return objects.get(user).fetch(request);
  }})};
  assert.equal((await call("/sync/library", "GET", undefined, "unknown", {"x-numa-sync-claims": JSON.stringify({sub:"a",did:"device-a"})})).status, 401);
  const response = await call("/sync/library", "POST", {bad:true});
  assert.equal(response.status, 400);
  assert.equal((await response.json()).error, "invalid_client");
  assert.equal((await call("/internal/library-sync", "GET")).status, 404);
});


test("conditional sync reads omit unchanged payloads and still enforce revocation", async () => {
  const { call, env } = await syncFixture();
  const first = await call("/sync/library");
  assert.equal(first.headers.get("etag"), '"0"');
  assert.equal((await call("/sync/library", "GET", undefined, "a", {"if-none-match": '"0"'})).status, 304);
  await call("/sync/library", "POST", packet("windows", 1, {savedPlaylists: [{id: "mine"}]}));
  const changed = await call("/sync/library", "GET", undefined, "a", {"if-none-match": '"0"'});
  assert.equal(changed.status, 200);
  assert.equal(changed.headers.get("etag"), '"1"');
  assert.equal((await changed.json()).library.savedPlaylists[0].id, "mine");
  const own = await call("/sync/library", "GET", undefined, "b", {"if-none-match": '"1"'});
  assert.equal(own.status, 200);
  assert.deepEqual((await own.json()).library, {});
  await env.DB.prepare("DELETE FROM devices WHERE id = ?").bind("device-a").run();
  assert.equal((await call("/sync/library", "GET", undefined, "a", {"if-none-match": '"1"'})).status, 401);
});

test("hub shares only in-flight reads and notifies directly without fetching itself", async () => {
  const { call, env } = await syncFixture();
  let reads = 0, invocations = 0;
  const notices = [];
  const hubs = new Map();
  env.CONNECT_HUB = {idFromName: name => name, get: user => ({fetch: request => {
    ++invocations;
    assert.equal(new URL(request.url).pathname, "/internal/library-sync");
    if (!hubs.has(user)) {
      const hub = new ConnectHub({getWebSockets: () => []}, env);
      hub.notifyLibraryChanged = change => notices.push(change);
      hubs.set(user, hub);
    }
    return hubs.get(user).fetch(request);
  }})};
  const prepare = env.DB.prepare.bind(env.DB);
  env.DB.prepare = sql => {
    const query = prepare(sql);
    if (sql !== "SELECT revision, payload FROM library_states WHERE user_id = ?") return query;
    return {bind: (...values) => {const bound = query.bind(...values); return {first: async () => {
      ++reads;
      await new Promise(resolve => setTimeout(resolve, 10));
      return bound.first();
    }}}};
  };
  const responses = await Promise.all(Array.from({length: 8}, () => call("/sync/library")));
  for (const response of responses) assert.deepEqual((await response.json()).library, {});
  assert.equal(reads, 1);
  await call("/sync/library");
  assert.equal(reads, 2, "Completed reads must not stay cached");
  const before = invocations;
  const response = await call("/sync/library", "POST", packet("windows", 1, {savedPlaylists: [{id: "one"}]}));
  const saved = (await response.json()).library;
  assert.equal(invocations - before, 1, "One hub invocation per commit, with no self-fetch");
  assert.equal(notices.length, 1);
  await call("/sync/library", "POST", packet("windows", 2, saved, saved));
  assert.equal(notices.length, 1, "Sequence acknowledgements do not wake other devices");
  assert.equal((await (await call("/sync/library")).json()).library.savedPlaylists[0].id, "one");
});
