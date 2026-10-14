import test from "node:test";
import assert from "node:assert/strict";
import {allowedControls} from "../js/tournament/tournament-dashboard.js";
import {tournamentControl,tournamentMatchOperation,tournamentParticipantStatus} from "../js/tournament/tournament-api.js";
test("administrative actions retain revision, reason and CSRF and never retry overrides",async()=>{
 const original=globalThis.fetch,calls=[];
 try {globalThis.fetch=async(url,options)=>{if(url.endsWith("csrf"))return {ok:true,json:async()=>({headerName:"X-CSRF-TOKEN",token:"csrf"})};calls.push({url,...options});return {ok:calls.length<3,json:async()=>calls.length<3?{revision:8}:{error:"STALE_TOURNAMENT_REVISION"}};};
  await tournamentControl("id/a","pause",7);await tournamentParticipantStatus("id/a","bot/a",8,"DISQUALIFIED","invalid output");await assert.rejects(tournamentMatchOperation("id/a","m/a","override",{revision:7,reason:"correction",winnerParticipantId:"b"}),/STALE_TOURNAMENT_REVISION/);
  assert.equal(calls.length,3);assert.equal(calls[0].url,"/api/tournaments/id%2Fa/control/pause");assert.equal(calls[2].headers["X-CSRF-TOKEN"],"csrf");assert.equal(JSON.parse(calls[2].body).reason,"correction");assert.equal(JSON.parse(calls[2].body).revision,7);
 }finally{globalThis.fetch=original;}
});
test("resume and next round controls honor pending review and manual round barriers",()=>{
 const cup={status:"RECOVERY_REQUIRED",reviews:{m:"review"},matches:[],autoAdvanceRounds:false,releasedRound:1,rounds:[{},{}]};assert.equal(allowedControls(cup).resume,false);
 cup.reviews={};cup.matches=[{status:"FAILED",round:1}];assert.equal(allowedControls(cup).resume,false);
 cup.status="PAUSED";cup.matches=[{status:"FINISHED",round:1},{status:"PENDING",round:2}];assert.equal(allowedControls(cup)["next-round"],true);assert.equal(allowedControls(cup).finish,false);
});
