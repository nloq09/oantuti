import test from 'node:test';
import assert from 'node:assert/strict';
import {listWorkers,issueWorkerToken,revokeWorkerToken,reviewWorker,canReviewWorker,workerPercent,controlWorker,workerEvents,workerControls} from '../js/admin/worker-api.js';
test('worker administration sends session CSRF, revision and reason without credential authorization or retries',async()=>{
 const before=globalThis.fetch,calls=[];globalThis.fetch=async(url,options)=>{calls.push({url,...options});return{ok:true,status:200,json:async()=>url.includes('csrf')?{headerName:'X-CSRF-TOKEN',token:'csrf'}:{items:[]}};};
 try{await listWorkers(2);await issueWorkerToken(1,15);await reviewWorker('id/other',7,'APPROVE','TRUSTED','Verified lab');await revokeWorkerToken('token/id');const requests=calls.filter(c=>!c.url.includes('csrf'));assert.equal(requests[0].url,'/api/admin/workers?page=2&size=20');assert.equal(requests[2].url,'/api/admin/workers/id%2Fother/review');assert.deepEqual(JSON.parse(requests[2].body),{revision:7,decision:'APPROVE',trustLevel:'TRUSTED',reason:'Verified lab'});assert.equal(requests[3].url,'/api/admin/workers/registration-tokens/token%2Fid/revoke');assert.ok(requests.every(r=>r.credentials==='same-origin'&&r.cache==='no-store'));assert.ok(requests.slice(1).every(r=>r.headers['X-CSRF-TOKEN']==='csrf'&&!r.headers.Authorization));let count=0;globalThis.fetch=async url=>{count++;return url.includes('csrf')?{ok:true,json:async()=>({headerName:'X-CSRF-TOKEN',token:'csrf'})}:{ok:false,status:409,json:async()=>({error:'STALE_WORKER_REVIEW'})};};await assert.rejects(reviewWorker('id',0,'APPROVE','TRUSTED','Stale'),e=>e.status===409);assert.equal(count,2);}
 finally{globalThis.fetch=before;}
});
test('worker review is only available to pending machines and missing resource measurements stay unknown',()=>{
 assert.equal(canReviewWorker({approvalStatus:'PENDING_APPROVAL',status:'OFFLINE'}),true);for(const approvalStatus of ['APPROVED','REJECTED'])assert.equal(canReviewWorker({approvalStatus,status:'ONLINE'}),false);assert.equal(canReviewWorker({approvalStatus:'PENDING_APPROVAL',status:'DISABLED'}),false);assert.equal(workerPercent(null),'—');assert.equal(workerPercent(0),'0.0%');assert.equal(workerPercent(62.345),'62.3%');
});
test('worker control policy offers finish-current drain and cancel-current disable, with irreversible revoke',()=>{
 const actions=row=>workerControls({approvalStatus:'APPROVED',credentialRevoked:false,...row}).map(a=>a[1]);
 assert.deepEqual(actions({status:'ONLINE'}),['DRAIN','DISABLE','REVOKE']);
 for(const status of ['DRAINING','DRAINED'])assert.deepEqual(actions({status}),['RESUME','DISABLE','REVOKE']);
 assert.deepEqual(actions({status:'DISABLED'}),['RESUME','REVOKE']);
 assert.deepEqual(actions({status:'DISABLED',credentialRevoked:true}),[]);
 assert.deepEqual(actions({approvalStatus:'PENDING_APPROVAL'}),['REVOKE']);
});
test('worker control and audit use encoded IDs and admin cookie CSRF, without automatic mutation retry',async()=>{
 const saved=globalThis.fetch,calls=[];globalThis.fetch=async(url,options)=>{calls.push({url,...options});return {ok:true,status:200,json:async()=>url.includes('csrf')?{headerName:'X-CSRF-TOKEN',token:'csrf'}:{items:[]}};};
 try{await controlWorker('worker/id',3,'REVOKE','Lost machine');await workerEvents('worker/id');const request=calls.find(c=>c.url.endsWith('/control'));assert.equal(request.url,'/api/admin/workers/worker%2Fid/control');assert.deepEqual(JSON.parse(request.body),{revision:3,action:'REVOKE',reason:'Lost machine'});assert.equal(request.headers['X-CSRF-TOKEN'],'csrf');assert.equal(request.credentials,'same-origin');assert.equal(request.headers.Authorization,undefined);assert.equal(calls.at(-1).url,'/api/admin/workers/worker%2Fid/events');}finally{globalThis.fetch=saved;}
});
