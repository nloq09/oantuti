import {createTournamentRefresh} from './refresh-controller.js';
import {renderTournamentAttentionPanel} from './organizer-attention.js';
import {node,link,date} from './player-dom.js';
import {renderTournamentHeader,updateHeaderTimers,tournamentPhase} from './tournament-header.js';
import {organizerNavigation,organizerSection,organizerFocus,organizerHealth,organizerPanels,organizerAccess,organizerGroups,organizerPhaseData} from './organizer-shell-model.js';
import {sessionRequest} from '../api/auth-api.js';
import {getAccount} from '../account/account-session.js';
import {renderOrganizerMetrics} from './organizer-metrics.js';
import {renderOrganizerOverview} from './organizer-overview.js';
import {organizerNextAction} from './organizer-actions.js';

export function renderOrganizerHeader(root,t,flow,onKeep,{stale=false}={}){
 root.replaceChildren();root.append(node('p','QUẢN LÝ GIẢI ĐẤU','organizer-kicker'));
 const header=renderTournamentHeader(root,t,flow),health=organizerHealth(t,flow,{stale}),state=node('p',health.text,'organizer-health');state.dataset.health=health.state;header.append(state);
 if(t.timeline&&!flow){header.append(node('p','Đang đồng bộ giai đoạn và hạn giờ từ server.'));}
 if(flow?.phaseEndsAt)header.append(node('p','Mốc hiện tại: '+date(flow.phaseEndsAt),'organizer-deadline'));
 const starts=t.timeline?.tournamentStartAt||t.timing?.startTime||t.startedAt;if(starts)header.append(node('p','Bắt đầu thi đấu: '+date(starts),'organizer-deadline'));
 if(flow?.registrationControl?.lockedAt)header.append(node('p',`${flow.registrationControl.lockedCount??'—'} người trong roster đã khóa`,'organizer-deadline'));
 if(flow?.registration)header.append(node('p',`${flow.registration.approved??'—'} người được duyệt · ${flow.registration.registered??'—'} đơn đăng ký`,'organizer-deadline'));
 const cleanup=[...(t.audit||[])].reverse().find(e=>['CLEANUP_CANDIDATE','CLEANUP_CLEARED','TOURNAMENT_RETAINED','TOURNAMENT_RESTORED'].includes(e.type));
 if(cleanup?.type==='CLEANUP_CANDIDATE'&&['DRAFT','CANCELLED'].includes(t.status)){
  const notice=node('p','Giải trống lâu không hoạt động đang chờ dọn. Thời điểm sớm nhất: '+date(cleanup.afterValue)+'.');notice.setAttribute('role','status');header.append(notice);
  if(onKeep){const keep=node('button','Giữ lại giải đấu');keep.onclick=async()=>{keep.disabled=true;try{await onKeep();}catch(error){notice.textContent='Chưa giữ được giải. Hãy tải lại và thử lại.';keep.disabled=false;}};header.append(keep);}
 }
 return header;
}

export function renderOrganizerSidebar(root,t,onSelect){
 root.replaceChildren();const items=organizerNavigation(t);
 for(const group of organizerGroups){const entries=items.filter(item=>item.group===group.key);if(!entries.length)continue;const section=node('section',undefined,'organizer-nav-group');section.dataset.group=group.key;section.append(node('h2',group.label));for(const item of entries){const a=link(item.label,item.href);a.dataset.section=item.key;if(onSelect&&organizerPanels[item.key]&&!(item.key==='registration'&&t.timeline))a.onclick=event=>{if(event.ctrlKey||event.metaKey||event.shiftKey||event.altKey)return;event.preventDefault();onSelect(item.key,true);};section.append(a);}root.append(section);}
}

