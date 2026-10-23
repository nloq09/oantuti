import test from "node:test";
import assert from "node:assert/strict";
import {bracketRounds,roundLabel,formatLabel} from "../js/tournament/bracket-renderer.js";
import {configureRules,createTournament} from "../js/tournament/tournament-api.js";
test("group knockout only renders actual bracket rounds after the group-stage offset",()=>{
 const t={format:"GROUP_KNOCKOUT",participants:[],matches:[],bracket:[{id:"semi",round:8,position:0},{id:"final",round:9,position:0}]};
 assert.deepEqual(bracketRounds(t).map(r=>r.label),["Bán kết","Chung kết"]);assert.equal(bracketRounds(t).length,2);assert.equal(formatLabel(t.format),"Vòng bảng → Loại trực tiếp");
});
test("bracket preserves server advancement and shows BYE separately from unresolved slots",()=>{
  const t={format:"SINGLE_ELIMINATION",participants:[{participantId:"a",displayName:"<Alpha>"}],matches:[],bracket:[
    {id:"one",round:1,position:0,slotA:"a",slotB:null,sourceA:null,sourceB:null,winnerParticipantId:"a",resolution:"BYE"},
    {id:"final",round:2,position:0,slotA:"a",slotB:null,sourceA:"one",sourceB:"two"},
  ]};
  const rounds=bracketRounds(t);assert.equal(rounds[0].nodes[0].b,"Miễn đấu");assert.equal(rounds[0].nodes[0].winner,"<Alpha>");assert.equal(rounds[1].nodes[0].b,"Chờ người thắng");assert.equal(rounds[1].nodes[0].winner,undefined);assert.equal(rounds[1].label,"Chung kết");
});
test("round and format labels distinguish round robin from elimination",()=>{
  assert.equal(roundLabel(3,5,"SINGLE_ELIMINATION"),"Tứ kết");assert.equal(roundLabel(3,7,"ROUND_ROBIN"),"Vòng 3");assert.equal(formatLabel("SINGLE_ELIMINATION"),"Loại trực tiếp");
});
test("creation and ranking rules send settings with CSRF and do not infer winners",async()=>{
  const original=globalThis.fetch,calls=[];
  try {globalThis.fetch=async(url,options)=>url.endsWith("csrf")?{ok:true,json:async()=>({headerName:"X-CSRF-TOKEN",token:"csrf"})}:{ok:true,json:async()=>{calls.push({url,...options});return {id:"cup"};}};
    await createTournament("Cup",{format:"SINGLE_ELIMINATION",maxParticipants:32,maxConcurrentMatches:2});
    await configureRules("cup",{maxConcurrentMatches:1,tieBreakOrder:["POINTS","HEAD_TO_HEAD","WINS"]});
    assert.equal(JSON.parse(calls[0].body).format,"SINGLE_ELIMINATION");assert.deepEqual(JSON.parse(calls[1].body).tieBreakOrder,["POINTS","HEAD_TO_HEAD","WINS"]);assert.equal(calls[1].headers["X-CSRF-TOKEN"],"csrf");assert.equal(calls[1].url,"/api/tournaments/cup/rules");
  } finally {globalThis.fetch=original;}
});
