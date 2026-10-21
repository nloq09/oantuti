import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {installDocument,text} from './helpers/organizer-dom.js';

test('review controller preserves drafts and explicit filters, blocks stale controls, and clears private snapshots on logout',async()=>{
 const saved=Object.fromEntries(['fetch','location','history','addEventListener','removeEventListener'].map(key=>[key,globalThis[key]]));
 const events=new EventTarget(),doc=installDocument(readFileSync(new URL('../tournament-applications.html',import.meta.url),'utf8'));doc.hidden=false;
 const base='/api/tournaments/cup',calls=[];let fail=null,revision=8;
 const counts={total:27,approved:2,needsReview:24,waitlisted:1,rejected:0,cancelled:0};
 const t=()=>({id:'cup',revision,status:'REGISTRATION',timeline:{registrationOpenAt:'2026-10-21T12:00:00Z'}});
 const summary=()=>({revision,serverNow:'2026-10-21T12:01:00Z',counts,capacity:10,remainingSlots:8,policy:{approvalPolicy:'HYBRID',requireAffiliationVerification:false},canReview:true,registrationOpen:true,canClose:true,registrationCloseAt:'2026-10-21T13:00:00Z',codingOpenAt:'2026-10-21T14:00:00Z',control:{enabled:true,lockedAt:null}});
 const pending={id:'entry',displayName:'Applicant',status:'NEEDS_REVIEW',intendedLanguage:'CPP',registeredAt:'2026-10-21T12:00:00Z'};
 const settle=async condition=>{for(let i=0;i<50;i++){if(condition())return;await new Promise(resolve=>setImmediate(resolve));}assert.ok(condition(),'UI update did not settle');};
 const invalidate=()=>{const event=new Event('rps-tournament-update');event.detail={id:'cup'};events.dispatchEvent(event);};
 try{
  globalThis.location={href:'http://localhost/admin/tournament-applications.html?id=cup',pathname:'/admin/tournament-applications.html',search:'?id=cup'};
  globalThis.history={replaceState:(_a,_b,url)=>{location.href=url.href;location.search=url.search;}};
  globalThis.addEventListener=events.addEventListener.bind(events);globalThis.removeEventListener=events.removeEventListener.bind(events);
  globalThis.fetch=async(url,options={})=>{
   calls.push({url,options});if(fail)return{ok:false,status:fail==='UNAUTHENTICATED'?401:503,json:async()=>({error:fail})};
   let data;
   if(url.endsWith('/csrf'))data={headerName:'X-CSRF',token:'token'};
   else if(url.endsWith('/review')){revision++;counts.needsReview--;counts.approved++;data={};}
   else if(url===base)data=t();
   else if(url.endsWith('/registration-summary'))data=summary();
   else data={revision,reviewOpen:true,codingOpenAt:summary().codingOpenAt,items:[{...pending,status:url.includes('APPROVED')?'APPROVED':'NEEDS_REVIEW'}]};
   return{ok:true,status:200,json:async()=>data};
  };
  await import('../js/tournament/application-review.js?controller-test');
  const root=doc.querySelector('#applications'),filter=doc.querySelector('#application-status'),dashboard=doc.querySelector('#registration-dashboard');
  await settle(()=>root.querySelector('textarea'));assert.equal(filter.value,'NEEDS_REVIEW');
  const explanation=root.querySelector('textarea');explanation.value='Do not erase my review';explanation.focus();invalidate();
  await settle(()=>calls.filter(c=>c.url.includes('participant-applications?')).length>=2);
  await new Promise(resolve=>setImmediate(resolve));assert.equal(root.querySelector('textarea'),explanation);assert.equal(explanation.value,'Do not erase my review');
  doc.activeElement=null;
  await root.querySelector('button').click();
  const mutation=calls.find(c=>c.url.endsWith('/review'));assert.deepEqual(JSON.parse(mutation.options.body),{decision:'APPROVED',reason:'Do not erase my review',revision:8});
  assert.equal(calls.filter(c=>c.url.endsWith('/review')).length,1);assert.equal(filter.value,'NEEDS_REVIEW');
  filter.value='';filter.onchange();await settle(()=>calls.at(-1).url===base+'/participant-applications?page=0');assert.match(location.search,/status=ALL/);
  invalidate();await new Promise(resolve=>setImmediate(resolve));await new Promise(resolve=>setImmediate(resolve));assert.equal(filter.value,'');
  fail='SERVER_ERROR';await doc.querySelector('#refresh-applications').click();
  assert.ok(root.querySelector('textarea'));assert.ok([...root.querySelectorAll('button,textarea'),...dashboard.querySelectorAll('button')].every(control=>control.disabled));
  assert.equal(doc.querySelector('#refresh-applications').disabled,false);
  fail=null;await doc.querySelector('#refresh-applications').click();assert.equal(root.querySelector('textarea').disabled,false);
  fail='UNAUTHENTICATED';await doc.querySelector('#refresh-applications').click();assert.equal(root.childElementCount,0);assert.equal(dashboard.childElementCount,0);assert.doesNotMatch(text(root),/Applicant/);
 }finally{events.dispatchEvent(new Event('pagehide'));for(const [key,value]of Object.entries(saved))if(value===undefined)delete globalThis[key];else globalThis[key]=value;}
});
