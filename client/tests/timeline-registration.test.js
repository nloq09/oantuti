import test from 'node:test';
import assert from 'node:assert/strict';
import {canApply,applicationPayload} from '../js/tournament/timeline-registration-model.js';
import {renderTimelineRegistration} from '../js/tournament/timeline-registration.js';

test('registration availability follows the server phase and excludes pending, rejected and future phases',()=>{
 for(const phase of ['DRAFT','UPCOMING','REGISTRATION_REVIEW','WAITING_FOR_CODING','CODING_OPEN','VALIDATING','CHECK_IN_OPEN','RUNNING','FINISHED'])assert.equal(canApply({phase,registrationStatus:'NOT_REGISTERED'}),false);
 for(const registrationStatus of ['NOT_REGISTERED','CANCELLED'])assert.equal(canApply({phase:'REGISTRATION_OPEN',registrationStatus}),true);
 for(const registrationStatus of ['PENDING','APPROVED','WAITLISTED','REJECTED'])assert.equal(canApply({phase:'REGISTRATION_OPEN',registrationStatus}),false);
});
test('application contains only a person’s intended language, optional school and rules acceptance',()=>{
 assert.deepEqual(applicationPayload('CPP',' UET ',true),{intendedLanguage:'CPP',affiliation:'UET',rulesAccepted:true});
 assert.throws(()=>applicationPayload('CPP','',false),/RULES_ACCEPTANCE_REQUIRED/);
 assert.throws(()=>applicationPayload('RUST','',true),/BOT_LANGUAGE_NOT_ALLOWED/);
 assert.throws(()=>applicationPayload('JAVA','x'.repeat(81),true),/INVALID_AFFILIATION/);
 assert.throws(()=>applicationPayload('JAVA','bad\nvalue',true),/INVALID_AFFILIATION/);
});
class Element {
 constructor(tag){this.tag=tag;this.children=[];this.dataset={};this.textContent='';}
 append(...nodes){this.children.push(...nodes);}
 setAttribute(){}
}
function setup(){globalThis.document={createElement:tag=>new Element(tag)};globalThis.Option=class extends Element{constructor(text,value){super('option');this.textContent=text;this.value=value;}};return new Element('main');}
const tournament={id:'cup',name:'Coding exam',format:'ROUND_ROBIN',timeline:{registrationOpenAt:'2026-10-19T01:00:00Z',registrationCloseAt:'2026-10-19T02:00:00Z',codingOpenAt:'2026-10-20T01:00:00Z',codingCloseAt:'2026-10-20T02:00:00Z',checkInOpenAt:'2026-10-20T03:00:00Z',tournamentStartAt:'2026-10-20T04:00:00Z'},botPolicy:{allowedLanguages:['CPP','JAVA','PYTHON']}};
function all(element){return [element,...element.children.filter(x=>x instanceof Element).flatMap(all)];}
test('upcoming screen offers rules and schedule without registration, bot selection or an editor',()=>{
 const root=setup();renderTimelineRegistration(root,tournament,{username:'player'},{phase:'UPCOMING'},()=>{});const nodes=all(root);
 assert.ok(nodes.some(x=>x.tag==='summary'&&x.textContent==='Xem luật'));
 assert.equal(nodes.some(x=>['form','button','select','textarea'].includes(x.tag)),false);
});
test('all submission modes show the same person registration form without bot controls',()=>{
 for(const submissionMode of ['PRE_UPLOADED_ONLY','CODE_IN_PLATFORM_ONLY','BOTH']){const root=setup();renderTimelineRegistration(root,{...tournament,botPolicy:{...tournament.botPolicy,submissionMode}},{username:'player'},{phase:'REGISTRATION_OPEN',registrationStatus:'NOT_REGISTERED'},()=>{});const nodes=all(root);assert.equal(nodes.filter(x=>x.tag==='form').length,1);assert.equal(nodes.filter(x=>x.tag==='select').length,1);assert.equal(nodes.some(x=>x.tag==='textarea'||x.type==='file'),false);assert.ok(nodes.some(x=>x.tag==='button'&&x.textContent==='Gửi đăng ký'));}
});
test('pending registration has a status and coding countdown without a duplicate form or workspace link',()=>{
 const root=setup();renderTimelineRegistration(root,tournament,{username:'player'},{phase:'REGISTRATION_OPEN',registrationStatus:'PENDING',registration:{displayName:'Player',intendedLanguage:'CPP'}},()=>{});const nodes=all(root);assert.equal(nodes.some(x=>x.tag==='form'),false);assert.ok(nodes.some(x=>x.dataset.codingStarts===tournament.timeline.codingOpenAt));assert.equal(nodes.some(x=>String(x.href).includes('tournament-code')),false);
});
test('approved participants wait with rules and protocol but no editor before coding',()=>{
 const root=setup();renderTimelineRegistration(root,tournament,{username:'player'},{phase:'WAITING_FOR_CODING',registrationStatus:'APPROVED',registration:{displayName:'Player',intendedLanguage:'CPP'},allowedLanguages:['CPP']},()=>{});const nodes=all(root);
 assert.ok(nodes.some(x=>x.textContent==='Phần thi lập trình chưa bắt đầu'));assert.ok(nodes.some(x=>x.href==='/bot-protocol.html'));assert.equal(nodes.some(x=>x.tag==='textarea'||String(x.href).includes('tournament-code')),false);
});
test('rejected participants see their reason and cannot open coding or submit another application',()=>{
 const root=setup();renderTimelineRegistration(root,tournament,{username:'player'},{phase:'REGISTRATION_REVIEW',registrationStatus:'REJECTED',registration:{displayName:'Player',intendedLanguage:'CPP',reviewReason:'Missing information'}},()=>{});const nodes=all(root);
 assert.ok(nodes.some(x=>x.textContent.includes('Missing information')));assert.equal(nodes.some(x=>x.tag==='form'||String(x.href).includes('tournament-code')),false);
});


