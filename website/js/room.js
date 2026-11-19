const params = new URLSearchParams(location.search);
const roomCode = (params.get("code") || "").toUpperCase();
const video = document.getElementById("remote-video");
const placeholder = document.getElementById("video-placeholder");
const statusLabel = document.getElementById("connection-label");
const roomLabel = document.getElementById("room-label");
const messages = document.getElementById("messages");
const chatForm = document.getElementById("chat-form");
const chatInput = document.getElementById("chat-input");

roomLabel.textContent = roomCode ? `ROOM ${roomCode}` : "NO ROOM";
let ws;
let peer;
let playerConnected = false;
let pendingCandidates = [];

function setStatus(text) { statusLabel.textContent = text; }

function addMessage(from, text, system = false) {
  const item = document.createElement("div");
  item.className = system ? "system-message" : "message";
  if (!system) {
    const who = document.createElement("strong");
    who.textContent = from;
    const body = document.createElement("p");
    body.textContent = text;
    item.append(who, body);
  } else {
    item.textContent = text;
  }
  messages.appendChild(item);
  messages.scrollTop = messages.scrollHeight;
}

function send(packet) {
  if (ws && ws.readyState === WebSocket.OPEN) ws.send(JSON.stringify(packet));
}

function createPeer() {
  if (peer) peer.close();
  peer = new RTCPeerConnection({
    iceServers: [{ urls: "stun:stun.l.google.com:19302" }]
  });

  peer.ontrack = event => {
    video.srcObject = event.streams[0];
    placeholder.hidden = true;
    setStatus("WATCHING");
  };
  peer.onicecandidate = event => {
    if (event.candidate) send({
      type: "signal",
      to: "player",
      data: { kind: "candidate", candidate: event.candidate }
    });
  };
  peer.onconnectionstatechange = () => {
    if (peer.connectionState === "connected") setStatus("LIVE");
    if (["failed", "disconnected"].includes(peer.connectionState)) setStatus("RECONNECTING");
  };
  return peer;
}

async function handleSignal(data) {
  if (!peer) createPeer();
  if (data.kind === "offer") {
    await peer.setRemoteDescription(data.description);
    for (const candidate of pendingCandidates) { try { await peer.addIceCandidate(candidate); } catch (err) { console.warn(err); } }
    pendingCandidates = [];
    const answer = await peer.createAnswer();
    await peer.setLocalDescription(answer);
    send({ type: "signal", to: "player", data: { kind: "answer", description: peer.localDescription } });
  } else if (data.kind === "candidate" && data.candidate) {
    if (!peer.remoteDescription) pendingCandidates.push(data.candidate);
    else { try { await peer.addIceCandidate(data.candidate); } catch (err) { console.warn(err); } }
  }
}

function connect() {
  if (!roomCode || roomCode.length !== 6) {
    setStatus("INVALID ROOM");
    addMessage("", "This room link is missing a valid code.", true);
    return;
  }
  const scheme = location.protocol === "https:" ? "wss" : "ws";
  ws = new WebSocket(`${scheme}://${location.host}/ws`);

  ws.onopen = () => send({ type: "join-room", code: roomCode });
  ws.onmessage = async event => {
    const msg = JSON.parse(event.data);
    if (msg.type === "joined-room") {
      setStatus(msg.playerConnected ? "CONNECTING TO PLAYER" : "WAITING FOR PLAYER");
      addMessage("", `Joined private room ${roomCode}.`, true);
    } else if (msg.type === "signal") {
      await handleSignal(msg.data);
    } else if (msg.type === "chat") {
      if (!msg.localEcho) addMessage(msg.from, msg.text);
    } else if (msg.type === "player-status") {
      setStatus(msg.connected ? "PLAYER ONLINE" : "PLAYER OFFLINE");
    } else if (msg.type === "room-closed") {
      setStatus("ROOM ENDED");
      addMessage("", msg.reason, true);
      placeholder.hidden = false;
      video.srcObject = null;
    } else if (msg.type === "error") {
      setStatus("ERROR");
      addMessage("", msg.message, true);
    }
  };
  ws.onclose = () => setStatus("DISCONNECTED");
  ws.onerror = () => setStatus("CONNECTION ERROR");
}

chatForm.addEventListener("submit", event => {
  event.preventDefault();
  const text = chatInput.value.trim();
  if (!text) return;
  if (!ws || ws.readyState !== WebSocket.OPEN) {
    addMessage("", "Not connected to the room.", true);
    return;
  }
  send({ type: "chat", text });
  addMessage("You", text);
  chatInput.value = "";
});

window.addEventListener("beforeunload", () => {
  if (peer) peer.close();
  if (ws) ws.close();
});

connect();
