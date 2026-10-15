import test from "node:test";import assert from "node:assert/strict";import {disputeDecisions}from "../js/admin/dispute-model.js";
test("dispute actions do not allow retrying completed games or overriding started knockout descendants",()=>{
 const m={id:"match",status:"FINISHED",round:1,participantA:{participantId:"a"},participantB:{participantId:"b"}},t={status:"PAUSED",format:"ROUND_ROBIN",matches:[m],participantStatuses:{}};const actions=data=>disputeDecisions(data).map(([,action])=>action);
 assert.ok(actions({status:"OPEN",match:m,tournament:t}).includes("override"));assert.ok(!actions({status:"OPEN",match:m,tournament:t}).includes("retry"));
 assert.deepEqual(actions({status:"RESOLVED",match:m,tournament:t}),[]);
 assert.ok(!actions({status:"OPEN",match:m,tournament:{...t,format:"SINGLE_ELIMINATION",matches:[m,{...m,round:2,status:"RUNNING"}]}}).includes("override"));
 assert.deepEqual(actions({status:"OPEN",match:m,tournament:{...t,status:"CANCELLED"}}),["reject"]);
});
