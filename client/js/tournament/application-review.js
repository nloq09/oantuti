import {createTournamentRefresh} from './refresh-controller.js';
import {node,link,date} from './player-dom.js';
import {organizerPageShell} from './organizer-shell.js';
import {sessionRequest} from '../api/auth-api.js';
import {errorText} from '../ui/toast.js';
import {reviewDecisions,reviewPayload} from './application-review-model.js';
import {applicationLabels} from './timeline-registration-model.js';
const id=new URLSearchParams(location.search).get('id'),base='/api/tournaments/'+encodeURIComponent(id),root=document.querySelector('#applications'),notice=document.querySelector('#review-notice'),filter=document.querySelector('#application-status');let page=0,loading=false,acting=false;
const requestedStatus=new URLSearchParams(location.search).get('status');if([...filter.options].some(option=>option.value===requestedStatus))filter.value=requestedStatus;
let refresh;const load=()=>refresh.request(true,'manual',{replace:true});
async function readApplications({isCurrent,signal,reason}){if(acting)return;loading=true;notice.textContent='';try{
 const data=await sessionRequest(base+'/participant-applications?page='+page+(filter.value?'&status='+encodeURIComponent(filter.value):''),undefined,undefined,{signal});if(!isCurrent())return;
 await organizerPageShell(await sessionRequest(base,undefined,undefined,{signal}),'registration');if(!isCurrent()||reason!=='manual'&&root.contains(document.activeElement)&&document.activeElement.matches('input,textarea,select'))return;
 root.replaceChildren(node('p',data.reviewOpen?'Có thể xét duyệt đến giờ bắt đầu lập trình: '+date(data.codingOpenAt):'Đã khóa xét duyệt. Chỉ xem danh sách.'));
 if(!data.items.length)root.append(node('p','Không có đơn đăng ký ở trang này.'));
 for(const entry of data.items){const card=node('article',undefined,'panel');card.append(node('h2',entry.displayName),node('p',(applicationLabels[entry.status]||entry.status)+' · '+entry.intendedLanguage),node('p','Trường/Đội: '+(entry.affiliation||'—')),node('p','Đăng ký lúc: '+date(entry.registeredAt)));if(entry.reviewReason)card.append(node('p','Lý do gần nhất: '+entry.reviewReason),node('p','Xét duyệt lúc: '+date(entry.reviewedAt)));
  const decisions=reviewDecisions(entry.status,data.reviewOpen);if(decisions.length){const reason=node('textarea');reason.maxLength=500;reason.setAttribute('aria-label','Lý do xét duyệt '+entry.displayName);const label=node('label','Lý do xét duyệt (bắt buộc)');label.append(reason);card.append(label);for(const decision of decisions){const button=node('button',({APPROVED:'Chấp nhận',WAITLISTED:'Đưa vào danh sách chờ',REJECTED:'Từ chối'})[decision]);button.onclick=async()=>{if(acting)return;acting=true;let success=false;root.querySelectorAll('button').forEach(b=>b.disabled=true);try{await sessionRequest(base+'/participant-applications/'+encodeURIComponent(entry.id)+'/review',reviewPayload(decision,reason.value,data.revision));success=true;}catch(error){notice.textContent=errorText(error.message);}finally{acting=false;root.querySelectorAll('button').forEach(b=>b.disabled=false);}if(success)await load();};card.append(button);}}
  root.append(card);
 }
 const controls=node('div',undefined,'actions'),prev=node('button','← Trước'),next=node('button','Sau →');prev.disabled=page===0;next.disabled=data.items.length<25;prev.onclick=()=>{page--;load();};next.onclick=()=>{page++;load();};controls.append(prev,node('span','Trang '+(page+1)),next);root.append(controls);
}catch(error){if(!isCurrent())return;notice.textContent=errorText(error.message);root.replaceChildren();}finally{loading=false;}}
document.querySelector('#refresh-applications').onclick=load;filter.onchange=()=>{page=0;const url=new URL(location.href);if(filter.value)url.searchParams.set('status',filter.value);else url.searchParams.delete('status');history.replaceState(null,'',url);load();};const back=document.querySelector('#back-to-tournament');back.href=(location.pathname.startsWith('/admin/')?'/admin/tournament-detail.html?id=':'/tournament.html?id=')+encodeURIComponent(id);refresh=createTournamentRefresh(readApplications,{tournamentId:id,connected:()=>root.isConnected});
