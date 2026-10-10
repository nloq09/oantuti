import test from "node:test";
import assert from "node:assert/strict";
import { savedBots, uploadBot } from "../js/api/bot-api.js";
import { createMatch, joinMatch } from "../js/api/match-api.js";
import { ClientState } from "../js/game/game-state.js";

test("upload capability persists and create/join send only bot credentials", async () => {
  const originals = {
    localStorage: globalThis.localStorage,
    fetch: globalThis.fetch,
  };
  const bot = {
    id: "bot-id",
    token: "private-bot-token",
    name: "My bot",
    language: "CPP",
  };
  let stored = "[]";
  const requests = [];
  try {
    globalThis.localStorage = {
      getItem: () => stored,
      setItem: (_, value) => {
        stored = value;
      },
    };
    globalThis.fetch = async (url, options) => {
      requests.push({ url, options });
      return {
        ok: true,
        json: async () =>
          url === "/api/bots" ? bot : { matchId: "ROOM", token: "seat-token" },
      };
    };
    await uploadBot("My bot", new Blob(["source"]));
    assert.deepEqual(savedBots(), [bot]);
    assert.equal(requests[0].options.body.get("name"), "My bot");
    assert.equal((await createMatch("HUMAN_VS_HUMAN", bot)).bot, bot);
    assert.deepEqual(JSON.parse(requests[1].options.body), {
      mode: "HUMAN_VS_HUMAN",
      botId: bot.id,
      botToken: bot.token,
    });
    await joinMatch("ROOM", bot);
    assert.deepEqual(JSON.parse(requests[2].options.body), {
      botId: bot.id,
      botToken: bot.token,
    });
    stored = "{}";
    assert.deepEqual(savedBots(), []);
    stored = "invalid json";
    assert.deepEqual(savedBots(), []);
    globalThis.fetch = async () => ({
      ok: false,
      json: async () => ({ error: "BOT_FILE_SIZE" }),
    });
    await assert.rejects(uploadBot("bot", new Blob()), /BOT_FILE_SIZE/);
    assert.equal(stored, "invalid json");
  } finally {
    Object.assign(globalThis, originals);
  }
});

test("owners observe uploaded bots without manual move controls", () => {
  const state = new ClientState();
  state.snapshot = {
    status: "PLAYING",
    playerId: "P1",
    gameState: { currentPlayer: "P1" },
    players: [{ playerId: "P1", bot: true }],
  };
  assert.equal(state.ownTurn, false);
  state.snapshot.players[0].bot = false;
  assert.equal(state.ownTurn, true);
  state.snapshot.gameState.currentPlayer = "P2";
  assert.equal(state.ownTurn, false);
});
