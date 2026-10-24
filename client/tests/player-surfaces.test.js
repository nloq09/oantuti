import test from 'node:test';
import assert from 'node:assert/strict';
import {installDocument,text} from './helpers/organizer-dom.js';
import {renderTimelineRegistration} from '../js/tournament/timeline-registration.js';
import {renderSeriesCards,seriesViewModels} from '../js/tournament/series-view.js';
import {renderTeams} from '../js/tournament/team-view.js';
import {renderBracket} from '../js/tournament/bracket-renderer.js';
const cup={id:'cup',status:'CHECK_IN',format:'SINGLE_ELIMINATION',checkInRequired:true,participants:[],matches:[],botPolicy:{allowedLanguages:['PYTHON']},timeline:{checkInCloseAt:'2026-10-24T06:00:00Z',tournamentStartAt:'2026-10-24T07:00:00Z'}};
const flow={phase:'CHECK_IN_OPEN',registrationStatus:'APPROVED',nextAction:'CHECK_IN',registration:{displayName:'Player'},checkIn:{registrationId:'r',canCheckIn:true,closesAt:cup.timeline.checkInCloseAt},finalValidation:{status:'PASSED',applied:true,version:1,language:'PYTHON',steps:{BUILD:'PASS',PROTOCOL:'PASS',TIMEOUT:'PASS',RESOURCE:'PASS',LEGAL_ACTION:'PASS',HIDDEN_TESTS:'PASS',TOURNAMENT_TESTS:'PASS',ARTIFACT_HASH:'PASS'}}};
const root=html=>installDocument('<main>'+html+'</main>').querySelector('main');

test('phase renderers keep a single surface on a bare page and inside an existing frame',()=>{
 globalThis.fetch=async()=>({ok:true,json:async()=>({items:[]})});
 for(const state of [flow,{...flow,phase:'READY_TO_START',nextAction:'OPEN_WAITING_ROOM',checkIn:{...flow.checkIn,status:'CHECKED_IN',checkedInCount:2,startsAt:cup.timeline.tournamentStartAt}},{...flow,phase:'SUBMISSION_FROZEN',nextAction:'VIEW_FROZEN_SUBMISSION',frozenSubmission:{decision:'MISSING'}},{...flow,phase:'VALIDATING',nextAction:'WAIT_FINAL_VALIDATION'}]){
  for(const framed of [false,true]){
   const page=root(framed?'<section class="panel"><div id="host"></div></section>':'<div id="host"></div>');
   renderTimelineRegistration(page.querySelector('#host'),cup,{id:'u'},state,()=>{},{singlePage:true,primaryCheckIn:true});
   assert.equal(page.querySelector('.panel .panel'),null,state.phase);
   assert.ok(page.querySelector('.panel'));assert.match(text(page),/Player|bài nộp|kiểm thử/i);
   if(state.nextAction==='OPEN_WAITING_ROOM'){
    assert.equal(page.querySelectorAll('details').length,1,'rules remain in the single page rules section');
    assert.match(text(page),/Thông báo/);assert.match(text(page),/Lịch thi đấu/);assert.match(text(page),/Chưa ghép trận/);
   }
  }
 }
});

test('player series rows preserve scores and game details while organizer cards keep their existing frame',()=>{
 const games=[{id:'m',round:1,bestOf:3,gameNumber:1,status:'FINISHED',participantA:{participantId:'a',displayName:'An'},participantB:{participantId:'b',displayName:'Bình'},winnerParticipantId:'a'}];
 const models=seriesViewModels(games,[{id:'m',status:'IN_PROGRESS',winsA:1,winsB:0}],3);
 for(const flatCards of [false,true]){
  const page=root('<section class="panel" id="host"></section>');renderSeriesCards(page.querySelector('#host'),models,{flatCards});
  assert.equal(Boolean(page.querySelector('.panel .panel')),!flatCards);
  assert.match(text(page),/An 1 – 0 Bình/);assert.match(text(page),/Các ván đấu \(1\)/);assert.match(text(page),/Thắng: An/);
 }
});

test('player team sections use unframed rows without changing organizer rendering or roster content',()=>{
 const tournament={...cup,format:'TEAM_ROUND_ROBIN',participants:[{participantId:'p',displayName:'Player'}],teamCompetition:{teams:[{id:'team',name:'UET',captain:'u',roster:['p']}],fixtures:[],ranking:[]}};
 for(const flatCards of [false,true]){
  const page=root('<section class="panel" id="host"></section>');renderTeams(page.querySelector('#host'),tournament,null,null,()=>{},{flatCards});
  assert.equal(Boolean(page.querySelector('.panel .panel')),!flatCards);assert.match(text(page),/UET/);assert.match(text(page),/Player/);
 }
});

test('player bracket keeps rounds, winners and focus targets without nested layout panels',()=>{
 const tournament={...cup,participants:[{participantId:'p',displayName:'Player'},{participantId:'q',displayName:'Opponent'}],bracket:[{round:1,position:1,matchId:'m',slotA:'p',slotB:'q',winnerParticipantId:'p',resolution:'WIN'}]};
 for(const flatCards of [false,true]){
  const page=root('<section class="player-detail-section" id="host"></section>');renderBracket(page.querySelector('#host'),tournament,{flatCards});
  assert.equal(Boolean(page.querySelector('.panel')),!flatCards);assert.match(text(page),/Chung kết/);assert.match(text(page),/Player/);assert.match(text(page),/Đã có kết quả/);assert.ok(page.querySelector('[data-match-id=m]'));assert.ok(page.querySelector('.bracket-winner'));
 }
});
