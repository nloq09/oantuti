import test from "node:test";
import assert from "node:assert/strict";
import { savedBots, uploadBot } from "../js/api/bot-api.js";
import {
  createMatch,
  joinMatch,
  createBotBattle,
} from "../js/api/match-api.js";
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

test("bot battles create a real room and attach the second bot through join", async () => {
  const original = globalThis.fetch;
  const requests = [];
  const bot = { id: "first", token: "first-token" },
    opponent = { id: "second", token: "second-token" };
  try {
    globalThis.fetch = async (url, options) => {
      requests.push({ url, body: JSON.parse(options.body) });
      return {
        ok: true,
        json: async () => ({
          matchId: "ROOM",
          playerId: url.endsWith("join") ? "P2" : "P1",
          token: "seat-token",
        }),
      };
    };
    await assert.rejects(createBotBattle(), /BOT_REQUIRED/);
    assert.equal(requests.length, 0);
    const seat = await createBotBattle(bot, opponent);
    assert.equal(seat.playerId, "P1");
    assert.deepEqual(requests, [
      {
        url: "/api/matches",
        body: {
          mode: "HUMAN_VS_HUMAN",
          botId: "first",
          botToken: "first-token",
        },
      },
      {
        url: "/api/matches/ROOM/join",
        body: { botId: "second", botToken: "second-token" },
      },
    ]);
    requests.length = 0;
    await createBotBattle(bot);
    assert.deepEqual(requests[0].body, {
      mode: "HUMAN_VS_BOT",
      botId: "first",
      botToken: "first-token",
    });
  } finally {
    globalThis.fetch = original;
  }
});
