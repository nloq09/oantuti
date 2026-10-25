import test from 'node:test';
import assert from 'node:assert/strict';
import {workerPolicy,saveWorkerPolicy,capacityLimit} from '../js/admin/worker-policy-api.js';
test('shared pool policy and individual capacity use revision, reason and session CSRF without group metadata',async()=>{
 const original=globalThis.fetch,calls=[];globalThis.fetch=async(url,options)=>{calls.push({url,...options});return{ok:true,status:200,json:async()=>url.includes('csrf')?{headerName:'X-CSRF-TOKEN',token:'csrf'}:{items:[]}};};
 try{await workerPolicy('cup/id');const policy={revision:2,mode:'AUTO',minimumTrust:'TRUSTED',maxMatches:8,reason:'Automatic pool'};await saveWorkerPolicy('cup/id',policy);await capacityLimit('worker/id',3,2,'Resource budget');const requests=calls.filter(r=>!r.url.includes('csrf'));
 assert.equal(requests[0].url,'/api/admin/tournaments/cup%2Fid/worker-policy');assert.deepEqual(JSON.parse(requests[1].body),policy);assert.equal(requests[2].url,'/api/admin/workers/worker%2Fid/capacity');assert.ok(requests.every(r=>r.credentials==='same-origin'&&!r.headers.Authorization));assert.ok(requests.filter(r=>r.method==='POST').every(r=>r.headers['X-CSRF-TOKEN']==='csrf'));
 }finally{globalThis.fetch=original;}
});
