import { HttpError, json, nowSeconds, requireSession } from "./http.js";
import { mergeLibrary } from "./library_merge.js";

export async function readSyncJson(request, limit = 3 * 1024 * 1024) {
  if (!request.headers.get("content-type")?.startsWith("application/json")) throw new HttpError(415, "invalid_type", "Use application/json");
  const reader = request.body?.getReader();
  if (!reader) throw new HttpError(400, "invalid_request", "Missing body");
  const parts = []; let size = 0;
  try { while (true) {
    const { value, done } = await reader.read(); if (done) break;
    size += value.length; if (size > limit) throw new HttpError(413, "too_large", "Sync document is too large"); parts.push(value);
  } } finally { await reader.cancel(); }
  const bytes = new Uint8Array(size); let offset = 0;
  for (const part of parts) { bytes.set(part, offset); offset += part.length; }
  try { return JSON.parse(new TextDecoder("utf-8", { fatal: true }).decode(bytes)); }
  catch { throw new HttpError(400, "invalid_request", "Invalid JSON"); }
}
function validate(body) {
  if (!/^[a-zA-Z0-9-]{16,80}$/.test(body?.client || "")) throw new HttpError(400, "invalid_client", "Install identifier is required");
  if (!Number.isSafeInteger(body.seq) || body.seq < 1) throw new HttpError(400, "invalid_sequence", "Request sequence is required");
  for (const key of ["base", "local", "counters"]) if (!body[key] || Array.isArray(body[key]) || typeof body[key] !== "object")
    throw new HttpError(400, "invalid_request", `Expected ${key}`);
  const lists = ["likedTracks", "savedAlbums", "savedArtists", "savedPlaylists", "playedTracks", "recentlyPlayed", "listeningDays", "hiddenPlaylistIds"];
  const maps = ["playCounts", "automaticCovers", "coverStyles"];
  for (const doc of [body.base, body.local]) for (const [key, value] of Object.entries(doc)) {
    if (lists.includes(key) && Array.isArray(value)) continue;
    if (maps.includes(key) && value && !Array.isArray(value) && typeof value === "object") continue;
    if (["version", "listeningTotalMs"].includes(key) && Number.isSafeInteger(value) && value >= 0) continue;
    throw new HttpError(400, "invalid_field", `Unsupported library field: ${key}`);
  }
  for (const field of ["counts", "heard"]) for (const value of Object.values(body.counters[field] || {}))
    if (!Number.isSafeInteger(value) || value < 0 || value > Number.MAX_SAFE_INTEGER / 1024) throw new HttpError(400, "invalid_counter", "Counter must be a nonnegative integer");
  if (body.counters.days !== undefined && !Array.isArray(body.counters.days)) throw new HttpError(400, "invalid_counter", "Days must be an array");
  for (const day of body.counters.days || []) {
    if (!day || !/^\d{4}-\d{2}-\d{2}$/.test(day.date || "") || typeof day.trackId !== "string" || !day.trackId ||
      !Number.isSafeInteger(day.listens) || day.listens < 0 || !Number.isSafeInteger(day.heardMs) || day.heardMs < 0)
      throw new HttpError(400, "invalid_counter", "Invalid listening day");
  }
}
export async function synchronizeLibrary(request, env, ctx) {
  const claims = await requireSession(request, env);
  if (request.method === "GET") {
    const row = await env.DB.prepare("SELECT revision, payload FROM library_states WHERE user_id = ?").bind(claims.sub).first();
    return json({ revision: row?.revision || 0, library: row ? JSON.parse(row.payload)?.library || {} : {} });
  }
  const body = await readSyncJson(request); validate(body);
  for (let attempt = 0; attempt < 4; attempt++) {
    const row = await env.DB.prepare("SELECT revision, payload FROM library_states WHERE user_id = ?").bind(claims.sub).first();
    const state = mergeLibrary(row ? JSON.parse(row.payload) : null, body), payload = JSON.stringify(state);
    if (new TextEncoder().encode(payload).length > 1800 * 1024) throw new HttpError(413, "library_limit", "Your library is larger than the cloud limit; local data is preserved");
    if (row?.payload === payload) return json({ revision: row.revision, library: state.library });
    const result = row ? await env.DB.prepare("UPDATE library_states SET payload = ?, revision = revision + 1, updated_at = ? WHERE user_id = ? AND revision = ?")
      .bind(payload, nowSeconds(), claims.sub, row.revision).run() : await env.DB.prepare(
        "INSERT INTO library_states (user_id, revision, payload, updated_at) VALUES (?, 1, ?, ?) ON CONFLICT(user_id) DO NOTHING")
      .bind(claims.sub, payload, nowSeconds()).run();
    if (result.meta?.changes) {
      const revision = (row?.revision || 0) + 1;
      if (env.CONNECT_HUB) {
        const notify = async () => { try {
          const hub = env.CONNECT_HUB.get(env.CONNECT_HUB.idFromName(claims.sub));
          await hub.fetch(new Request("https://internal/internal/library-changed", { method: "POST",
            body: JSON.stringify({ user: claims.sub, device: claims.did, revision }) }));
        } catch { /* Clients also reconcile on reconnect and periodic refresh. */ } };
        if (ctx?.waitUntil) ctx.waitUntil(notify()); else await notify();
      }
      return json({ revision, library: state.library });
    }
  }
  throw new HttpError(409, "sync_busy", "Another device is syncing; retry shortly");
}
