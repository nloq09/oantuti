import test from 'node:test';import assert from 'node:assert/strict';
import {tournamentPhase} from '../js/tournament/tournament-header.js';
import {tournamentNextAction} from '../js/tournament/next-action.js';
import {registrationForm} from '../js/tournament/registration-form.js';
import {setup,descendants} from './helpers/tournament-dom.js';
test('manual deadline displays review while preserving authoritative timeline phase',()=>{
 assert.equal(tournamentPhase({status:'REGISTRATION',acceptingRegistrations:false}),'REGISTRATION_REVIEW');
 assert.equal(tournamentPhase({status:'REGISTRATION',acceptingRegistrations:true}),'REGISTRATION_OPEN');
 assert.equal(tournamentPhase({status:'REGISTRATION',acceptingRegistrations:false,timeline:{}},{phase:'CODING_OPEN'}),'CODING_OPEN');
});
test('expired manual admission suppresses player signup while pending applications still wait for review',async()=>{
 const tournament={id:'cup',status:'REGISTRATION',acceptingRegistrations:false},account={id:'u'};
 assert.equal(tournamentNextAction({tournament,account,me:{states:[]}}).type,'REGISTRATION_CLOSED');
 assert.equal(tournamentNextAction({tournament,account,me:{states:[{status:'PENDING_APPROVAL',registration:{id:'r'}}]}}).type,'WAIT_APPROVAL');
 const root=setup();await registrationForm(root,tournament,{states:[]},()=>{});
 assert.equal(descendants(root).some(n=>n.tag==='form'),false);assert.match(descendants(root).map(n=>n.textContent).join(' '),/hết hạn nhận đơn/);
});
