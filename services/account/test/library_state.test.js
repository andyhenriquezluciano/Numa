import assert from "node:assert/strict";
import { test } from "node:test";
import { createD1 } from "./d1.js";
import { signJwt } from "../src/jwt.js";
import { handleRequest } from "../src/service.js";

async function fixture() {
  const { privateKey } = await crypto.subtle.generateKey({ name: "Ed25519" }, true, ["sign", "verify"]);
  const SIGNING_KEY = JSON.stringify({ ...await crypto.subtle.exportKey("jwk", privateKey), kid: "test" });
  const env = { DB: createD1(), SIGNING_KEY, PUBLIC_URL: "https://account.test" };
  const tokens = {};
  for (const id of ["a", "b"]) {
    await env.DB.prepare("INSERT INTO users (id, created_at, updated_at) VALUES (?, 0, 0)").bind(id).run();
    await env.DB.prepare("INSERT INTO devices (id, user_id, name, platform, refresh_hash, rotated_at, created_at, last_seen_at) VALUES (?, ?, '', 'android', ?, 0, 0, 0)")
      .bind(`device-${id}`, id, `hash-${id}`).run();
    tokens[id] = await signJwt({ iss: env.PUBLIC_URL, aud: "numa", sub: id, did: `device-${id}`,
      iat: 0, exp: Math.floor(Date.now() / 1000) + 3600 }, SIGNING_KEY);
  }
  const call = (method, user = "a", { revision, body } = {}) => handleRequest(new Request(`${env.PUBLIC_URL}/library/state`, {
    method, headers: { authorization: `Bearer ${tokens[user] || "invalid"}`, "content-type": "application/json",
      ...(revision !== undefined ? { "if-match": `"${revision}"` } : {}) },
    ...(body !== undefined ? { body: JSON.stringify(body) } : {}),
  }), env);
  return { call, env };
}
const document = { version: 1, library: { playCounts: { song: 3 }, likedTracks: [] } };

test("library is private and a revoked device cannot read it", async () => {
  const { call, env } = await fixture();
  assert.equal((await call("PUT", "a", { revision: 0, body: document })).status, 200);
  assert.equal((await (await call("GET", "b")).json()).document, null);
  assert.equal((await call("GET", "unknown")).status, 401);
  await env.DB.prepare("DELETE FROM devices WHERE id = ?").bind("device-a").run();
  assert.equal((await call("GET")).status, 401);
});
test("concurrent and stale writes require an explicit merge", async () => {
  const { call } = await fixture();
  const results = await Promise.all([call("PUT", "a", { revision: 0, body: document }), call("PUT", "a", { revision: 0, body: document })]);
  assert.deepEqual(results.map(r => r.status).sort(), [200, 409]);
  assert.equal((await call("PUT", "a", { revision: 1, body: document })).status, 200);
  assert.equal((await call("PUT", "a", { revision: 1, body: document })).status, 409);
  assert.equal((await call("GET")).headers.get("etag"), '"2"');
});
test("delete preserves revision history and old writers cannot recreate state", async () => {
  const { call } = await fixture();
  await call("PUT", "a", { revision: 0, body: document });
  assert.equal((await call("DELETE", "a", { revision: 1 })).status, 200);
  const deleted = await (await call("GET")).json();
  assert.equal(deleted.revision, 2);
  assert.equal(deleted.document, null);
  assert.ok(deleted.updatedAt > 0);
  assert.equal((await call("PUT", "a", { revision: 0, body: document })).status, 409);
  assert.equal((await call("PUT", "a", { revision: 2, body: document })).status, 200);
});
test("revision, schema and body size are validated", async () => {
  const { call } = await fixture();
  assert.equal((await call("PUT", "a", { body: document })).status, 428);
  assert.equal((await call("PUT", "a", { revision: 0, body: { version: 1, library: { sessions: [] } } })).status, 400);
  assert.equal((await call("PUT", "a", { revision: 0, body: { padding: "x".repeat(600_000) } })).status, 413);
});
