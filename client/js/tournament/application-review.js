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
import {queueFilters,filterKeys,bulkReviewPayload,localDateTime} from './application-queue-model.js';
import {renderApplicationCard,decisionLabels} from './application-review-card.js';
import {openApplicationDetails} from './application-details.js';
import {confirmOrganizerDecision} from './organizer-decision-dialog.js';

const id=new URLSearchParams(location.search).get('id'),base='/api/tournaments/'+encodeURIComponent(id);
const root=document.querySelector('#applications'),dashboard=document.querySelector('#registration-dashboard'),notice=document.querySelector('#review-notice'),filter=document.querySelector('#application-status'),heading=document.querySelector('#application-queue-heading'),queueCount=document.querySelector('#application-queue-count');
const filterForm=document.querySelector('#application-filters'),bulkRoot=document.querySelector('#bulk-review-controls'),bulkResult=document.querySelector('#bulk-review-result');
let page=0,acting=false,initialized=false,refresh,filters=queueFilters(location.search),snapshotRevision=null,currentData=null,detailsDialog=null,bulkDialog=null;
const selected=new Map();
for(const [code,label]of Object.entries(eligibilityReasonLabels)){const option=node('option',label);option.value=code;document.querySelector('#filter-reason').append(option);}
function fillFilters(){for(const key of [...filterKeys,'size'])document.querySelector('#filter-'+key).value=['from','until'].includes(key)?localDateTime(filters[key]):filters[key]||'';}
fillFilters();
const load=()=>refresh.request(true,'manual',{replace:true});
const focusQueue=()=>{heading.scrollIntoView?.({block:'start'});heading.focus();};
function rememberFilter(){const url=new URL(location.href);url.searchParams.set('status',filter.value||'ALL');for(const key of [...filterKeys,'size']){if(filters[key])url.searchParams.set(key,filters[key]);else url.searchParams.delete(key);}history.replaceState(null,'',url);}
function clearSelection(){selected.clear();}
function setFiltersDisabled(value){filterForm.querySelectorAll('input,select,button').forEach(control=>control.disabled=value);}
function applyFilters(){
 if(acting)return;
 try{const next={size:Number(document.querySelector('#filter-size').value)};for(const key of filterKeys){const value=document.querySelector('#filter-'+key).value.trim();if(value)next[key]=['from','until'].includes(key)?new Date(value).toISOString():value;}
  if(next.from&&next.until&&Date.parse(next.from)>=Date.parse(next.until))throw Error('INVALID_FILTER');
  filters=next;page=0;initialized=true;clearSelection();rememberFilter();load();
 }catch(error){notice.textContent=errorText(error.message==='Invalid time value'?'INVALID_FILTER':error.message);}
}
function renderBulkControls(){
 bulkRoot.replaceChildren();if(!currentData?.reviewOpen)return;
 const entries=currentData.items.filter(entry=>reviewDecisions(entry.status,true).length),all=node('input'),label=node('label','Chọn tất cả đơn có thể xét duyệt trên trang này');all.type='checkbox';all.id='select-current-applications';all.disabled=!entries.length||acting;all.checked=entries.length>0&&entries.every(entry=>selected.has(entry.id));all.indeterminate=selected.size>0&&!all.checked;
 all.onchange=()=>{selected.clear();if(all.checked)for(const entry of entries)selected.set(entry.id,entry);for(const control of root.querySelectorAll('[data-application-selection]'))control.checked=selected.has(control.dataset.applicationSelection);renderBulkControls();};label.prepend(all);bulkRoot.append(label,node('p',`Đã chọn ${selected.size} đơn. Lựa chọn chỉ áp dụng cho trang hiện tại.`));
 const actions=node('div',undefined,'actions');for(const [decision,title]of Object.entries(decisionLabels)){const button=node('button',title+' các đơn đã chọn');button.type='button';button.disabled=!selected.size||acting;button.onclick=()=>{
  if(acting||!selected.size)return;const entries=[...selected.values()],revision=snapshotRevision;
  bulkDialog=confirmOrganizerDecision(title+' '+entries.length+' đơn',`Áp dụng cho ${entries.length} đơn đã chọn. Server kiểm tra từng đơn, số suất và trạng thái mới nhất. Các đơn lỗi sẽ được báo riêng.`,async reason=>{
   const payload=bulkReviewPayload(decision,reason,revision,entries);acting=true;disableSnapshot();setFiltersDisabled(true);
   try{const result=await sessionRequest(base+'/participant-applications/bulk-review',payload);bulkResult.replaceChildren(node('p',`${result.succeeded} đơn đã xử lý · ${result.failed} đơn không xử lý được.`));for(const item of result.items.filter(item=>!item.success)){const entry=entries.find(entry=>entry.id===item.id);bulkResult.append(node('p',(entry?.displayName||'Đơn đăng ký')+': '+errorText(item.error)));}clearSelection();}
   catch(error){bulkResult.replaceChildren(node('p',errorText(error.message)+' Hãy cập nhật trước khi thử lại; không tự gửi lại yêu cầu.'));throw error;}
   finally{acting=false;setFiltersDisabled(false);}
   await load();
  },{danger:decision==='REJECTED'});
 };actions.append(button);}bulkRoot.append(actions);
}
async function decide(entry,decision,explanation){
 if(acting)return;let payload;try{payload=reviewPayload(decision,explanation,snapshotRevision);}catch(error){notice.textContent=errorText(error.message);return;}
 acting=true;disableSnapshot();setFiltersDisabled(true);bulkResult.replaceChildren();
 try{await sessionRequest(base+'/participant-applications/'+encodeURIComponent(entry.id)+'/review',payload);clearSelection();}
 catch(error){notice.textContent=errorText(error.message)+' Hãy cập nhật danh sách trước khi thử lại.';return;}
 finally{acting=false;setFiltersDisabled(false);}
 await load();
}
function selectFilter(status){
 if(acting)return;
 if(initialized&&filter.value===status&&page===0){focusQueue();return;}
 filter.value=status;page=0;initialized=true;clearSelection();rememberFilter();load().then(focusQueue);
}
function disableSnapshot(){for(const host of [root,dashboard,bulkRoot])host.querySelectorAll('button,input,textarea,select').forEach(control=>control.disabled=true);}
async function readApplications({isCurrent,signal,reason}){
 if(acting)return;
 try{
  const snapshot=await readRegistrationDashboard(base,{request:sessionRequest,search:location.search,status:initialized?filter.value:null,page,filters,signal,isCurrent});
  if(!snapshot||!isCurrent())return;
  const {tournament,summary,data}=snapshot;
  await organizerPageShell(tournament,'registration');
  if(!isCurrent())return;
  // Background updates must not erase an explanation being typed for a review.
  if(reason!=='manual'&&(bulkDialog?.open||detailsDialog?.open||root.contains(document.activeElement)&&document.activeElement.matches('input,textarea,select')))return;
  const focused=dashboard.contains(document.activeElement)?document.activeElement.dataset.registrationFocus:null;
  filter.value=snapshot.status;page=snapshot.page;initialized=true;
  const reviewOpen=data.reviewOpen&&summary.canReview&&!summary.control.lockedAt;
  const selectionChanged=selected.size>0&&snapshotRevision!==summary.revision;
  if(snapshotRevision!==summary.revision)clearSelection();snapshotRevision=summary.revision;
  currentData={...data,reviewOpen};if(!reviewOpen)clearSelection();
  const visibleIds=new Set(data.items.map(entry=>entry.id));for(const key of selected.keys())if(!visibleIds.has(key))selected.delete(key);
  notice.textContent=reviewOpen?'Có thể xét duyệt đến giờ bắt đầu lập trình: '+date(data.codingOpenAt):'Đã khóa xét duyệt. Chỉ xem danh sách.';
  if(selectionChanged)notice.textContent+=' Đã xóa lựa chọn vì dữ liệu vừa thay đổi; hãy chọn lại các đơn cần xử lý.';
  renderRegistrationDashboard(dashboard,tournament,summary,{status:filter.value,selectFilter,refresh:load,focusRoster:()=>{const roster=document.querySelector('#registration-roster');roster.scrollIntoView?.({block:'center'});roster.querySelector('button')?.focus();}});
  if(focused)dashboard.querySelector('[data-registration-focus="'+focused+'"]')?.focus();
  const total=data.total??applicationCount(summary.counts,filter.value),size=data.size||filters.size||applicationPageSize;
  heading.textContent=filter.value==='NEEDS_REVIEW'?'Đơn cần xử lý':filter.value?(applicationLabels[filter.value]||'Danh sách đăng ký'):'Tất cả đơn đăng ký';
  queueCount.textContent=total?`${total} đơn · Đang hiển thị ${page*size+1}–${page*size+data.items.length}`:'0 đơn';
  root.replaceChildren();
  if(!data.items.length){root.append(node('p',filter.value==='NEEDS_REVIEW'?'Không còn đơn cần xử lý.':'Không có đơn đăng ký phù hợp.'));if(filter.value||filterKeys.some(key=>filters[key])){const all=node('button','Xóa bộ lọc và xem tất cả đơn');all.onclick=()=>{filters={size:25};fillFilters();filter.value='';applyFilters();};root.append(all);}}
  for(const entry of data.items)root.append(renderApplicationCard(entry,{reviewOpen,checked:selected.has(entry.id),onSelect:(item,checked)=>{if(checked)selected.set(item.id,item);else selected.delete(item.id);renderBulkControls();},onDecision:decide,onDetails:item=>{detailsDialog?.close();detailsDialog=openApplicationDetails(base,item,{onClose:()=>detailsDialog=null}).dialog;}}));
  renderBulkControls();
  const controls=node('div',undefined,'actions'),prev=node('button','← Trước'),next=node('button','Sau →');
  prev.disabled=page===0;next.disabled=(page+1)*size>=total;
  prev.onclick=()=>{if(!acting){page--;clearSelection();load();}};next.onclick=()=>{if(!acting){page++;clearSelection();load();}};
  controls.hidden=total<=size;controls.append(prev,node('span','Trang '+(page+1)),next);root.append(controls);
 }catch(error){
  if(!isCurrent())return;
  notice.textContent=errorText(error.message);
  if(['ACCOUNT_DISABLED','UNAUTHENTICATED','AUTHENTICATION_REQUIRED','UNAUTHORIZED','FORBIDDEN','NOT_FOUND','TOURNAMENT_NOT_FOUND','TOURNAMENT_FORBIDDEN'].includes(error.message)){root.replaceChildren();dashboard.replaceChildren();bulkRoot.replaceChildren();bulkResult.replaceChildren();clearSelection();detailsDialog?.close();bulkDialog?.close();queueCount.textContent='';}
  else disableSnapshot();
 }
}
document.querySelector('#refresh-applications').onclick=load;
filter.onchange=applyFilters;
filterForm.onsubmit=event=>{event.preventDefault();applyFilters();};
document.querySelector('#clear-application-filters').onclick=()=>{if(acting)return;filters={size:25};fillFilters();filter.value='';applyFilters();};
document.querySelector('#back-to-tournament').href=(location.pathname.startsWith('/admin/')?'/admin/tournament-detail.html?id=':'/tournament.html?id=')+encodeURIComponent(id);
refresh=createTournamentRefresh(readApplications,{tournamentId:id,connected:()=>root.isConnected});
