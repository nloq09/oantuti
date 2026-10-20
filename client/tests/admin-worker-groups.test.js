import test from 'node:test';
import assert from 'node:assert/strict';
import {groupList,createGroup,changeGroup,workerPolicy,saveWorkerPolicy,capacityLimit} from '../js/admin/worker-group-api.js';
test('group membership, capacity and tournament routing use revision, reason and session CSRF',async()=>{
 const original=globalThis.fetch,calls=[];globalThis.fetch=async(url,options)=>{calls.push({url,...options});return{ok:true,status:200,json:async()=>url.includes('csrf')?{headerName:'X-CSRF-TOKEN',token:'csrf'}:{items:[]}};};
 try{await groupList();await createGroup('LAB_A','Official lab');await changeGroup('g/id',4,null,'worker',true,'Allowed');await workerPolicy('cup/id');const policy={revision:2,mode:'REMOTE_REQUIRED',groupId:'group',minimumTrust:'TRUSTED',maxMatches:8,reason:'Official'};await saveWorkerPolicy('cup/id',policy);await capacityLimit('worker/id',3,2,'Resource budget');const requests=calls.filter(r=>!r.url.includes('csrf'));
 assert.equal(requests[2].url,'/api/admin/worker-groups/g%2Fid');assert.deepEqual(JSON.parse(requests[2].body),{revision:4,enabled:null,workerId:'worker',member:true,reason:'Allowed'});assert.equal(requests[3].url,'/api/admin/tournaments/cup%2Fid/worker-policy');assert.deepEqual(JSON.parse(requests[4].body),policy);assert.equal(requests[5].url,'/api/admin/workers/worker%2Fid/capacity');assert.ok(requests.every(r=>r.credentials==='same-origin'&&!r.headers.Authorization));assert.ok(requests.filter(r=>r.method==='POST').every(r=>r.headers['X-CSRF-TOKEN']==='csrf'));
 }finally{globalThis.fetch=original;}
});
