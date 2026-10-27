import { bodyJson, failure, json, roomCode } from "./http.js";
export { PartyRoom } from "./room.js";

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    if (request.method === "OPTIONS") return json({ ok: true });
    if (url.pathname === "/health" && request.method === "GET") return json({ ok: true, service: "numa-party" });
    try {
      if (request.method === "POST" || request.headers.get("Upgrade")?.toLowerCase() === "websocket") {
        const key = request.headers.get("CF-Connecting-IP") || "unknown";
        if (env.ROOM_LIMITER && !(await env.ROOM_LIMITER.limit({ key })).success)
          return failure("Demasiadas solicitudes. Inténtalo más tarde.", 429);
      }
      if (url.pathname === "/rooms" && request.method === "POST") {
        const body = await bodyJson(request);
        for (let attempt = 0; attempt < 5; attempt++) {
          const code = roomCode();
          const room = env.ROOMS.get(env.ROOMS.idFromName(code));
          const response = await room.fetch(new Request(`${url.origin}/rooms/${code}/create`, {
            method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(body),
          }));
          if (response.status !== 409) return response;
        }
        return failure("No se pudo crear la sala. Inténtalo de nuevo.", 503);
      }
      const match = url.pathname.match(/^\/rooms\/([A-HJ-NP-Z2-9]{6})\/(join|socket)$/);
      if (match && ((match[2] === "join" && request.method === "POST") ||
          (match[2] === "socket" && request.method === "GET"))) {
        return env.ROOMS.get(env.ROOMS.idFromName(match[1])).fetch(request);
      }
      const invitation = url.pathname.match(/^\/rooms\/([A-HJ-NP-Z2-9]{6})$/);
      if (invitation && request.method === "GET") return new Response(
        `Sala de NÜMA: ${invitation[1]}\n\nAbre NÜMA, entra en Salas e introduce este código.`,
        { headers: { "Content-Type": "text/plain; charset=utf-8", "Cache-Control": "no-store" } });
      return failure("Ruta no disponible.", 404);
    } catch {
      return failure("Solicitud no válida.", 400);
    }
  },
};
