import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {organizerNextAction} from '../js/tournament/organizer-actions.js';
import {organizerSection} from '../js/tournament/organizer-shell-model.js';
import {resolveTournamentViewState} from '../js/tournament/navigation.js';
import {attentionItems} from '../js/tournament/organizer-attention.js';
import {renderBracket} from '../js/tournament/bracket-renderer.js';
import {renderWithdrawalActions} from '../js/tournament/withdrawal.js';
import {node,link} from '../js/tournament/player-dom.js';
import {setup,descendants,Element} from './helpers/tournament-dom.js';
const base={id:'cup',revision:3,status:'PAUSED',format:'ROUND_ROBIN',participants:[],registrations:[],matches:[{id:'g',status:'FINISHED'}],progress:{total:1,finished:1},rounds:[{number:1}]};
test('organizer can reach review decisions and finish before tournament is terminal',()=>{
 for(const reviews of [{},{g:'review'}]){const t={...base,reviews},flow={tournamentId:t.id,revision:t.revision,phase:'PAUSED'},action=organizerNextAction(t,flow);assert.equal(action.section,'results');assert.equal(organizerSection(t,action.section,flow),'results');if(reviews.g)assert.match(attentionItems(t,flow).find(i=>i.key==='review').href,/view=results/);}
 assert.equal(organizerSection({...base,status:'REGISTRATION',matches:[],progress:{total:0}},'results',{phase:'REGISTRATION_OPEN'}),'overview');
});
function dom(){const root=setup();Element.prototype.replaceChildren=function(...nodes){this.children=nodes;};Element.prototype.prepend=function(...nodes){this.children.unshift(...nodes);};return root;}
test('review of an earlier BO3 game suppresses bracket winner until resolved',()=>{
 const t={id:'cup',format:'SINGLE_ELIMINATION',participants:[{participantId:'a',displayName:'An'},{participantId:'b',displayName:'Bình'}],bracket:[{round:1,position:1,matchId:'g1',slotA:'a',slotB:'b',winnerParticipantId:'a',resolution:'WIN'}],series:[{id:'g1',bestOf:3,winsA:2,winsB:0,currentGame:2,gameIds:['g1','g2'],reviewRequired:true}],matches:[{id:'g2',seriesId:'g1',status:'FINISHED',executionState:'FINISHED',reviewRequired:false}]};
 const root=dom();renderBracket(root,t);assert.equal(descendants(root).some(n=>n.className==='bracket-winner'),false);assert.match(descendants(root).map(n=>n.textContent).join(' '),/trọng tài xem xét/);assert.doesNotMatch(descendants(root).map(n=>n.textContent).join(' '),/Đã có kết quả/);
 t.series[0].reviewRequired=false;renderBracket(root,t);assert.equal(descendants(root).some(n=>n.className==='bracket-winner'),true);
});
const source=await readFile(new URL('../js/tournament/player-detail.js',import.meta.url),'utf8');const start=source.indexOf('function render('),end=source.indexOf('\nfunction myStatus(',start);assert.ok(start>=0&&end>start);
test('actual player shell exposes withdrawal across live tabs and removes it after withdrawal',()=>{
 for(const status of ['RUNNING','PAUSED','RECOVERY_REQUIRED'])for(const tab of ['overview','my-matches']){
  const root=dom();root.querySelector=()=>null;root.contains=()=>false;
  const state={tournament:{...base,status},tab,me:{states:[{participantId:'p',status:'WAITING_MATCH',registration:{displayName:'An'}}]}};
  const deps={root,state,account:{id:'u'},flow:null,primaryAction:null,renderPending:false,resolveTournamentViewState,tabView:{reconcile(){}},rememberTournamentFocus:()=>()=>{},renderPlayerShell:()=>({}),node,link,isLobby:true,id:'cup',load:()=>{},renderWithdrawalActions,content:()=>{}};
  const render=new Function(...Object.keys(deps),source.slice(start,end)+';return render;')(...Object.values(deps));render();assert.equal(descendants(root).filter(n=>n.textContent==='Rút khỏi giải').length,1);
  state.me.states[0].status='WITHDRAWN';render();assert.equal(descendants(root).some(n=>n.textContent==='Rút khỏi giải'),false);
 }
});
