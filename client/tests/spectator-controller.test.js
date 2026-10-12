import test from "node:test";
import assert from "node:assert/strict";
import { ClientState } from "../js/game/game-state.js";
import { GameSocket } from "../js/socket/game-socket.js";
test("spectator state is read only and socket never sends seat credentials or player actions", () => {
  const original = {
    WebSocket: globalThis.WebSocket,
    location: globalThis.location,
  };
  class Socket {
    static OPEN = 1;
    constructor() {
      this.readyState = 1;
      this.messages = [];
    }
    send(value) {
      this.messages.push(JSON.parse(value));
    }
    close() {}
  }
  try {
    globalThis.WebSocket = Socket;
    globalThis.location = { protocol: "https:", host: "game.test" };
    const socket = new GameSocket(
      { matchId: "M", playerId: "P1", token: "secret", role: "SPECTATOR" },
      () => {},
      () => {},
    );
    socket.socket.onopen();
    assert.deepEqual(socket.socket.messages, [
      { type: "SPECTATE_MATCH", matchId: "M" },
    ]);
    assert.equal(socket.send("ACTION", {}), false);
    assert.equal(socket.send("READY"), false);
    const state = new ClientState();
    state.role = "SPECTATOR";
    state.connected = true;
    state.snapshot = {
      status: "PLAYING",
      playerId: "P1",
      players: [],
      gameState: { currentPlayer: "P1" },
    };
    assert.equal(state.interactive, false);
    assert.equal(state.ownTurn, false);
    socket.close();
  } finally {
    Object.assign(globalThis, original);
  }
});
