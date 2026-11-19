const express = require("express");
const http = require("http");
const path = require("path");
const { WebSocketServer, WebSocket } = require("ws");
const crypto = require("crypto");

const PORT = Number(process.env.PORT || 3000);
const app = express();
const server = http.createServer(app);
const wss = new WebSocketServer({ server, path: "/ws" });
const rooms = new Map();

app.use(express.static(path.join(__dirname, "..", "website")));
app.get("/health", (_req, res) => res.json({ ok: true, rooms: rooms.size }));

function makeCode() {
  const alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
  let code;
  do {
    code = Array.from(crypto.randomBytes(6), b => alphabet[b % alphabet.length]).join("");
  } while (rooms.has(code));
  return code;
}

function send(ws, payload) {
  if (ws && ws.readyState === WebSocket.OPEN) {
    ws.send(JSON.stringify(payload));
  }
}

function roomFor(ws) {
  return ws.roomCode ? rooms.get(ws.roomCode) : null;
}

function removeSocket(ws) {
  const room = roomFor(ws);
  if (!room) return;

  if (room.player === ws) {
    for (const coach of room.coaches.values()) {
      send(coach, { type: "room-closed", reason: "The player ended the session." });
      coach.roomCode = null;
      coach.role = null;
    }
    rooms.delete(ws.roomCode);
  } else {
    room.coaches.delete(ws.peerId);
    send(room.player, { type: "coach-left", peerId: ws.peerId });
    if (room.coaches.size === 0 && !room.player) rooms.delete(ws.roomCode);
  }
  ws.roomCode = null;
  ws.peerId = null;
  ws.role = null;
}

wss.on("connection", ws => {
  ws.isAlive = true;
  ws.on("pong", () => { ws.isAlive = true; });

  ws.on("message", raw => {
    let msg;
    try { msg = JSON.parse(raw.toString()); }
    catch { return send(ws, { type: "error", message: "Invalid message format." }); }

    if (msg.type === "create-room") {
      if (ws.roomCode) return send(ws, { type: "error", message: "This connection already belongs to a room." });
      const code = makeCode();
      const room = { player: ws, coaches: new Map(), createdAt: Date.now() };
      rooms.set(code, room);
      ws.roomCode = code;
      ws.role = "player";
      return send(ws, { type: "room-created", code });
    }

    if (msg.type === "join-room") {
      const code = String(msg.code || "").toUpperCase().replace(/[^A-Z0-9]/g, "");
      const room = rooms.get(code);
      if (!room) return send(ws, { type: "error", message: "Room not found. Check the code and try again." });
      if (ws.roomCode) return send(ws, { type: "error", message: "This connection already belongs to a room." });
      if (room.coaches.size >= 4) return send(ws, { type: "error", message: "This room is full (maximum 4 coaches in 0.1)." });

      const peerId = crypto.randomUUID();
      ws.roomCode = code;
      ws.role = "coach";
      ws.peerId = peerId;
      room.coaches.set(peerId, ws);
      send(ws, { type: "joined-room", code, peerId, playerConnected: room.player.readyState === WebSocket.OPEN });
      send(room.player, { type: "coach-joined", peerId });
      return;
    }

    const room = roomFor(ws);
    if (!room) return send(ws, { type: "error", message: "Join or create a room first." });

    if (msg.type === "signal") {
      const target = msg.to === "player" ? room.player : room.coaches.get(msg.to);
      if (!target) return send(ws, { type: "error", message: "Signaling target is no longer connected." });
      return send(target, { type: "signal", from: ws.peerId || "player", data: msg.data });
    }

    if (msg.type === "chat") {
      const text = String(msg.text || "").trim().slice(0, 300);
      if (!text) return;
      const packet = {
        type: "chat",
        from: ws.role === "player" ? "Player" : "Coach",
        text,
        timestamp: Date.now()
      };
      if (ws.role === "player") {
        for (const coach of room.coaches.values()) send(coach, packet);
      } else {
        send(room.player, packet);
        send(ws, { ...packet, localEcho: true });
      }
      return;
    }

    if (msg.type === "player-ready") {
      for (const [peerId, coach] of room.coaches) send(ws, { type: "coach-available", peerId });
      for (const coach of room.coaches.values()) send(coach, { type: "player-status", connected: true });
      return;
    }

    if (msg.type === "close-room") {
      removeSocket(ws);
      send(ws, { type: "room-ended" });
      return;
    }
  });

  ws.on("close", () => removeSocket(ws));
  ws.on("error", () => removeSocket(ws));
});

const heartbeat = setInterval(() => {
  for (const ws of wss.clients) {
    if (!ws.isAlive) { ws.terminate(); continue; }
    ws.isAlive = false;
    ws.ping();
  }
}, 30000);
wss.on("close", () => clearInterval(heartbeat));

server.listen(PORT, () => {
  console.log(`Live Game Coaching 0.1 running at http://localhost:${PORT}`);
});
