import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {installDocument,text} from './helpers/organizer-dom.js';

const cup={id:'registration-cup',name:'UET',ownerUserId:'organizer',status:'REGISTRATION',revision:1,format:'SINGLE_ELIMINATION',checkInRequired:true,participants:[],maxParticipants:100,approvedParticipantCount:0,botPolicy:{submissionMode:'BOTH',allowedLanguages:['PYTHON','CPP','JAVA']},registrationPolicy:{requireAffiliationVerification:true},timeline:{registrationOpenAt:'2026-10-24T01:00:00Z',registrationCloseAt:'2026-10-24T03:00:00Z',codingOpenAt:'2026-10-24T04:00:00Z',codingCloseAt:'2026-10-24T05:00:00Z'}};
const start={phase:'REGISTRATION_OPEN',nextAction:'REGISTER',registrationStatus:'NOT_REGISTERED',serverNow:'2026-10-24T02:00:00Z',phaseEndsAt:cup.timeline.registrationCloseAt,codingOpenAt:cup.timeline.codingOpenAt,revision:1,allowedLanguages:['PYTHON','CPP','JAVA']};
const settle=async()=>{for(let n=0;n<15;n++)await new Promise(done=>setImmediate(done));};
const event={preventDefault(){}};

test('single detail page preserves registration, disclosures and server-authoritative phase changes',{timeout:15000},async t=>{
 const previous={fetch:globalThis.fetch,setTimeout:globalThis.setTimeout,clearTimeout:globalThis.clearTimeout,setInterval:globalThis.setInterval,clearInterval:globalThis.clearInterval};
 let events,doc,root,flow=structuredClone(start),sequence=0,posts=0,response,release,readFailure=false,timers=new Map(),timerId=0;
 globalThis.window=globalThis;
 globalThis.CustomEvent=class extends Event{constructor(type,options){super(type);this.detail=options?.detail;}};
 globalThis.WebSocket=class{send(){}close(){}};
 globalThis.setTimeout=(fn,delay)=>{const id=++timerId;timers.set(id,{fn,delay});return id;};globalThis.clearTimeout=id=>timers.delete(id);
 globalThis.setInterval=()=>++timerId;globalThis.clearInterval=()=>{};
 globalThis.fetch=async(url,options={})=>{
  let data,status=200;if(readFailure&&url.endsWith('/summary'))throw Error('NETWORK');
  if(url==='/api/auth/me')data={authenticated:true,user:{id:'player',username:'tester',displayName:'Player',role:'USER'}};
  else if(url==='/api/auth/csrf')data={headerName:'X-CSRF-TOKEN',token:'fixture'};
  else if(url.endsWith('/summary'))data={...cup,revision:flow.revision,status:flow.tournamentStatus||cup.status,progress:{total:flow.matchCount||0},rounds:[],stages:[],matches:[],bracket:flow.matchCount?[{id:'node'}]:[],ranking:[],replayCount:flow.matchCount||0,participantOwners:{},participantStatuses:{}};
  else if(url.endsWith('/me'))data={tournamentId:cup.id,revision:flow.revision,states:flow.ownStates||[]};
  else if(url.includes('/my-matches?')||url.includes('/matches?'))data={tournamentId:cup.id,revision:flow.revision,items:[],series:[],total:0,serverNow:flow.serverNow};
  else if(url.endsWith('/participant-flow'))data=structuredClone(flow);
  else if(url.endsWith('/participant-registration')){
   posts++;const body=JSON.parse(options.body);await new Promise(done=>release=done);
   if(response?.error){status=409;data=response;}
   else{flow={...flow,revision:flow.revision+1,registrationStatus:'NEEDS_REVIEW',nextAction:'WAIT_FOR_APPROVAL',registration:{displayName:'Player',intendedLanguage:body.intendedLanguage,affiliation:body.affiliation}};data=structuredClone(flow);}
  }else throw Error('Unexpected request '+url);
  return{ok:status===200,status,json:async()=>data};
 };
 async function boot(hash='',page='tournament-detail.html'){
  events?.dispatchEvent(new Event('pagehide'));timers.clear();
  doc=installDocument(readFileSync(new URL('../tournament-detail.html',import.meta.url),'utf8'));doc.hidden=false;
  events=new EventTarget();globalThis.addEventListener=events.addEventListener.bind(events);globalThis.removeEventListener=events.removeEventListener.bind(events);globalThis.dispatchEvent=events.dispatchEvent.bind(events);
  globalThis.location=new URL('http://example.test/'+page+'?id='+cup.id+hash);location.replace=()=>{};
  globalThis.history={replaceState(_a,_b,url){globalThis.location=new URL(url,location.href);location.replace=()=>{};}};
  await import('../js/tournament/player-detail.js?registration-disclosure-'+(++sequence));await settle();root=doc.querySelector('#tournament-player');
 }
 async function refresh(){events.dispatchEvent(new Event('online'));await settle();}
 const visit=hash=>{history.replaceState(null,'',location.pathname+location.search+'#'+hash);events.dispatchEvent(new Event('hashchange'));};
 const panel=()=>root.querySelector('#player-registration-panel');
 const form=()=>panel().querySelector('form');
 const inputs=()=>({language:form().querySelector('select'),school:form().querySelector('input'),accept:form().querySelectorAll('input').find(input=>input.type==='checkbox'),submit:form().querySelector('button[type=submit]')||form().querySelector('button')});
 const fill=()=>{const {language,school,accept}=inputs();language.value='JAVA';language.onchange();school.value='UET Team';school.oninput();accept.checked=true;accept.onchange();};
 try{
  await t.test('default page keeps the original shell and task card; only the action opens a single form',async()=>{
   await boot();assert.equal(form(),null);assert.equal(panel().hidden,true);
   const shellText=text(root.querySelector('.tournament-header')),stepsText=text(root.querySelector('.tournament-progress'));
   const cta=root.querySelector('#player-primary-action-REGISTER');cta.onclick(event);
   assert.equal(panel().hidden,false);assert.equal(root.querySelectorAll('form').length,1);assert.equal(doc.activeElement,inputs().language);
   assert.equal(root.querySelector('[role=tab]'),null);assert.equal(root.querySelector('.tournament-tabs'),null);assert.equal(location.hash,'#registration');
   assert.equal(panel().previousElementSibling,root.querySelector('.player-next-action'),'form sits immediately after the unchanged task card');
   const first=form();fill();root.querySelector('#player-primary-action-REGISTER').onclick(event);
   assert.equal(form(),first);assert.equal(inputs().school.value,'UET Team');
   visit('overview');assert.equal(root.querySelector('#tournament-info').open,true);assert.equal(panel().hidden,false);
   visit('registration');assert.equal(form(),first);assert.equal(inputs().language.value,'JAVA');assert.equal(inputs().accept.checked,true);
   assert.equal(text(root.querySelector('.tournament-header')),shellText);assert.equal(text(root.querySelector('.tournament-progress')),stepsText);
   assert.equal(panel().querySelector('.panel'),null,'registration has just one panel');assert.equal(panel().querySelector('[role=timer]'),null,'no duplicate countdown in the form');
  });
  await t.test('rules link expands the full rules without hiding the draft; forced reads preserve both disclosures',async()=>{
   const original=form();panel().querySelector('.registration-rules-link').onclick(event);assert.equal(root.querySelector('#tournament-rules').open,true);assert.equal(panel().hidden,false);assert.match(text(root.querySelector('#tournament-rules')),/Đi một ô theo 8 hướng/);
   visit('registration');await refresh();assert.equal(form(),original);assert.equal(inputs().school.value,'UET Team');
  });
  await t.test('transient polling failures leave the form, focus and expanded rules intact',async()=>{
   const original=form();readFailure=true;await refresh();assert.equal(form(),original);assert.equal(inputs().school.value,'UET Team');assert.equal(root.querySelector('#tournament-rules').open,true);assert.ok(root.querySelector('#player-read-error'));
   readFailure=false;await refresh();assert.equal(root.querySelector('#player-read-error'),null);assert.equal(form(),original);assert.equal(root.querySelector('#tournament-rules').open,true);
  });
  await t.test('server failure keeps the draft; viewing other sections never duplicates an in-flight request',async()=>{
   response={error:'INVALID_AFFILIATION'};const original=form(),first=original.onsubmit(event);original.onsubmit(event);await settle();
   assert.equal(posts,1);assert.equal(inputs().submit.textContent,'Đang đăng ký…');assert.equal(inputs().submit.disabled,true);
   visit('rules');visit('registration');await refresh();assert.equal(form(),original);assert.equal(inputs().submit.disabled,true);
   release();await first;assert.match(text(panel()),/80 ký tự/);assert.equal(inputs().school.value,'UET Team');assert.equal(inputs().submit.disabled,false);
  });
  await t.test('confirmed registration closes the form, shows pending approval and read-only details; reload reads the server',async()=>{
   response=null;const promise=form().onsubmit(event);await settle();release();await promise;await settle();
   assert.equal(posts,2);assert.equal(panel().hidden,true);assert.match(text(root.querySelector('.player-next-action')),/Chờ ban tổ chức xác nhận/);
   root.querySelector('.registration-information-link').onclick(event);assert.equal(panel().hidden,false);assert.equal(form(),null);assert.match(text(panel()),/UET Team/);assert.match(text(panel()),/Java/);
   await boot();assert.equal(panel().hidden,true);assert.equal(form(),null);assert.match(text(root.querySelector('.player-next-action')),/Chờ ban tổ chức xác nhận/);
  });
  await t.test('approved status and coding access follow server capabilities',async()=>{
   flow={...flow,registrationStatus:'APPROVED',nextAction:'WAIT_FOR_CODING'};await refresh();assert.match(text(root.querySelector('.player-next-action')),/đã được duyệt/);
   flow={...flow,phase:'CODING_OPEN',nextAction:'OPEN_CODE_WORKSPACE',canAccessWorkspace:true,canModifyWorkspace:true};await refresh();const cta=root.querySelector('#player-primary-action-OPEN_CODE');assert.equal(cta.textContent,'Vào lập trình');assert.match(cta.href,/tournament-code/);
   root.querySelector('.registration-information-link').onclick(event);assert.equal(form(),null);assert.match(text(panel()),/UET Team/);assert.equal(panel().querySelector('.validation-panel'),null,'registration details stay separate from the current phase view');
  });
  await t.test('deadline transition retains entered fields but explains why submission is disabled',async()=>{
   flow=structuredClone(start);await boot('#registration');fill();
   flow={...flow,phase:'REGISTRATION_REVIEW',nextAction:'WAIT_FOR_CODING',serverNow:cup.timeline.registrationCloseAt};await refresh();
   assert.equal(inputs().school.value,'UET Team');assert.equal(inputs().submit.disabled,true);assert.match(text(panel()),/Đăng ký đã đóng hoặc chưa mở/);
   await form().onsubmit(event);assert.equal(posts,2);
   flow={...flow,phase:'CODING_OPEN',nextAction:'VIEW_REGISTRATION_STATUS'};await refresh();assert.equal(inputs().school.value,'UET Team');assert.equal(inputs().submit.disabled,true);assert.equal(panel().hidden,false);
  });
  await t.test('legacy overview, rules and registration hashes reach the corresponding sections without granting phase actions',async()=>{
   for(const hash of ['overview','rules','tournament-rules','registration','tournament-registration-form','player-registration-panel','coding','validation','check-in']){
    flow=structuredClone(start);await boot('#'+hash);
    if(['registration','tournament-registration-form','player-registration-panel'].includes(hash)){assert.equal(panel().hidden,false);assert.ok(form());}
    else if(hash==='overview')assert.equal(root.querySelector('#tournament-info').open,true);
    else if(hash.includes('rules'))assert.equal(root.querySelector('#tournament-rules').open,true);
    assert.equal(root.querySelector('[role=tab]'),null);assert.equal(root.querySelector('textarea'),null);assert.equal(root.querySelector('#player-primary-action-OPEN_CODE'),null);
   }
  });
  await t.test('live and finished pages retain personal matches, standings, bracket, replays and complete rules as sections',async()=>{
   flow={...structuredClone(start),phase:'RUNNING',tournamentStatus:'RUNNING',matchCount:1,registrationStatus:'APPROVED',nextAction:'VIEW_REGISTRATION_STATUS',registration:{displayName:'Player'},ownStates:[{participantId:'p',status:'WAITING_MATCH',registration:{displayName:'Player',controllerType:'BOT'}}]};
   await boot('', 'tournament-lobby.html');assert.ok(root.querySelector('#my-matches'));assert.ok(root.querySelector('#ranking'));assert.ok(root.querySelector('#bracket'));assert.ok(root.querySelector('#matches'));assert.ok(root.querySelector('#replays'));assert.equal(root.querySelector('.tournament-tabs'),null);
   visit('matches');await settle();assert.equal(root.querySelector('#matches').open,true);assert.match(text(root.querySelector('#matches')),/Chưa có trận phù hợp/);
   visit('ranking');assert.equal(root.querySelector('#ranking').open,true);visit('replays');await settle();assert.equal(root.querySelector('#replays').open,true);
   flow={...flow,phase:'FINISHED',tournamentStatus:'FINISHED',revision:flow.revision+1};await refresh();assert.match(text(root.querySelector('#tournament-phase-content')),/Giải đấu đã kết thúc/);assert.equal(root.querySelector('#matches').open,true);assert.equal(root.querySelector('#ranking').open,true);visit('rules');assert.match(text(root.querySelector('#tournament-rules')),/Đi một ô theo 8 hướng/);
  });
  await t.test('backend cutoff and ineligibility errors explain the rejection without erasing input',async()=>{
   for(const error of ['REGISTRATION_NOT_OPEN','ACCOUNT_NOT_ELIGIBLE']){
    flow=structuredClone(start);await boot('#registration');fill();response={error};const pending=form().onsubmit(event);await settle();release();await pending;
    assert.equal(inputs().school.value,'UET Team');assert.equal(inputs().submit.disabled,true);assert.doesNotMatch(text(panel()),/Đã gửi|thành công/);assert.match(text(panel()),error==='REGISTRATION_NOT_OPEN'?/kết thúc/:/không còn đủ điều kiện/);
   }
  });
 }finally{
  events?.dispatchEvent(new Event('pagehide'));Object.assign(globalThis,previous);
 }
});
