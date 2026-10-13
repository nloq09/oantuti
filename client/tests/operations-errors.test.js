import test from "node:test";
import assert from "node:assert/strict";
import { sessionRequest } from "../js/api/auth-api.js";
import { startTournament } from "../js/tournament/tournament-api.js";
import { errorText } from "../js/ui/toast.js";

test("overload and throttling remain explicit errors and mutations are never automatically retried", async () => {
  const original = globalThis.fetch;
  try {
    for (const code of ["SERVER_BUSY", "RATE_LIMITED"]) {
      let mutations = 0;
      globalThis.fetch = async url => url.endsWith("csrf")
        ? {ok: true, json: async () => ({headerName: "X-CSRF-TOKEN", token: "csrf"})}
        : (mutations++, {ok: false, status: code === "SERVER_BUSY" ? 503 : 429, json: async () => ({error: code})});
      await assert.rejects(startTournament("cup"), new RegExp(code));
      assert.equal(mutations, 1);
      await assert.rejects(sessionRequest("/api/matches", {mode: "HUMAN_VS_HUMAN"}), new RegExp(code));
      assert.equal(mutations, 2);
      assert.notEqual(errorText(code), errorText("unknown"));
    }
  } finally { globalThis.fetch = original; }
});
