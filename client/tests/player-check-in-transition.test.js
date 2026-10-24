import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {installDocument,text} from './helpers/organizer-dom.js';
import {renderTimelineRegistration} from '../js/tournament/timeline-registration.js';
import {tournamentNextAction} from '../js/tournament/next-action.js';
import {validationStageCodes} from '../js/tournament/validation-detail-state.js';
import {workspacePhaseDestination} from '../js/tournament/tournament-routes.js';

const cup={id:'phase-cup',ownerUserId:'owner',name:'Check-in transition',status:'REGISTRATION',revision:8,format:'ROUND_ROBIN',checkInRequired:true,participants:[],approvedParticipantCount:1,maxParticipants:10,timeline:{codingCloseAt:'2026-10-24T10:00:00Z',checkInOpenAt:'2026-10-24T10:10:00Z',checkInCloseAt:'2026-10-24T10:20:00Z',tournamentStartAt:'2026-10-24T10:25:00Z'},botPolicy:{submissionMode:'BOTH',allowedLanguages:['PYTHON'],allowDraftSave:true,maxSourceBytes:524288,maxSubmissionVersions:10}};
const validation={status:'PASSED',applied:true,language:'PYTHON',version:1,versionId:'v1',originalVersionId:'v1',steps:Object.fromEntries(validationStageCodes.map(code=>[code,'PASS']))};
const checkIn={status:'BOT_READY',canCheckIn:true,registrationId:'registration',opensAt:cup.timeline.checkInOpenAt,closesAt:cup.timeline.checkInCloseAt,startsAt:cup.timeline.tournamentStartAt};
const readyFlow={phase:'CHECK_IN_OPEN',nextAction:'CHECK_IN',registrationStatus:'APPROVED',registration:{displayName:'Player'},revision:8,canAccessWorkspace:true,canModifyWorkspace:false,serverNow:cup.timeline.checkInOpenAt,codingCloseAt:cup.timeline.codingCloseAt,submissionStatus:'BOT_READY',finalValidation:validation,checkIn};

test('server check-in capability takes precedence over unloaded historical validation details',()=>{
 for(const steps of [null,{},undefined]){
  const doc=installDocument('<main></main>'),root=doc.querySelector('main'),flow={...readyFlow,finalValidation:{...validation,steps}};
  const action=tournamentNextAction({tournament:cup,flow,account:{id:'player'}});
  assert.equal(action.command,'CHECK_IN');
  renderTimelineRegistration(root,cup,{id:'player'},flow,()=>{});
  assert.ok(root.querySelector('.timeline-check-in'));
  assert.equal(root.querySelector('.validation-panel'),null);
  assert.match(text(root),/Check-in đã mở/);
 }
});

test('local countdown or a stale check-in hint during validation never grants check-in',()=>{
 const flow={...readyFlow,phase:'VALIDATING',nextAction:'VIEW_FINAL_VALIDATION'};
 assert.equal(tournamentNextAction({tournament:cup,flow,account:{id:'player'}}).type,'WAIT_CHECK_IN');
 const denied={...readyFlow,checkIn:{...checkIn,canCheckIn:false}};
 assert.equal(tournamentNextAction({tournament:cup,flow:denied,account:{id:'player'}}).enabled,false);
});

test('workspace navigation follows server check-in phases while coding, emergency editing and validation stay in place',()=>{
 for(const phase of ['CHECK_IN_OPEN','READY_TO_START'])assert.equal(workspacePhaseDestination('cup/a',{phase,canAccessWorkspace:true}),'/tournament-detail.html?id=cup%2Fa#check-in');
 for(const phase of ['CODING_OPEN','SUBMISSION_FROZEN','VALIDATING',undefined])assert.equal(workspacePhaseDestination('cup',{phase,canAccessWorkspace:true,emergencyCoding:true}),null);
});

const settle=async()=>{for(let n=0;n<20;n++)await new Promise(done=>setImmediate(done));};

