import test from "node:test";
import assert from "node:assert/strict";
import {
  createTournament,
  addParticipant,
  startTournament,
  removeParticipant,
  canManageTournament,
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

test("ownership uses account identity; local capability tokens never authorize mutations", async () => {
  const originalFetch = globalThis.fetch, originalStorage = globalThis.localStorage, calls = [];
  globalThis.localStorage = {getItem: () => "legacy-owner-secret", setItem: () => {throw new Error("must not store capabilities");}};
  globalThis.fetch = async (url, options) => {
    if(url.endsWith("csrf")) return {ok:true,json:async()=>({headerName:"X-CSRF-TOKEN",token:"csrf"})};
    calls.push({url,...options});return {ok:true,json:async()=>({id:"T",ownerUserId:"alice"})};
  };
  try {
    await createTournament("Open Cup");await startTournament("T");await removeParticipant("T","P");
    for(const call of calls) {assert.equal(call.headers["X-Tournament-Token"],undefined);assert.equal(call.headers["X-CSRF-TOKEN"],"csrf");assert.equal(call.credentials,"same-origin");}
    assert.equal(canManageTournament({ownerUserId:"alice"},{id:"alice",role:"USER"}),true);
    assert.equal(canManageTournament({ownerUserId:"alice"},{id:"bob",role:"USER"}),false);
    assert.equal(canManageTournament({ownerUserId:"alice"},{id:"bob",role:"ADMIN"}),true);
    assert.equal(canManageTournament({ownerUserId:null},undefined),false);
  } finally {globalThis.fetch=originalFetch;globalThis.localStorage=originalStorage;}
});
