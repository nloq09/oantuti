import {attentionItems} from '../js/tournament/organizer-attention.js';
import test from 'node:test';
import assert from 'node:assert/strict';
import {readSnapshotKey} from '../js/tournament/read-snapshot.js';
import {renderPreTournamentTeams} from '../js/tournament/team-view.js';
import {tournamentNextAction} from '../js/tournament/next-action.js';
import {matchResult} from '../js/tournament/match-execution.js';
import {renderDeadlinePolicy} from '../js/tournament/deadline-policy-editor.js';
import {timelineEditor} from '../js/tournament/create-timeline-editor.js';
import {sampleTimeline,validateTimeline} from '../js/tournament/create-timeline-model.js';
import {setup,descendants,Element} from './helpers/tournament-dom.js';
function dom(){const root=setup();globalThis.Node=Element;Element.prototype.replaceChildren=function(...children){this.children=children;};Element.prototype.addEventListener=()=>{};globalThis.Option=class extends Element{constructor(text,value){super('option');this.textContent=text;this.value=value;}};return root;}
const cup={id:'cup',revision:3,status:'REGISTRATION',format:'TEAM_ROUND_ROBIN',timeline:{},participants:[{participantId:'a',displayName:'Alice'}],registrations:[],teamCompetition:{teams:[],fixtures:[],ranking:[]}},me={states:[{participantId:'a',registration:{controllerType:'BOT'}}]},account={id:'alice'};
test('timeline player can create or join teams before running instead of waiting for an impossible start',()=>{
 for(const phase of ['VALIDATING','CHECK_IN_OPEN','READY_TO_START']){const root=dom();renderPreTournamentTeams(root,cup,me,account,()=>{});assert.equal(root.children[0].id,'teams');assert.ok(descendants(root).some(n=>n.textContent==='Tạo đội'),phase);}
 const root=dom();renderPreTournamentTeams(root,{...cup,teamCompetition:{...cup.teamCompetition,teams:[{id:'other',name:'Team',captain:'bob',roster:[]}]}},me,account,()=>{});assert.ok(descendants(root).some(n=>n.textContent==='Gia nhập đội'));
 const action=tournamentNextAction({tournament:cup,flow:{nextAction:'OPEN_WAITING_ROOM'},me,account});assert.equal(action.type,'MANAGE_TEAM');assert.equal(action.href,'#teams');
 const checked=tournamentNextAction({tournament:cup,flow:{nextAction:'CHECK_IN',checkIn:{canCheckIn:true}},me,account});assert.equal(checked.command,'CHECK_IN');
 const individual=dom();renderPreTournamentTeams(individual,{...cup,format:'SWISS'},me,account,()=>{});assert.equal(individual.children.length,0);
});
test('transport time alone does not repaint while new execution state heartbeat errors or review still do',()=>{
 const a={serverNow:'one',items:[{executionState:'QUEUED',reviewRequired:false,execution:{lastHeartbeatAt:'one'}}]};assert.equal(readSnapshotKey(a),readSnapshotKey({...a,serverNow:'two'}));assert.notEqual(readSnapshotKey(a),readSnapshotKey({...a,items:[{...a.items[0],executionState:'RUNNING_REMOTE'}]}));assert.notEqual(readSnapshotKey(a),readSnapshotKey({...a,items:[{...a.items[0],reviewRequired:true}]}));assert.notEqual(readSnapshotKey([a,false]),readSnapshotKey([a,true]));assert.notEqual(readSnapshotKey(a),readSnapshotKey({...a,items:[{...a.items[0],execution:{lastHeartbeatAt:'two'}}]}));
});
test('reviewed completed results stay provisional until review closes',()=>{
 const m={executionState:'FINISHED',winnerParticipantId:'a',participantA:{participantId:'a',playerName:'Alice'},participantB:{participantId:'b',playerName:'Bob'}};assert.equal(matchResult({...m,reviewRequired:true}),'Kết quả đang được trọng tài xem xét');assert.equal(matchResult(m),'Thắng: Alice');
});
test('draft without a timeline exposes full recovery form but published legacy cups do not',()=>{
 const root=dom();renderDeadlinePolicy(root,{...cup,status:'DRAFT',timeline:null,participants:[]},()=>{});assert.equal(root.hidden,false);assert.ok(descendants(root).some(n=>n.textContent==='Tiếp tục cấu hình timeline'));assert.equal(descendants(root).filter(n=>n.type==='datetime-local').length,10);
 const closed=dom();renderDeadlinePolicy(closed,{...cup,timeline:null},()=>{});assert.equal(closed.hidden,true);const human=dom();renderDeadlinePolicy(human,{...cup,status:'DRAFT',timeline:null,participants:[],participantPolicy:'HUMAN_ONLY'},()=>{});assert.equal(human.hidden,true);
});
test('full draft timeline can be populated edited and serialized in local device time',()=>{
 const root=dom(),editor=timelineEditor(root),timeline={...validateTimeline(sampleTimeline(new Date('2026-10-20T12:00:00'))).timeline,deadlinePolicy:'REQUIRE_EXPLICIT_SUBMIT',validationFailurePolicy:'USE_LAST_VALID_VERSION'};editor.setValue(timeline);assert.deepEqual(editor.read(),timeline);
});

test('organizer sees a scoped actionable infrastructure hold instead of a generic no-show cancellation',()=>{const items=attentionItems({...cup,timelineStartIssue:'VALIDATION_INFRA_BLOCKED'},{tournamentId:cup.id,revision:cup.revision,phase:'READY_TO_START'});const warning=items.find(item=>item.key==='validation-blocked');assert.equal(warning.severity,'CRITICAL');assert.equal(warning.href,'/tournament-final-validation.html?id=cup');});

test('organizer polling preserves an unfinished draft form until server revision actually changes',()=>{const root=dom(),t={...cup,status:'DRAFT',timeline:null,participants:[]};renderDeadlinePolicy(root,t,()=>{});const field=descendants(root).find(n=>n.type==='datetime-local');field.value='2026-12-01T12:00';renderDeadlinePolicy(root,{...t},()=>{});assert.ok(descendants(root).includes(field));assert.equal(field.value,'2026-12-01T12:00');renderDeadlinePolicy(root,{...t,revision:t.revision+1},()=>{});assert.ok(!descendants(root).includes(field));});
