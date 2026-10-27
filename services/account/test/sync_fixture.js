import { createD1 } from "./d1.js";
import { signJwt } from "../src/jwt.js";
import { handleRequest } from "../src/service.js";

export async function syncFixture() {
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
  const call = (path, method = "GET", body, user = "a", headers = {}) => handleRequest(new Request(`${env.PUBLIC_URL}${path}`, {
    method, headers: { authorization: `Bearer ${tokens[user] || "invalid"}`, "content-type": "application/json", ...headers },
    ...(body !== undefined ? { body: body instanceof Uint8Array ? body : JSON.stringify(body) } : {}),
  }), env);
  return { call, env };
}

export const packet = (client, seq, local = {}, base = {}, counts = {}) => ({
  client: client.padEnd(16, "-"), seq, local, base, counters: { counts, heard: {}, days: [] },
});
