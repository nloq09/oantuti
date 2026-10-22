import {resolvePlayerTournamentViewState} from './player-view-state.js';
import {node,link} from './player-dom.js';
import {renderTournamentHeader} from './tournament-header.js';
import {renderProgressStepper} from './progress-stepper.js';
import {tournamentPhase} from './tournament-header.js';
import {tournamentNextAction} from './next-action.js';
import {errorText} from '../ui/toast.js';

export function renderPlayerNextActionCard(root,action,commands={}){
 const card=node('section',undefined,'panel player-next-action');card.dataset.nextAction=action.type;
 card.append(node('h2','Việc của bạn'),node('h3',action.label),node('p',action.description));
 if(action.deadline){const timer=node('p');timer.dataset[action.command==='READY_CHECK'?'readyDeadline':'deadline']=action.deadline;card.append(timer);}
 if(action.enabled&&action.href){const cta=link(action.label,action.href);cta.className='button primary';cta.id='player-primary-action-'+action.type;card.append(cta);}
 else if(action.enabled&&commands[action.command]){
  const button=node('button',action.label,'primary'),notice=node('p');button.id='player-primary-action-'+action.type;notice.setAttribute('role','status');
  button.onclick=async()=>{if(button.disabled)return;button.disabled=true;card.dataset.submitting='true';try{await commands[action.command](action.payload);}catch(error){notice.textContent=errorText(error.message);}finally{delete card.dataset.submitting;button.disabled=false;}};
  card.append(button,notice);
 }else if(action.reasonDisabled)card.append(node('small',action.reasonDisabled,'next-action-reason'));
 root.append(card);return card;
}
export function renderTournamentShell(root,{tournament,flow}){
 const view=resolvePlayerTournamentViewState({tournament,flow});renderTournamentHeader(root,tournament,flow,{audience:'player',phase:tournament.timeline?view.phase:tournamentPhase(tournament,flow),phaseLabel:tournament.timeline?view.phaseLabel:null});
 const optionalSteps=tournament.timeline?[]:[1,2,...(tournament.checkInRequired?[]:[3])];
 renderProgressStepper(root,tournament.timeline?view.phase:tournamentPhase(tournament,flow),{optionalSteps});
}
export function renderPlayerShell(root,{tournament,flow,me,account,commands}){
 renderTournamentShell(root,{tournament,flow});
 const action=tournamentNextAction({tournament,flow,me,account});
 renderPlayerNextActionCard(root,action,commands);return action;
}
