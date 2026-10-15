import test from "node:test";import assert from "node:assert/strict";import {lifecycleActions}from "../js/admin/tournament-model.js";
test("lifecycle controls respect participant capacity reviews failed matches and terminal status",()=>{
 const state={status:"DRAFT",participants:[],matches:[],reviews:{},registrations:[],autoAdvanceRounds:true};const actions=t=>lifecycleActions(t).map(([,action])=>action);
 assert.deepEqual(actions(state),["open-registration","cancel"]);
 assert.ok(actions({...state,participants:[{},{}]}).includes("start"));assert.ok(!actions({...state,participants:[{},{}],checkInRequired:true}).includes("start"));
 assert.ok(!actions({...state,status:"PAUSED",reviews:{id:"review"}}).includes("resume"));assert.ok(!actions({...state,status:"RECOVERY_REQUIRED",matches:[{status:"FAILED"}]}).includes("resume"));
 assert.deepEqual(actions({...state,status:"FINISHED"}),[]);assert.deepEqual(actions({...state,status:"CANCELLED"}),[]);
});
