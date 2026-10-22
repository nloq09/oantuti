import {createTournamentRefresh} from './refresh-controller.js';
import {getAccount,loginLink} from '../account/account-session.js';
import {organizerPageShell} from './organizer-shell.js';
import {renderParticipantDiscipline} from './organizer-discipline.js';
import {sessionRequest} from '../api/auth-api.js';
import {node} from './player-dom.js';
import {errorText} from '../ui/toast.js';
import {renderValidationSummary,renderValidationCard,renderValidationPagination,validationLabels} from './organizer-validation-view.js';
const root=document.querySelector('#final-validation-dashboard'),params=new URLSearchParams(location.search),id=params.get('id'),base='/api/tournaments/'+encodeURIComponent(id);
let page=0,busy=false,refresh,search=params.get('search')?.slice(0,100)||'',historyMode=params.get('history')==='true',filterStatus=Object.hasOwn(validationLabels,params.get('status'))?params.get('status'):'';
const drafts=new Map();let lastUpdate;
const reload=(force=true)=>refresh?.request(force,'manual',{replace:true});
function queryChanged(){page=0;const url=new URL(location.href);for(const [key,value]of [['status',filterStatus],['search',search],['history',historyMode?'true':'']])if(value)url.searchParams.set(key,value);else url.searchParams.delete(key);window.history.replaceState(null,'',url);reload();}
function filter(status){filterStatus=status;queryChanged();}
async function readValidation({isCurrent,signal,force,reason}){
 if(busy)return;
 try{
  const query=new URLSearchParams({view:'dashboard',page:String(page),history:String(historyMode)});if(filterStatus)query.set('status',filterStatus);if(search)query.set('search',search);
  const [t,data,metrics]=await Promise.all([sessionRequest(base,undefined,undefined,{signal}),sessionRequest(base+'/final-validations?'+query,undefined,undefined,{signal}),sessionRequest(base+'/organizer-metrics',undefined,undefined,{signal})]);
  if(!isCurrent()||busy)return;if(data.revision!==t.revision||metrics.revision!==t.revision){const status=root.querySelector('[data-validation-refresh]');if(status)status.textContent='Đang đồng bộ dữ liệu. Thao tác sẽ dùng phiên bản mới nhất từ server.';return;}
  await organizerPageShell(t,'validation');if(!isCurrent())return;
  if(!(force&&reason==='manual')&&root.contains(document.activeElement)&&document.activeElement.matches('input,textarea,select'))return;
  if(page>0&&!data.items.length){page=Math.max(0,Math.ceil(data.total/data.size)-1);reload();return;}
  root.replaceChildren();lastUpdate=performance.now();
  const heading=node('div',undefined,'validation-dashboard-heading'),title=node('div'),stamp=node('small','Tự cập nhật · Vừa xong'),update=node('button','↻ Cập nhật');stamp.dataset.validationRefresh='';stamp.setAttribute('role','status');update.onclick=()=>reload();title.append(node('h1','Kiểm thử chính thức'),stamp);heading.append(title,update);root.append(heading);
  renderValidationSummary(root,t,data,metrics,{filter});
  const results=node('section',undefined,'validation-list');results.append(node('h2','Danh sách kiểm thử'));
  const filters=node('form',undefined,'validation-filters'),select=node('select'),label=node('label','Trạng thái');select.setAttribute('aria-label','Trạng thái kiểm thử');select.append(new Option('Tất cả',''));for(const [key,value]of Object.entries(validationLabels))if(key!=='EMERGENCY_OPEN'||data.summary.emergencyOpen||filterStatus===key)select.append(new Option(value,key));select.value=filterStatus;select.onchange=()=>filter(select.value);label.append(select);
  const find=node('input'),findLabel=node('label','Tìm người chơi'),submit=node('button','Tìm'),historical=node('input'),historyLabel=node('label');find.type='search';find.maxLength=100;find.value=search;find.placeholder='Tên đăng nhập hoặc tên hiển thị';findLabel.append(find);submit.type='submit';historical.type='checkbox';historical.checked=historyMode;historyLabel.append(historical,node('span','Bao gồm lịch sử'));historical.onchange=()=>{historyMode=historical.checked;queryChanged();};filters.onsubmit=event=>{event.preventDefault();search=find.value.trim();queryChanged();};filters.append(label,findLabel,submit,historyLabel);results.append(filters,node('p',data.total+' bài khớp bộ lọc'+(historyMode?' · Đang xem cả lịch sử (chỉ đọc)':'')));
  if(!data.items.length)results.append(node('p',filterStatus||search||historyMode?'Không có bài phù hợp với bộ lọc hiện tại.':'Chưa có bài nào được đưa vào kiểm thử chính thức.'));
  const act=async(v,action,reason,deadline)=>{if(busy)return;busy=true;try{await sessionRequest(base+'/final-validations/'+encodeURIComponent(v.id)+'/'+action,{revision:data.revision,reason,deadline});drafts.delete(v.id);}catch(error){throw new Error(errorText(error.message));}finally{busy=false;}await reload();};
  for(const v of data.items)renderValidationCard(results,v,data,{act,saved:drafts.get(v.id),remember:(key,value)=>drafts.set(key,{...drafts.get(key),...value})});
  renderValidationPagination(results,data,next=>{page=next;reload();});root.append(results);
  if(!location.pathname.startsWith('/admin/')){const discipline=node('section',undefined,'organizer-discipline');renderParticipantDiscipline(discipline,t,()=>reload());root.append(discipline);}
 }catch(error){if(!isCurrent())return;root.replaceChildren(node('h1','Không tải được kiểm thử'),node('p',errorText(error.message)));const retry=node('button','Thử lại');retry.onclick=()=>reload();root.append(retry);}
}
const age=setInterval(()=>{const stamp=root.querySelector('[data-validation-refresh]');if(stamp&&lastUpdate!==undefined)stamp.textContent='Tự cập nhật · '+Math.floor((performance.now()-lastUpdate)/1000)+' giây trước';},1000);
addEventListener('pagehide',event=>{if(!event.persisted)clearInterval(age);});
root.addEventListener('focusout',()=>{if(!busy)refresh?.request(false,'blur');});
const account=await getAccount();if(!account)location.replace(loginLink());else refresh=createTournamentRefresh(readValidation,{tournamentId:id,interval:3000,connected:()=>root.isConnected});