export function mountOrganizerShell(main,{tournament,account,section='overview',selectPanel,onSnapshot,onPhase,onCommand}={}){
 if(!organizerAccess(tournament,account))return null;
 const header=node('div',undefined,'organizer-heading'),layout=node('div',undefined,'organizer-layout'),menu=node('details',undefined,'organizer-menu'),summary=node('summary','Menu quản lý giải'),nav=node('nav'),content=node('div',undefined,'organizer-content'),phaseNote=node('p',undefined,'organizer-phase-note');
 main.classList.add('organizer-page');menu.open=true;nav.setAttribute('aria-label','Quản lý giải đấu');menu.append(summary,nav);content.append(...main.childNodes);layout.append(menu,content);main.append(header,layout);content.prepend(phaseNote);
 const metricsBox=node('section',undefined,'control-card organizer-phase-metrics');phaseNote.after(metricsBox);const attentionBox=node('section',undefined,'organizer-attention');phaseNote.after(attentionBox);const overviewRoot=content.querySelector('#organizer-overview'),registrationDashboard=content.querySelector('#registration-dashboard');phaseNote.hidden=true;
 let current=tournament,flow=null,metrics=null,metricsStale=false,active=organizerSection(current,section),stopped=false,revision=-1,menuKey='',overviewKey='',clock;
 function show(key,updateUrl=false){active=organizerSection(current,key);if(selectPanel)selectPanel(organizerPanels[active]||'overview');for(const item of nav.querySelectorAll('a')){if(item.dataset.section===active)item.setAttribute('aria-current','page');else item.removeAttribute('aria-current');}if(updateUrl){const url=new URL(location.href);url.searchParams.set('view',active);if(!['registration','check-in','matches'].includes(active))url.searchParams.delete('filter');history.replaceState(null,'',url);}if(updateUrl&&matchMedia('(max-width:800px)').matches)menu.open=false;
  metricsBox.hidden=active==='overview'||Boolean(registrationDashboard&&active==='registration')||Boolean(current.timeline&&content.querySelector('#submission-dashboard')&&active==='submissions');attentionBox.hidden=metricsBox.hidden;if(active!=='overview'){if(overviewRoot)overviewRoot.replaceChildren();overviewKey='';if(!metricsBox.hidden&&!metricsBox.contains(document.activeElement))renderOrganizerMetrics(metricsBox,current,metrics,{section:active,stale:metricsStale});if(!attentionBox.hidden&&!attentionBox.contains(document.activeElement))renderTournamentAttentionPanel(attentionBox,current,metrics,{stale:metricsStale});}else{metricsBox.replaceChildren();attentionBox.replaceChildren();}
  const decision=organizerNextAction(current,metrics,{stale:metricsStale}),nextOverviewKey=JSON.stringify([current.status,flow?.phase,current.timelineStartIssue,decision.command,decision.href,metricsStale]);
  if(active==='overview'&&overviewRoot&&(!overviewRoot.contains(document.activeElement)||nextOverviewKey!==overviewKey)){overviewKey=nextOverviewKey;renderOrganizerOverview(overviewRoot,current,metrics,{stale:metricsStale,onCommand:name=>{if(organizerNextAction(current,metrics,{stale:metricsStale}).command===name)return onCommand?.(name);return refresh();}});}
 }
 function paint(){
  renderOrganizerHeader(header,current,flow,async()=>{await sessionRequest('/api/tournaments/'+encodeURIComponent(current.id)+'/keep',{revision:current.revision,reason:'Chủ giải xác nhận tiếp tục sử dụng'});await refresh();},{stale:metricsStale});const items=organizerNavigation(current),key=JSON.stringify(items);
  if(key!==menuKey){menuKey=key;renderOrganizerSidebar(nav,current,selectPanel?show:null);}
  const focus=organizerFocus(tournamentPhase(current,flow));for(const a of nav.querySelectorAll('a'))a.dataset.phaseFocus=String(a.dataset.section===focus);
  if(onPhase)onPhase(current,flow,{stale:metricsStale});show(active);
 }
 function update(t){if(t.revision<revision)return false;if(!organizerAccess(t,account)){stop();layout.hidden=true;header.replaceChildren(node('p','Bạn không còn quyền quản lý giải này.'));return false;}revision=t.revision;current=t;if(flow?.revision!==t.revision)flow=null;if(metrics?.revision!==t.revision)metrics=null;paint();return true;}
 async function readMetrics({signal,isCurrent}){try{const [t,schedule]=await Promise.all([sessionRequest('/api/tournaments/'+encodeURIComponent(current.id),undefined,undefined,{signal}),sessionRequest('/api/tournaments/'+encodeURIComponent(current.id)+'/organizer-metrics',undefined,undefined,{signal})]);if(!isCurrent()||t.revision<revision)return;if(onSnapshot)onSnapshot(t);else if(!update(t))return;if(schedule.revision===revision&&schedule.tournamentId===current.id){flow=metrics=organizerPhaseData(t,schedule);metricsStale=false;phaseNote.hidden=true;paint();}else{flow=metrics=null;metricsStale=true;phaseNote.hidden=false;phaseNote.replaceChildren(node('p','Đang đồng bộ phiên bản giải. Chờ dữ liệu khớp trước khi thao tác.'));paint();}}catch(error){if(!isCurrent())return;if(['UNAUTHENTICATED','TOURNAMENT_FORBIDDEN','ACCOUNT_DISABLED'].includes(error.message)){stop();layout.hidden=true;header.replaceChildren(node('p','Phiên đăng nhập hoặc quyền quản lý đã thay đổi. Hãy tải lại trang.'));return;}metricsStale=true;phaseNote.hidden=false;phaseNote.replaceChildren(node('p','Không cập nhật được thông tin quản lý. Dữ liệu có thể đã cũ; hãy chờ đồng bộ trước khi thao tác.'));paint();}}
 let refreshController;const refresh=()=>refreshController?.request(true);function stop(){stopped=true;refreshController?.dispose();clearInterval(clock);}
 const media=matchMedia('(max-width:800px)');menu.open=!media.matches;media.addEventListener('change',event=>menu.open=!event.matches);
 update(tournament);refreshController=createTournamentRefresh(readMetrics,{tournamentId:tournament.id,connected:()=>layout.isConnected});const tick=()=>{clearInterval(clock);if(!document.hidden&&!stopped)clock=setInterval(()=>updateHeaderTimers(main),1000);};tick();document.addEventListener('visibilitychange',tick);addEventListener('pagehide',event=>{clearInterval(clock);if(!event.persisted)document.removeEventListener('visibilitychange',tick);});addEventListener('pageshow',event=>{if(event.persisted)tick();});
 return {update,refresh,select:show,stop,content,get snapshot(){return flow;}};
}

let auxiliaryShell;
export async function organizerPageShell(t,section){
 if(location.pathname.startsWith('/admin/'))return;
 if(auxiliaryShell){auxiliaryShell.update(t);return;}
 const account=await getAccount();if(!organizerAccess(t,account))return;
 auxiliaryShell=mountOrganizerShell(document.querySelector('main'),{tournament:t,account,section});
}
