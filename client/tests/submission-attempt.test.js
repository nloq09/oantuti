import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {installDocument,text,descendants} from './helpers/organizer-dom.js';
import {attemptActive,judgeAttempt,submissionSummary,submitReason} from '../js/tournament/submission-attempt.js';

test('judge reports AC only after committed success, and distinguishes compiler test timeout and infrastructure errors',()=>{
 assert.match(submissionSummary(null),/Chưa có bản dự thi hợp lệ/);
 for(const status of ['QUEUED','BUILDING','TESTING','FINALIZING'])assert.notEqual(judgeAttempt({id:'new',status,testResult:'{"passed":true}'}).code,'AC');
 assert.equal(judgeAttempt({id:'new',status:'SUCCEEDED',version:4,finishedAt:'2026-10-24T06:17:00Z'}).code,'AC');
 for(const [errorCode,message,expected]of [['BUILD_FAILED','SyntaxError: line 3','CE'],['TEST_FAILED','PROTOCOL_ERROR','WA'],['TEST_FAILED','TIMEOUT','TLE'],['TEST_FAILED','MEMORY_LIMIT_EXCEEDED','MLE'],['TEST_FAILED','SANDBOX_UNAVAILABLE','ERROR'],['SUBMISSION_PROCESSING_FAILED','interrupted','ERROR']]){
  const judged=judgeAttempt({id:'new',status:'FAILED',errorCode,buildLog:message,errorDetail:message,testResult:JSON.stringify({cases:[{status:'FAIL',message}]})});assert.equal(judged.code,expected);assert.ok(judged.error);
 }
 assert.equal(attemptActive({status:'FINALIZING'}),true);
});
test('submission permissions explain disabled controls and keep the draft distinct from an uncertain request',()=>{
 const allowed={policy:{maxSourceBytes:30,allowResubmitBeforeDeadline:true},entryMode:'CODE',source:'print(1)',accepted:true};
 assert.equal(submitReason(allowed),'');
 assert.match(submitReason({...allowed,source:'é'.repeat(16)}),/30 byte/);
 assert.match(submitReason({...allowed,attempt:{status:'TESTING'}}),/đang được xử lý/);
 assert.match(submitReason({...allowed,writable:false}),/hết hạn/);
 assert.equal(submitReason({...allowed,pending:true,writable:false}),'','an uncertain request can be checked idempotently after the cutoff');
});

