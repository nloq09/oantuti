import test from 'node:test';
import assert from 'node:assert/strict';
import {registerBot,approveRegistration,rejectRegistration,withdrawRegistration,checkInRegistration,closeCheckIn,createTournament} from '../js/tournament/tournament-api.js';

test('registration, review, withdrawal and check-in requests use encoded IDs and cookie/CSRF authorization',async()=>{
 const original=globalThis.fetch,calls=[];
 try {
  globalThis.fetch=async(url,options)=>url.endsWith('/csrf')?{ok:true,json:async()=>({headerName:'X-CSRF-TOKEN',token:'csrf'})}: (calls.push({url,...options}),{ok:true,json:async()=>({status:'REGISTRATION'})});
  await registerBot('cup/id',{id:'bot',ownerUserId:'spoof'});await approveRegistration('cup/id','entry/id');await rejectRegistration('cup/id','entry/id');await withdrawRegistration('cup/id','entry/id');await checkInRegistration('cup/id','entry/id');await closeCheckIn('cup/id');await createTournament('Cup',{checkInRequired:true,maxParticipants:4});
  assert.deepEqual(calls.map(c=>c.method),['POST','POST','POST','DELETE','POST','POST','POST']);
  assert.equal(calls[0].url,'/api/tournaments/cup%2Fid/registrations');assert.deepEqual(JSON.parse(calls[0].body),{botId:'bot'});
  assert.equal(calls[1].url,'/api/tournaments/cup%2Fid/registrations/entry%2Fid/approve');
  assert.deepEqual(JSON.parse(calls[4].body),{registrationId:'entry/id'});
  assert.equal(JSON.parse(calls[6].body).checkInRequired,true);
  for(const c of calls){assert.equal(c.credentials,'same-origin');assert.equal(c.headers['X-CSRF-TOKEN'],'csrf');}
 }finally{globalThis.fetch=original;}
});
