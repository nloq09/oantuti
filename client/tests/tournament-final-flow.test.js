import test from 'node:test';
import assert from 'node:assert/strict';
import {renderPlayerShell} from '../js/tournament/player-shell.js';
import {renderResults} from '../js/tournament/competition-view.js';
import {organizerFocus,organizerNavigation} from '../js/tournament/organizer-shell-model.js';
import {renderOrganizerMetrics} from '../js/tournament/organizer-metrics.js';
import {attentionItems} from '../js/tournament/organizer-attention.js';
import {rememberTournamentFocus} from '../js/tournament/keyboard-focus.js';
import {table} from '../js/tournament/player-dom.js';
import {timelineEditor} from '../js/tournament/create-timeline-editor.js';
import {setup,descendants,Element} from './helpers/tournament-dom.js';

const cup={id:'cup',name:'Final regression',status:'REGISTRATION',format:'ROUND_ROBIN',timeline:{},participants:[],maxParticipants:32,revision:4};
const text=root=>descendants(root).map(n=>n.textContent).join(' ');
function dom(){const root=setup();globalThis.Node=Element;Element.prototype.replaceChildren=function(...children){this.children=children;};Element.prototype.contains=function(n){return descendants(this).includes(n);};return root;}

test('player journey keeps one authorized next action from upcoming through checked-in waiting room',()=>{
 const steps=[
  ['UPCOMING',null,'VIEW_RULES'],['REGISTRATION_OPEN','REGISTER','REGISTER'],
  ['REGISTRATION_OPEN','WAIT_FOR_APPROVAL','WAIT_APPROVAL'],['WAITING_FOR_CODING','WAIT_FOR_CODING','WAIT_CODING'],
  ['CODING_OPEN','OPEN_CODE_WORKSPACE','OPEN_CODE'],['SUBMISSION_FROZEN','VIEW_FROZEN_SUBMISSION','VIEW_FROZEN_SUBMISSION'],
  ['VALIDATING','VIEW_FINAL_VALIDATION','WAIT_VALIDATION'],['CHECK_IN_OPEN','CHECK_IN','CHECK_IN'],
  ['READY_TO_START','OPEN_WAITING_ROOM','WAIT_START']
 ];
 for(const [phase,nextAction,type]of steps){const root=dom();const action=renderPlayerShell(root,{tournament:cup,account:{id:'u'},flow:{phase,nextAction,canAccessWorkspace:true,canModifyWorkspace:true,checkIn:{canCheckIn:true,registrationId:'r'}},commands:{CHECK_IN:async()=>{}}});assert.equal(action.type,type,phase);assert.equal(descendants(root).filter(n=>n.tag==='a'||n.tag==='button').length,action.enabled?1:0);assert.equal(root.children[2].dataset.nextAction,type);}
});

test('remote execution journey never opens a live board before replay is available; final result overrides stale coding rights',()=>{
 for(const executionState of ['QUEUED','ASSIGNED','STARTING','RUNNING_REMOTE','RESULT_PENDING','RETRYING','RECOVERING']){
  const root=dom(),action=renderPlayerShell(root,{tournament:{...cup,status:'RUNNING'},account:{id:'u'},me:{states:[{participantId:'entry',status:'PLAYING',registration:{controllerType:'BOT'},currentMatch:{id:'remote-game'}}],matchMonitor:{items:[{id:'remote-game',participantA:{participantId:'entry'},executionState,execution:{attempt:1}}]}}});assert.equal(action.type,'TRACK_MATCH');assert.equal(action.href,'/tournament-lobby.html?id=cup#my-match');assert.ok(!descendants(root).some(n=>n.href?.includes('/game.html')));
 }
 const root=dom(),action=renderPlayerShell(root,{tournament:{...cup,status:'FINISHED',timeline:{resultPublishAt:'2099-01-01T00:00:00Z'}},account:{id:'u'},flow:{nextAction:'OPEN_CODE_WORKSPACE',canAccessWorkspace:true,canModifyWorkspace:true}});assert.equal(action.type,'VIEW_RESULT');assert.equal(action.href,'/tournament-detail.html?id=cup#ranking');
});

