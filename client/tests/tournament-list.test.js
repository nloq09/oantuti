import test from "node:test";
import assert from "node:assert/strict";
import {filterTournaments,tournamentGroup} from "../js/tournament/tournament-list-model.js";
test("format status and owner filters combine without modifying source order",()=>{
 const cups=[{id:"a",format:"ROUND_ROBIN",status:"RUNNING",ownerUserId:"one"},{id:"b",format:"SINGLE_ELIMINATION",status:"FINISHED",ownerUserId:"two"}];
 assert.deepEqual(filterTournaments(cups,{format:"ROUND_ROBIN",owner:"one"}).map(t=>t.id),["a"]);assert.deepEqual(filterTournaments(cups,{status:"RUNNING",owner:"two"}),[]);assert.equal(cups[0].id,"a");
 assert.equal(tournamentGroup("RECOVERY_REQUIRED"),"Đang diễn ra");assert.equal(tournamentGroup("CHECK_IN"),"Mở đăng ký / Check-in");assert.equal(tournamentGroup("READY"),"Sắp diễn ra");
});
