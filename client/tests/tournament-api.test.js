import test from "node:test";
import assert from "node:assert/strict";
import {
  createTournament,
  addParticipant,
  startTournament,
  removeParticipant,
} from "../js/tournament/tournament-api.js";
test("tournament requests preserve authorization credentials and use correct methods", async () => {
  const original = globalThis.fetch;
  const calls = [];
  try {
    globalThis.fetch = async (url, options) => {
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
      botToken: "secret",
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
