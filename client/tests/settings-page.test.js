import test from 'node:test';import assert from 'node:assert/strict';import {readFileSync} from 'node:fs';import {installDocument,descendants,text} from './helpers/organizer-dom.js';import {renderManualTiming} from '../js/tournament/organizer-manual-timing.js';import {cup} from './helpers/organizer-fixtures.js';
const html=readFileSync(new URL('../tournament.html',import.meta.url),'utf8');
test('actual page keeps configuration in Settings and moves every operation to its destination',()=>{
 const doc=installDocument(html),settings=doc.querySelector('#admin-tab');
 for(const id of ['competition-rules','deadline-policy','organizer-policy-settings','organizer-danger-zone'])assert.ok(settings.querySelector('#'+id),id);
 for(const id of ['seeding','admin-match','override-winner','result-match','open-registration','start','close-check-in','participant-operations'])assert.equal(settings.querySelector('#'+id),null,id);
 assert.ok(doc.querySelector('#matches').querySelector('#request-review'));assert.ok(doc.querySelector('#matches').querySelector('[data-admin-control=pause]'));assert.ok(doc.querySelector('#ranking').querySelector('#override-result'));assert.ok(doc.querySelector('#ranking').querySelector('[data-admin-control=finish]'));assert.ok(doc.querySelector('#bracket').querySelector('#seeding'));
 assert.ok(doc.querySelector('#participants-tab').querySelector('#open-registration'));assert.ok(doc.querySelector('#organizer-check-in').querySelector('#close-check-in'));
 assert.equal(settings.children.at(-1).id,'organizer-danger-zone');const ids=descendants(doc).filter(n=>n.id).map(n=>n.id);assert.equal(new Set(ids).size,ids.length,'duplicate ids break handlers');assert.equal(settings.querySelector('pre'),null);
});
test('manual timing edits use existing POST endpoint and reject invalid equality before sending',async()=>{
 const doc=installDocument('<body><main></main></body>'),root=doc.querySelector('main'),t=cup({status:'DRAFT',participants:[]});let refreshes=0;renderManualTiming(root,t,()=>{refreshes++;});const inputs=root.querySelectorAll('input'),form=root.querySelector('form');inputs[0].value=inputs[1].value='2026-11-01T10:00';let calls=[];const original=globalThis.fetch;
 try{globalThis.fetch=async(url,options)=>{calls.push({url,options});return url.endsWith('csrf')?{status:200,ok:true,json:async()=>({headerName:'X-CSRF',token:'csrf'})}:{status:204,ok:true};};await form.onsubmit({preventDefault(){}});assert.equal(calls.length,0);assert.match(text(root),/Hạn check-in phải sau/);inputs[1].value='2026-11-01T11:00';await form.onsubmit({preventDefault(){}});assert.equal(calls.at(-1).url,'/api/tournaments/cup%2Fa/timing');assert.equal(JSON.parse(calls.at(-1).options.body).revision,4);assert.equal(refreshes,1);}finally{globalThis.fetch=original;}
});
test('locked manual timing is readable without unusable editing controls',()=>{const doc=installDocument('<body><main></main></body>'),root=doc.querySelector('main');renderManualTiming(root,cup(),()=>{});assert.equal(root.querySelectorAll('input,button,select,form').length,0);assert.match(text(root),/Lịch đã khóa/);});
test('all organizer routes load the scoped theme and responsive rules do not target Player pages',()=>{
 for(const name of ['tournament.html','tournament-applications.html','tournament-submissions.html','tournament-final-validation.html'])assert.match(readFileSync(new URL('../'+name,import.meta.url),'utf8'),/organizer-control-center.css/);
 for(const name of ['tournament-detail.html','tournament-code.html','tournament-lobby.html'])assert.doesNotMatch(readFileSync(new URL('../'+name,import.meta.url),'utf8'),/organizer-control-center.css/);
 const css=readFileSync(new URL('../css/organizer-control-center.css',import.meta.url),'utf8');assert.match(css,/@media\(max-width:800px\)/);assert.match(css,/@media\(max-width:480px\)/);assert.match(css,/minmax\(0,1fr\)/);assert.match(css,/focus-visible/);
});
