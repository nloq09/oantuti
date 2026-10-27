import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {resolve,dirname} from 'node:path';
import {fileURLToPath} from 'node:url';
import {installDocument,text} from './helpers/organizer-dom.js';
import {readNotificationFeed,subscribeNotificationFeed} from '../js/tournament/notification-feed.js';
import {lazyTournamentCreate} from '../js/tournament/lazy-create.js';
import {createRegistrationDisclosure} from '../js/tournament/registration-disclosure-core.js';
import {createPlayerDetailSections} from '../js/tournament/player-detail-sections.js';
import {mountPlayerPublicPage} from '../js/tournament/player-public-pages.js';
import {headerModel} from '../js/tournament/tournament-header.js';
import {node} from '../js/tournament/player-dom.js';
const tick=async()=>{for(let i=0;i<12;i++)await new Promise(resolve=>setImmediate(resolve));};
const deferred=()=>{let resolve,reject;const promise=new Promise((a,b)=>{resolve=a;reject=b;});return{promise,resolve,reject};};
function setup(html='<main></main>'){
 const previous={fetch:globalThis.fetch,addEventListener:globalThis.addEventListener,removeEventListener:globalThis.removeEventListener};
 const doc=installDocument(html);doc.hidden=false;const events=new EventTarget();globalThis.addEventListener=events.addEventListener.bind(events);globalThis.removeEventListener=events.removeEventListener.bind(events);globalThis.location=new URL('http://example.test/tournament-detail.html?id=cup');
 return{doc,events,root:doc.querySelector('main'),close(){events.dispatchEvent(new Event('pagehide'));Object.assign(globalThis,previous);}};
}
test('notification readers share one request, abort independently and forced refresh replaces cached feed',async()=>{
 const env=setup(),wait=deferred(),updates=[];let calls=0;const unsubscribe=subscribeNotificationFeed(feed=>updates.push(feed));
 try{globalThis.fetch=()=>{calls++;return wait.promise;};const abort=new AbortController();const first=readNotificationFeed({force:true,signal:abort.signal}),second=readNotificationFeed({force:true});const cancelled=assert.rejects(first,error=>error.name==='AbortError');abort.abort();await cancelled;assert.equal(calls,1);wait.resolve({ok:true,json:async()=>({items:[],unread:3,active:[]})});assert.equal((await second).unread,3);assert.equal(updates.length,1);assert.equal((await readNotificationFeed()).unread,3);assert.equal(calls,1);
 globalThis.fetch=async()=>{calls++;return{ok:true,json:async()=>({items:[],unread:0,active:[]})};};assert.equal((await readNotificationFeed({force:true})).unread,0);assert.equal(calls,2);
 }finally{unsubscribe();env.close();}
});
test('a failed notification request can recover without retaining old personal data',async()=>{
 const env=setup();try{globalThis.fetch=async()=>({ok:false,status:401,json:async()=>({error:'UNAUTHENTICATED'})});await assert.rejects(readNotificationFeed({force:true}),/UNAUTHENTICATED/);globalThis.fetch=async()=>({ok:true,json:async()=>({items:[],unread:1,active:[]})});assert.equal((await readNotificationFeed()).unread,1);}finally{env.close();}
});
test('create form loads once on demand and retains fields when opened repeatedly',async()=>{
 const env=setup('<main><form><input value=""></form></main>'),wait=deferred();let loads=0,mounts=0;
 try{const view=lazyTournamentCreate(env.root,{load:()=>{loads++;return wait.promise;}});assert.equal(loads,0);const first=view.ensure();assert.equal(first,view.ensure());assert.equal(env.root.getAttribute('aria-busy'),'true');await tick();assert.equal(loads,1);wait.resolve(()=>{mounts++;});await first;const input=env.root.querySelector('input');input.value='Typed name';await view.ensure();assert.equal(input.value,'Typed name');assert.equal(mounts,1);assert.equal(env.root.querySelector('form').getAttribute('inert'),null);view.dispose();}finally{env.close();}
});
test('create load failure keeps its draft and disposing a pending loader prevents late mounting',async()=>{
 const env=setup('<main><form><input></form></main>');try{let fail=true,mounts=0;const view=lazyTournamentCreate(env.root,{load:async()=>{if(fail)throw Error('offline');return()=>mounts++;}});env.root.querySelector('input').value='Keep me';await view.ensure();assert.match(text(env.root),/Chưa tải được/);assert.equal(env.root.querySelector('input').value,'Keep me');fail=false;await view.ensure();assert.equal(mounts,1);view.dispose();const wait=deferred();const late=lazyTournamentCreate(env.root,{load:()=>wait.promise});const pending=late.ensure();late.dispose();wait.resolve(()=>mounts++);await pending;assert.equal(mounts,1);}finally{env.close();}
});
test('closing registration during its first module load never reopens it or moves the panel',async()=>{
 const env=setup(),wait=deferred();let tab='overview',loads=0;const t={id:'cup',status:'REGISTRATION',botPolicy:{},registrationPolicy:{},participants:[]},account={id:'player',username:'player'},me={states:[]};let view;
 function paint(){env.root.replaceChildren();const card=node('section',undefined,'player-next-action'),cta=node('a','Đăng ký');cta.id='player-primary-action-REGISTER';card.append(cta);env.root.append(card);view.attach(env.root,{tournament:t,account,me,tab});}
 try{view=createRegistrationDisclosure({sectionMode:true,selectTab:next=>{tab=next;paint();},loadRenderers:()=>{loads++;return wait.promise;},refresh(){},onRegistered(){}});paint();view.open();assert.equal(env.root.querySelector('#player-registration-panel').hidden,false);tab='overview';paint();await tick();wait.resolve({registrationForm:root=>{const form=node('form');form.append(node('input'));root.append(form);}});await tick();assert.equal(env.root.querySelector('#player-registration-panel').hidden,true);view.open();assert.equal(loads,1);const panel=env.root.querySelector('#player-registration-panel');assert.equal(panel.previousElementSibling,env.root.querySelector('.player-next-action'));const input=panel.querySelector('input');input.value='Draft';view.open();assert.equal(panel.querySelector('input'),input);assert.equal(input.value,'Draft');}finally{env.close();}
});
test('a section closed while its module loads stops the late controller and can open cleanly again',async()=>{
 const env=setup(),sections=createPlayerDetailSections(),wait=deferred();let stops=0;
 try{sections.begin({tournament:{id:'cup',revision:1},snapshot:'1'});const element=sections.attach(env.root,{id:'ranking',title:'Bảng điểm',render:()=>wait.promise});sections.finish();sections.open('ranking');element.open=false;element.dispatchEvent(new Event('toggle'));wait.resolve({stop:()=>stops++});await tick();assert.equal(stops,1);assert.equal(element.open,false);sections.clear();}finally{env.close();}
});
test('public participant paging fetches only visible pages and ignores a response after closing',async()=>{
 const env=setup(),calls=[];let pending;
 try{globalThis.fetch=async(url,options)=>{calls.push({url,options});if(pending)return pending.promise;const page=Number(new URL(url,'http://example.test').searchParams.get('page'));return{ok:true,json:async()=>({revision:1,page,size:20,total:4000,items:Array.from({length:20},(_,i)=>({displayName:'Player '+(page*20+i),ownerName:'Owner',status:'ACTIVE'}))})};};const view=mountPlayerPublicPage(env.root,{id:'cup',revision:1},'participants');await tick();assert.equal(env.root.querySelectorAll('tbody tr').length,20);assert.match(calls[0].url,/page=0&size=20/);env.root.querySelectorAll('button')[1].onclick();await tick();assert.match(calls[1].url,/page=1&size=20/);assert.match(text(env.root),/Player 20/);pending=deferred();env.root.querySelectorAll('button')[2].onclick();await tick();view.stop();assert.equal(calls.at(-1).options.signal.aborted,true);pending.resolve({ok:true,json:async()=>({revision:2,page:0,size:20,total:1,items:[{displayName:'Must not paint'}]})});await tick();assert.doesNotMatch(text(env.root),/Must not paint/);}finally{env.close();}
});
test('light summaries preserve the exact visible participant count and discovery excludes unrelated code',()=>{
 assert.equal(headerModel({name:'Cup',format:'SINGLE_ELIMINATION',status:'RUNNING',participants:[],participantCount:4000,maxParticipants:4000},null,{audience:'player'}).count,4000);
 const seen=new Set();function scan(file){file=resolve(file);if(seen.has(file))return;seen.add(file);for(const match of readFileSync(file,'utf8').matchAll(/^import\s*(?:[^;\n]*?from\s*)?["'](\.[^"']+)["']/gm))scan(resolve(dirname(file),match[1]));}
 scan(fileURLToPath(new URL('../js/tournament/tournament-list.js',import.meta.url)));assert.ok(seen.size<=20,`Discovery loaded ${seen.size} modules`);for(const file of seen)assert.doesNotMatch(file.replaceAll('\\','/'),/tournament-create-form|team-view|code-workspace|audit-presentation|next-action/);
});
test('a temporary session lookup error can be retried without reloading the page',async()=>{
 const env=setup();try{let calls=0;globalThis.fetch=async()=>{calls++;if(calls===1)throw Error('offline');return{ok:true,json:async()=>({authenticated:true,user:{id:'recovered'}})};};const {getAccount}=await import('../js/account/account-session.js?performance-retry');await assert.rejects(getAccount(),/offline/);assert.equal((await getAccount()).id,'recovered');assert.equal(calls,2);}finally{env.close();}
});
