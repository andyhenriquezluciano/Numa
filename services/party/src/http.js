const headers = {
  "Content-Type": "application/json; charset=utf-8",
  "Cache-Control": "no-store",
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Methods": "GET, POST, OPTIONS",
  "Access-Control-Allow-Headers": "Content-Type",
};

export function json(body, status = 200) {
  return new Response(JSON.stringify(body), { status, headers });
}

export function failure(message, status = 400) {
  return json({ ok: false, error: message }, status);
}

export async function bodyJson(request) {
  const reader = request.body?.getReader();
  if (!reader) return {};
  let bytes = 0;
  const chunks = [];
  while (true) {
    const { done, value } = await reader.read();
    if (done) break;
    bytes += value.byteLength;
    if (bytes > 4096) {
      await reader.cancel();
      throw new Error("Solicitud demasiado grande.");
    }
    chunks.push(value);
  }
  const buffer = new Uint8Array(bytes);
  let offset = 0;
  for (const chunk of chunks) { buffer.set(chunk, offset); offset += chunk.byteLength; }
  const body = JSON.parse(new TextDecoder().decode(buffer) || "{}");
  if (!body || typeof body !== "object" || Array.isArray(body)) throw new Error("Solicitud no válida.");
  return body;
}

export async function hashToken(token) {
  const bytes = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(token));
  return Array.from(new Uint8Array(bytes), value => value.toString(16).padStart(2, "0")).join("");
}

export function secretToken() {
  return Array.from(crypto.getRandomValues(new Uint8Array(32)), value => value.toString(16).padStart(2, "0")).join("");
}

export function roomCode() {
  const alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
  return Array.from(crypto.getRandomValues(new Uint8Array(6)), value => alphabet[value & 31]).join("");
}

export function displayName(value) {
  if (value != null && typeof value !== "string") throw new Error("Nombre no válido.");
  return value?.trim().replace(/[\u0000-\u001f]/g, " ").slice(0, 60) || "NÜMA";
}
