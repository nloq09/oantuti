import test from 'node:test';
import assert from 'node:assert/strict';
import {installDocument,text} from './helpers/organizer-dom.js';
import {renderTimelineRegistration} from '../js/tournament/timeline-registration.js';
import {registrationPresentation,canWithdrawRegistration} from '../js/tournament/timeline-registration-model.js';
import {notificationText,NotificationCursor} from '../js/tournament/notifications.js';
import {playerFlowCapabilities} from '../js/tournament/player-state.js';
import {reviewPayload} from '../js/tournament/application-review-model.js';
import {bulkReviewPayload} from '../js/tournament/application-queue-model.js';

const t={id:'cup/a',format:'SWISS',botPolicy:{allowedLanguages:['CPP']},timeline:{registrationOpenAt:'2026-10-22T00:00:00Z',registrationCloseAt:'2026-10-22T01:00:00Z',codingOpenAt:'2026-10-22T02:00:00Z'}};
const person={displayName:'Player',intendedLanguage:'CPP',reviewReason:'Private security note',reasonCodes:['INTERNAL_CHECK']};
const flow={phase:'REGISTRATION_OPEN',registrationStatus:'NEEDS_REVIEW',registration:person,serverNow:'2026-10-22T00:30:00Z',codingOpenAt:t.timeline.codingOpenAt,registrationControl:{enabled:true,lockedAt:null},canWithdraw:true};
const root=()=>installDocument('<body><main></main></body>').querySelector('main');
test('player state cards cover approved, review, waitlist, rejected and cancelled without internal reasons',()=>{
 for(const status of ['APPROVED','PENDING','NEEDS_REVIEW','WAITLISTED','REJECTED','CANCELLED']){
  const host=root();renderTimelineRegistration(host,t,{username:'player'},{...flow,registrationStatus:status,registration:{...person,waitlistPosition:12,publicMessage:'<img onerror=alert(1)> Public explanation'},canWithdraw:false},()=>{});
  assert.doesNotMatch(text(host),/Private security note|INTERNAL_CHECK/);assert.match(text(host),/Public explanation/);assert.equal(host.querySelector('img'),null);
  if(status==='WAITLISTED')assert.match(text(host),/#12/);
  if(['NEEDS_REVIEW','PENDING'].includes(status))assert.match(text(host),/không cần gửi lại/);
 }
});
test('waitlist never invents a position or promises promotion after roster lock or deadline',()=>{
 const state={...flow,registrationStatus:'WAITLISTED',canWithdraw:false,registrationControl:{enabled:true,lockedAt:'2026-10-22T01:30:00Z'}};
 assert.match(registrationPresentation(state).description,/chưa có suất thi đấu chính thức/);assert.equal(canWithdrawRegistration(state),false);
 const host=root();renderTimelineRegistration(host,t,{username:'player'},{...state,registration:{...person,waitlistPosition:0}},()=>{});assert.doesNotMatch(text(host),/#0|Chủ giải sẽ xác nhận/);
 assert.equal(canWithdrawRegistration({...flow,canWithdraw:false}),false);
 assert.equal(canWithdrawRegistration({...flow,canWithdraw:undefined,serverNow:t.timeline.codingOpenAt}),false);
});
test('signup renders the final server decision immediately without optimistic pending and blocks duplicate sends',async()=>{
 const host=root(),original=globalThis.fetch;let finishRequest,finishRefresh,calls=0;
 try{
  globalThis.fetch=async(url)=>url.endsWith('/csrf')?{ok:true,status:200,json:async()=>({headerName:'X-CSRF',token:'csrf'})}:new Promise(resolve=>{calls++;finishRequest=()=>resolve({ok:true,status:200,json:async()=>({...flow,registrationStatus:'APPROVED'})});});
  renderTimelineRegistration(host,t,{username:'player'},{...flow,registrationStatus:'NOT_REGISTERED',registration:null,canWithdraw:false},()=>new Promise(resolve=>finishRefresh=resolve));
  const form=host.querySelector('form');form.querySelector('input[type=checkbox]')?.setAttribute('checked','checked');
  const inputs=form.querySelectorAll('input');inputs.at(-1).checked=true;
  const operation=form.onsubmit({preventDefault(){}});await form.onsubmit({preventDefault(){}});
  for(let i=0;i<10&&!finishRequest;i++)await new Promise(resolve=>setImmediate(resolve));
  assert.equal(calls,1);assert.match(text(host),/Đang đăng ký/);assert.doesNotMatch(text(host),/Đang chờ ban tổ chức xác nhận/);
  finishRequest();for(let i=0;i<10&&!finishRefresh;i++)await new Promise(resolve=>setImmediate(resolve));
  assert.match(text(host),/Đăng ký đã được chấp nhận/);assert.equal(host.querySelector('form'),null);assert.doesNotMatch(text(host),/Đang chờ ban tổ chức xác nhận/);
  finishRefresh();await operation;
 }finally{globalThis.fetch=original;}
});
test('personal application notifications describe final admission states and cursor avoids duplicate toasts',()=>{
 for(const [state,pattern]of [['APPROVED',/được chấp nhận/],['NEEDS_REVIEW',/Chờ ban tổ chức/],['WAITLISTED',/danh sách chờ/]])assert.match(notificationText({type:'PARTICIPANT_APPLICATION_SUBMITTED',state}),pattern);
 assert.match(notificationText({type:'PARTICIPANT_APPLICATION_WITHDRAWN'}),/hủy đăng ký/);
 const cursor=new NotificationCursor(),item={id:'one',type:'PARTICIPANT_APPLICATION_SUBMITTED',state:'APPROVED',read:false};assert.deepEqual(cursor.update([]),[]);assert.equal(cursor.update([item]).length,1);assert.equal(cursor.update([item]).length,0);
});
test('review payloads explicitly separate public text from the internal audit reason',()=>{
 assert.deepEqual(reviewPayload('REJECTED','Internal note',9,'Public explanation'),{decision:'REJECTED',reason:'Internal note',revision:9,publicMessage:'Public explanation'});
 const bulk=bulkReviewPayload('REJECTED','Internal note',9,[{id:'a',status:'NEEDS_REVIEW',registeredAt:'2026-10-22T00:30:00Z'}],'Public explanation');assert.equal(bulk.publicMessage,'Public explanation');assert.equal(bulk.reason,'Internal note');
 assert.throws(()=>reviewPayload('REJECTED','reason',9,'x'.repeat(501)),/INVALID_PUBLIC_MESSAGE/);
});

test('player refresh invalidates capabilities when withdrawal expires even without a phase or revision change',()=>{
 const before={...flow,phase:'REGISTRATION_REVIEW',revision:9,nextAction:'WAIT_FOR_APPROVAL'};
 assert.notEqual(playerFlowCapabilities(before),playerFlowCapabilities({...before,canWithdraw:false}));
 const host=root();renderTimelineRegistration(host,t,{username:'player'},before,()=>{});assert.ok(host.querySelector('button'));host.replaceChildren();renderTimelineRegistration(host,t,{username:'player'},{...before,canWithdraw:false,serverNow:t.timeline.codingOpenAt},()=>{});assert.equal(host.querySelector('button'),null);
});
