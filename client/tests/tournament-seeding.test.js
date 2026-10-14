import test from 'node:test';
import assert from 'node:assert/strict';
import {configureSeeding} from '../js/tournament/tournament-api.js';
test('seeding preserves 64-bit seed text and manual mapping with session and CSRF',async()=>{
 const original=globalThis.fetch,calls=[];
 try {globalThis.fetch=async(url,options)=>url.endsWith('/csrf')?{ok:true,json:async()=>({headerName:'X-CSRF-TOKEN',token:'csrf'})}:(calls.push({url,...options}),{ok:true,json:async()=>({})});
  await configureSeeding('cup/id',{strategy:'MANUAL',seed:'9223372036854775807',manualSeeds:{alpha:2,beta:1}});
  assert.equal(calls[0].url,'/api/tournaments/cup%2Fid/seeding');assert.equal(calls[0].method,'POST');assert.equal(calls[0].credentials,'same-origin');assert.equal(calls[0].headers['X-CSRF-TOKEN'],'csrf');assert.equal(JSON.parse(calls[0].body).seed,'9223372036854775807');assert.deepEqual(JSON.parse(calls[0].body).manualSeeds,{alpha:2,beta:1});
 }finally{globalThis.fetch=original;}
});
