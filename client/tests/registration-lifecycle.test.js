import test from 'node:test';
import assert from 'node:assert/strict';
import {installDocument,text} from './helpers/organizer-dom.js';
import {renderRegistrationLifecycle} from '../js/tournament/registration-lifecycle.js';
import {notificationText} from '../js/tournament/notifications.js';
const tournament={id:'cup/a',status:'REGISTRATION',timeline:{}};
const summary={control:{enabled:true,closedAt:null,lockedAt:null},counts:{approved:2,needsReview:0,waitlisted:1,rejected:0},registrationOpen:true,canClose:true,canLock:false,revision:7,registrationCloseAt:'2026-10-21T13:00:00Z',codingOpenAt:'2026-10-21T14:00:00Z',serverNow:'2026-10-21T12:00:00Z'};
function root(){return installDocument('<body><main></main></body>').querySelector('main');}
test('registration closes automatically and never requires a manual roster lock',()=>{
 const open=root();renderRegistrationLifecycle(open,tournament,summary,()=>{});assert.equal(open.querySelector('button').textContent,'Đóng cửa đăng ký');assert.doesNotMatch(text(open),/Chốt danh sách thi đấu/);
 const closed=root();renderRegistrationLifecycle(closed,tournament,{...summary,registrationOpen:false,canClose:false,canLock:true},()=>{});assert.equal(closed.querySelectorAll('button').length,0);assert.match(text(closed),/không cần chốt danh sách thủ công/);
});
test('pending applications remain restricted without blocking approved participants',()=>{
 const host=root();renderRegistrationLifecycle(host,tournament,{...summary,registrationOpen:false,canClose:false,counts:{...summary.counts,needsReview:24}},()=>{});assert.match(text(host),/24 đơn chưa được duyệt/);assert.match(host.querySelector('a').href,/status=NEEDS_REVIEW/);assert.equal(host.querySelectorAll('button').length,0);
});
test('locked roster shows durable summary and no mutation controls',()=>{
 const host=root();renderRegistrationLifecycle(host,tournament,{...summary,control:{enabled:true,lockedAt:'2026-10-21T13:30:00Z',lockedCount:2,lockedByName:'Organizer'}},()=>{});assert.match(text(host),/Danh sách đã được chốt trước đó/);assert.match(text(host),/2 người/);assert.match(text(host),/Organizer/);assert.equal(host.querySelectorAll('button,input,textarea').length,0);
});
test('close confirmation sends reason and revision only after confirmation',async()=>{
 const host=root();let calls=[],refreshed=0;const original=globalThis.fetch;
 try{globalThis.fetch=async(url,options)=>{calls.push({url,options});return url.endsWith('csrf')?{status:200,ok:true,json:async()=>({headerName:'X-CSRF',token:'csrf'})}:{status:204,ok:true};};renderRegistrationLifecycle(host,tournament,summary,()=>{refreshed++;});host.querySelector('button').click();assert.equal(calls.length,0);assert.match(text(document.body),/không cần chốt danh sách/);const form=document.querySelector('dialog form');form.querySelector('textarea').value='Finish registration';await form.onsubmit({preventDefault(){}});assert.equal(calls.at(-1).url,'/api/tournaments/cup%2Fa/registration/close');assert.deepEqual(JSON.parse(calls.at(-1).options.body),{revision:7,reason:'Finish registration'});assert.equal(refreshed,1);}finally{globalThis.fetch=original;}
});
test('coding opens on schedule even when no manual roster lock exists',()=>{
 const host=root();renderRegistrationLifecycle(host,tournament,{...summary,serverNow:'2026-10-21T13:55:00Z'},()=>{});assert.match(text(host),/Phần lập trình tự mở lúc/);assert.match(notificationText({type:'WAITLIST_PROMOTED'}),/danh sách chờ sang được chấp nhận/);
});


test('draft and upcoming registration are not described as closed',()=>{
 for(const t of [{...tournament,status:'DRAFT'},{...tournament,timeline:{registrationOpenAt:'2026-10-22T12:00:00Z'}}]){const host=root();renderRegistrationLifecycle(host,t,summary,()=>{});assert.match(text(host),/chưa mở đăng ký/);assert.doesNotMatch(text(host),/Đã đóng nhận đơn/);assert.equal(host.querySelectorAll('button').length,0);}
});

test('organizer may open registration early or leave it to the server schedule',()=>{const host=root();renderRegistrationLifecycle(host,{...tournament,status:'DRAFT',timeline:{registrationOpenAt:'2026-10-22T12:00:00Z'}},{...summary,canOpen:true,canClose:false},()=>{});assert.match(text(host),/Server sẽ tự mở lúc/);assert.equal(host.querySelector('button').textContent,'Mở đăng ký ngay');assert.doesNotMatch(text(host),/phần lập trình vẫn bị khóa/);});
