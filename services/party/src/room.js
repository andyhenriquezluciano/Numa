import { bodyJson, displayName, failure, hashToken, json, secretToken } from "./http.js";

const lifetime = 6 * 60 * 60 * 1000;
const maxParticipants = 12;
const maxMessage = 60000;

export class PartyRoom {
  constructor(ctx, env) {
    this.ctx = ctx;
    this.env = env;
    this.budgets = new Map();
    this.ready = ctx.blockConcurrencyWhile(async () => {
      this.data = await ctx.storage.get("room") || null;
    });
  }

  async fetch(request) {
    await this.ready;
    return this.ctx.blockConcurrencyWhile(() => this.fetchRequest(request));
  }

  async fetchRequest(request) {
    const url = new URL(request.url);
    try {
      if (url.pathname.endsWith("/create") && request.method === "POST") {
        if (this.data) return failure("La sala ya existe.", 409);
        const body = await bodyJson(request);
        const name = displayName(body.hostName);
        const now = Date.now();
        this.data = { room: { id: url.pathname.split("/")[2], hostId: "", createdAt: now,
          expiresAt: now + lifetime, closed: false, maxParticipants }, members: {}, lastState: null };
        const participant = await this.member(name);
        this.data.room.hostId = participant.id;
        await this.save();
        await this.ctx.storage.setAlarm(this.data.room.expiresAt);
        return json({ ok: true, data: { room: this.publicRoom(), participant: { ...participant, role: "host" } } });
      }
      if (!this.data || this.data.room.closed || Date.now() >= this.data.room.expiresAt)
        return failure("La sala no está disponible.", 404);
      if (url.pathname.endsWith("/join") && request.method === "POST") {
        const body = await bodyJson(request);
        const name = displayName(body.name);
        for (const [id, member] of Object.entries(this.data.members)) {
          if (id !== this.data.room.hostId && !member.openedAt && Date.now() - member.joinedAt > 60000)
            delete this.data.members[id];
        }
        if (Object.keys(this.data.members).length >= maxParticipants) return failure("La sala está llena.", 409);
        const participant = await this.member(name);
        await this.save();
        return json({ ok: true, data: { room: this.publicRoom(), participant } });
      }
      if (url.pathname.endsWith("/socket") && request.method === "GET") {
        if (request.headers.get("Upgrade")?.toLowerCase() !== "websocket") return failure("Se requiere WebSocket.", 426);
        const id = url.searchParams.get("participantId");
        const token = url.searchParams.get("token") || "";
        const member = this.data.members[id];
        if (!member || token.length !== 64 || member.tokenHash !== await hashToken(token))
          return failure("Acceso a la sala no válido.", 401);
        const pair = new WebSocketPair();
        const [client, server] = Object.values(pair);
        const previous = this.sockets(id);
        this.ctx.acceptWebSocket(server, [id]);
        server.serializeAttachment({ id });
        member.openedAt = Date.now();
        await this.save();
        for (const socket of previous) socket.close(1000, "reconnected");
        this.send(server, { type: "party:welcome", room: this.publicRoom(), role: this.peer(id).role,
          peers: Object.keys(this.data.members).filter(peer => peer !== id && this.sockets(peer).length).map(peer => this.peer(peer)),
          iceServers: [{ urls: ["stun:stun.cloudflare.com:3478"] }], lastState: this.data.lastState });
        this.broadcast({ type: "peer:joined", peer: this.peer(id) }, id);
        return new Response(null, { status: 101, webSocket: client });
      }
      return failure("Ruta no disponible.", 404);
    } catch {
      return failure("Solicitud no válida.", 400);
    }
  }

  async member(name) {
    const id = crypto.randomUUID();
    const token = secretToken();
    const joinedAt = Date.now();
    this.data.members[id] = { id, name, joinedAt, tokenHash: await hashToken(token), openedAt: 0 };
    return { id, name, joinedAt, role: "guest", token };
  }

  publicRoom() {
    const base = this.env.PUBLIC_URL.replace(/\/$/, "");
    const room = this.data.room;
    return { ...room, participantCount: Object.keys(this.data.members).length,
      socketUrl: `${base.replace(/^http/, "ws")}/rooms/${room.id}/socket`,
      joinUrl: `${base}/rooms/${room.id}/join`, shareUrl: `${base}/rooms/${room.id}` };
  }

  peer(id) {
    const member = this.data.members[id];
    return { id, name: member.name, joinedAt: member.joinedAt, role: id === this.data.room.hostId ? "host" : "guest" };
  }

