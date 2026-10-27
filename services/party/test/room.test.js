import test from "node:test";
import assert from "node:assert/strict";
import { PartyRoom } from "../src/room.js";
import worker from "../src/index.js";

class Context {
  constructor(values = new Map()) {
    this.values = values;
    this.sockets = [];
    this.queue = Promise.resolve();
    this.storage = {
      get: async key => structuredClone(values.get(key)),
      put: async (key, value) => values.set(key, structuredClone(value)),
      setAlarm: async time => { this.alarmTime = time; },
      deleteAlarm: async () => { this.alarmTime = null; },
      deleteAll: async () => values.clear(),
    };
  }
  blockConcurrencyWhile(callback) {
    const result = this.queue.then(callback);
    this.queue = result.catch(() => {});
    return result;
  }
  getWebSockets() { return this.sockets; }
}

class Socket {
  constructor(id) { this.id = id; this.readyState = 1; this.messages = []; }
  deserializeAttachment() { return { id: this.id }; }
  send(value) { this.messages.push(JSON.parse(value)); }
  close(code, reason) { this.readyState = 3; this.closed = { code, reason }; }
}

const env = { PUBLIC_URL: "https://numa-party.example.com" };
function request(action, body = {}) {
  return new Request(`${env.PUBLIC_URL}/rooms/ABC234/${action}`, {
    method: "POST", body: JSON.stringify(body), headers: { "Content-Type": "application/json" },
  });
}
async function setup() {
  const context = new Context();
  const room = new PartyRoom(context, env);
  const created = await room.fetch(request("create", { hostName: "PC" }));
  assert.equal(created.status, 200);
  const host = (await created.json()).data.participant;
  const joined = await room.fetch(request("join", { name: "Android" }));
  const guest = (await joined.json()).data.participant;
  const hostSocket = new Socket(host.id);
  const guestSocket = new Socket(guest.id);
  context.sockets.push(hostSocket, guestSocket);
  return { context, room, host, guest, hostSocket, guestSocket };
}

test("creation returns private credentials and stores only token hashes", async () => {
  const { context, room, host, guest } = await setup();
  assert.equal(host.role, "host");
  assert.equal(guest.role, "guest");
  assert.equal(host.token.length, 64);
  assert.notEqual(host.token, guest.token);
  const persisted = JSON.stringify(context.values.get("room"));
  assert.ok(!persisted.includes(host.token) && !persisted.includes(guest.token));
  assert.equal(room.publicRoom().socketUrl, "wss://numa-party.example.com/rooms/ABC234/socket");
  assert.equal(room.publicRoom().participantCount, 2);
});

test("room membership survives hibernation without publishing credentials", async () => {
  const { context, host } = await setup();
  const restored = new PartyRoom(new Context(context.values), env);
  await restored.ready;
  assert.equal(restored.data.room.hostId, host.id);
  assert.ok(!JSON.stringify(restored.publicRoom()).includes("token"));
  assert.ok(!JSON.stringify(restored.peer(host.id)).includes("token"));
});

test("invalid websocket credentials are rejected before an upgrade", async () => {
  const { room, host } = await setup();
  const response = await room.fetch(new Request(`${env.PUBLIC_URL}/rooms/ABC234/socket?participantId=${host.id}&token=${"0".repeat(64)}`,
    { headers: { Upgrade: "websocket" } }));
  assert.equal(response.status, 401);
});

test("unknown and expired rooms cannot be joined", async () => {
  const missing = new PartyRoom(new Context(), env);
  assert.equal((await missing.fetch(request("join"))).status, 404);
  const { room } = await setup();
  room.data.room.expiresAt = Date.now() - 1;
  assert.equal((await room.fetch(request("join"))).status, 404);
});

test("concurrent guests cannot exceed the participant limit", async () => {
  const { room } = await setup();
  const responses = await Promise.all(Array.from({ length: 20 }, (_, i) => room.fetch(request("join", { name: `Guest ${i}` }))));
  assert.equal(responses.filter(r => r.status === 200).length, 10);
  assert.equal(room.publicRoom().participantCount, 12);
});

