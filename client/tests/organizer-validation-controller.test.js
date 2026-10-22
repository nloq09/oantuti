import test from 'node:test';import assert from 'node:assert/strict';import {readFileSync} from 'node:fs';
import {installDocument,text} from './helpers/organizer-dom.js';
const settle=async()=>{for(let i=0;i<14;i++)await new Promise(r=>setImmediate(r));};
test('real dashboard controller refreshes results, searches on server and keeps summary independent of filters',{timeout:8000},async()=>{
 const doc=installDocument(readFileSync(new URL('../tournament-final-validation.html',import.meta.url),'utf8'));doc.hidden=false;
 const events=new EventTarget();globalThis.addEventListener=events.addEventListener.bind(events);globalThis.removeEventListener=events.removeEventListener.bind(events);globalThis.dispatchEvent=events.dispatchEvent.bind(events);globalThis.CustomEvent=class extends Event{constructor(type,options){super(type);this.detail=options?.detail;}};
 globalThis.location=new URL('http://example.test/admin/final-validation.html?id=cup');globalThis.history={replaceState(_a,_b,url){globalThis.location=new URL(url,location.href);}};globalThis.window={history:globalThis.history};
 const t={id:'cup',revision:5,name:'Tournament name',status:'REGISTRATION',timeline:{checkInOpenAt:'2026-10-24T10:00:00Z'}},calls=[];
 let state='RUNNING';const original=globalThis.fetch;
 globalThis.fetch=async(url,options={})=>{calls.push({url,options});let data;
 if(url==='/api/auth/me')data={authenticated:true,user:{id:'admin',role:'ADMIN'}};
 else if(url.endsWith('/organizer-metrics'))data={revision:5,phase:'VALIDATING'};
 else if(url.includes('/final-validations?')){const params=new URL(url,location.origin).searchParams,filtered=params.has('search')||params.has('status');data={revision:5,total:filtered?0:1,page:0,size:25,history:false,retryWindowOpen:true,summary:{total:1,running:state==='RUNNING'?1:0,passed:state==='PASSED'?1:0},items:filtered?[]:[{id:'v1',displayName:'A',version:1,language:'PYTHON',status:state,applied:true,steps:{}}]};}
 else if(url==='/api/tournaments/cup')data=t;else throw Error('Unexpected URL '+url);
 return {ok:true,status:200,json:async()=>data};};
 try{
  await import('../js/tournament/final-validation-dashboard.js?integration');await settle();const root=doc.querySelector('#final-validation-dashboard');assert.match(text(root),/1 bài vẫn đang/);assert.doesNotMatch(text(root),/Tournament name ·|← Quản lý giải|Sau khi hết giờ lập trình/);assert.equal(root.querySelector('.validation-pagination'),null);
  state='PASSED';events.dispatchEvent(new CustomEvent('rps-tournament-update',{detail:{id:'cup',revision:5}}));await settle();assert.match(text(root),/Tất cả bài hiện tại đã kiểm thử xong/);
  const form=root.querySelector('.validation-filters');form.querySelector('input').value='alice';form.onsubmit({preventDefault(){}});await settle();assert.ok(calls.some(c=>c.url.includes('search=alice')));assert.match(text(root),/Không có bài phù hợp/);assert.match(text(root),/Tất cả bài hiện tại đã kiểm thử xong/);
  const select=root.querySelector('select');select.value='FAILED';select.onchange();await settle();assert.ok(calls.some(c=>c.url.includes('status=FAILED')&&c.url.includes('search=alice')));assert.equal(new URL(location.href).searchParams.get('status'),'FAILED');assert.ok(calls.every(c=>c.options.method===undefined||c.options.method==='GET'));
 }finally{events.dispatchEvent(new Event('pagehide'));globalThis.fetch=original;}
});
