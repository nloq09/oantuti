import test from 'node:test';
import assert from 'node:assert/strict';
import {installDocument,text} from './helpers/organizer-dom.js';
import {registrationPolicyEditor,renderRegistrationPolicy,eligibilityReasonLabels} from '../js/tournament/registration-policy.js';
import {applicationLabels,canApply} from '../js/tournament/timeline-registration-model.js';

function root(){return installDocument('<body><main></main></body>').querySelector('main');}
test('new registration policy editor defaults to hybrid and requires explicit affiliation verification',()=>{
 const host=root(),editor=registrationPolicyEditor(host);
 assert.deepEqual(editor.read(),{approvalPolicy:'HYBRID',requireAffiliationVerification:false});
 host.querySelector('select').value='AUTO_APPROVE';host.querySelector('input').checked=true;
 assert.deepEqual(editor.read(),{approvalPolicy:'AUTO_APPROVE',requireAffiliationVerification:true});
 editor.setEnabled(false);assert.equal(editor.box.disabled,true);assert.equal(editor.box.hidden,true);
});
test('locked registration policy is readable without editing controls',()=>{
 const host=root();renderRegistrationPolicy(host,{id:'cup',status:'REGISTRATION',timeline:{},registrationPolicy:{approvalPolicy:'MANUAL_REVIEW'}},()=>{});
 assert.match(text(host),/Duyệt thủ công/);assert.equal(host.querySelectorAll('form,input,select,button').length,0);
});
test('draft policy update sends reason revision and existing cookie CSRF to tournament scope',async()=>{
 const host=root();let refreshed=0,calls=[];const original=globalThis.fetch;
 try{
  globalThis.fetch=async(url,options)=>{calls.push({url,options});return url.endsWith('csrf')?{status:200,ok:true,json:async()=>({headerName:'X-CSRF',token:'csrf'})}:{status:204,ok:true};};
  renderRegistrationPolicy(host,{id:'cup/a',revision:8,status:'DRAFT',timeline:{},registrationPolicy:{approvalPolicy:'MANUAL_REVIEW',requireAffiliationVerification:false}},()=>{refreshed++;});
  host.querySelector('select').value='HYBRID';host.querySelectorAll('input')[1].value='Use automatic approval with exceptions';
  await host.querySelector('form').onsubmit({preventDefault(){}});
  const call=calls.at(-1);assert.equal(call.url,'/api/tournaments/cup%2Fa/registration-policy');assert.equal(call.options.method,'PUT');
  assert.deepEqual(JSON.parse(call.options.body),{revision:8,policy:{approvalPolicy:'HYBRID',requireAffiliationVerification:false},reason:'Use automatic approval with exceptions'});assert.equal(refreshed,1);
 }finally{globalThis.fetch=original;}
});
test('needs review is a confirmation state rather than an invitation to register again',()=>{
 assert.match(applicationLabels.NEEDS_REVIEW,/ban tổ chức xác nhận/);
 assert.equal(canApply({phase:'REGISTRATION_OPEN',registrationStatus:'NEEDS_REVIEW'}),false);
 assert.match(eligibilityReasonLabels.AFFILIATION_NOT_VERIFIED,/xác minh/);
});