test("playback state comes only from the authenticated host", async () => {
  const { room, host, hostSocket, guestSocket } = await setup();
  await room.webSocketMessage(guestSocket, JSON.stringify({ type: "party:update", from: host.id, payload: { track: { id: "spoof" } } }));
  assert.equal(room.data.lastState, null);
  assert.equal(guestSocket.messages.at(-1).type, "error");
  await room.webSocketMessage(hostSocket, JSON.stringify({ type: "party:update", payload: { track: { id: "song" }, currentTime: 12 } }));
  assert.equal(guestSocket.messages.at(-1).payload.track.id, "song");
  assert.equal(room.data.lastState.from, host.id);
});

test("guest clock requests cannot spoof identity or control playback", async () => {
  const { room, host, guest, hostSocket, guestSocket } = await setup();
  await room.webSocketMessage(guestSocket, JSON.stringify({ type: "party:request", from: host.id, payload: { action: "play" } }));
  assert.equal(hostSocket.messages.length, 0);
  await room.webSocketMessage(guestSocket, JSON.stringify({ type: "party:request", from: host.id,
    payload: { action: "sync-clock", clientSentAt: 123 } }));
  assert.equal(hostSocket.messages.at(-1).from, guest.id);
  assert.equal(hostSocket.messages.at(-1).payload.clientSentAt, 123);
});

test("host transfer changes authority immediately", async () => {
  const { room, hostSocket, guest, guestSocket } = await setup();
  await room.webSocketMessage(guestSocket, JSON.stringify({ type: "party:host-transfer", participantId: guest.id }));
  assert.notEqual(room.data.room.hostId, guest.id);
  await room.webSocketMessage(hostSocket, JSON.stringify({ type: "party:host-transfer", participantId: guest.id }));
  assert.equal(room.data.room.hostId, guest.id);
  await room.webSocketMessage(hostSocket, JSON.stringify({ type: "party:close" }));
  assert.equal(room.data.room.closed, false);
  await room.webSocketMessage(guestSocket, JSON.stringify({ type: "party:close" }));
  assert.equal(room.data.room.closed, true);
});

test("host departure closes the room and invalidates all sessions", async () => {
  const { room, hostSocket, guestSocket } = await setup();
  hostSocket.readyState = 3;
  await room.webSocketClose(hostSocket);
  assert.equal(room.data.room.closed, true);
  assert.equal(guestSocket.messages.at(-1).reason, "host_left");
  assert.equal((await room.fetch(request("join"))).status, 404);
  assert.deepEqual(room.data.members, {});
});

test("guest departure preserves the host and broadcasts presence", async () => {
  const { room, host, hostSocket, guest, guestSocket } = await setup();
  guestSocket.readyState = 3;
  await room.webSocketClose(guestSocket);
  assert.equal(room.data.room.hostId, host.id);
  assert.equal(hostSocket.messages.at(-1).participantId, guest.id);
  assert.equal(room.publicRoom().participantCount, 1);
});

test("oversized UTF-8 messages close only the offending connection", async () => {
  const { room, hostSocket, guestSocket } = await setup();
  await room.webSocketMessage(guestSocket, "á".repeat(30001));
  assert.equal(guestSocket.closed.code, 1009);
  assert.equal(hostSocket.readyState, 1);
});

test("room expiry removes stored state and credentials", async () => {
  const { context, room, guestSocket } = await setup();
  room.data.room.expiresAt = Date.now() - 1;
  await room.alarm();
  assert.equal(context.values.size, 0);
  assert.equal(room.data, null);
  assert.equal(guestSocket.readyState, 3);
});

test("HTTP body limits apply even without a content length", async () => {
  const room = new PartyRoom(new Context(), env);
  const response = await room.fetch(request("create", { hostName: "x".repeat(5000) }));
  assert.equal(response.status, 400);
  assert.equal(room.data, null);
});

test("edge rate limits prevent requests reaching durable objects", async () => {
  let called = false;
  const response = await worker.fetch(new Request(`${env.PUBLIC_URL}/rooms`, { method: "POST", body: "{}" }), {
    ROOM_LIMITER: { limit: async () => ({ success: false }) },
    ROOMS: { idFromName: () => { called = true; } },
  });
  assert.equal(response.status, 429);
  assert.equal(called, false);
});
