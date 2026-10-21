import test from 'node:test';import assert from 'node:assert/strict';import {installDocument,text,descendants} from './helpers/organizer-dom.js';import {renderOrganizerOverview,recentOrganizerActivity} from '../js/tournament/organizer-overview.js';import {cup,metrics} from './helpers/organizer-fixtures.js';
test('overview answers phase, next action and issues without configuration forms',()=>{
 const doc=installDocument('<body><main></main></body>'),root=doc.querySelector('main'),t=cup({timeline:{},participants:[]}),data=metrics(t,{phase:'SUBMISSION_FROZEN'});renderOrganizerOverview(root,t,data);
 assert.match(text(root),/Đã khóa bài/);assert.match(text(root),/Vào kiểm thử/);assert.match(text(root),/6 bài đã khóa/);assert.match(text(root),/Cần xử lý/);assert.equal(root.querySelectorAll('input,select,textarea,form').length,0);assert.ok(root.querySelector('a').href.includes('tournament-final-validation'));
});
test('empty attention is compact and mismatched data cannot masquerade as healthy or invent zero counts',()=>{
 const doc=installDocument('<body><main></main></body>'),root=doc.querySelector('main'),t=cup(),data=metrics(t,{registration:{pending:0},phase:'REGISTRATION_OPEN'});renderOrganizerOverview(root,t,data);
 const attention=root.querySelector('.organizer-attention');assert.equal(attention.dataset.empty,'true');assert.equal(attention.querySelector('h2'),null);assert.match(text(attention),/Không có vấn đề cần xử lý/);
 renderOrganizerOverview(root,t,{...data,tournamentId:'other'});assert.match(text(root),/Đang lấy thống kê/);assert.equal(root.querySelectorAll('dd').length,0);assert.equal(root.querySelector('[data-organizer-command]'),null);
});
test('recent activity is bounded readable and uses text nodes for untrusted names and reasons',()=>{
 const events=Array.from({length:20},(_,i)=>({type:i===19?'TOURNAMENT_PAUSED':'OTHER',at:'2026-11-01T10:00:00Z',reason:'<script>alert(1)</script>'})),t=cup({audit:events});assert.equal(recentOrganizerActivity(t).length,8);assert.equal(recentOrganizerActivity(t)[0].label,'Giải tạm dừng');
 const doc=installDocument('<body><main></main></body>');renderOrganizerOverview(doc.querySelector('main'),t,metrics(t));assert.equal(doc.querySelectorAll('script').length,0);assert.equal(doc.querySelector('.organizer-recent').querySelectorAll('li').length,8);assert.doesNotMatch(text(doc),/"type":/);
});
