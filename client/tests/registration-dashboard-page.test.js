import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {installDocument,text} from './helpers/organizer-dom.js';
import {initialApplicationFilter,readRegistrationDashboard,registrationDashboardState} from '../js/tournament/registration-dashboard-model.js';
import {renderRegistrationDashboard} from '../js/tournament/registration-dashboard.js';
import {updateHeaderTimers} from '../js/tournament/tournament-header.js';

const tournament={id:'cup/a',revision:8,status:'REGISTRATION',timeline:{registrationOpenAt:'2026-10-21T12:00:00Z'}};
const summary={revision:8,serverNow:'2026-10-21T12:01:00Z',counts:{total:3842,approved:3701,needsReview:24,waitlisted:105,rejected:12,cancelled:0},capacity:4000,remainingSlots:299,policy:{approvalPolicy:'HYBRID',requireAffiliationVerification:false},canEditPolicy:false,canReview:true,registrationOpen:true,canClose:true,canLock:false,registrationCloseAt:'2026-10-21T13:00:00Z',codingOpenAt:'2026-10-21T14:00:00Z',control:{enabled:true,lockedAt:null}};
const host=()=>installDocument('<body><main></main></body>').querySelector('main');
function api({t=tournament,s=summary,items=[],listRevision=8}={}){const calls=[];return {calls,request:async url=>{calls.push(url);return url.endsWith('registration-summary')?s:url.includes('participant-applications')?{revision:listRevision,items}:t;}};}

