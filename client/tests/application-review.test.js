import test from 'node:test';
import assert from 'node:assert/strict';
import {reviewDecisions,reviewPayload} from '../js/tournament/application-review-model.js';
import {adminDestination,safeNext} from '../js/account/account-ui.js';

test('review controls respect cancellation, current decision and server review window',()=>{
 assert.deepEqual(reviewDecisions('PENDING',true),['APPROVED','WAITLISTED','REJECTED']);
 assert.deepEqual(reviewDecisions('APPROVED',true),['WAITLISTED','REJECTED']);
 assert.deepEqual(reviewDecisions('WAITLISTED',true),['APPROVED','REJECTED']);
 assert.deepEqual(reviewDecisions('CANCELLED',true),[]);
 assert.deepEqual(reviewDecisions('PENDING',false),[]);
});
test('review sends an explicit reason and original revision without inventing an owner or approving on retry',()=>{
 assert.deepEqual(reviewPayload('APPROVED',' Accepted ',17),{decision:'APPROVED',reason:'Accepted',revision:17});
 assert.throws(()=>reviewPayload('REJECTED',' ',17),/REASON_REQUIRED/);
 assert.throws(()=>reviewPayload('APPROVED','x'.repeat(501),17),/REASON_REQUIRED/);
 assert.throws(()=>reviewPayload('FORGED','Reason',17),/INVALID_REGISTRATION_TRANSITION/);
});
test('admin review stays within management while normal users keep the owner page',()=>{
 assert.equal(adminDestination({role:'ADMIN'},'/tournament-applications.html','?id=cup'),'/admin/applications.html?id=cup');
 assert.equal(adminDestination({role:'USER'},'/tournament-applications.html','?id=cup'),null);
 assert.equal(safeNext('/tournament-applications.html?id=cup'),'/tournament-applications.html?id=cup');
});
