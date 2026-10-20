import test from 'node:test';
import assert from 'node:assert/strict';
import {renderPlayerShell,renderPlayerNextActionCard} from '../js/tournament/player-shell.js';
import {progressSteps} from '../js/tournament/progress-stepper.js';
import {renderTimelineRegistration} from '../js/tournament/timeline-registration.js';
import {setup,descendants} from './helpers/tournament-dom.js';
const tournament={id:'cup',name:'Exam',status:'REGISTRATION',format:'ROUND_ROBIN',participants:[],maxParticipants:16,timeline:{}};
test('player shell preserves header stepper and exactly one action before phase content for every pre-tournament screen',()=>{
 for(const [phase,nextAction]of [['UPCOMING',null],['REGISTRATION_OPEN','REGISTER'],['WAITING_FOR_CODING','WAIT_FOR_CODING'],['CODING_OPEN','OPEN_CODE_WORKSPACE'],['SUBMISSION_FROZEN','VIEW_FROZEN_SUBMISSION'],['VALIDATING','VIEW_FINAL_VALIDATION'],['CHECK_IN_OPEN','CHECK_IN'],['READY_TO_START','OPEN_WAITING_ROOM']]){
  const root=setup();renderPlayerShell(root,{tournament,account:{id:'u'},flow:{phase,nextAction,canAccessWorkspace:true,canModifyWorkspace:true,checkIn:{canCheckIn:true,registrationId:'r'}},commands:{CHECK_IN:async()=>{}}});
  assert.deepEqual(root.children.map(n=>n.tag),['header','nav','section']);
  assert.equal(descendants(root).filter(n=>n.tag==='button'||n.tag==='a').length,['WAIT_FOR_CODING','OPEN_WAITING_ROOM'].includes(nextAction)?0:1);
 }
});
test('one action dispatches only the supplied command and prevents duplicate submission',async()=>{
 const root=setup();let calls=0,release;const pending=new Promise(r=>{release=r;});
 const card=renderPlayerNextActionCard(root,{type:'CHECK_IN',label:'Check-in',description:'Ready',enabled:true,command:'CHECK_IN',payload:{registrationId:'r'}},{CHECK_IN:async payload=>{assert.equal(payload.registrationId,'r');calls++;await pending;}});
 const button=descendants(card).find(n=>n.tag==='button'),first=button.onclick();await button.onclick();assert.equal(calls,1);assert.equal(card.dataset.submitting,'true');release();await first;assert.equal(button.disabled,false);
 const disabled=setup();renderPlayerNextActionCard(disabled,{label:'Wait',description:'Wait',enabled:false,command:'CHECK_IN'});assert.equal(descendants(disabled).some(n=>n.tag==='button'),false);
});
test('stepper represents optional legacy stages and a failed validation without granting navigation',()=>{
 const optional=progressSteps('RUNNING',{optionalSteps:[1,2,3]});assert.deepEqual(optional.map(n=>n.status),['COMPLETED','OPTIONAL','OPTIONAL','OPTIONAL','CURRENT','LOCKED']);
 assert.equal(progressSteps('VALIDATING',{failedStep:2})[2].status,'FAILED');
});
test('phase content defers coding and check-in controls to the one primary shell action',()=>{
 const root=setup();renderTimelineRegistration(root,tournament,{id:'u'},{phase:'CODING_OPEN',registrationStatus:'APPROVED',nextAction:'OPEN_CODE_WORKSPACE'},()=>{},{primaryWorkspace:true});
 assert.equal(descendants(root).some(n=>n.tag==='a'),false);
 const check=setup();renderTimelineRegistration(check,tournament,{id:'u'},{nextAction:'CHECK_IN',registration:{displayName:'Player'},checkIn:{closesAt:'2026-10-20T12:00:00Z'}},()=>{},{primaryCheckIn:true});
 assert.equal(descendants(check).some(n=>n.tag==='button'),false);
});