test('needs-review registration stays visible with cancellation and never offers duplicate signup',()=>{
 const root=setup();renderTimelineRegistration(root,tournament,{username:'player'},{phase:'REGISTRATION_OPEN',registrationStatus:'NEEDS_REVIEW',registration:{displayName:'Player',intendedLanguage:'CPP'}},()=>{});const nodes=all(root);
 assert.equal(nodes.some(x=>x.tag==='form'),false);assert.ok(nodes.some(x=>x.textContent.includes('ban tổ chức xác nhận')));assert.ok(nodes.some(x=>x.tag==='button'&&x.textContent.includes('Hủy')));
});


test('waitlisted player sees only their current position and has no coding access',()=>{
 const root=setup();renderTimelineRegistration(root,tournament,{username:'player'},{phase:'REGISTRATION_OPEN',registrationStatus:'WAITLISTED',registration:{displayName:'Player',intendedLanguage:'CPP',waitlistPosition:12}},()=>{});const nodes=all(root);
 assert.ok(nodes.some(x=>x.textContent==='Vị trí danh sách chờ: #12'));assert.equal(nodes.some(x=>x.tag==='form'||String(x.href).includes('tournament-code')),false);
});


test('closed admission still allows withdrawal before explicit roster lock',()=>{
 const root=setup();renderTimelineRegistration(root,tournament,{username:'player'},{phase:'REGISTRATION_REVIEW',registrationStatus:'APPROVED',registrationControl:{enabled:true,lockedAt:null},registration:{displayName:'Player',intendedLanguage:'CPP'}},()=>{});assert.ok(all(root).some(x=>x.tag==='button'&&x.textContent.includes('Hủy')));assert.equal(all(root).some(x=>x.tag==='form'),false);
});

test('locked roster removes withdrawal controls while waiting for coding',()=>{
 const root=setup();renderTimelineRegistration(root,tournament,{username:'player'},{phase:'WAITING_FOR_CODING',registrationStatus:'APPROVED',registrationControl:{enabled:true,lockedAt:'2026-10-19T02:05:00Z'},registration:{displayName:'Player',intendedLanguage:'CPP'}},()=>{});assert.equal(all(root).some(x=>x.tag==='button'&&x.textContent.includes('Hủy')),false);
});