  sockets(id) {
    return this.ctx.getWebSockets().filter(socket => socket.readyState === 1 && (!id || socket.deserializeAttachment()?.id === id));
  }

  send(socket, message) { try { socket.send(JSON.stringify(message)); } catch {} }

  broadcast(message, except = "") {
    for (const socket of this.sockets()) if (socket.deserializeAttachment()?.id !== except) this.send(socket, message);
  }

  save() { return this.ctx.storage.put("room", this.data); }

  async webSocketMessage(socket, raw) {
    await this.ready;
    return this.ctx.blockConcurrencyWhile(() => this.receive(socket, raw));
  }

  async receive(socket, raw) {
    const id = socket.deserializeAttachment()?.id;
    if (!this.data?.members[id] || this.data.room.closed) return socket.close(1008, "invalid_session");
    if (Date.now() >= this.data.room.expiresAt) return this.closeRoom("expired");
    if (typeof raw !== "string" || new TextEncoder().encode(raw).byteLength > maxMessage)
      return socket.close(1009, "message_too_large");
    const second = Math.floor(Date.now() / 1000);
    const budget = this.budgets.get(id);
    const used = budget?.second === second ? budget.used + 1 : 1;
    this.budgets.set(id, { second, used });
    if (used > 30) return socket.close(1008, "rate_limit");
    let message;
    try { message = JSON.parse(raw); } catch { return this.send(socket, { type: "error", error: "Mensaje no válido." }); }
    if (!message || typeof message !== "object" || Array.isArray(message)) return;
    const host = id === this.data.room.hostId;
    const denied = () => this.send(socket, { type: "error", error: "Solo el anfitrión puede realizar esta acción." });
    switch (message.type) {
      case "party:update":
        if (!host) return denied();
        if (!message.payload || typeof message.payload !== "object" || Array.isArray(message.payload)) return;
        this.data.lastState = { type: "party:update", from: id, payload: message.payload };
        await this.save();
        this.broadcast(this.data.lastState, id);
        break;
      case "party:request":
        if (message.payload?.action !== "sync-clock") return denied();
        for (const hostSocket of this.sockets(this.data.room.hostId))
          this.send(hostSocket, { type: "party:request", from: id, payload: { action: "sync-clock", clientSentAt: message.payload.clientSentAt } });
        break;
      case "signal":
        if (!this.data.members[message.to] || !["offer", "answer", "ice"].includes(message.kind)) return;
        if (!message.data || typeof message.data !== "object" || Array.isArray(message.data)) return;
        for (const target of this.sockets(message.to))
          this.send(target, { type: "signal", from: id, kind: message.kind, data: message.data });
        break;
      case "party:host-transfer":
        if (!host) return denied();
        if (!this.data.members[message.participantId] || !this.sockets(message.participantId).length) return;
        this.data.room.hostId = message.participantId;
        await this.save();
        this.broadcast({ type: "party:host-changed", hostId: message.participantId });
        break;
      case "party:close":
        if (!host) return denied();
        await this.closeRoom("host_closed");
        break;
    }
  }

  async webSocketClose(socket) {
    await this.ready;
    return this.ctx.blockConcurrencyWhile(() => this.disconnected(socket));
  }
  async webSocketError(socket) { return this.webSocketClose(socket); }

  async disconnected(socket) {
    await this.ready;
    const id = socket.deserializeAttachment()?.id;
    if (!this.data?.members[id] || this.data.room.closed) return;
    if (this.sockets(id).some(current => current !== socket)) return;
    this.budgets.delete(id);
    if (id === this.data.room.hostId) return this.closeRoom("host_left");
    delete this.data.members[id];
    await this.save();
    this.broadcast({ type: "peer:left", participantId: id });
  }

  async closeRoom(reason) {
    if (!this.data || this.data.room.closed) return;
    this.data.room.closed = true;
    this.broadcast({ type: "party:closed", reason });
    for (const socket of this.sockets()) socket.close(1000, reason);
    this.data.members = {};
    this.data.lastState = null;
    await this.save();
    await this.ctx.storage.setAlarm(Date.now() + 60000);
  }

  async alarm() {
    await this.ready;
    return this.ctx.blockConcurrencyWhile(() => this.expire());
  }

  async expire() {
    if (!this.data) return;
    if (!this.data.room.closed && Date.now() < this.data.room.expiresAt)
      return this.ctx.storage.setAlarm(this.data.room.expiresAt);
    await this.closeRoom("expired");
    this.data = null;
    await this.ctx.storage.deleteAll();
    await this.ctx.storage.deleteAlarm();
  }
}
