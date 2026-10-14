import test from "node:test";
import assert from "node:assert/strict";
import { adminRequest, AdminError } from "../js/admin/admin-api.js";
test("administration requests retain cookie CSRF and structured status without automatic mutation retries",async()=>{
  const previous=globalThis.fetch,calls=[];
  globalThis.fetch=async(url,options)=>{calls.push({url,...options});return {ok:true,status:200,json:async()=>url.includes("csrf")?{headerName:"X-CSRF-TOKEN",token:"test-csrf"}:{items:[]}};};
  try{
    await adminRequest("dashboard");await adminRequest("users/id/disable",{reason:"Audit reason"});
    assert.equal(calls[0].credentials,"same-origin");assert.equal(calls[2].method,"POST");assert.equal(calls[2].headers["X-CSRF-TOKEN"],"test-csrf");assert.deepEqual(JSON.parse(calls[2].body),{reason:"Audit reason"});
    globalThis.fetch=async()=>({ok:false,status:409,json:async()=>({error:"STALE_TOURNAMENT"})});
    await assert.rejects(adminRequest("dashboard"),error=>error instanceof AdminError&&error.status===409&&error.message==="STALE_TOURNAMENT");
  }finally{globalThis.fetch=previous;}
});
