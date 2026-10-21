import test from 'node:test';
import assert from 'node:assert/strict';
import {tournamentNextAction} from '../js/tournament/next-action.js';
const tournament={id:'cup/a',status:'REGISTRATION',timeline:{}},account={id:'user'};
const resolve=flow=>tournamentNextAction({tournament,account,flow});
test('global coding and check-in phases never manufacture personal permission',()=>{
 for(const phase of ['CODING_OPEN','CHECK_IN_OPEN','READY_TO_START']){const action=resolve({phase});assert.equal(action.enabled,false);assert.equal(action.href,null);}
 for(const [nextAction,extra]of [['OPEN_CODE_WORKSPACE',{canAccessWorkspace:true,canModifyWorkspace:false}],['CHECK_IN',{checkIn:{canCheckIn:false}}]])assert.equal(resolve({phase:'CODING_OPEN',nextAction,...extra}).enabled,false);
 assert.equal(tournamentNextAction({tournament,flow:{nextAction:'CHECK_IN',checkIn:{canCheckIn:true}}}).type,'LOGIN');
});
test('server decisions map one primary action for registration coding freeze validation and check-in',()=>{
 const cases=[['REGISTER','REGISTER'],['WAIT_FOR_APPROVAL','WAIT_APPROVAL'],['WAIT_FOR_CODING','WAIT_CODING'],['VIEW_FROZEN_SUBMISSION','VIEW_FROZEN_SUBMISSION'],['VIEW_FINAL_VALIDATION','WAIT_VALIDATION'],['OPEN_WAITING_ROOM','WAIT_START'],['CHECK_IN_MISSED','CHECK_IN_MISSED'],['WAITLISTED','WAITLISTED']];
 for(const [nextAction,type]of cases)assert.equal(resolve({phase:'REGISTRATION_OPEN',nextAction}).type,type);
 assert.equal(resolve({phase:'CODING_OPEN',nextAction:'OPEN_CODE_WORKSPACE',canAccessWorkspace:true,canModifyWorkspace:true}).href,'/tournament-code.html?id=cup%2Fa');
 const check=resolve({nextAction:'CHECK_IN',checkIn:{canCheckIn:true,registrationId:'r'}});assert.equal(check.command,'CHECK_IN');assert.deepEqual(check.payload,{registrationId:'r'});
 assert.equal(resolve({nextAction:'VIEW_FINAL_VALIDATION',finalValidation:{status:'FAILED'}}).type,'VIEW_VALIDATION_FAILURE');
 assert.match(resolve({nextAction:'VIEW_FINAL_VALIDATION',finalValidation:{status:'INFRA_ERROR'}}).description,/hạ tầng/);
});
test('running action requires an assigned local room and suppresses stale terminal participant actions',()=>{
 const base={tournament:{...tournament,status:'RUNNING'},account},item={status:'PLAYING',registration:{id:'r',controllerType:'HUMAN'},currentMatch:{id:'m',onlineMatchId:'room'}};
 assert.equal(tournamentNextAction({...base,me:{states:[item]}}).command,'ENTER_MATCH');
 assert.equal(tournamentNextAction({...base,me:{states:[{...item,currentMatch:{id:'m'}}]}}).type,'WAIT_NEXT_MATCH');
 assert.equal(tournamentNextAction({...base,tournament:{...base.tournament,matches:[{id:'m',status:'RUNNING',executionState:'RUNNING_LOCAL',remote:false,onlineMatchId:'room'}]},me:{states:[{...item,registration:{controllerType:'BOT'}}]}}).type,'WATCH_MATCH');
 for(const status of ['WITHDRAWN','DISQUALIFIED','REJECTED','MISSED_CHECK_IN','ELIMINATED'])assert.equal(tournamentNextAction({...base,me:{states:[{...item,status}]}}).command,undefined);
 assert.equal(tournamentNextAction({...base,me:{states:[{...item,status:'ELIMINATED'}]}}).href,'/tournament-lobby.html?id=cup%2Fa#bracket');
 assert.equal(tournamentNextAction({...base,tournament:{...tournament,status:'FINISHED'},flow:{nextAction:'CHECK_IN'}}).type,'VIEW_RESULT');
 assert.equal(tournamentNextAction({...base,tournament:{...tournament,status:'CANCELLED'},flow:{nextAction:'CHECK_IN'}}).type,'TOURNAMENT_CANCELLED');
});
test('legacy readiness uses the actual human window and selects it ahead of passive bot registrations',()=>{
 const bot={status:'WAITING_MATCH',registration:{controllerType:'BOT'}},human={status:'READY_CHECK',registration:{controllerType:'HUMAN'},nextMatch:{id:'m',readyWindowId:'w'}};
 const action=tournamentNextAction({tournament:{...tournament,status:'RUNNING'},account,me:{states:[bot,human]}});
 assert.equal(action.type,'READY_CHECK');assert.deepEqual(action.payload,{matchId:'m',windowId:'w'});
 assert.equal(tournamentNextAction({tournament:{...tournament,timeline:null},account,me:{states:[]}}).type,'REGISTER');
});

