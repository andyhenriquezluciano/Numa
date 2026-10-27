import { HttpError, requireSession } from "./http.js";
import { parseWebp } from "./webp.js";

export async function libraryCover(request, env, digest) {
  const claims = await requireSession(request, env);
  if (request.method === "GET" || request.method === "HEAD") {
    const row = await env.DB.prepare("SELECT bytes, size FROM library_covers WHERE user_id = ? AND digest = ?").bind(claims.sub, digest).first();
    if (!row) throw new HttpError(404, "not_found", "No such cover");
    return new Response(request.method === "HEAD" ? null : new Uint8Array(row.bytes), { headers: {
      "content-type": "image/webp", "content-length": String(row.size), "cache-control": "private, no-store", "x-content-type-options": "nosniff" } });
  }
  const reader = request.body?.getReader(); if (!reader) throw new HttpError(400, "invalid_image", "Missing cover");
  const parts = []; let size = 0;
  try { while (true) { const { value, done } = await reader.read(); if (done) break;
    size += value.length; if (size > 1048576) throw new HttpError(413, "too_large", "Cover exceeds 1 MiB"); parts.push(value);
  } } finally { await reader.cancel(); }
  const bytes = new Uint8Array(size); let offset = 0; for (const part of parts) { bytes.set(part, offset); offset += part.length; }
  const actual = [...new Uint8Array(await crypto.subtle.digest("SHA-256", bytes))].map(v => v.toString(16).padStart(2, "0")).join("");
  if (actual !== digest) throw new HttpError(400, "digest_mismatch", "Cover checksum does not match");
  try { const info = parseWebp(bytes); if (info.animated || info.width > 2048 || info.height > 2048) throw Error(); }
  catch { throw new HttpError(400, "invalid_image", "Use a still WebP up to 2048 pixels"); }
  try { await env.DB.prepare("INSERT INTO library_covers (user_id, digest, bytes, size) VALUES (?, ?, ?, ?) ON CONFLICT(user_id,digest) DO NOTHING")
    .bind(claims.sub, digest, bytes.buffer, size).run(); }
  catch (error) { if (String(error).includes("cover_quota")) throw new HttpError(413, "cover_quota", "Cloud covers exceed 20 MiB"); throw error; }
  return new Response(null, { status: 204 });
}
