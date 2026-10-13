import test from "node:test";
import assert from "node:assert/strict";
import { accountMatches, accountMatch, accountStats } from "../js/api/account-api.js";

test("history and stats use the session account, encode filters and never send an owner ID", async () => {
  const original=globalThis.fetch,calls=[];
  try {
    globalThis.fetch=async (url,options)=>{calls.push({url,...options});return {ok:true,json:async()=>({matches:[],total:0})};};
    await accountMatches("win",2,10);await accountMatch("archive/id");await accountStats();
    assert.equal(calls[0].url,"/api/account/matches?filter=win&page=2&size=10");assert.equal(calls[1].url,"/api/account/matches/archive%2Fid");assert.equal(calls[2].url,"/api/account/stats");
    for(const call of calls) {assert.equal(call.method,"GET");assert.equal(call.credentials,"same-origin");assert.equal(call.cache,"no-store");assert.equal(call.body,undefined);assert.ok(!call.url.includes("userId"));}
    globalThis.fetch=async ()=>({ok:false,json:async()=>({error:"UNAUTHENTICATED"})});await assert.rejects(accountMatches(),/UNAUTHENTICATED/);
  } finally {globalThis.fetch=original;}
});
