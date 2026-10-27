import {renderRules} from './player-rules.js';
import {createRegistrationDisclosure} from './registration-disclosure-core.js';
import {playerSurface} from './player-surface.js';
import {ownerTournamentRoute} from './tournament-routes.js';
import {resolveTournamentViewState} from './navigation.js';
import {statusText,languageText} from './player-labels.js';
import {createPlayerDetailSections,playerSectionTarget,renderPlayerTournamentInfo} from './player-detail-sections.js';

import {currentMatchOpen} from './match-execution.js';
import {readSnapshotKey} from './read-snapshot.js';
import {rememberTournamentFocus} from './keyboard-focus.js';
import {createTournamentRefresh} from './refresh-controller.js';

import {renderPlayerShell} from './player-shell.js';
import {tournamentNextAction} from './next-action.js';
import {updateHeaderTimers} from './tournament-header.js';
import {timelineLobbyDestination,timelineStartText} from './timeline-lobby.js';


import {renderWithdrawalActions} from "./withdrawal.js";



import{TournamentMatchEntry,currentHumanMatch}from "./match-entry.js";
import{ServerClock}from "./check-in.js";import{checkInRegistration}from "./tournament-api.js";import{errorText}from "../ui/toast.js";

import{getAccount,loginLink}from "../account/account-session.js";import{tournamentRequest,watchTournament}from "./tournament-api.js";import{TournamentClientState,playerFlowCapabilities,participantLabels}from "./player-state.js";import{node,link,date,table}from "./player-dom.js";
const matchEntry=new TournamentMatchEntry();
const serverClock=new ServerClock(),isLobby=location.pathname.endsWith('tournament-lobby.html');
const root=document.querySelector('#tournament-player'),id=new URLSearchParams(location.search).get('id'),state=new TournamentClientState();
const pageSections=createPlayerDetailSections(),tabView=pageSections;
let renderPending=false,flow,account,primaryAction,stream,registrationOpen=false,pendingHash=location.hash.slice(1);
let playerRefresh;
const refreshPlayerTournamentState=(force=false,reason='manual')=>playerRefresh.request(force,reason,{replace:force});
const load=refreshPlayerTournamentState;
const refreshAfterAction=()=>load(true);
const refreshSectionAfterAction=async key=>{pageSections.clearDirty(key);await load(true);};
const registrationView=createRegistrationDisclosure({sectionMode:true,loadRenderers:timeline=>timeline?import('./timeline-registration.js'):import('./registration-form.js'),selectTab:key=>{registrationOpen=key==='registration';render(true);},onOpen:()=>rememberSection('registration'),onClose:()=>{if(playerSectionTarget(location.hash.slice(1))==='player-registration-panel')history.replaceState(null,'',location.pathname+location.search);},onRules:()=>navigateSection('rules'),refresh:refreshAfterAction,onRegistered:async result=>{if(result)flow=result;registrationOpen=false;history.replaceState(null,'',location.pathname+location.search);render(true);await load(true);}});
function rememberSection(key){history.replaceState(null,'',location.pathname+location.search+'#'+key);}
function navigateSection(hash,remember=true){
 if(!state.tournament)return;
 const key=playerSectionTarget(hash);
 if(remember)rememberSection(hash);
 if(key==='player-registration-panel'){registrationView.open();return;}
 let target=pageSections.open(key)||document.getElementById(key);
 if(!target)target=document.getElementById('tournament-phase-content');
 if(target?.hidden)target=root.querySelector('.player-next-action')||root.querySelector('.tournament-header');
 if(!target)return;
 for(let parent=target.parentElement;parent&&parent!==root;parent=parent.parentElement)if(parent.matches('details'))parent.open=true;
 if(!target.matches('summary'))target.tabIndex=-1;
 target.scrollIntoView?.({behavior:'smooth',block:'start'});target.focus({preventScroll:true});
}
function showPlayerReadError(){
 if(root.querySelector('#player-read-error'))return;
 const notice=node('div',undefined,'player-read-error'),message=node('p','Không cập nhật được trạng thái từ server. Nội dung và dữ liệu đang nhập được giữ lại.'),retry=node('button','Thử lại');notice.id='player-read-error';message.setAttribute('role','status');retry.onclick=()=>load(true);notice.append(message,retry);root.append(notice);
}