test('first visit prioritizes exceptions; explicit all or other filters survive refresh',()=>{
 assert.equal(initialApplicationFilter('?id=a',summary.counts),'NEEDS_REVIEW');
 for(const value of ['ALL',''])assert.equal(initialApplicationFilter('?status='+value,summary.counts),'');
 for(const value of ['APPROVED','WAITLISTED','REJECTED','CANCELLED'])assert.equal(initialApplicationFilter('?status='+value,summary.counts),value);
 assert.equal(initialApplicationFilter('?status=PENDING',summary.counts),'NEEDS_REVIEW');
 assert.equal(initialApplicationFilter('',{needsReview:0}),'');
 assert.equal(initialApplicationFilter('?status=unknown',{needsReview:1}),'NEEDS_REVIEW');
});
test('dashboard reads aggregates plus one server page, not thousands of application details',async()=>{
 const fixture=api({items:Array(24).fill({status:'NEEDS_REVIEW'})});
 const result=await readRegistrationDashboard('/cup',{request:fixture.request});
 assert.equal(result.summary.counts.total,3842);assert.equal(result.data.items.length,24);
 assert.deepEqual(fixture.calls,['/cup','/cup/registration-summary','/cup/participant-applications?page=0&status=NEEDS_REVIEW']);
 const all=api({items:Array(25).fill({})});await readRegistrationDashboard('/cup',{request:all.request,status:''});
 assert.equal(all.calls.at(-1),'/cup/participant-applications?page=0');
});
test('selected filter stays selected after needs-review becomes empty and page clamps after removal',async()=>{
 const fixture=api({s:{...summary,counts:{...summary.counts,needsReview:0}}});
 const result=await readRegistrationDashboard('/cup',{request:fixture.request,status:'NEEDS_REVIEW',page:3});
 assert.equal(result.status,'NEEDS_REVIEW');assert.equal(result.page,0);
 const all=api();const last=await readRegistrationDashboard('/cup',{request:all.request,status:'',page:999});assert.equal(last.page,153);
});
test('mixed revision is retried once then rejected before rendering review controls',async()=>{
 const fixture=api({listRevision:7});
 await assert.rejects(readRegistrationDashboard('/cup',{request:fixture.request}),/REGISTRATION_SNAPSHOT_CHANGED/);
 assert.equal(fixture.calls.length,6);
 const mismatched=api({t:{...tournament,revision:7}});
 await assert.rejects(readRegistrationDashboard('/cup',{request:mismatched.request}),/REGISTRATION_SNAPSHOT_CHANGED/);
 assert.equal(mismatched.calls.length,4);assert.ok(mismatched.calls.every(url=>!url.includes('participant-applications')));
});
test('superseded dashboard requests cannot expose an old snapshot and forward AbortSignal',async()=>{
 const fixture=api();assert.equal(await readRegistrationDashboard('/cup',{request:fixture.request,isCurrent:()=>false}),null);assert.equal(fixture.calls.length,2);
 const abort=new AbortController();let observed;
 await assert.rejects(readRegistrationDashboard('/cup',{signal:abort.signal,request:async(_u,_b,_m,options)=>{observed=options.signal;throw new Error('offline');}}),/offline/);
 assert.equal(observed,abort.signal);
});
test('aggregate cards have exact totals and route directly to each queue',()=>{
 const root=host(),filters=[];renderRegistrationDashboard(root,tournament,summary,{status:'NEEDS_REVIEW',selectFilter:value=>filters.push(value)});
 const cards=root.querySelectorAll('[data-registration-metric]');assert.equal(cards.length,5);
 assert.deepEqual(cards.map(card=>card.querySelector('strong').textContent),['3.842','3.701','24','105','12']);
 for(const card of cards)card.click();assert.deepEqual(filters,['','APPROVED','NEEDS_REVIEW','WAITLISTED','REJECTED']);
 assert.equal(cards[2].getAttribute('aria-pressed'),'true');
 const primary=root.querySelector('[data-registration-focus="review-queue"]');assert.equal(primary.textContent,'Xử lý 24 đơn');primary.click();assert.equal(filters.at(-1),'NEEDS_REVIEW');
 assert.match(text(root),/299 suất/);assert.equal(root.querySelectorAll('.registration-roster').length,1);
});
test('closed review is read only, policy edit is server authorized and locked roster has no mutations',()=>{
 const root=host();renderRegistrationDashboard(root,tournament,{...summary,canReview:false,canClose:false,registrationOpen:false,control:{enabled:true,lockedAt:'2026-10-21T13:30:00Z',lockedCount:3701,lockedByName:'<img onerror=alert(1)>'}});
 assert.equal(root.querySelector('[data-registration-focus="review-queue"]').textContent,'Xem 24 đơn');
 assert.equal(root.querySelector('[data-registration-focus="edit-policy"]'),null);
 assert.equal(root.querySelector('.registration-roster').querySelectorAll('button,input,textarea').length,0);
 assert.equal(root.querySelector('img'),null);assert.match(text(root),/<img onerror/);
 renderRegistrationDashboard(root,{...tournament,status:'DRAFT'},{...summary,canEditPolicy:true,canClose:false});
 assert.equal(root.querySelector('[data-registration-focus="edit-policy"]').href,'/tournament.html?id=cup%2Fa&view=settings#deadline-policy');
});
test('empty exception queue directs organizer to roster lock when ready',()=>{
 const root=host();let focused=0;renderRegistrationDashboard(root,tournament,{...summary,counts:{...summary.counts,needsReview:0},canLock:true,canClose:false,registrationOpen:false},{focusRoster:()=>focused++});
 assert.equal(root.querySelector('[data-registration-focus="review-queue"]'),null);
 root.querySelector('[data-registration-focus="registration-next"]').click();assert.equal(focused,1);assert.match(text(root),/Không có đơn cần xử lý/);
});
test('admission states and countdown use server time across draft, scheduled, open and closed phases',()=>{
 assert.match(registrationDashboardState({...tournament,status:'DRAFT'},summary).label,/Chưa mở/);
 assert.match(registrationDashboardState(tournament,{...summary,serverNow:'2026-10-21T11:00:00Z'}).label,/Sắp mở/);
 assert.match(registrationDashboardState(tournament,{...summary,registrationOpen:false}).label,/Còn thời gian xét duyệt/);
 const root=host();renderRegistrationDashboard(root,tournament,summary);
 const timer=root.querySelector('[role="timer"]');assert.equal(timer.textContent,'Còn 00:59:00');
 updateHeaderTimers(root,Number(timer.dataset.receivedAt)+60000);assert.equal(timer.textContent,'Còn 00:58:00');
 updateHeaderTimers(root,Number(timer.dataset.receivedAt)+3600000);assert.match(timer.textContent,/Chờ server cập nhật/);
});
test('dashboard markup has unique ids, separate queue and scoped responsive layout',()=>{
 const html=readFileSync(new URL('../tournament-applications.html',import.meta.url),'utf8'),ids=[...html.matchAll(/id="([^"]+)"/g)].map(match=>match[1]);assert.equal(new Set(ids).size,ids.length);
 const doc=installDocument(html);assert.ok(doc.querySelector('#registration-dashboard'));assert.ok(doc.querySelector('.registration-queue #applications'));assert.ok(doc.querySelector('#application-queue-heading'));
 const css=readFileSync(new URL('../css/organizer-control-center.css',import.meta.url),'utf8');assert.match(css,/@media\(max-width:480px\)\{\.organizer-page \.registration-summary-grid/);assert.match(css,/\.registration-queue #applications\{display:grid;gap:16px/);
});
