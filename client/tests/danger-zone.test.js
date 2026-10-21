import test from 'node:test';import assert from 'node:assert/strict';import {installDocument,text} from './helpers/organizer-dom.js';import {confirmOrganizerDecision,organizerDecisionReason} from '../js/tournament/organizer-decision-dialog.js';import {cancelTournament} from '../js/tournament/tournament-api.js';
test('danger confirmation requires a bounded reason and keeps failure retryable',async()=>{
 const doc=installDocument('<body></body>');let calls=[];const dialog=confirmOrganizerDecision('Hủy giải?','Không thể khôi phục.',async reason=>{calls.push(reason);throw Error('STALE_TOURNAMENT_REVISION');}),form=dialog.querySelector('form'),input=dialog.querySelector('textarea'),confirm=dialog.querySelectorAll('button').at(-1);
 input.value=' ';await form.onsubmit({preventDefault(){}});assert.equal(calls.length,0);assert.equal(dialog.open,true);assert.match(text(dialog),/Nhập lý do/);
 input.value='  Wrong schedule  ';await form.onsubmit({preventDefault(){}});assert.deepEqual(calls,['Wrong schedule']);assert.equal(dialog.open,true);assert.equal(confirm.disabled,false);assert.equal(input.disabled,false);assert.throws(()=>organizerDecisionReason('x'.repeat(501)));
});
test('successful confirmation closes and cancellation does not run on cancel or escape',async()=>{
 const doc=installDocument('<body></body>');let calls=0;let dialog=confirmOrganizerDecision('Hủy','Dừng các trận.',()=>{calls++;return true;});dialog.querySelector('button').onclick();assert.equal(calls,0);assert.equal(doc.querySelector('dialog'),null);
 dialog=confirmOrganizerDecision('Hủy','Dừng các trận.',()=>{calls++;return true;});dialog.querySelector('textarea').value='Valid reason';await dialog.querySelector('form').onsubmit({preventDefault(){}});assert.equal(calls,1);assert.equal(doc.querySelector('dialog'),null);
});
test('cancel endpoint retains CSRF reason revision without mutation retries',async()=>{
 const original=globalThis.fetch,calls=[];try{globalThis.fetch=async(url,options)=>url.endsWith('csrf')?{ok:true,json:async()=>({headerName:'X-CSRF',token:'csrf'})}:(calls.push({url,options}),{ok:false,json:async()=>({error:'STALE_TOURNAMENT_REVISION'})});await assert.rejects(cancelTournament('cup/a',{revision:4,reason:'Reviewed cancellation'}),/STALE_TOURNAMENT_REVISION/);assert.equal(calls.length,1);assert.equal(calls[0].url,'/api/tournaments/cup%2Fa/cancel');assert.equal(calls[0].options.headers['X-CSRF'],'csrf');assert.deepEqual(JSON.parse(calls[0].options.body),{revision:4,reason:'Reviewed cancellation'});}finally{globalThis.fetch=original;}
});
