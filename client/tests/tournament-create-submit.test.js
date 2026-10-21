import test from 'node:test';import assert from 'node:assert/strict';
import {createTournamentSubmission} from '../js/tournament/tournament-create-submit.js';
import {mountCreateWizard} from '../js/tournament/tournament-create-wizard.js';
import {setupPage,wizardNodes} from './helpers/tournament-page-dom.js';
const config={name:'Cup',settings:{format:'SWISS',bestOf:5,participantPolicy:'BOT_ONLY',maxParticipants:100,maxConcurrentMatches:2,checkInRequired:true,botPolicy:{submissionMode:'BOTH'}},timeline:{codingOpenAt:'2026-10-21T08:00:00Z'},bots:[]};
test('creation is only sent from final confirmation and redirects using the existing organizer route',async()=>{
 setupPage();const nodes=wizardNodes(),calls=[];let destination;const submit=createTournamentSubmission({configuration:()=>config,create:async(name,settings)=>{calls.push([name,settings]);return{id:'cup/id',revision:0};},request:async(path,body,method)=>{calls.push([path,body,method]);return{revision:1};},add:async()=>{},navigate:url=>destination=url});const wizard=mountCreateWizard({...nodes,validateStep:()=>true,onSubmit:submit});await nodes.form.onsubmit({preventDefault(){}});assert.equal(calls.length,0);while(wizard.step<5)wizard.advance();assert.equal(calls.length,0);await nodes.form.onsubmit({preventDefault(){}});assert.equal(calls.length,2);assert.deepEqual(calls[0],['Cup',config.settings]);assert.equal(calls[1][0],'/api/tournaments/cup%2Fid/timeline');assert.equal(calls[1][2],'PUT');assert.equal(destination,'/tournament.html?id=cup%2Fid');await nodes.form.onsubmit({preventDefault(){}});assert.equal(calls.length,2);
});
test('partial configuration failure exposes the created draft and blocks duplicate creation',async()=>{
 let count=0,draft,error;const submit=createTournamentSubmission({configuration:()=>config,create:async()=>{count++;return{id:'draft',revision:0};},request:async()=>{throw Error('network');},navigate:()=>assert.fail('must not redirect after partial failure'),onError:(e,d)=>{error=e;draft=d;}});await submit();await submit();assert.equal(count,1);assert.equal(error.message,'network');assert.equal(draft.id,'draft');
});
test('guest or busy submission cannot create a draft and pre-create failure can be retried',async()=>{
 let count=0;const guest=createTournamentSubmission({canCreate:false,configuration:()=>config,create:async()=>count++});await guest();assert.equal(count,0);let finish;const submit=createTournamentSubmission({configuration:()=>({...config,timeline:null}),create:()=>{count++;return new Promise(resolve=>finish=resolve);},navigate:()=>{}});const first=submit();await submit();assert.equal(count,1);finish({id:'cup'});await first;
 let failures=0;const retry=createTournamentSubmission({configuration:()=>config,create:async()=>{failures++;throw Error('offline');}});await retry();await retry();assert.equal(failures,2);
});
