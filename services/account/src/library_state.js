import { HttpError, json, nowSeconds, requireSession } from "./http.js";

const MAX_BYTES = 512 * 1024;
const FIELDS = new Set(["likedTracks", "savedAlbums", "savedArtists", "savedPlaylists", "recentlyPlayed",
  "playedTracks", "playCounts", "listeningDays", "hiddenPlaylistIds", "automaticCovers", "coverStyles"]);

async function boundedJson(request) {
  if (!request.headers.get("content-type")?.startsWith("application/json")) {
    throw new HttpError(415, "invalid_type", "Use application/json");
  }
  const reader = request.body?.getReader();
  if (!reader) throw new HttpError(400, "invalid_request", "Missing body");
  const chunks = [];
  let size = 0;
  try {
    while (true) {
      const { value, done } = await reader.read();
      if (done) break;
      size += value.length;
      if (size > MAX_BYTES) throw new HttpError(413, "too_large", "Library exceeds 512 KiB");
      chunks.push(value);
    }
  } finally { await reader.cancel(); }
  const bytes = new Uint8Array(size);
  let offset = 0;
  for (const chunk of chunks) { bytes.set(chunk, offset); offset += chunk.length; }
  try { return JSON.parse(new TextDecoder("utf-8", { fatal: true }).decode(bytes)); }
  catch { throw new HttpError(400, "invalid_request", "Invalid JSON"); }
}

export function validateLibraryState(body) {
  if (!body || Array.isArray(body) || body.version !== 1 || !body.library || Array.isArray(body.library) || typeof body.library !== "object") {
    throw new HttpError(400, "invalid_request", "Expected a version 1 library document");
  }
  for (const [key, value] of Object.entries(body.library)) {
    if (!FIELDS.has(key)) throw new HttpError(400, "invalid_field", `Unsupported library field: ${key}`);
    if (key === "playCounts" || key === "automaticCovers" || key === "coverStyles") {
      if (!value || Array.isArray(value) || typeof value !== "object") throw new HttpError(400, "invalid_field", "Expected object");
    } else if (!Array.isArray(value)) throw new HttpError(400, "invalid_field", "Expected array");
  }
  const payload = JSON.stringify({ version: 1, library: body.library });
  if (new TextEncoder().encode(payload).length > MAX_BYTES) throw new HttpError(413, "too_large", "Library exceeds 512 KiB");
  return payload;
}

/** Revision matching prevents an older device silently overwriting a newer library. */
export async function libraryState(request, env) {
  const claims = await requireSession(request, env);
  if (request.method === "GET") {
    const row = await env.DB.prepare("SELECT revision, payload, updated_at FROM library_states WHERE user_id = ?").bind(claims.sub).first();
    return json(row ? { revision: row.revision, updatedAt: row.updated_at, document: JSON.parse(row.payload) }
      : { revision: 0, document: null }, 200, { etag: `"${row?.revision || 0}"` });
  }
  const expected = request.headers.get("if-match");
  if (!/^"[0-9]{1,12}"$/.test(expected || "")) throw new HttpError(428, "revision_required", "Read the library and send its ETag as If-Match");
  const revision = Number(expected.slice(1, -1));
  const current = await env.DB.prepare("SELECT payload FROM library_states WHERE user_id = ?").bind(claims.sub).first();
  if (current && JSON.parse(current.payload)?.version === 2)
    throw new HttpError(409, "sync_required", "Use the synchronization API for this library");
  if (request.method === "DELETE") {
    // A tombstone keeps revisions monotonic, including after deletion and recreation.
    const result = await env.DB.prepare("UPDATE library_states SET payload = ?, revision = revision + 1, updated_at = ? WHERE user_id = ? AND revision = ?")
      .bind("null", nowSeconds(), claims.sub, revision).run();
    if (!result.meta?.changes) throw new HttpError(409, "revision_conflict", "Read the latest library before retrying");
    return json({ revision: revision + 1 }, 200, { etag: `"${revision + 1}"` });
  }
  const payload = validateLibraryState(await boundedJson(request));
  const result = revision === 0
    ? await env.DB.prepare("INSERT INTO library_states (user_id, revision, payload, updated_at) VALUES (?, 1, ?, ?) ON CONFLICT(user_id) DO NOTHING")
      .bind(claims.sub, payload, nowSeconds()).run()
    : await env.DB.prepare("UPDATE library_states SET payload = ?, revision = revision + 1, updated_at = ? WHERE user_id = ? AND revision = ?")
      .bind(payload, nowSeconds(), claims.sub, revision).run();
  if (!result.meta?.changes) throw new HttpError(409, "revision_conflict", "Read and merge the latest library before retrying");
  return json({ revision: revision + 1 }, 200, { etag: `"${revision + 1}"` });
}
