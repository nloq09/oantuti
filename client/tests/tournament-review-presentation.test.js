import test from 'node:test';
import assert from 'node:assert/strict';
import {resolveTournamentNavigation,resolveTournamentViewState,supportsBracket} from '../js/tournament/navigation.js';
import {mapSubmissionStatus,mapRegistrationStatus,ruleSummary,friendlyName} from '../js/tournament/presentation.js';
import {seriesViewModels,renderSeriesCards} from '../js/tournament/series-view.js';
import {resolvePlayerTournamentViewState} from '../js/tournament/player-view-state.js';
import {playerValidationModel} from '../js/tournament/final-validation-view.js';
const navigation=(format,phase,extra={})=>resolveTournamentNavigation({tournamentFormat:format,tournamentPhase:phase,participantState:{},matchAvailability:{hasMatches:true,hasReplays:true,hasBracket:true},...extra}).filter(t=>t.visible);
test('A: running round robin has standings and never a bracket, even if stale bracket data exists',()=>{const tabs=navigation('ROUND_ROBIN','RUNNING');assert.ok(tabs.some(t=>t.id==='ranking'));assert.ok(!tabs.some(t=>t.id==='bracket'));assert.equal(supportsBracket('SWISS'),false);});
test('organizer keeps setup and audit access without future matches, results or replay',()=>{
 const tabs=navigation('ROUND_ROBIN','REGISTRATION_OPEN',{viewerRole:'ORGANIZER',timelineEnabled:true,matchAvailability:{}});
 assert.ok(tabs.some(t=>t.id==='registration'));assert.ok(tabs.some(t=>t.id==='settings'));assert.ok(tabs.some(t=>t.id==='audit'));
 assert.equal(tabs.find(t=>t.id==='bracket').label,'Hạt giống');
 assert.ok(!tabs.some(t=>['matches','results','replays','validation','check-in'].includes(t.id)));
});
test('finished organizer hides obsolete seed setup for round robin while preserving historical submissions',()=>{
 const tabs=navigation('ROUND_ROBIN','FINISHED',{viewerRole:'ORGANIZER',timelineEnabled:true});
 assert.ok(tabs.some(t=>t.id==='submissions'));assert.ok(tabs.some(t=>t.id==='validation'));assert.ok(tabs.some(t=>t.id==='results'));
 assert.ok(!tabs.some(t=>t.id==='bracket'));
});
test('B: registration exposes enrollment/rules, not future competition or replay',()=>{const tabs=navigation('SINGLE_ELIMINATION','REGISTRATION_OPEN');assert.deepEqual(tabs.map(t=>t.id),['overview','registration','rules']);});
test('C: approved coding player defaults to coding, never validation; explicit overview remains selectable',()=>{const tabs=navigation('ROUND_ROBIN','CODING_OPEN',{activeTab:''});assert.equal(tabs.find(t=>t.active).id,'coding');assert.equal(tabs.some(t=>t.id==='validation'),false);assert.equal(navigation('ROUND_ROBIN','CODING_OPEN',{activeTab:'overview'}).find(t=>t.active).id,'overview');});
test('D: missing submission after deadline cannot claim to have a locked artifact',()=>{const v=resolvePlayerTournamentViewState({tournament:{id:'cup'},flow:{phase:'SUBMISSION_FROZEN',submissionStatus:'SUBMISSION_MISSING',frozenSubmission:{decision:'MISSING'}}});assert.equal(v.hasLockedSubmission,false);assert.equal(v.primaryAction.label,'Không có bài nộp hợp lệ');});
test('E: passed official validation has eight passed steps and no pending placeholders',()=>{const keys=['BUILD','PROTOCOL','TIMEOUT','RESOURCE','LEGAL_ACTION','HIDDEN_TESTS','TOURNAMENT_TESTS','ARTIFACT_HASH'];const view=playerValidationModel({status:'PASSED',applied:true,language:'PYTHON',version:2,steps:Object.fromEntries(keys.map(k=>[k,'PASSED']))});assert.equal(view.passed,8);assert.equal(view.total,8);assert.equal(view.steps.length,8);assert.ok(view.steps.every(s=>s.status==='PASSED'));});
test('F: navigation resolves an obsolete Back hash against the current authoritative phase',()=>{const t={id:'cup',format:'ROUND_ROBIN',status:'FINISHED',progress:{total:1,finished:1},matches:[],bracket:[]};const v=resolveTournamentViewState(t,{phase:'FINISHED'},null,'coding');assert.equal(v.activeTab,'overview');assert.ok(!v.navigation.some(n=>n.id==='coding'&&n.visible));});
test('G: BO5 3–1 produces one server-scored series with four ordered game rows',()=>{const a={participantId:'a',displayName:'An'},b={participantId:'b',displayName:'Bình'};const games=[1,2,3,4].map(i=>({id:'g'+i,seriesId:'s',round:1,gameNumber:i,participantA:a,participantB:b,status:'FINISHED',winnerParticipantId:i===2?'b':'a'}));const view=seriesViewModels(games.slice().reverse(),[{id:'s',winsA:3,winsB:1,status:'FINISHED',winnerParticipantId:'a'}],5);assert.equal(view.length,1);assert.deepEqual([view[0].scoreA,view[0].scoreB],[3,1]);assert.deepEqual(view[0].games.map(g=>g.gameNumber),[1,2,3,4]);});
test('partial page does not invent a BO5 score or final series status',()=>{const view=seriesViewModels([{id:'g',seriesId:'s',status:'FINISHED',participantA:{participantId:'a'},participantB:{participantId:'b'},winnerParticipantId:'a'}],[],5)[0];assert.equal(view.scoreA,null);assert.notEqual(view.status,'FINISHED');});
test('H: finished tournament is compact with result-first navigation and no coding/check-in tabs',()=>{const v=resolveTournamentViewState({id:'cup',format:'SWISS',status:'FINISHED',progress:{total:3,finished:3}},null,null);assert.equal(v.compact,true);assert.equal(v.navigation.find(t=>t.active).label,'Kết quả');assert.ok(!v.navigation.some(t=>['bracket','coding','check-in'].includes(t.id)&&t.visible));});
test('player mappers never echo unknown enums, hashes or internal identities',()=>{assert.equal(mapSubmissionStatus('SUBMITTED'),'Đã nộp bài');assert.equal(mapRegistrationStatus('APPROVED'),'Đã được duyệt');assert.equal(mapSubmissionStatus('NEW_INTERNAL_500'),'Chưa nộp bài');assert.equal(friendlyName('ui_owner_a123','Ban tổ chức'),'Ban tổ chức');const rows=ruleSummary({bestOf:3,rulesSnapshot:{maxTurns:200,turnSeconds:30,artifactHash:'secret',unknownKey:'hidden'},tieBreakOrder:['POINTS','WINS']});assert.match(JSON.stringify(rows),/Số lượt tối đa/);assert.doesNotMatch(JSON.stringify(rows),/maxTurns|turnSeconds|artifactHash|secret|unknownKey|POINTS/);});

