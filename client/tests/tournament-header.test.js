import test from 'node:test';
import assert from 'node:assert/strict';
import {headerModel,renderTournamentHeader,countdownText,updateHeaderTimers} from '../js/tournament/tournament-header.js';
import {setup,descendants} from './helpers/tournament-dom.js';
const tournament={name:'Cup <script>',format:'SINGLE_ELIMINATION',status:'REGISTRATION',participants:[{}],maxParticipants:4000};
test('header retains identity roster count lifecycle and authoritative phase across the timeline',()=>{
 for(const phase of ['UPCOMING','REGISTRATION_OPEN','WAITING_FOR_CODING','CODING_OPEN','SUBMISSION_FROZEN','VALIDATING','CHECK_IN_OPEN','READY_TO_START','RUNNING','FINISHED']){
  const root=setup();renderTournamentHeader(root,tournament,{phase});const nodes=descendants(root);
  assert.equal(nodes.find(n=>n.tag==='h1').textContent,tournament.name);
  assert.ok(nodes.some(n=>n.textContent==='1 / 4000 suất thi đấu'));
  assert.equal(nodes.find(n=>n.dataset.phase).dataset.phase,phase);
  assert.equal(nodes.some(n=>n.tag==='button'),false);
 }
 assert.equal(headerModel(tournament,null).phase,'REGISTRATION_OPEN');
});
test('header timer uses server time plus elapsed monotonic time and never announces an automatic transition',()=>{
 const serverNow='2026-10-20T01:00:00Z',deadline='2026-10-20T02:00:00Z';
 assert.equal(countdownText(deadline,serverNow,30000),'Còn 00:59:30');
 assert.match(countdownText(deadline,serverNow,3600001),/Chờ server/);
 assert.match(countdownText(deadline,null),/server/);
 const root=setup();renderTournamentHeader(root,tournament,{phase:'CODING_OPEN',serverNow,phaseEndsAt:deadline});
 const timer=descendants(root).find(n=>n.attributes.role==='timer');root.querySelectorAll=()=>[timer];updateHeaderTimers(root,Number(timer.dataset.receivedAt)+1000);
 assert.equal(timer.textContent,'Còn 00:59:59');
});
