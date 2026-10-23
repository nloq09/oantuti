import test from 'node:test';import assert from 'node:assert/strict';import {readFileSync} from 'node:fs';
import {installDocument,text} from './helpers/organizer-dom.js';import {cup,metrics} from './helpers/organizer-fixtures.js';
const tick=async()=>{for(let i=0;i<8;i++)await new Promise(resolve=>setImmediate(resolve));};
test('real page controller mounts all moved blocks, navigates tabs and keeps mutation metadata',{timeout:5000},async()=>{
 const doc=installDocument(readFileSync(new URL('../tournament.html',import.meta.url),'utf8'));doc.hidden=false;
 const events=new EventTarget();globalThis.addEventListener=events.addEventListener.bind(events);globalThis.removeEventListener=events.removeEventListener.bind(events);globalThis.dispatchEvent=events.dispatchEvent.bind(events);
 globalThis.CustomEvent=class extends Event{constructor(type,options){super(type);this.detail=options.detail;}};
 globalThis.location=new URL('http://example.test/tournament.html?id=cup%2Fa');globalThis.history={replaceState(_a,_b,url){globalThis.location=new URL(url,location.href);}};globalThis.matchMedia=()=>({matches:false,addEventListener(){}});
 globalThis.WebSocket=class{send(){}close(){}};
 let current=cup({status:'DRAFT',participants:[],seedingStrategy:'RANDOM',seedingSeed:'42'}),calls=[],flowPhase=null,serverNow='2026-11-01T10:00:00Z';const original=globalThis.fetch;
 globalThis.fetch=async(url,options={})=>{
  let data;if(url==='/api/account/bots')data=[];else if(url==='/api/auth/me')data={authenticated:true,user:{id:'owner',role:'USER'}};else if(url.endsWith('/csrf'))data={headerName:'X-CSRF',token:'csrf'};
  else if(url.includes('/organizer-metrics'))data=metrics(current,{serverNow,...(flowPhase&&!['CANCELLED','FINISHED'].includes(current.status)?{phase:flowPhase}:{})});
  else if(url.includes('/match-monitor'))data={tournamentId:current.id,revision:current.revision,serverNow:'2026-11-01T10:00:00Z',items:[],total:0,page:0,size:20,queuePositionNote:'Không có hàng đợi.'};
  else if(url.endsWith('/open-registration')){calls.push({url,options});current={...current,status:'REGISTRATION',revision:current.revision+1};data=current;}
  else if(url.endsWith('/cancel')){calls.push({url,options});current={...current,status:'CANCELLED',revision:current.revision+1};data=current;}
  else if(url==='/api/tournaments/cup%2Fa')data=current;else throw Error('Unexpected API: '+url);
  return {ok:true,status:200,json:async()=>data};
 };
 try{
  await import('../js/tournament/tournament-detail.js?organizer-integration');await tick();
  assert.ok(doc.querySelector('.organizer-layout'));assert.equal(doc.querySelectorAll('.organizer-nav-group').length,4);assert.match(text(doc.querySelector('#organizer-overview')),/Mở đăng ký/);
  for(const [section,panel]of [['settings','admin-tab'],['bracket','bracket'],['overview','overview']]){doc.querySelector('[data-section='+section+']').click();assert.equal(doc.querySelector('#'+panel).hidden,false);}
  await doc.querySelector('#organizer-overview [data-organizer-command=open-registration]').click();await tick();assert.equal(current.status,'REGISTRATION');assert.equal(JSON.parse(calls[0].options.body).constructor,Object);
  const fields=['registrationOpenAt','registrationCloseAt','codingOpenAt','codingCloseAt','validationStartAt','validationDeadline','checkInOpenAt','checkInCloseAt','tournamentStartAt','estimatedEndAt'],timeline=Object.fromEntries(fields.map((key,i)=>[key,new Date(Date.parse(serverNow)+i*3600000).toISOString()]));
  current={...current,checkInRequired:true,timeline,participants:cup().participants,revision:current.revision+1};flowPhase='SUBMISSION_FROZEN';events.dispatchEvent(new CustomEvent('rps-tournament-update',{detail:{id:current.id}}));await tick();assert.match(text(doc.querySelector('#organizer-overview')),/Vào kiểm thử/);
  flowPhase='CHECK_IN_OPEN';events.dispatchEvent(new CustomEvent('rps-tournament-update',{detail:{id:current.id}}));await tick();assert.match(text(doc.querySelector('#organizer-overview')),/Xem check-in/);doc.querySelector('[data-section=check-in]').click();assert.equal(doc.querySelector('#close-check-in').hidden,true,'timeline check-in cannot be closed through the manual command');
  current={...current,revision:current.revision+1,registrations:current.participants.map(p=>({id:p.participantId,displayName:p.displayName,status:'CHECKED_IN'}))};flowPhase='READY_TO_START';serverNow=new Date(Date.parse(timeline.tournamentStartAt)-1000).toISOString();events.dispatchEvent(new CustomEvent('rps-tournament-update',{detail:{id:current.id}}));await tick();assert.equal(doc.querySelector('#start').hidden,true);
  serverNow=timeline.tournamentStartAt;events.dispatchEvent(new CustomEvent('rps-tournament-update',{detail:{id:current.id}}));await tick();assert.equal(doc.querySelector('#start').hidden,false,'phase and clock changes refresh even without a revision increment');
  current={...current,status:'RUNNING',revision:current.revision+1,matches:[{id:'game',status:'PENDING',round:1,participantA:current.participants[0],participantB:current.participants[1]}]};flowPhase='RUNNING';events.dispatchEvent(new CustomEvent('rps-tournament-update',{detail:{id:current.id}}));await tick();
  for(const section of ['overview','matches','overview']){doc.querySelector('[data-section='+section+']').click();assert.equal(doc.querySelectorAll('#organizer-worker-summary').length,1,'only the visible dashboard owns worker summary');}
  doc.querySelector('[data-section=settings]').click();await doc.querySelector('#cancel').click();const dialog=doc.querySelector('dialog');assert.ok(dialog);dialog.querySelector('textarea').value='Reviewed cancellation';const revision=current.revision;await dialog.querySelector('form').onsubmit({preventDefault(){}});await tick();
  assert.equal(current.status,'CANCELLED');assert.deepEqual(JSON.parse(calls.at(-1).options.body),{revision,reason:'Reviewed cancellation'});assert.equal(doc.querySelector('#cancel').hidden,true);assert.equal(doc.querySelector('dialog'),null);assert.equal(doc.querySelector('#toasts'),null,'controller should not fail on a moved or missing element');
 }finally{events.dispatchEvent(new Event('pagehide'));globalThis.fetch=original;}
});
