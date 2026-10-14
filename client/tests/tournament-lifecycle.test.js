import test from "node:test";
import assert from "node:assert/strict";
import {openRegistration,closeRegistration} from "../js/tournament/tournament-api.js";

test("lifecycle controls use session and CSRF, encode tournament ID and surface stale-state rejection without retry",async()=>{
  const original=globalThis.fetch,calls=[];
  try {
    globalThis.fetch=async(url,options)=>url.endsWith("csrf")?{ok:true,json:async()=>({headerName:"X-CSRF-TOKEN",token:"csrf"})}:
      (calls.push({url,...options}),{ok:true,json:async()=>({id:"T",status:"REGISTRATION"})});
    await openRegistration("T/id");await closeRegistration("T/id");
    assert.deepEqual(calls.map(call=>call.url),["/api/tournaments/T%2Fid/open-registration","/api/tournaments/T%2Fid/close-registration"]);
    for(const call of calls){assert.equal(call.method,"POST");assert.equal(call.credentials,"same-origin");assert.equal(call.headers["X-CSRF-TOKEN"],"csrf");assert.equal(call.body,"{}");}
    globalThis.fetch=async(url,options)=>url.endsWith("csrf")?{ok:true,json:async()=>({headerName:"X-CSRF-TOKEN",token:"csrf"})}:
      (calls.push({url,...options}),{ok:false,json:async()=>({error:"INVALID_TOURNAMENT_TRANSITION"})});
    await assert.rejects(openRegistration("T"),/INVALID_TOURNAMENT_TRANSITION/);assert.equal(calls.length,3);
  }finally{globalThis.fetch=original;}
});