const settle=async()=>{for(let n=0;n<20;n++)await new Promise(done=>setImmediate(done));};
let moduleId=0;
function fixture({storage=new Map(),restoredAttempt=null,policyOverrides={}}={}){
 const previous={fetch:globalThis.fetch,setTimeout:globalThis.setTimeout,clearTimeout:globalThis.clearTimeout,setInterval:globalThis.setInterval,clearInterval:globalThis.clearInterval,navigator:Object.getOwnPropertyDescriptor(globalThis,'navigator')};
 const doc=installDocument(readFileSync(new URL('../tournament-code.html',import.meta.url),'utf8'));doc.hidden=false;Object.defineProperty(doc.getElementById('coding-layout'),'classList',{value:{toggle(){}}});
 const events=new EventTarget(),timers=new Map();let timerId=0;
 globalThis.window=globalThis;globalThis.addEventListener=events.addEventListener.bind(events);globalThis.removeEventListener=events.removeEventListener.bind(events);globalThis.dispatchEvent=events.dispatchEvent.bind(events);
 globalThis.CustomEvent=class extends Event{constructor(type,options){super(type);this.detail=options?.detail;}};
 Object.defineProperty(globalThis,'navigator',{configurable:true,value:{onLine:true}});
 globalThis.location=new URL('http://example.test/tournament-code.html?id=submit-cup&entry=code');location.replace=()=>{};globalThis.history={replaceState(){}};
 globalThis.sessionStorage={getItem:key=>storage.get(key)??null,setItem:(key,value)=>storage.set(key,value),removeItem:key=>storage.delete(key)};
 globalThis.setTimeout=(fn,delay)=>{const id=++timerId;timers.set(id,{fn,delay});return id;};globalThis.clearTimeout=id=>timers.delete(id);globalThis.setInterval=()=>++timerId;globalThis.clearInterval=()=>{};
 const policy={submissionMode:'BOTH',allowedLanguages:['PYTHON'],allowDraftSave:true,allowPublicTests:true,allowCustomTestMatch:true,maxSourceBytes:524288,maxSubmissionVersions:10,allowResubmitBeforeDeadline:true};
 Object.assign(policy,policyOverrides);
 const t={id:'submit-cup',name:'Submit cup',ownerUserId:'owner',status:'REGISTRATION',revision:1,participants:[],botPolicy:policy,serverNow:new Date().toISOString(),submissionWindowOpen:!(policy.submissionDeadline&&Date.parse(policy.submissionDeadline)<=Date.now())};
 let draft={id:'workspace',language:'PYTHON',revision:0,sourceCode:'print("saved")',status:'DRAFT',updatedAt:new Date().toISOString()},attempt=restoredAttempt,submission={status:'NO_SUBMISSION'},uncertain=false;
 const posts=[];
 globalThis.fetch=async(url,options={})=>{let data;
  if(url==='/api/auth/me')data={authenticated:true,user:{id:'player',role:'USER'}};
  else if(url==='/api/auth/csrf')data={headerName:'X-CSRF-TOKEN',token:'test'};
  else if(url==='/api/tournaments/submit-cup')data=t;
  else if(url.endsWith('/submission/me/attempts')&&options.method==='POST'){
   const body=JSON.parse(options.body);posts.push(body);if(attempt?.id!==body.requestId)attempt={id:body.requestId,status:'QUEUED',version:posts.length,versionId:'v'+posts.length,language:'PYTHON'};data=attempt;if(uncertain){uncertain=false;throw new TypeError('Network disconnected');}
  }else if(url.endsWith('/submission/me/attempts/latest'))data=attempt||{status:'NO_SUBMISSION'};
  else if(url.endsWith('/submission/me/versions'))data=[];
  else if(url.endsWith('/submission/me'))data=submission;
  else if(url==='/api/account/bots')data=[];
  else if(url.endsWith('/workspace/me')&&options.method==='PUT'){const body=JSON.parse(options.body);draft={...draft,sourceCode:body.sourceCode,revision:draft.revision+1};data=draft;}
  else if(url.endsWith('/workspace/me?language=PYTHON'))data=draft;
  else if(url.endsWith('/workspace/me?language=CPP'))data={id:'workspace-cpp',language:'CPP',revision:0,sourceCode:'int main(){}',status:'DRAFT',updatedAt:draft.updatedAt};
  else throw Error('Unexpected request '+url);
  return{ok:true,status:200,json:async()=>structuredClone(data)};
 };
 return{doc,storage,posts,load:async()=>{await import('../js/tournament/code-workspace.js?submit-flow-'+(++moduleId));await settle();},setAttempt:value=>attempt=value,setSubmission:value=>submission=value,uncertain:()=>uncertain=true,
  edit:value=>{const editor=doc.getElementById('source');editor.value=value;editor.dispatchEvent(new Event('input'));},
  poll:async()=>{const entry=[...timers].find(([,timer])=>timer.delay===1500);assert.ok(entry,'attempt poll scheduled');timers.delete(entry[0]);entry[1].fn();await settle();},
  cleanup(){events.dispatchEvent(new Event('pagehide'));globalThis.fetch=previous.fetch;globalThis.setTimeout=previous.setTimeout;globalThis.clearTimeout=previous.clearTimeout;globalThis.setInterval=previous.setInterval;globalThis.clearInterval=previous.clearInterval;if(previous.navigator)Object.defineProperty(globalThis,'navigator',previous.navigator);else delete globalThis.navigator;}
 };
}

