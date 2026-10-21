import {renderRegistrationDashboard} from './registration-dashboard.js';
import {readRegistrationDashboard,applicationCount,applicationPageSize} from './registration-dashboard-model.js';
import {eligibilityReasonLabels} from './registration-policy.js';
import {createTournamentRefresh} from './refresh-controller.js';
import {node,date} from './player-dom.js';
import {organizerPageShell} from './organizer-shell.js';
import {sessionRequest} from '../api/auth-api.js';
import {errorText} from '../ui/toast.js';
import {reviewDecisions,reviewPayload} from './application-review-model.js';
import {applicationLabels} from './timeline-registration-model.js';

const id=new URLSearchParams(location.search).get('id'),base='/api/tournaments/'+encodeURIComponent(id);
const root=document.querySelector('#applications'),dashboard=document.querySelector('#registration-dashboard'),notice=document.querySelector('#review-notice'),filter=document.querySelector('#application-status'),heading=document.querySelector('#application-queue-heading'),queueCount=document.querySelector('#application-queue-count');
let page=0,acting=false,initialized=false,refresh;
const load=()=>refresh.request(true,'manual',{replace:true});
const focusQueue=()=>{heading.scrollIntoView?.({block:'start'});heading.focus();};
function rememberFilter(){const url=new URL(location.href);url.searchParams.set('status',filter.value||'ALL');history.replaceState(null,'',url);}
function selectFilter(status){
 if(acting)return;
 if(initialized&&filter.value===status&&page===0){focusQueue();return;}
 filter.value=status;page=0;initialized=true;rememberFilter();load().then(focusQueue);
}
function disableSnapshot(){for(const host of [root,dashboard])host.querySelectorAll('button,input,textarea,select').forEach(control=>control.disabled=true);}
async function readApplications({isCurrent,signal,reason}){
 if(acting)return;
 try{
  const snapshot=await readRegistrationDashboard(base,{request:sessionRequest,search:location.search,status:initialized?filter.value:null,page,signal,isCurrent});
  if(!snapshot||!isCurrent())return;
  const {tournament,summary,data}=snapshot;
  await organizerPageShell(tournament,'registration');
  if(!isCurrent())return;
  // Background updates must not erase an explanation being typed for a review.
  if(reason!=='manual'&&root.contains(document.activeElement)&&document.activeElement.matches('input,textarea,select'))return;
  const focused=dashboard.contains(document.activeElement)?document.activeElement.dataset.registrationFocus:null;
  filter.value=snapshot.status;page=snapshot.page;initialized=true;
  const reviewOpen=data.reviewOpen&&summary.canReview&&!summary.control.lockedAt;
  notice.textContent=reviewOpen?'Có thể xét duyệt đến giờ bắt đầu lập trình: '+date(data.codingOpenAt):'Đã khóa xét duyệt. Chỉ xem danh sách.';
  renderRegistrationDashboard(dashboard,tournament,summary,{status:filter.value,selectFilter,refresh:load,focusRoster:()=>{const roster=document.querySelector('#registration-roster');roster.scrollIntoView?.({block:'center'});roster.querySelector('button')?.focus();}});
  if(focused)dashboard.querySelector('[data-registration-focus="'+focused+'"]')?.focus();
  const total=applicationCount(summary.counts,filter.value);
  heading.textContent=filter.value==='NEEDS_REVIEW'?'Đơn cần xử lý':filter.value?(applicationLabels[filter.value]||'Danh sách đăng ký'):'Tất cả đơn đăng ký';
  queueCount.textContent=total?`${total} đơn · Đang hiển thị ${page*applicationPageSize+1}–${page*applicationPageSize+data.items.length}`:'0 đơn';
  root.replaceChildren();
  if(!data.items.length){root.append(node('p',filter.value==='NEEDS_REVIEW'?'Không còn đơn cần xử lý.':'Không có đơn đăng ký phù hợp.'));if(filter.value){const all=node('button','Xem tất cả đơn');all.onclick=()=>selectFilter('');root.append(all);}}
  for(const entry of data.items){
   const card=node('article',undefined,'panel');
   card.append(node('h3',entry.displayName),node('p',(applicationLabels[entry.status]||entry.status)+' · '+entry.intendedLanguage),node('p','Trường/Đội: '+(entry.affiliation||'—')),node('p','Đăng ký lúc: '+date(entry.registeredAt)));
   if(entry.waitlistPosition)card.append(node('p','Vị trí danh sách chờ: #'+entry.waitlistPosition));
   for(const code of entry.reasonCodes||[])card.append(node('p',(entry.status==='NEEDS_REVIEW'?'':'Khi gửi đơn: ')+(eligibilityReasonLabels[code]||'Cần ban tổ chức kiểm tra')));
   if(entry.reviewReason)card.append(node('p','Lý do gần nhất: '+entry.reviewReason),node('p','Xét duyệt lúc: '+date(entry.reviewedAt)));
   const decisions=reviewDecisions(entry.status,reviewOpen);
   if(decisions.length){
    const explanation=node('textarea');explanation.maxLength=500;explanation.setAttribute('aria-label','Lý do xét duyệt '+entry.displayName);
    const label=node('label','Lý do xét duyệt (bắt buộc)'),actions=node('div',undefined,'actions');label.append(explanation);card.append(label,actions);
    for(const decision of decisions){
     const button=node('button',({APPROVED:'Chấp nhận',WAITLISTED:'Đưa vào danh sách chờ',REJECTED:'Từ chối'})[decision]);
     button.onclick=async()=>{
      if(acting)return;
      let payload;try{payload=reviewPayload(decision,explanation.value,data.revision);}catch(error){notice.textContent=errorText(error.message);return;}
      acting=true;disableSnapshot();filter.disabled=true;
      try{await sessionRequest(base+'/participant-applications/'+encodeURIComponent(entry.id)+'/review',payload);}
      catch(error){notice.textContent=errorText(error.message)+' Hãy cập nhật danh sách trước khi thử lại.';return;}
      finally{acting=false;filter.disabled=false;}
      await load();
     };
     actions.append(button);
    }
   }
   root.append(card);
  }
  const controls=node('div',undefined,'actions'),prev=node('button','← Trước'),next=node('button','Sau →');
  prev.disabled=page===0;next.disabled=(page+1)*applicationPageSize>=total;
  prev.onclick=()=>{if(!acting){page--;load();}};next.onclick=()=>{if(!acting){page++;load();}};
  controls.hidden=total<=applicationPageSize;controls.append(prev,node('span','Trang '+(page+1)),next);root.append(controls);
 }catch(error){
  if(!isCurrent())return;
  notice.textContent=errorText(error.message);
  if(['ACCOUNT_DISABLED','UNAUTHENTICATED','AUTHENTICATION_REQUIRED','UNAUTHORIZED','FORBIDDEN','NOT_FOUND','TOURNAMENT_NOT_FOUND','TOURNAMENT_FORBIDDEN'].includes(error.message)){root.replaceChildren();dashboard.replaceChildren();queueCount.textContent='';}
  else disableSnapshot();
 }
}
document.querySelector('#refresh-applications').onclick=load;
filter.onchange=()=>{page=0;initialized=true;rememberFilter();load();};
document.querySelector('#back-to-tournament').href=(location.pathname.startsWith('/admin/')?'/admin/tournament-detail.html?id=':'/tournament.html?id=')+encodeURIComponent(id);
refresh=createTournamentRefresh(readApplications,{tournamentId:id,connected:()=>root.isConnected});
