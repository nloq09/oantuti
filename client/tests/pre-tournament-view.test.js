import test from 'node:test';
import assert from 'node:assert/strict';
import {setup,descendants,Element} from './helpers/tournament-dom.js';
import {renderTimelineRegistration} from '../js/tournament/timeline-registration.js';
import {renderFinalValidation,validationStages} from '../js/tournament/final-validation-view.js';
import {renderFrozenSubmission} from '../js/tournament/frozen-submission.js';
import {renderTimelineCheckIn} from '../js/tournament/timeline-check-in.js';
import {statusBadge} from '../js/tournament/phase-view.js';
const t={id:'cup',format:'SINGLE_ELIMINATION',maxParticipants:16,botPolicy:{allowedLanguages:['PYTHON']},timeline:{registrationOpenAt:'2026-10-20T01:00:00Z',registrationCloseAt:'2026-10-20T02:00:00Z',codingOpenAt:'2026-10-20T03:00:00Z',codingCloseAt:'2026-10-20T04:00:00Z',validationStartAt:'2026-10-20T04:00:00Z',validationDeadline:'2026-10-20T05:00:00Z',checkInOpenAt:'2026-10-20T05:00:00Z',checkInCloseAt:'2026-10-20T06:00:00Z',tournamentStartAt:'2026-10-20T07:00:00Z'}};
const text=root=>descendants(root).map(n=>n.textContent).join(' ');
test('upcoming includes full published timeline, server countdown and rules without early forms',()=>{
 const root=setup();renderTimelineRegistration(root,t,null,{phase:'UPCOMING',serverNow:'2026-10-20T00:00:00Z'},()=>{});
 assert.match(text(root),/Còn 01:00:00/);assert.match(text(root),/Hạn kiểm thử/);assert.ok(descendants(root).some(n=>n.id==='tournament-rules'));assert.equal(descendants(root).some(n=>['form','select','textarea'].includes(n.tag)),false);
});
test('coding screen never displays the already elapsed waiting countdown',()=>{
 const root=setup();renderTimelineRegistration(root,t,{id:'u'},{phase:'CODING_OPEN',registrationStatus:'APPROVED'},()=>{},{primaryWorkspace:true});assert.equal(descendants(root).some(n=>n.dataset.codingStarts),false);
});
test('registration status cards preserve reasons while never offering source or final bot selection',()=>{
 for(const registrationStatus of ['PENDING','APPROVED','WAITLISTED','REJECTED']){const root=setup();renderTimelineRegistration(root,t,{id:'u'},{phase:'REGISTRATION_REVIEW',registrationStatus,registration:{displayName:'Player',intendedLanguage:'PYTHON',reviewReason:'Reviewed'}},()=>{});assert.match(text(root),/Reviewed/);assert.equal(descendants(root).some(n=>['form','textarea','select'].includes(n.tag)),false);}
});
test('frozen source link identifies the selected immutable version and missing submissions have no fake snapshot',()=>{
 const root=setup();renderFrozenSubmission(root,t,{canAccessWorkspace:true,frozenSubmission:{versionId:'a/b',version:12,language:'CPP',decision:'AUTO_SUBMITTED',sourceHash:'s',artifactHash:'a'}});
 const href=descendants(root).find(n=>n.tag==='a').href;assert.match(href,/view=source&version=a%2Fb&language=CPP/);assert.match(text(root),/Bài đã được khóa/);
 const missing=setup();renderFrozenSubmission(missing,t,{canAccessWorkspace:true,frozenSubmission:{decision:'MISSING'}});assert.equal(descendants(missing).some(n=>n.tag==='a'),false);assert.equal(text(missing).includes('PASS'),false);
});
test('validation displays every server step and distinguishes infrastructure failure from bot failure',()=>{
 for(const status of ['QUEUED','RUNNING','FAILED','INFRA_ERROR','PASSED']){const root=setup();renderFinalValidation(root,t,{finalValidation:{status,applied:status==='PASSED',versionId:'v',originalVersionId:'v',language:'PYTHON',version:1,steps:Object.fromEntries(Object.keys(validationStages).map(k=>[k,status==='PASSED'?'PASS':'PENDING']))}});assert.equal(descendants(root).filter(n=>n.tag==='li').length,8);assert.equal(descendants(root).some(n=>['button','input','textarea'].includes(n.tag)),false);assert.equal(text(root).includes('TOURNAMENT READY'),status==='PASSED');}
 const root=setup();assert.notEqual(statusBadge('INFRA_ERROR').textContent,statusBadge('FAILED').textContent);
});
test('waiting room shows bot readiness without manager controls and has a clear schedule empty state',()=>{
 const root=setup();globalThis.fetch=async()=>({ok:true,json:async()=>({items:[]})});renderTimelineCheckIn(root,{...t,matches:[]},{nextAction:'OPEN_WAITING_ROOM',registration:{displayName:'Player'},finalValidation:{status:'PASSED',applied:true},checkIn:{checkedInCount:2,startsAt:t.timeline.tournamentStartAt}},()=>{});
 for(const label of ['✓ Đã check-in','Bot Ready ✓','Chưa ghép trận','Thông báo'])assert.ok(text(root).includes(label));assert.equal(descendants(root).some(n=>n.tag==='button'),false);
});
