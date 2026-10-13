import test from "node:test";
import assert from "node:assert/strict";
import {
  createTournament,
  addParticipant,
  startTournament,
  removeParticipant,
  tournamentOwnerToken,
} from "../js/tournament/tournament-api.js";
test("tournament requests preserve authorization credentials and use correct methods", async () => {
  const original = globalThis.fetch;
  const calls = [];
  try {
    globalThis.fetch = async (url, options) => {
      if(url.endsWith("csrf")) return {ok:true,json:async()=>({headerName:"X-CSRF-TOKEN",token:"csrf"})};
      calls.push({ url, ...options });
      return { ok: true, json: async () => ({ id: "T" }) };
    };
    await createTournament("Cup");
    await addParticipant("T", { id: "B", token: "secret", name: "Bot" });
    await startTournament("T");
    await removeParticipant("T", "P");
    assert.deepEqual(JSON.parse(calls[0].body), {
      name: "Cup",
      format: "ROUND_ROBIN",
    });
    assert.deepEqual(JSON.parse(calls[1].body), {
      botId: "B",
      displayName: "Bot",
    });
    assert.equal(calls[3].method, "DELETE");
    globalThis.fetch = async () => ({
      ok: false,
      json: async () => ({ error: "TOURNAMENT_LOCKED" }),
    });
    await assert.rejects(startTournament("T"), /TOURNAMENT_LOCKED/);
  } finally {
    globalThis.fetch = original;
  }
});

test("owner capability is stored locally and sent only to its tournament; withdrawal uses session authorization", async () => {
  const originalFetch = globalThis.fetch, originalStorage = globalThis.localStorage;
  const entries = new Map(), calls = [];
  globalThis.localStorage = { getItem: key => entries.get(key), setItem: (key, value) => entries.set(key, value) };
  globalThis.fetch = async (url, options) => {
    if(url.endsWith("csrf")) return {ok:true,json:async()=>({headerName:"X-CSRF-TOKEN",token:"csrf"})};
    calls.push({ url, ...options });
    return { ok: true, json: async () => ({ id: "T", ownerToken: "owner-secret" }) };
  };
  try {
    await createTournament("Open Cup");
    assert.equal(tournamentOwnerToken("T"), "owner-secret");
    await startTournament("T");
    assert.equal(calls[1].headers["X-Tournament-Token"], "owner-secret");
    await startTournament("OTHER");
    assert.equal(calls[2].headers["X-Tournament-Token"], undefined);
    entries.clear();
    await removeParticipant("T", "P", { token: "bot-secret" });
    assert.equal(calls[3].headers["X-Bot-Token"], undefined);
    assert.equal(calls[3].headers["X-CSRF-TOKEN"], "csrf");
    assert.equal(calls[3].headers["X-Tournament-Token"], undefined);
    assert.ok(!calls[3].url.includes("secret"));
  } finally {
    globalThis.fetch = originalFetch;
    if (originalStorage === undefined) delete globalThis.localStorage;
    else globalThis.localStorage = originalStorage;
  }
});