test('organizer journey keeps each phase in the appropriate scoped dashboard and warnings link to existing review screens',()=>{
 for(const [phase,focus]of [['REGISTRATION_OPEN','registration'],['CODING_OPEN','submissions'],['VALIDATING','validation'],['CHECK_IN_OPEN','check-in'],['READY_TO_START','check-in'],['RUNNING','matches'],['RECOVERY_REQUIRED','matches'],['FINISHED','results']]){
  const root=dom(),data={tournamentId:cup.id,revision:cup.revision,phase,registration:{pending:2},coding:{available:true},validation:{infraError:1},checkIn:{missing:2},running:{error:1},workers:{offline:1}};
  assert.equal(organizerFocus(phase),focus);const nav=organizerNavigation(cup);assert.ok(nav.some(item=>item.key===focus));renderOrganizerMetrics(root,cup,data);assert.ok(text(root).length);for(const item of attentionItems(cup,data)){assert.ok(item.href.includes('id=cup'));assert.ok(!item.href.startsWith('/admin/'));}
 }
});

test('finished solo and team results are visible immediately; review state never announces an invalid winner',()=>{
 const participants=[{participantId:'a',displayName:'Winner'},{participantId:'b',displayName:'Runner'}];
 const root=dom();renderResults(root,{...cup,status:'RUNNING'},null);assert.equal(root.children.length,0);
 renderResults(root,{...cup,status:'FINISHED',timeline:{resultPublishAt:'2099-01-01T00:00:00Z'},participants,champion:participants[0],ranking:[{...participants[0],played:1},{...participants[1],played:1}]},null);assert.match(text(root),/Vô địch: Winner/);assert.match(text(root),/Á quân/);
 for(const team of [false,true]){const review=dom();renderResults(review,{...cup,status:'FINISHED',reviews:{m:{}},...(team?{teamCompetition:{}}:{})},null);assert.match(text(review),/trọng tài xem xét/);assert.doesNotMatch(text(review),/không có.*vô địch|Không có.*vô địch/);}
 const team=dom();renderResults(team,{...cup,status:'FINISHED',teamCompetition:{champion:{name:'School'}}},null);assert.match(text(team),/Đội vô địch: School/);
});

test('named scroll region remains keyboard reachable and repaint restores only the same enabled identified control',()=>{
 const root=dom(),region=table([['Name',r=>r.name]],[{name:'A'}]);root.append(region);assert.equal(region.tabIndex,0);assert.equal(region.attributes.role,'region');assert.ok(region.attributes['aria-label']);assert.equal(descendants(region).find(n=>n.tag==='th').scope,'col');
 const button=new Element('button');button.id='player-tab-ranking';root.append(button);document.activeElement=button;const restore=rememberTournamentFocus(root);let focused=0;const replacement=new Element('button');replacement.id=button.id;replacement.focus=()=>focused++;document.getElementById=()=>replacement;root.replaceChildren(replacement);restore();assert.equal(focused,1);replacement.disabled=true;restore();assert.equal(focused,1);root.replaceChildren();restore();assert.equal(focused,1);const retained=new Element('button');retained.focus=()=>focused++;root.append(retained);document.activeElement=retained;const keep=rememberTournamentFocus(root);root.replaceChildren(retained);keep();assert.equal(focused,2);
});

test('timeline form has ten labelled required dates, polite errors and no inactive publication input',()=>{
 const root=dom();Element.prototype.addEventListener=()=>{};globalThis.Option=class extends Element{constructor(text,value){super('option');this.textContent=text;this.value=value;}};
 timelineEditor(root);const nodes=descendants(root),inputs=nodes.filter(n=>n.tag==='input');assert.equal(inputs.length,10);assert.ok(inputs.every(n=>n.required));for(const input of inputs){assert.ok(nodes.some(n=>n.tag==='label'&&n.htmlFor===input.id));assert.ok(nodes.some(n=>n.id===input.attributes['aria-describedby']&&n.attributes['aria-live']==='polite'));}assert.ok(!nodes.some(n=>n.id==='timeline-resultPublishAt'));assert.match(text(root),/Không có lịch công bố riêng/);
});
