import test from "node:test";import assert from "node:assert/strict";import {lifecycleActions}from "../js/admin/tournament-model.js";
test("lifecycle controls respect participant capacity reviews failed matches and terminal status",()=>{
 const state={status:"DRAFT",participants:[],matches:[],reviews:{},registrations:[],autoAdvanceRounds:true};const actions=t=>lifecycleActions(t).map(([,action])=>action);
 assert.deepEqual(actions(state),["open-registration","cancel"]);
 assert.ok(actions({...state,participants:[{},{}]}).includes("start"));assert.ok(!actions({...state,participants:[{},{}],checkInRequired:true}).includes("start"));
 assert.ok(!actions({...state,status:"PAUSED",reviews:{id:"review"}}).includes("resume"));assert.ok(!actions({...state,status:"RECOVERY_REQUIRED",matches:[{status:"FAILED"}]}).includes("resume"));
 assert.deepEqual(actions({...state,status:"FINISHED"}),[]);assert.deepEqual(actions({...state,status:"CANCELLED"}),[]);
});
test("knockout finish follows the resolved final bracket rather than a post-finish champion projection",()=>{
 const base={status:"PAUSED",format:"SINGLE_ELIMINATION",participants:[{},{}],matches:[{status:"FINISHED"}],reviews:{},champion:null};
 const actions=t=>lifecycleActions(t).map(([,action])=>action);
 assert.ok(!actions({...base,bracket:[{resolution:null}]}).includes("finish"));
 assert.ok(actions({...base,bracket:[{resolution:"WIN"}]}).includes("finish"));
 assert.ok(actions({...base,bracket:[{resolution:"NO_ELIGIBLE_PARTICIPANT"}]}).includes("finish"));
 assert.ok(!actions({...base,bracket:[{resolution:"WIN"}],reviews:{match:"review"}}).includes("finish"));
});
