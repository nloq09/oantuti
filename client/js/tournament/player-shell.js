import {resolvePlayerTournamentViewState} from './player-view-state.js';
import {node,link,date} from './player-dom.js';
import {countdownText} from './tournament-header.js';
import {renderTournamentHeader} from './tournament-header.js';
import {renderProgressStepper} from './progress-stepper.js';
import {tournamentPhase} from './tournament-header.js';
import {tournamentNextAction} from './next-action.js';
import {errorText} from '../ui/toast.js';
import {playerSurface} from './player-surface.js';

export function renderPlayerNextActionCard(root,action,commands={}){
 const card=playerSurface(root,'player-next-action');card.dataset.nextAction=action.type;
 card.append(node('h2',['WAIT_CHECK_IN','WAIT_VALIDATION','VIEW_VALIDATION_FAILURE','WAIT_INFRA_RETRY'].includes(action.type)?'Bước tiếp theo':'Việc của bạn'),node('h3',action.title||action.label));
 if(action.opensAt){card.append(node('p','Check-in mở lúc: '+date(action.opensAt)));const timer=node('p',countdownText(action.opensAt,action.serverNow));timer.dataset.phaseDeadline=action.opensAt;timer.dataset.serverNow=action.serverNow||'';timer.dataset.receivedAt=String(performance.now());timer.setAttribute('role','timer');card.append(timer);}
 if(action.type==='WAIT_CHECK_IN'&&!action.opensAt)card.append(node('p','Giờ mở check-in: Chưa công bố.'));
 card.append(node('p',action.description));
 if(action.deadline){const timer=node('p');timer.dataset[action.command==='READY_CHECK'?'readyDeadline':'deadline']=action.deadline;card.append(timer);}
 if(action.enabled&&action.href){const cta=link(action.label,action.href);cta.className='button primary';cta.id='player-primary-action-'+action.type;card.append(cta);}
 else if(action.enabled&&commands[action.command]){
  const button=node('button',action.label,'primary'),notice=node('p');button.id='player-primary-action-'+action.type;notice.setAttribute('role','status');
  button.onclick=async()=>{if(button.disabled)return;button.disabled=true;card.dataset.submitting='true';try{await commands[action.command](action.payload);}catch(error){notice.textContent=errorText(error.message);}finally{delete card.dataset.submitting;button.disabled=false;}};
  card.append(button,notice);
 }else if(action.reasonDisabled&&action.reasonDisabled!==action.description)card.append(node('small',action.reasonDisabled,'next-action-reason'));
 root.append(card);return card;
}
export function renderTournamentShell(root,{tournament,flow}){
 const view=resolvePlayerTournamentViewState({tournament,flow});renderTournamentHeader(root,tournament,flow,{audience:'player',phase:tournament.timeline?view.phase:tournamentPhase(tournament,flow),phaseLabel:tournament.timeline?view.phaseLabel:null});
 if(view.compact||tournament.status==='FINISHED')return;
 const optionalSteps=tournament.timeline?[]:[1,2,...(tournament.checkInRequired?[]:[3])];
 renderProgressStepper(root,tournament.timeline?view.phase:tournamentPhase(tournament,flow),{optionalSteps});
}
export function renderPlayerShell(root,{tournament,flow,me,account,commands}){
 renderTournamentShell(root,{tournament,flow});
 const action=tournamentNextAction({tournament,flow,me,account});
 if(tournament.status!=='FINISHED'&&!(flow?.phase==='VALIDATING'&&(flow.finalValidation||flow.validationDetailState)))renderPlayerNextActionCard(root,action,commands);return action;
}
