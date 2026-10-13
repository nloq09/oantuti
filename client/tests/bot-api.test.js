import test from "node:test";
import assert from "node:assert/strict";
import { savedBots, uploadBot, refreshBots, legacyBots, importLegacyBot, deleteBot } from "../js/api/bot-api.js";
import {
  createMatch,
  joinMatch,
  createBotBattle,
} from "../js/api/match-api.js";
import { ClientState } from "../js/game/game-state.js";

test("account list replaces local capabilities; upload and match use cookie and CSRF", async () => {
  const originals = { localStorage: globalThis.localStorage, fetch: globalThis.fetch };
  const bot = { id: "bot-id", name: "My bot", language: "CPP", status: "UPLOADED" };
  let stored = JSON.stringify([{...bot, token: "legacy-secret"}]);
  let owned = [], calls = [];
  try {
    globalThis.localStorage = { getItem: () => stored, setItem: (_, value) => stored = value };
    globalThis.fetch = async (url, options) => {
      if(url === "/api/auth/csrf") return { ok: true, json: async () => ({ headerName: "X-CSRF-TOKEN", token: "csrf" }) };
      calls.push({ url, options });
      if(url === "/api/bots") owned = [bot];
      return { ok: true, json: async () => url === "/api/account/bots" ? owned : url === "/api/bots" ? bot : {matchId: "ROOM", token: "seat-token"} };
    };
    assert.deepEqual(await refreshBots(), []);
    assert.equal(legacyBots()[0].token, "legacy-secret");
    const before = stored;
    await uploadBot("My bot", new Blob(["source"]));
    assert.deepEqual(savedBots(), [bot]);
    assert.equal(stored, before);
    const upload = calls.find(c => c.url === "/api/bots");
    assert.equal(upload.options.body.get("name"), "My bot");
    assert.equal(upload.options.headers["X-CSRF-TOKEN"], "csrf");
    assert.equal(upload.options.credentials, "same-origin");
    await createMatch("HUMAN_VS_HUMAN", {...bot, token: "ignored"});
    await joinMatch("ROOM", {...bot, token: "ignored"});
    const games = calls.filter(c => c.url.startsWith("/api/matches"));
    assert.deepEqual(JSON.parse(games[0].options.body), {mode: "HUMAN_VS_HUMAN", botId: bot.id});
    assert.deepEqual(JSON.parse(games[1].options.body), {botId: bot.id});
    assert.equal(games[0].options.headers["X-CSRF-TOKEN"], "csrf");
    owned = []; await refreshBots(); assert.deepEqual(savedBots(), []);
  } finally { Object.assign(globalThis, originals); }
});

test("legacy claims remove local proof only after successful account import", async () => {
  const originals = { localStorage: globalThis.localStorage, fetch: globalThis.fetch };
  const legacy = {id: "old", name: "Old bot", token: "proof"};
  let stored = JSON.stringify([legacy]), denied = true;
  try {
    globalThis.localStorage = {getItem: () => stored, setItem: (_, value) => stored = value};
    globalThis.fetch = async (url, options) => {
      if(url.endsWith("csrf")) return {ok: true, json: async () => ({headerName: "X-CSRF-TOKEN", token: "csrf"})};
      if(url.endsWith("/import")) {
        assert.deepEqual(JSON.parse(options.body), {botId: "old", botToken: "proof"});
        return {ok: !denied, json: async () => denied ? {error: "BOT_ALREADY_OWNED"} : {id: "old", name: "Old bot"}};
      }
      return {ok: true, json: async () => [{id: "old", name: "Old bot"}]};
    };
    await assert.rejects(importLegacyBot(legacy), /BOT_ALREADY_OWNED/);
    assert.deepEqual(legacyBots(), [legacy]);
    denied = false; await importLegacyBot(legacy);
    assert.deepEqual(legacyBots(), []); assert.equal(savedBots()[0].id, "old");
    globalThis.fetch = async () => ({ok: false, json: async () => ({error: "UNAUTHENTICATED"})});
    assert.deepEqual(await refreshBots(), []);
  } finally { Object.assign(globalThis, originals); }
});

test("soft deletion accepts 204 and refreshes authoritative bot list", async () => {
  const original = globalThis.fetch;
  try {
    globalThis.fetch = async (url, options) => {
      if(url.endsWith("csrf")) return {ok: true, json: async () => ({headerName: "X-CSRF-TOKEN", token: "csrf"})};
      if(options.method === "DELETE") {
        assert.equal(options.headers["X-CSRF-TOKEN"], "csrf");
        return {ok: true, status: 204};
      }
      return {ok: true, json: async () => []};
    };
    await deleteBot("old"); assert.deepEqual(savedBots(), []);
  } finally { globalThis.fetch = original; }
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
      if(url.endsWith("csrf")) return {ok:true,json:async()=>({headerName:"X-CSRF-TOKEN",token:"csrf"})};
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
        },
      },
      {
        url: "/api/matches/ROOM/join",
        body: { botId: "second" },
      },
    ]);
    requests.length = 0;
    await createBotBattle(bot);
    assert.deepEqual(requests[0].body, {
      mode: "HUMAN_VS_BOT",
      botId: "first",
    });
  } finally {
    globalThis.fetch = original;
  }
});