async function readPlayer({force,isCurrent,signal,reason}){try{
 if(['resume','visible','popstate'].includes(reason)&&flow?.phase==='VALIDATING'&&flow.finalValidation){flow={...flow,validationDetailState:'LOADING'};render(true);}
const [nextAccount,t]=await Promise.all([getAccount(),tournamentRequest('/'+encodeURIComponent(id)+'/summary?light=true',undefined,undefined,{}, {signal})]);if(!isCurrent())return;
const ownerDestination=ownerTournamentRoute(t,nextAccount);if(ownerDestination){playerRefresh?.dispose();location.replace(ownerDestination);return;}account=nextAccount;
const previousMatches=readSnapshotKey(state.tournament?.matches),previousMonitor=readSnapshotKey([state.me?.matchMonitor,state.me?.matchMonitorError]);
const monitorQuery=new URLSearchParams({page:0,size:20,status:'ALL',groupBySeries:(t.bestOf||1)>1});
const [me,nextFlow,monitor]=await Promise.all([account?tournamentRequest('/'+encodeURIComponent(id)+'/me',undefined,undefined,{}, {signal}):null,t.timeline?tournamentRequest('/'+encodeURIComponent(id)+(account?'/participant-flow':'/timeline'),undefined,undefined,{}, {signal}):null,account&&['RUNNING','PAUSED','RECOVERY_REQUIRED','FINISHED','CANCELLED'].includes(t.status)?tournamentRequest('/'+encodeURIComponent(id)+'/my-matches?'+monitorQuery,undefined,undefined,{}, {signal}).catch(()=>({readError:true})):null]);
if(!isCurrent())return;if(me?.states?.length&&monitor){me.matchMonitor=monitor.revision===t.revision?monitor:null;me.matchMonitorError=monitor.revision!==t.revision;}
const previousCapabilities=playerFlowCapabilities(flow),previousPhase=flow?.phase,previousRegistration=flow?.registrationStatus,previousSubmission=flow?.submissionStatus,previousCheckIn=JSON.stringify(flow?.checkIn),previousValidation=JSON.stringify(flow?.finalValidation);
serverClock.update(nextFlow?.serverNow||me?.serverTime);const previousAdmission=state.tournament?.acceptingRegistrations,previousRevision=state.tournament?.revision;if(!state.update(t,me,nextFlow)){if(reason!=='revision-retry')refreshPlayerTournamentState(true,'revision-retry');return;}{flow=nextFlow;const destination=t.timeline&&timelineLobbyDestination(location.pathname,id,flow?.phase,location.search,location.hash);if(destination){playerRefresh.dispose();location.replace(destination);return;}if(renderPending||force||previousMatches!==readSnapshotKey(t.matches)||previousMonitor!==readSnapshotKey([me?.matchMonitor,me?.matchMonitorError])||previousCapabilities!==playerFlowCapabilities(flow)||previousRevision!==t.revision||previousAdmission!==t.acceptingRegistrations||previousPhase!==flow?.phase||previousRegistration!==flow?.registrationStatus||previousSubmission!==flow?.submissionStatus||previousCheckIn!==JSON.stringify(flow?.checkIn)||previousValidation!==JSON.stringify(flow?.finalValidation))render(force||previousPhase!==flow?.phase||previousCapabilities!==playerFlowCapabilities(flow));const current=currentHumanMatch(me?.states||[]);if(current&&!document.hidden&&!new URLSearchParams(location.search).has("stay"))matchEntry.enter(id,current.id).catch(()=>{});}}catch(error){if(!isCurrent())return;renderPending=true;if(state.tournament){if(flow?.phase==='VALIDATING'&&flow.validationDetailState==='LOADING'){flow={...flow,validationDetailState:'ERROR'};render(true);}showPlayerReadError();}else{tabView.clear();root.replaceChildren(node("h1","Không tải được giải đấu"),node("p","Không tải được trạng thái mới từ server."));const retry=node('button','Thử lại');retry.onclick=()=>refreshPlayerTournamentState(true);root.append(retry);}}}
function render(force=false){
 if(!force&&(root.querySelector('[data-submitting=true]')||root.contains(document.activeElement)&&document.activeElement.matches('input,select,textarea'))){renderPending=true;return;}
 renderPending=false;
 const t=state.tournament,view=resolveTournamentViewState(t,flow,state.me,'');
 const restoreFocus=rememberTournamentFocus(root);
 pageSections.begin({tournament:t,me:state.me,account,flow,phase:view.phase,snapshot:readSnapshotKey([t,state.me,flow?{...flow,serverNow:undefined}:null])});
 root.replaceChildren();
 primaryAction=renderPlayerShell(root,{tournament:t,flow,me:state.me,account,commands:{CHECK_IN:async payload=>{await checkInRegistration(id,payload.registrationId);await refreshAfterAction();},READY_CHECK:async payload=>{await tournamentRequest('/'+encodeURIComponent(id)+'/ready',payload);await refreshAfterAction();},ENTER_MATCH:payload=>matchEntry.enter(id,payload.matchId)}});
 registrationView.attach(root,{tournament:t,flow,account,me:state.me,tab:registrationOpen?'registration':'overview',primaryAction});
 const main=pageSections.attach(root,{id:'tournament-phase-content',details:false,identity:view.phase,render:(panel,context)=>renderPhaseContent(panel,t,view.phase,context.isCurrent)});
 main.hidden=!main.querySelector('.player-section-body').childElementCount;
 if(t.timelineStartIssue)root.append(node('p',timelineStartText(t.timelineStartIssue),'notice'));
 const actions=node('div',undefined,'actions player-secondary-actions');
 if(account?.id===t.ownerUserId)actions.append(link('Quản lý giải','/tournament.html?id='+encodeURIComponent(id)));
 if(['CHECK_IN','READY','RUNNING','PAUSED','RECOVERY_REQUIRED'].includes(t.status))actions.append(link(isLobby?'Chi tiết giải':'Vào lobby giải',(isLobby?'/tournament-detail.html?id=':'/tournament-lobby.html?id=')+encodeURIComponent(id)));
 renderWithdrawalActions(actions,t,state.me,refreshAfterAction);if(actions.childElementCount)root.append(actions);
 const omitted=new Set(['overview','registration','rules','coding','validation','check-in']);
 if(account&&state.me?.states?.length&&['RUNNING','PAUSED','RECOVERY_REQUIRED'].includes(t.status))omitted.add('my-matches');
 for(const item of view.navigation.filter(item=>item.visible&&!omitted.has(item.id)))pageSections.attach(root,{id:item.id,title:item.label,render:(panel,context)=>renderRelatedContent(panel,t,item.id,context.isCurrent)});
 pageSections.attach(root,{id:'tournament-info',title:'Thông tin giải',render:panel=>renderPlayerTournamentInfo(panel,t)});
 pageSections.attach(root,{id:'tournament-rules',title:'Luật giải',render:panel=>{renderRules(panel,t);panel.querySelector('h2')?.remove();if(!t.rulesSnapshot?.boardRows)panel.append(node('p','Bàn cờ 9 × 9.'));if(t.botPolicy?.allowedLanguages?.length)panel.append(node('p','Ngôn ngữ cho phép: '+t.botPolicy.allowedLanguages.map(languageText).join(', ')));panel.append(node('p','Kết quả và quyền thao tác do server xác nhận.'));}});
 pageSections.finish();restoreFocus();
 if(pendingHash){const hash=pendingHash;pendingHash=null;navigateSection(hash,false);}
}
function renderPhaseContent(panel,t,phase,isCurrent){
 if(t.status==='FINISHED'){panel.append(node('p','Đang tải kết quả…'));return import('./competition-view.js').then(module=>{if(isCurrent()){panel.replaceChildren();module.renderResults(panel,t,state.me);}});}
 if(['RUNNING','PAUSED','RECOVERY_REQUIRED'].includes(t.status)){
  if(account&&state.me?.states?.length){const host=playerSurface(panel,'player-match-list');host.id='my-matches';panel.append(host);return import('./match-monitor.js').then(module=>isCurrent()?module.mountMatchMonitor(host,t,{mine:true,flatCards:true,initial:state.me?.matchMonitor}):undefined);}
  const status=node('section',undefined,'panel player-phase-message');status.append(node('h2',t.status==='PAUSED'?'Giải tạm dừng':t.status==='RECOVERY_REQUIRED'?'Chờ khôi phục giải':'Thi đấu'),node('p','Theo dõi các trận đấu, bảng điểm và nhánh đấu trong các mục bên dưới.'));panel.append(status);return;
 }
 if(!t.timeline){myStatus(panel,t,{registration:false});return;}
 if(['SUBMISSION_FROZEN','VALIDATING','CHECK_IN_OPEN','READY_TO_START'].includes(phase)){
  panel.append(node('p','Đang tải trạng thái bài nộp…'));return import('./timeline-registration.js').then(module=>{if(isCurrent()){panel.replaceChildren();module.renderTimelineRegistration(panel,t,account,flow,refreshAfterAction,{singlePage:true,primaryCheckIn:primaryAction.command==='CHECK_IN',primaryWorkspace:['OPEN_CODE','EMERGENCY_RESUBMIT'].includes(primaryAction.type),primaryLogin:primaryAction.type==='LOGIN'});}});
 }
 if(phase==='CODING_OPEN'){
  if(!account||!flow?.canAccessWorkspace)return;
  const coding=node('section',undefined,'panel player-phase-message');coding.append(node('h2','Lập trình bot'),node('p',flow.canModifyWorkspace?'Mở phần lập trình từ Việc của bạn để viết hoặc chọn bot, kiểm thử và nộp bài theo luật giải.':'Bài hiện chỉ được xem. Server chưa cấp quyền chỉnh sửa.'),node('p','Hạn nộp: '+date(flow.codingCloseAt||t.timeline.codingCloseAt)));panel.append(coding);return;
 }
 if(flow?.registrationStatus==='APPROVED'&&['REGISTRATION_OPEN','REGISTRATION_REVIEW','WAITING_FOR_CODING'].includes(phase)){
  const waiting=node('section',undefined,'panel player-phase-message');waiting.append(node('h2','Chờ lập trình'),node('p','Đăng ký của bạn đã được duyệt.'),node('p','Lập trình bắt đầu: '+date(flow.codingOpenAt||t.timeline.codingOpenAt)));panel.append(waiting);
 }
}
function myStatus(root,t,options={}){const panel=node("section",undefined,"panel my-tournament-status");panel.id="player-personal-status";panel.append(node("h2","Trạng thái của tôi"));if(!account){panel.append(link("Đăng nhập để tham gia",loginLink()));}else if(!state.me?.states.length)panel.append(node("p","Chưa đăng ký giải này."));
 for(const item of state.me?.states||[]){const card=node("article");card.append(node("h3",item.registration.displayName),node("strong",participantLabels[item.status]||statusText(item.status)));const match=item.currentMatch||item.nextMatch;if(match)card.append(node("p",`Vòng ${match.round} · Ván ${match.gameNumber||1}/${match.bestOf||1} · Đối thủ: ${match.opponent}`),node("p","Lịch dự kiến: "+date(match.scheduledAt)));if(item.status==="CHECK_IN_REQUIRED"&&!(primaryAction?.command==="CHECK_IN"&&primaryAction.payload?.registrationId===item.registration.id)){const button=node("button","Check-in ngay"),status=node("p");status.setAttribute("role","status");button.onclick=async()=>{button.disabled=true;try{await checkInRegistration(t.id,item.registration.id);load(true);}catch(error){status.textContent=errorText(error.message);button.disabled=false;}};card.append(button,status);if(item.deadline){const countdown=node("p");countdown.dataset.deadline=item.deadline;card.append(countdown);}else card.append(node("p","Check-in kết thúc khi chủ giải khóa danh sách."));}if(match&&t.bestOf>1){const series=match.series||t.series?.find(s=>s.id===match.seriesId);if(series)card.append(node("p",`BO${t.bestOf} · Tỷ số ${series.participantA.displayName} ${series.winsA} – ${series.winsB} ${series.participantB.displayName}`));}if(item.team)card.append(node("p","Đội: "+item.team.name+(item.captain?" · Bạn là đội trưởng":"")));if(item.swiss){card.append(node("p",`Swiss vòng ${item.swiss.currentRound}/${item.swiss.totalRounds} · Buchholz ${item.buchholz}`),table([["Vòng",r=>r.round],["Đối thủ",r=>r.opponent],["Kết quả",r=>statusText(r.result)]],item.swissHistory||[]));}if(item.group)card.append(node("p",`Bảng ${item.group} · Hạng #${item.groupRank?.rank||"—"}${item.qualified?" · ✓ Đã đi tiếp":""}`));if(item.team){const rank=t.teamCompetition?.ranking.find(r=>r.id===item.team.id);if(rank)card.append(node("p",`Hạng đội #${rank.rank} · ${rank.points} điểm`));}if(item.rank&&t.format!=="TEAM_ROUND_ROBIN")card.append(node("p",`Hạng #${item.rank.rank} · ${item.rank.points} điểm`));card.append(node("p",tournamentNextAction({tournament:t,flow,me:{states:[item],matchMonitor:state.me?.matchMonitor},account}).description));if(primaryAction?.type!=="WATCH_MATCH"&&item.currentMatch&&item.registration.controllerType!=="HUMAN"){const open=currentMatchOpen(t,state.me,item.currentMatch.id);if(open)card.append(open);}if(item.status==="REJECTED"){card.append(node("p",item.rejectionReason||"Chủ giải chưa cung cấp lý do từ chối."));}if(item.missedReady)card.append(node("p","Đã có trận bị xử thua vì không xác nhận sẵn sàng đúng hạn.","notice"));readyCard(card,item);panel.append(card);}root.append(panel);
}
function readyCard(card,item){if(primaryAction?.payload?.matchId===(item.currentMatch||item.nextMatch)?.id)return;if(item.nextMatch?.readyWindowId&&item.registration.controllerType==="HUMAN"&&["READY_CHECK","READY_CONFIRMED"].includes(item.status)){const button=node("button",item.nextMatch.readyConfirmed?"Đã sẵn sàng":"Sẵn sàng thi đấu"),status=node("p"),countdown=node("p");countdown.dataset.readyDeadline=item.nextMatch.readyDeadline;status.setAttribute("role","status");button.disabled=item.nextMatch.readyConfirmed;button.onclick=async()=>{button.disabled=true;try{await tournamentRequest("/"+encodeURIComponent(id)+"/ready",{matchId:item.nextMatch.id,windowId:item.nextMatch.readyWindowId});load(true);}catch(error){status.textContent=errorText(error.message);button.disabled=false;}};card.append(countdown,button,status);}if(item.currentMatch&&item.registration.controllerType==="HUMAN"){const enter=node("button","Vào trận"),status=node("p");enter.onclick=()=>matchEntry.enter(id,item.currentMatch.id).catch(error=>status.textContent=errorText(error.message));card.append(enter,status);}}
function renderRelatedContent(panel,t,key,isCurrent){
 panel.append(node('p','Đang tải nội dung…'));
 return import('./player-related-content.js').then(module=>{if(!isCurrent())return;panel.replaceChildren();return module.renderRelatedContent(panel,t,key,{account,me:state.me,refresh:()=>refreshSectionAfterAction(key),isCurrent});});
}
const updateCountdowns=()=>{updateHeaderTimers(root);for(const element of root.querySelectorAll("[data-coding-starts]")){const seconds=serverClock.remaining(element.dataset.codingStarts);element.textContent=seconds===0?"Đã đến giờ lập trình · Đang cập nhật từ server":`Phần lập trình bắt đầu sau: ${Math.floor(seconds/3600)}:${String(Math.floor(seconds/60)%60).padStart(2,"0")}:${String(seconds%60).padStart(2,"0")}`;}for(const element of root.querySelectorAll("[data-tournament-starts]")){const seconds=serverClock.remaining(element.dataset.tournamentStarts);element.textContent=seconds===0?"Đã đến giờ bắt đầu · Chờ server xác nhận":`Giải bắt đầu sau: ${Math.floor(seconds/3600)}:${String(Math.floor(seconds/60)%60).padStart(2,"0")}:${String(seconds%60).padStart(2,"0")}`;}for(const element of root.querySelectorAll("[data-deadline]")){const seconds=serverClock.remaining(element.dataset.deadline);element.textContent=seconds===0?"Đã hết hạn · Đang chờ xác nhận từ server":`Check-in còn ${Math.floor(seconds/60)}:${String(seconds%60).padStart(2,"0")}`;}for(const element of root.querySelectorAll("[data-ready-deadline]")){const seconds=serverClock.remaining(element.dataset.readyDeadline);element.textContent=seconds===0?"Đã hết hạn · Chờ server xác nhận":`Xác nhận sẵn sàng trong ${seconds}s`; }};let countdownTimer;const startCountdowns=()=>{clearInterval(countdownTimer);if(!document.hidden){updateCountdowns();countdownTimer=setInterval(updateCountdowns,1000);}};startCountdowns();document.addEventListener('visibilitychange',startCountdowns);addEventListener('pagehide',event=>{clearInterval(countdownTimer);if(!event.persisted)document.removeEventListener('visibilitychange',startCountdowns);});addEventListener('pageshow',event=>{if(event.persisted)startCountdowns();});


if(!id)root.replaceChildren(node('h1','Thiếu mã giải'),link('Khám phá giải','/tournaments.html'));
else{playerRefresh=createTournamentRefresh(readPlayer,{tournamentId:id,immediate:false});await load();stream=watchTournament(id);}
addEventListener('hashchange',()=>navigateSection(location.hash.slice(1),false));
addEventListener('pagehide',event=>{if(!event.persisted)pageSections.clear();});
const restorePlayerRoute=()=>{if(new URLSearchParams(location.search).get('id')!==id){location.reload();return;}pendingHash=location.hash.slice(1);refreshPlayerTournamentState(true,'popstate');};
addEventListener('popstate',restorePlayerRoute);
addEventListener('pagehide',event=>{if(!event.persisted)removeEventListener('popstate',restorePlayerRoute);});
