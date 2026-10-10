import test from "node:test";
import assert from "node:assert/strict";
import { GameSocket } from "../js/socket/game-socket.js";

test("join, reconnect backoff, full snapshot synchronization and stop", () => {
  const originals = {
    WebSocket: globalThis.WebSocket,
    location: globalThis.location,
    setTimeout,
    clearTimeout,
  };
  const sockets = [],
    timers = [],
    statuses = [],
    messages = [];
  class FakeSocket {
    static OPEN = 1;
    constructor(url) {
      this.url = url;
      this.readyState = 0;
      this.sent = [];
      sockets.push(this);
    }
    send(text) {
      this.sent.push(JSON.parse(text));
    }
    open() {
      this.readyState = 1;
      this.onopen();
    }
    message(type, payload = {}) {
      this.onmessage({ data: JSON.stringify({ type, payload }) });
    }
    close() {
      this.readyState = 3;
      this.onclose();
    }
  }
  try {
    globalThis.location = { protocol: "https:", host: "game.example" };
    globalThis.WebSocket = FakeSocket;
    globalThis.setTimeout = (callback, delay) => {
      const timer = { callback, delay };
      timers.push(timer);
      return timer;
    };
    globalThis.clearTimeout = (timer) => {
      if (timer) timer.cancelled = true;
    };
    const seat = { matchId: "ROOM", playerId: "P1", token: "private-seat" };
    const game = new GameSocket(
      seat,
      (m) => messages.push(m),
      (s) => statuses.push(s),
    );
    assert.equal(sockets[0].url, "wss://game.example/ws/game");
    assert.equal(game.send("READY"), false);
    sockets[0].open();
    assert.deepEqual(sockets[0].sent[0], { type: "JOIN_MATCH", ...seat });
    sockets[0].message("GAME_STATE");
    sockets[0].message("GAME_STATE");
    assert.equal(statuses.filter((s) => s === "connected").length, 1);
    assert.equal(messages.length, 2);
    assert.equal(messages[0].resync, true);
    assert.equal(messages[1].resync, undefined);
    for (const delay of [1000, 2000, 3000, 5000, 5000]) {
      sockets.at(-1).close();
      assert.equal(timers.at(-1).delay, delay);
      timers.at(-1).callback();
      sockets.at(-1).open();
      assert.equal(sockets.at(-1).sent[0].type, "RESUME_MATCH");
    }
    sockets.at(-1).message("GAME_STATE");
    assert.equal(game.send("ACTION", { type: "PASS" }, 12), true);
    assert.equal(sockets.at(-1).sent.at(-1).sequence, 12);
    sockets.at(-1).close();
    assert.equal(timers.at(-1).delay, 1000);
    const count = timers.length;
    game.close();
    assert.equal(timers.length, count);
    assert.equal(timers.at(-1).cancelled, true);
  } finally {
    Object.assign(globalThis, originals);
  }
});