import {setup,descendants} from './helpers/tournament-dom.js';
import {organizerNavigation} from '../js/tournament/organizer-shell-model.js';
import {tournamentNextAction} from '../js/tournament/next-action.js';
test('tracking action selects the personal matches tab using current navigation',()=>{
 const t={id:'cup',format:'ROUND_ROBIN',status:'RUNNING',progress:{total:1}};
 const me={states:[{participantId:'a',status:'PLAYING',registration:{controllerType:'BOT'},currentMatch:{id:'g'}}],matchMonitor:{items:[{id:'g',participantA:{participantId:'a'},executionState:'QUEUED'}]}};
 const action=tournamentNextAction({tournament:t,me,account:{id:'u'}});
 assert.equal(resolveTournamentViewState(t,null,me,action.href.split('#')[1]).activeTab,'my-matches');
});
test('player and organizer replay tabs use actual replay count across summary pages',()=>{
 const t={id:'cup',format:'ROUND_ROBIN',status:'FINISHED',progress:{total:1,finished:1},matches:[],replayCount:0};
 const player=cup=>resolveTournamentViewState(cup,null,null).navigation.some(n=>n.id==='replays'&&n.visible);
 const owner=cup=>organizerNavigation(cup,null).some(n=>n.key==='replays');
 for(const visible of [player,owner]){assert.equal(visible(t),false);assert.equal(visible({...t,replayCount:1}),true);assert.equal(visible({...t,replayCount:undefined,matches:[{historyMatchId:'history'}]}),true);}
});
test('series review takes precedence over recorded winner and marks score provisional',()=>{
 const root=setup(),game={id:'g',seriesId:'s',round:1,gameNumber:1,status:'FINISHED',executionState:'FINISHED',reviewRequired:true,winnerParticipantId:'a',participantA:{participantId:'a',displayName:'An'},participantB:{participantId:'b',displayName:'Bình'}};
 renderSeriesCards(root,seriesViewModels([game],[{id:'s',status:'PENDING',winsA:1,winsB:0}],3));
 const text=descendants(root).map(n=>n.textContent).join(' ');assert.match(text,/trọng tài xem xét/);assert.match(text,/Tỉ số tạm thời/);assert.doesNotMatch(text,/Thắng: An/);
});
test('series distinguishes administrative forfeit from normal game winner',()=>{
 const render=extra=>{const root=setup();renderSeriesCards(root,seriesViewModels([{id:'g',round:1,status:'FINISHED',executionState:'FINISHED',winnerParticipantId:'a',participantA:{participantId:'a',displayName:'An'},participantB:{participantId:'b',displayName:'Bình'},...extra}],[],3));return descendants(root).map(n=>n.textContent).join(' ');};
 assert.match(render({decisionReason:'FORFEIT'}),/Xử thua theo luật/);assert.doesNotMatch(render({failure:'NO_SHOW'}),/Thắng:/);assert.match(render({}),/Thắng: An/);
});

test('locked submission CTA selects validation from overview instead of an unmounted anchor',()=>{
 const t={id:'cup',format:'ROUND_ROBIN',status:'REGISTRATION',timeline:{}};
 const flow={phase:'SUBMISSION_FROZEN',registrationStatus:'APPROVED',frozenSubmission:{versionId:'v',decision:'EXPLICIT_SUBMIT'}};
 const action=tournamentNextAction({tournament:t,flow,me:{states:[]},account:{id:'u'}});
 assert.equal(action.href,'#validation');assert.equal(resolveTournamentViewState(t,flow,null,action.href.slice(1)).activeTab,'validation');
});