test('real workspace follows polled server phase to check-in despite persistent source access; detail then advances to waiting room',{timeout:10000},async()=>{
 const doc=installDocument(readFileSync(new URL('../tournament-code.html',import.meta.url),'utf8'));doc.hidden=false;
 Object.defineProperty(doc.getElementById('coding-layout'),'classList',{value:{toggle(){}}});
 const events=new EventTarget(),timers=new Map(),intervals=new Map(),storage=new Map(),requests=[],destinations=[];let timerId=0;
 const previous={fetch:globalThis.fetch,setTimeout:globalThis.setTimeout,clearTimeout:globalThis.clearTimeout,setInterval:globalThis.setInterval,clearInterval:globalThis.clearInterval,navigator:Object.getOwnPropertyDescriptor(globalThis,'navigator')};
 globalThis.addEventListener=events.addEventListener.bind(events);globalThis.removeEventListener=events.removeEventListener.bind(events);globalThis.dispatchEvent=events.dispatchEvent.bind(events);globalThis.window=globalThis;
 globalThis.CustomEvent=class extends Event{constructor(type,options){super(type);this.detail=options?.detail;}};
 Object.defineProperty(globalThis,'navigator',{configurable:true,value:{onLine:true}});
 globalThis.sessionStorage={getItem:key=>storage.get(key)??null,setItem:(key,value)=>storage.set(key,value),removeItem:key=>storage.delete(key)};
 globalThis.location=new URL('http://example.test/tournament-code.html?id=phase-cup');location.replace=url=>destinations.push(url);
 globalThis.history={replaceState(_a,_b,url){const replace=location.replace;globalThis.location=new URL(url,location.href);location.replace=replace;}};
 globalThis.WebSocket=class{send(){}close(){}};
 globalThis.setTimeout=(fn,delay)=>{const id=++timerId;timers.set(id,{fn,delay});return id;};globalThis.clearTimeout=id=>timers.delete(id);
 globalThis.setInterval=(fn,delay)=>{const id=++timerId;intervals.set(id,{fn,delay});return id;};globalThis.clearInterval=id=>intervals.delete(id);
 let flow={...readyFlow,phase:'CODING_OPEN',nextAction:'OPEN_CODE_WORKSPACE',canModifyWorkspace:true,serverNow:'2026-10-24T09:00:00Z',submissionStatus:'NO_SUBMISSION',finalValidation:null,checkIn:{...checkIn,canCheckIn:false}};
 globalThis.fetch=async(url,options={})=>{requests.push({url,options});let data;
  if(url==='/api/auth/me')data={authenticated:true,user:{id:'player',role:'USER'}};
  else if(url==='/api/tournaments/phase-cup'||url.endsWith('/summary'))data=cup;
  else if(url.endsWith('/participant-flow'))data=structuredClone(flow);
  else if(url==='/api/tournaments/phase-cup/me')data={tournamentId:cup.id,revision:8,states:[]};
  else if(url.endsWith('/submission/me/versions')||url==='/api/account/bots')data=[];
  else if(url.endsWith('/submission/me'))data={status:'NO_SUBMISSION'};
  else if(url==='/api/tournaments/phase-cup/workspace/me?language=PYTHON')data={id:'workspace',language:'PYTHON',revision:1,status:'DRAFT',sourceCode:'print("saved")',updatedAt:'2026-10-24T09:00:00Z'};
  else if(url.endsWith('/notifications'))data={items:[]};
  else throw Error('Unexpected request '+url);
  return{ok:true,status:200,json:async()=>data};
 };
 const poll=async()=>{const entry=[...timers].find(([,timer])=>timer.delay===5000);assert.ok(entry,'phase polling remains active');timers.delete(entry[0]);entry[1].fn();await settle();};
 try{
  await import('../js/tournament/code-workspace.js?check-in-transition');await settle();assert.deepEqual(destinations,[]);
  const editor=doc.getElementById('source');assert.equal(editor.disabled,false);editor.value='print("unsaved local draft")';editor.dispatchEvent(new Event('input'));
  flow={...readyFlow,phase:'VALIDATING',nextAction:'VIEW_FINAL_VALIDATION',serverNow:'2026-10-24T10:05:00Z',checkIn:{...checkIn,canCheckIn:false}};
  await poll();assert.match(text(doc.querySelector('#frozen-summary')),/KIỂM THỬ HOÀN TẤT/);assert.deepEqual(destinations,[]);
  flow={...readyFlow,finalValidation:{...validation,steps:null}};
  await poll();assert.deepEqual(destinations,['/tournament-detail.html?id=phase-cup#check-in']);assert.equal(intervals.size,0);assert.equal(timers.size,0);
  assert.equal(JSON.parse(storage.get('rps-code:player:phase-cup:PYTHON')).sourceCode,editor.value,'retain the unsaved draft without uploading it after deadline');
  const unload=new Event('beforeunload',{cancelable:true});events.dispatchEvent(unload);assert.equal(unload.defaultPrevented,false,'a preserved draft does not block the phase transition');
  assert.equal(requests.some(({options})=>options.method&&options.method!=='GET'),false,'phase updates never perform check-in automatically');

  events.dispatchEvent(new Event('pagehide'));const restored=new Event('pageshow');Object.defineProperty(restored,'persisted',{value:true});events.dispatchEvent(restored);await settle();assert.equal(destinations.length,2,'returning from browser cache rechecks the phase and leaves the closed workspace again');assert.equal(timers.size,0);assert.equal(intervals.size,0);

  events.dispatchEvent(new Event('pagehide'));
  const detail=installDocument(readFileSync(new URL('../tournament-detail.html',import.meta.url),'utf8'));detail.hidden=false;
  globalThis.location=new URL(destinations[0],'http://example.test');
  await import('../js/tournament/player-detail.js?check-in-transition');await settle();const root=detail.querySelector('#tournament-player');
  assert.match(text(root.querySelector('[aria-current=step]')),/Check-in/);assert.ok(root.querySelector('.timeline-check-in'));assert.equal(root.querySelector('.validation-panel'),null);assert.equal(root.querySelector('.player-next-action').dataset.nextAction,'CHECK_IN');
  flow={...flow,phase:'READY_TO_START',nextAction:'OPEN_WAITING_ROOM',checkIn:{...checkIn,status:'CHECKED_IN',canCheckIn:false,checkedInAt:'2026-10-24T10:11:00Z',checkedInCount:1}};
  await poll();assert.match(text(root),/Phòng chờ giải đấu/);assert.equal(root.querySelector('.validation-panel'),null);assert.equal(root.querySelector('.player-next-action').dataset.nextAction,'WAIT_START');
 }finally{
  events.dispatchEvent(new Event('pagehide'));globalThis.fetch=previous.fetch;globalThis.setTimeout=previous.setTimeout;globalThis.clearTimeout=previous.clearTimeout;globalThis.setInterval=previous.setInterval;globalThis.clearInterval=previous.clearInterval;
  if(previous.navigator)Object.defineProperty(globalThis,'navigator',previous.navigator);else delete globalThis.navigator;
 }
});