const playing={...tournament,status:'RUNNING'},bot={participantId:'bot-entry',status:'PLAYING',registration:{controllerType:'BOT'},currentMatch:{id:'bot-game',onlineMatchId:'old-room'}},remote={id:'bot-game',status:'PENDING',remote:true,executionState:'RUNNING_REMOTE',participantA:{participantId:'bot-entry'},participantB:{participantId:'opponent'}};
test('bot primary action uses public remote projection when personal monitor fails',()=>{
 for(const state of ['QUEUED','ASSIGNED','STARTING','RUNNING_REMOTE','RESULT_PENDING','RETRYING','RECOVERING']){
  const action=tournamentNextAction({tournament:{...playing,matches:[{...remote,executionState:state}]},account,me:{states:[bot],matchMonitorError:true}});
  assert.equal(action.type,'TRACK_MATCH');assert.ok(action.href.endsWith('#my-match'));assert.equal(action.command,undefined);assert.ok(!action.href.includes('old-room'));
 }
});
test('missing or terminal execution data cannot turn a stale bot assignment into a live link',()=>{
 for(const matches of [[],...['FINISHED','ERROR','CANCELLED','UNKNOWN'].map(executionState=>[{...remote,executionState}])]){
  const action=tournamentNextAction({tournament:{...playing,matches},account,me:{states:[bot],matchMonitorError:true}});assert.equal(action.type,'TRACK_MATCH');assert.match(action.description,/đồng bộ/);assert.ok(!action.href.includes('spectate='));
 }
});
test('local bot viewing uses the projected room rather than a stale participant room',()=>{
 const local={...remote,status:'RUNNING',executionState:'RUNNING_LOCAL',remote:false,onlineMatchId:'new/room'};
 const action=tournamentNextAction({tournament:{...playing,matches:[local]},account,me:{states:[bot]}});assert.equal(action.type,'WATCH_MATCH');assert.equal(action.href,'/game.html?spectate=new%2Froom&tournament=cup%2Fa');
 const pending=tournamentNextAction({tournament:{...playing,matches:[local]},account,me:{states:[bot],matchMonitor:{items:[{...remote,execution:{attempt:1},executionState:'RESULT_PENDING'}]}}});assert.equal(pending.type,'TRACK_MATCH');assert.match(pending.description,/xác nhận kết quả/);
});
test('a playable human seat takes priority over another owned remote bot',()=>{
 const human={participantId:'human',status:'PLAYING',registration:{controllerType:'HUMAN'},currentMatch:{id:'human-game',onlineMatchId:'human-room'}};
 const action=tournamentNextAction({tournament:{...playing,matches:[remote]},account,me:{states:[bot,human],matchMonitor:{items:[{...remote,execution:{attempt:1}}]}}});assert.equal(action.command,'ENTER_MATCH');assert.deepEqual(action.payload,{matchId:'human-game'});
});
test('human ready check takes priority over a bot execution but pauses still suppress readiness',()=>{
 const human={participantId:'human',status:'READY_CHECK',registration:{controllerType:'HUMAN'},nextMatch:{id:'ready-game',readyWindowId:'window'}};
 const context={tournament:{...playing,matches:[remote]},account,me:{states:[bot,human],matchMonitor:{items:[{...remote,execution:{attempt:1}}]}}};assert.equal(tournamentNextAction(context).command,'READY_CHECK');
 assert.equal(tournamentNextAction({...context,tournament:{...context.tournament,status:'PAUSED'}}).type,'WAIT_RESUME');
});
test('queued bot actions stay scoped to the selected participant and prefer a real queued assignment',()=>{
 const waiting={...bot,status:'WAITING_MATCH',currentMatch:null,nextMatch:{id:'bot-game'}},passive={participantId:'passive',status:'WAITING_MATCH',registration:{controllerType:'BOT'}};
 const action=tournamentNextAction({tournament:{...playing,matches:[{...remote,executionState:'QUEUED'}]},account,me:{states:[passive,waiting]}});assert.equal(action.type,'TRACK_MATCH');assert.match(action.description,/chờ máy/);
 const unrelated=tournamentNextAction({tournament:{...playing,matches:[remote]},account,me:{states:[passive]}});assert.equal(unrelated.type,'WAIT_NEXT_MATCH');
});
