import {node,link,date} from './player-dom.js';
import {renderTournamentHeader,updateHeaderTimers,phaseLabels} from './tournament-header.js';
import {organizerNavigation,organizerSection,organizerFocus,organizerHealth,organizerPanels,organizerAccess} from './organizer-shell-model.js';
import {sessionRequest} from '../api/auth-api.js';
import {getAccount} from '../account/account-session.js';

export function renderOrganizerHeader(root,t,flow){
 root.replaceChildren();root.append(node('p','QUẢN LÝ GIẢI ĐẤU','organizer-kicker'));
 const header=renderTournamentHeader(root,t,flow),health=organizerHealth(t),state=node('p',health.text,'organizer-health');state.dataset.health=health.state;header.append(state);
 if(t.timeline&&!flow){header.append(node('p','Đang đồng bộ giai đoạn và hạn giờ từ server.'));}
 if(flow?.phaseEndsAt)header.append(node('p','Mốc hiện tại: '+date(flow.phaseEndsAt),'organizer-deadline'));
 return header;
}

export function mountOrganizerShell(main,{tournament,account,section='overview',selectPanel}={}){
 if(!organizerAccess(tournament,account))return null;
 const header=node('div',undefined,'organizer-heading'),layout=node('div',undefined,'organizer-layout'),menu=node('details',undefined,'organizer-menu'),summary=node('summary','Menu quản lý giải'),nav=node('nav'),content=node('div',undefined,'organizer-content'),phaseNote=node('p',undefined,'organizer-phase-note');
 main.classList.add('organizer-page');menu.open=true;nav.setAttribute('aria-label','Quản lý giải đấu');menu.append(summary,nav);content.append(...main.childNodes);layout.append(menu,content);main.append(header,layout);content.prepend(phaseNote);
 let current=tournament,flow=null,active=organizerSection(current,section),stopped=false,inFlight=false,revision=-1,menuKey='',poll,clock;
 function show(key,updateUrl=false){active=organizerSection(current,key);if(selectPanel)selectPanel(organizerPanels[active]||'overview');for(const item of nav.querySelectorAll('a')){if(item.dataset.section===active)item.setAttribute('aria-current','page');else item.removeAttribute('aria-current');}if(updateUrl){const url=new URL(location.href);url.searchParams.set('view',active);history.replaceState(null,'',url);}if(updateUrl&&matchMedia('(max-width:800px)').matches)menu.open=false;}
 function paint(){
  renderOrganizerHeader(header,current,flow);const items=organizerNavigation(current),key=JSON.stringify(items);
  if(key!==menuKey){menuKey=key;nav.replaceChildren();for(const item of items){const a=link(item.label,item.href);a.dataset.section=item.key;if(selectPanel&&organizerPanels[item.key]&&!(item.key==='registration'&&current.timeline))a.onclick=event=>{if(event.ctrlKey||event.metaKey||event.shiftKey||event.altKey)return;event.preventDefault();show(item.key,true);};nav.append(a);}}
  const focus=organizerFocus(flow?.phase||current.status),target=items.find(item=>item.key===focus);for(const a of nav.querySelectorAll('a'))a.dataset.phaseFocus=String(a.dataset.section===focus);
  phaseNote.replaceChildren(node('span',flow?'Giai đoạn: '+(phaseLabels[flow.phase]||flow.phase)+'. ':'Giai đoạn đang được server xác nhận. '));if(target)phaseNote.append(link('Đi tới '+target.label.toLowerCase(),target.href));show(active);
 }
 function update(t){if(t.revision<revision)return false;if(!organizerAccess(t,account)){stop();layout.hidden=true;header.replaceChildren(node('p','Bạn không còn quyền quản lý giải này.'));return false;}revision=t.revision;current=t;if(flow?.revision!==t.revision)flow=null;paint();return true;}
 async function refresh(){if(stopped||inFlight)return;inFlight=true;try{const t=await sessionRequest('/api/tournaments/'+encodeURIComponent(current.id)),schedule=await sessionRequest('/api/tournaments/'+encodeURIComponent(current.id)+'/timeline');if(stopped||t.revision<revision)return;if(!update(t))return;if(schedule.revision===revision){flow=schedule;paint();}}catch{if(!stopped){phaseNote.replaceChildren(node('p','Không cập nhật được thông tin quản lý. Dữ liệu đang hiển thị có thể đã cũ; hãy tải lại trước khi thao tác.'));}}finally{inFlight=false;}}
 function start(){stopped=false;poll=setInterval(refresh,5000);clock=setInterval(()=>updateHeaderTimers(header),1000);refresh();}
 function stop(){stopped=true;clearInterval(poll);clearInterval(clock);}
 const media=matchMedia('(max-width:800px)');menu.open=!media.matches;media.addEventListener('change',event=>menu.open=!event.matches);
 update(tournament);start();addEventListener('pagehide',stop);addEventListener('pageshow',event=>{if(event.persisted&&stopped)start();});
 return {update,refresh,select:show,stop,content};
}

let auxiliaryShell;
export async function organizerPageShell(t,section){
 if(location.pathname.startsWith('/admin/'))return;
 if(auxiliaryShell){auxiliaryShell.update(t);return;}
 const account=await getAccount();if(!organizerAccess(t,account))return;
 auxiliaryShell=mountOrganizerShell(document.querySelector('main'),{tournament:t,account,section});
}
