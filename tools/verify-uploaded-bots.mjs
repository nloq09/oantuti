// Smoke-test repository-owned source bots through the real upload/room/WebSocket APIs.
// Start a test server first, then: node tools/verify-uploaded-bots.mjs
import assert from "node:assert/strict";
import { randomUUID } from "node:crypto";
import { readFile } from "node:fs/promises";

const base = process.env.RPS_TEST_URL || "http://127.0.0.1:8081";
const cookies = new Map();
async function sessionFetch(path, options = {}) {
  const response = await fetch(base + path, {...options, headers: {...options.headers, Cookie: [...cookies].map(([name, value]) => name + "=" + value).join("; ")}});
  for(const cookie of response.headers.getSetCookie()) {
    const pair = cookie.split(";")[0], equal = pair.indexOf("=");cookies.set(pair.slice(0, equal), pair.slice(equal + 1));
  }
  return response;
}
async function request(path, options = {}) {
  if(options.method && options.method !== "GET") {
    const response = await sessionFetch("/api/auth/csrf"), csrf = await response.json();
    assert.equal(response.status, 200);
    options = {...options, headers: {...options.headers, [csrf.headerName]: csrf.token}};
  }
  const response = await sessionFetch(path, options);
  const data = await response.json();
  assert.ok(response.ok, `${path}: HTTP ${response.status} ${data.error || ""}`);
  return data;
}
const username = "verify_" + randomUUID().slice(0, 8), password = randomUUID() + randomUUID();
await request("/api/auth/register", {method: "POST", headers: {"Content-Type":"application/json"}, body: JSON.stringify({username, email: username + "@example.com", displayName: "Verification", password})});
await request("/api/auth/login", {method: "POST", headers: {"Content-Type":"application/json"}, body: JSON.stringify({username, password})});
const capabilities = await request("/api/bots/capabilities");
for (const [language, filename] of [["PYTHON", "main.py"], ["CPP", "main.cpp"], ["JAVA", "Main.java"]]) {
  assert.equal(capabilities.languages[language], true, `${language}: Docker/image not ready`);
  const body = new FormData();
  body.append("name", `Verification ${language}`);
  body.append("file", new File([await readFile(new URL(`../client/assets/bot-examples/${filename}`, import.meta.url))], filename));
  const bot = await request("/api/bots", { method: "POST", body });
  const seat = await request("/api/matches", {
    method: "POST", headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ mode: "HUMAN_VS_BOT", seed: 42, botId: bot.id }),
  });
  const socket = new WebSocket(base.replace(/^http/, "ws") + "/ws/game");
  const states = [], waiters = [];
  socket.addEventListener("message", event => {
    const message = JSON.parse(event.data);
    if (message.type !== "GAME_STATE") return;
    const snapshot = message.payload.snapshot;
    const waiter = waiters.find(value => value.predicate(snapshot));
    if (waiter) { waiters.splice(waiters.indexOf(waiter), 1); clearTimeout(waiter.timer); waiter.resolve(snapshot); }
    else states.push(snapshot);
  });
  function next(predicate, timeout = 35000) {
    const index = states.findIndex(predicate);
    if (index !== -1) return Promise.resolve(states.splice(index, 1)[0]);
    return new Promise((resolve, reject) => {
      const waiter = { predicate, resolve, timer: null };
      waiter.timer = setTimeout(() => { waiters.splice(waiters.indexOf(waiter), 1); reject(new Error(`${language}: state timeout`)); }, timeout);
      waiters.push(waiter);
    });
  }
  const credentials = { matchId: seat.matchId, playerId: seat.playerId, token: seat.token };
  try {
    await new Promise((resolve, reject) => {
      const timer = setTimeout(() => reject(new Error("WebSocket connection timeout")), 10000);
      socket.addEventListener("open", () => { clearTimeout(timer); resolve(); }, { once: true });
      socket.addEventListener("error", () => { clearTimeout(timer); reject(new Error("WebSocket connection failed")); }, { once: true });
    });
    socket.send(JSON.stringify({ type: "JOIN_MATCH", ...credentials }));
    const initial = await next(() => true);
    assert.equal(initial.status, "PLAYING");
    assert.equal(initial.players.find(player => player.playerId === "P1").botName, bot.name);
    const advanced = await next(snapshot => snapshot.status === "FINISHED" || snapshot.gameState.sequence > initial.gameState.sequence);
    assert.equal(advanced.botError, null, `${language}: bot failed`);
    assert.equal(advanced.status, "PLAYING");
    assert.ok(advanced.gameState.events.some(event => event.type === "PIECE_MOVED"));
    socket.send(JSON.stringify({ type: "LEAVE", ...credentials }));
    const finished = await next(snapshot => snapshot.status === "FINISHED", 5000);
    assert.equal(finished.gameState.winner, "P2");
    console.log(`${language}: PASS (upload, auto placement, container move, owner leave)`);
  } finally {
    if (socket.readyState === WebSocket.OPEN) socket.send(JSON.stringify({ type: "LEAVE", ...credentials }));
    socket.close();
    for (const waiter of waiters) clearTimeout(waiter.timer);
  }
}
