import test from "node:test";
import assert from "node:assert/strict";
import { register, login, logout, currentUser } from "../js/api/auth-api.js";

test("session auth uses same-origin cookies and refreshes CSRF after every session change", async () => {
  const original = globalThis.fetch, calls = [];
  let tokens = 0;
  globalThis.fetch = async (url, options) => {
    calls.push({url,...options});
    return {ok:true,json:async () => url.endsWith("/csrf")
      ? {headerName:"X-CSRF-TOKEN",token:"csrf-" + ++tokens} : {authenticated:true}};
  };
  try {
    await register({username:"alice",email:"alice@example.com",password:"long-password",displayName:"Alice"});
    await login("alice", "long-password");
    await currentUser();
    await logout();
    assert.equal(tokens,3);
    assert.equal(calls[1].headers["X-CSRF-TOKEN"],"csrf-1");
    assert.equal(calls[3].headers["X-CSRF-TOKEN"],"csrf-2");
    assert.equal(calls[6].headers["X-CSRF-TOKEN"],"csrf-3");
    assert.ok(calls.every(call => call.credentials === "same-origin" && call.cache === "no-store"));
    assert.equal(calls[4].method,"GET");
    assert.deepEqual(JSON.parse(calls[3].body),{username:"alice",password:"long-password"});
    assert.ok(calls.every(call => !call.url.includes("password")));
  } finally { globalThis.fetch=original; }
});

test("failed CSRF and invalid login surface errors without retrying or storing credentials", async () => {
  const original=globalThis.fetch;let calls=0;
  try {
    globalThis.fetch=async () => {calls++;return {ok:false,json:async()=>({error:"UNAVAILABLE"})};};
    await assert.rejects(login("alice","long-password"), /UNAVAILABLE/);
    assert.equal(calls,1);
    globalThis.fetch=async url => ({ok:url.endsWith("csrf"),json:async()=>url.endsWith("csrf")
      ? {headerName:"X-CSRF-TOKEN",token:"token"} : {error:"INVALID_CREDENTIALS"}});
    await assert.rejects(login("alice","wrong-password"), /INVALID_CREDENTIALS/);
  } finally {globalThis.fetch=original;}
});
