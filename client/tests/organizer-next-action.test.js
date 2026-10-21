import test from 'node:test';import assert from 'node:assert/strict';import {organizerNextAction,organizerActionAvailability,matchOperationAvailability} from '../js/tournament/organizer-actions.js';import {cup,metrics,now} from './helpers/organizer-fixtures.js';
test('organizer next action follows server phase and uses only existing routes',()=>{
 for(const [phase,section]of [['REGISTRATION_OPEN','registration'],['CODING_OPEN','submissions'],['SUBMISSION_FROZEN','validation'],['VALIDATING','validation'],['CHECK_IN_OPEN','check-in'],['RUNNING','matches'],['PAUSED','matches'],['FINISHED','results'],['CANCELLED','audit']]){const t=cup({timeline:{tournamentStartAt:'2026-11-01T12:00:00Z'},matches:[{status:'PENDING'}],status:['FINISHED','CANCELLED','RUNNING','PAUSED'].includes(phase)?phase:'REGISTRATION'}),action=organizerNextAction(t,metrics(t,{phase}));assert.equal(action.section,section);assert.ok(!action.href.includes('/admin/'));}
});
test('start cannot be promised before server deadline, without roster, under validation block or stale data',()=>{
 const t=cup({timeline:{tournamentStartAt:now},status:'REGISTRATION',registrations:[{id:'a',status:'CHECKED_IN'},{id:'b',status:'CHECKED_IN'}]}),data=metrics(t,{phase:'READY_TO_START',serverNow:'2026-11-01T09:59:59Z'});assert.equal(organizerActionAvailability(t,data).start,false);assert.equal(organizerActionAvailability(t,{...data,serverNow:now}).start,true);
 assert.equal(organizerActionAvailability({...t,registrations:[]},{...data,serverNow:now}).start,false,'unchecked roster cannot be offered a start that would cancel the tournament');
 assert.equal(organizerActionAvailability({...t,participants:[]},{...data,serverNow:now}).start,false);assert.equal(organizerActionAvailability({...t,timelineStartIssue:'VALIDATION_INFRA_BLOCKED'},{...data,serverNow:now}).start,false);
 assert.equal(organizerActionAvailability(t,{...data,revision:3}).start,false);assert.equal(organizerActionAvailability(t,{...data,serverNow:now},{stale:true}).start,false);assert.equal(organizerNextAction(t,{...data,serverNow:now}).command,'start');
});
test('manual deadlines and lifecycle minima apply before showing commands',()=>{
 const t=cup({status:'DRAFT',participants:[],timing:{registrationDeadline:now}});assert.equal(organizerActionAvailability(t,metrics(t)).openRegistration,false);
 const open={...t,timing:{registrationDeadline:'2026-11-01T11:00:00Z'}};assert.equal(organizerNextAction(open,metrics(open)).command,'open-registration');assert.equal(organizerActionAvailability(open,null).openRegistration,false);
 const team=cup({format:'TEAM_ROUND_ROBIN'});assert.equal(organizerActionAvailability(team,metrics(team)).closeRegistration,false);
 const pending=metrics(cup(),{phase:'REGISTRATION_REVIEW'});assert.match(organizerNextAction(cup(),pending).description,/2 đơn/);
});
test('match operations mirror review retry and downstream locking guards',()=>{
 const m={id:'m',round:1,status:'FINISHED',seriesId:'s',gameNumber:1},t=cup({status:'RUNNING',matches:[m],reviews:{m:'Review'}});
 assert.equal(matchOperationAvailability(t,m).override,false);assert.equal(matchOperationAvailability({...t,status:'PAUSED'},m).override,true);assert.equal(matchOperationAvailability({...t,status:'FINISHED'},m).override,true);
 assert.equal(matchOperationAvailability({...t,status:'PAUSED',bestOf:3,matches:[m,{seriesId:'s',gameNumber:2}]},m).override,false);
 assert.equal(matchOperationAvailability({...t,status:'PAUSED',format:'SWISS',matches:[m,{round:2}]},m).override,false);
 assert.equal(matchOperationAvailability({...t,status:'PAUSED'},{...m,status:'FAILED'}).retry,true);assert.equal(matchOperationAvailability(t,{...m,status:'FAILED'}).retry,false);
 assert.equal(matchOperationAvailability({...t,status:'CANCELLED'},m).keep,false);
});