test('the real editor submits one captured source, permits later edits, and displays failures without replacing the current bot',async()=>{
 const f=fixture();try{await f.load();const button=f.doc.getElementById('submit-bot'),editor=f.doc.getElementById('source');assert.equal(f.doc.getElementById('submission-version'),null);assert.equal(button.textContent,'Submit');assert.equal(f.doc.getElementById('judge-result').hidden,true);assert.equal(f.doc.getElementById('accept-rules'),null);assert.equal(f.doc.getElementById('workspace-tools'),null);const order=descendants(f.doc);assert.ok(order.indexOf(editor)<order.indexOf(button));assert.ok(order.indexOf(button)<order.indexOf(f.doc.getElementById('judge-result')));f.edit('print("snapshot")');
  await button.click();await settle();await button.click();assert.equal(f.posts.length,1);assert.equal(f.posts[0].sourceCode,'print("snapshot")');assert.equal(editor.disabled,false);f.edit('print("next draft")');assert.equal(f.posts[0].sourceCode,'print("snapshot")');
  f.setAttempt({id:f.posts[0].requestId,status:'TESTING',version:1,versionId:'v1'});await f.poll();assert.match(f.doc.getElementById('attempt-state').textContent,/Đang chạy test cases/);
  f.setSubmission({status:'SUBMITTED',version:1,versionId:'v1',submittedAt:new Date().toISOString()});f.setAttempt({id:f.posts[0].requestId,status:'SUCCEEDED',version:1,versionId:'v1',finishedAt:new Date().toISOString()});await f.poll();assert.equal(f.doc.getElementById('verdict').textContent,'AC');assert.match(f.doc.getElementById('attempt-state').textContent,/Accepted.*v1/);assert.equal(editor.value,'print("next draft")');
  // Reconcile the deliberately unsaved local draft using the existing conflict controls.
  if(!f.doc.getElementById('conflict').hidden){await f.doc.getElementById('keep-local').click();await settle();}
  await button.click();await settle();assert.equal(f.posts.length,2);f.setAttempt({id:f.posts[1].requestId,status:'FAILED',version:2,errorCode:'TEST_FAILED',errorDetail:'lỗi ở bài kiểm thử 3'});await f.poll();assert.equal(f.doc.getElementById('verdict').textContent,'WA');assert.match(f.doc.getElementById('judge-error').textContent,/3/);assert.match(f.doc.getElementById('retained-submission').textContent,/Bản v1 vẫn được dùng/);
 }finally{f.cleanup();}
});

test('manual submission window is closed by server deadlines without discarding source',async()=>{
 const f=fixture({policyOverrides:{submissionDeadline:'2000-01-01T00:00:00Z'}});try{await f.load();assert.equal(f.doc.getElementById('submit-bot').disabled,true);assert.equal(f.doc.getElementById('source').disabled,true);assert.match(f.doc.getElementById('language').title,/hết giờ/);assert.match(f.doc.getElementById('entry-mode').title,/hết giờ/);assert.match(f.doc.getElementById('submit-reason').textContent,/hết hạn/);assert.equal(f.doc.getElementById('source').value,'print("saved")');}finally{f.cleanup();}
});

test('local-only drafts survive language and entry-mode changes when server draft saving is disabled',async()=>{
 const f=fixture({policyOverrides:{allowDraftSave:false,allowedLanguages:['PYTHON','CPP']}});try{
  await f.load();f.edit('print("local python")');const languages=f.doc.getElementById('language'),mode=f.doc.getElementById('entry-mode');assert.equal(languages.disabled,false);assert.equal(mode.disabled,false);
  languages.value='CPP';await languages.onchange();await settle();assert.equal(f.doc.getElementById('source').value,'int main(){}');
  languages.value='PYTHON';await languages.onchange();await settle();assert.equal(f.doc.getElementById('source').value,'print("local python")');assert.equal(f.doc.getElementById('conflict').hidden,true);
  mode.value='IMPORT';await mode.onchange();await settle();mode.value='CODE';await mode.onchange();await settle();assert.equal(f.doc.getElementById('source').value,'print("local python")');assert.equal(f.posts.length,0);
  sessionStorage.setItem=()=>{throw new Error('Storage quota');};f.edit('print("latest in memory")');languages.value='CPP';await languages.onchange();await settle();languages.value='PYTHON';await languages.onchange();await settle();assert.equal(f.doc.getElementById('source').value,'print("latest in memory")');
 }finally{f.cleanup();}
});

test('an ambiguous response retries the same request snapshot and reload restores the durable running attempt',async()=>{
 const f=fixture();let saved,attempt;try{await f.load();f.edit('print("original")');f.uncertain();await f.doc.getElementById('submit-bot').click();f.edit('print("changed after send")');assert.match(f.doc.getElementById('submit-bot').textContent,/Kiểm tra lần gửi/);await f.doc.getElementById('submit-bot').click();assert.equal(f.posts.length,2);assert.deepEqual(f.posts[0],f.posts[1]);saved=f.storage;attempt={id:f.posts[0].requestId,status:'BUILDING',version:1,versionId:'v1'};
 }finally{f.cleanup();}
 const restored=fixture({storage:saved,restoredAttempt:attempt});try{await restored.load();assert.match(restored.doc.getElementById('attempt-state').textContent,/Đang biên dịch/);assert.equal(restored.doc.getElementById('submit-bot').disabled,true);assert.equal(restored.posts.length,0,'reload reads progress without resubmitting');}finally{restored.cleanup();}
});
