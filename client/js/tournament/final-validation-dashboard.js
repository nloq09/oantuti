import {createTournamentRefresh} from './refresh-controller.js';
import {getAccount,loginLink} from '../account/account-session.js';
import {organizerPageShell} from './organizer-shell.js';
import {renderDeadlineInfo} from './deadline-info.js';
import {renderParticipantDiscipline} from './organizer-discipline.js';
import {finalValidationActions} from './organizer-validation-actions.js';
import {sessionRequest} from '../api/auth-api.js';
import {node,link} from './player-dom.js';
import {validationStages,validationReason} from './validation-management-labels.js';
import {errorText} from '../ui/toast.js';
const isAdmin=location.pathname.startsWith('/admin/');
const root=document.querySelector('#final-validation-dashboard'),id=new URLSearchParams(location.search).get('id'),base='/api/tournaments/'+encodeURIComponent(id);let page=0,busy=false;
const requestedStatus=new URLSearchParams(location.search).get('status');let filterStatus=['QUEUED','RUNNING','PASSED','FAILED','INFRA_ERROR','EMERGENCY_OPEN'].includes(requestedStatus)?requestedStatus:'';
let refresh;const render=(force=false)=>refresh.request(force,'manual',{replace:true});
async function readValidation({force,isCurrent,signal,reason}){if(busy)return;try{const [t,data]=await Promise.all([sessionRequest(base,undefined,undefined,{signal}),sessionRequest(base+'/final-validations?page='+page+(filterStatus?'&status='+encodeURIComponent(filterStatus):''),undefined,undefined,{signal})]);if(!isCurrent()||busy||data.revision!==t.revision)return;await organizerPageShell(t,'validation');if(!isCurrent())return;if(!(force&&reason==='manual')&&root.contains(document.activeElement)&&document.activeElement.matches('input,textarea,select'))return;root.replaceChildren(node('h1',t.name+' · Kiểm thử chính thức'),link('← Quản lý giải',(isAdmin?'/admin/tournament-detail.html?id=':'/tournament.html?id=')+encodeURIComponent(id)),node('p','Chỉ kiểm thử bài đã khóa. Không mở lại editor tự động; lỗi hạ tầng có thể chạy lại trước hạn kiểm thử.'));
 if(!isAdmin){renderDeadlineInfo(root,t);const discipline=node('section',undefined,'organizer-discipline');renderParticipantDiscipline(discipline,t,()=>render(true));root.append(discipline);}
 const filter=node('select'),filterLabel=node('label','Lọc trạng thái kiểm thử');for(const [value,label]of [['','Tất cả (gồm lịch sử)'],['QUEUED','Đang xếp hàng'],['RUNNING','Đang chạy'],['PASSED','Đã đạt'],['FAILED','Không đạt'],['INFRA_ERROR','Lỗi hạ tầng'],['EMERGENCY_OPEN','Đang nộp khẩn cấp']])filter.append(new Option(label,value));filter.value=filterStatus;filter.onchange=()=>{filterStatus=filter.value;page=0;const url=new URL(location.href);if(filterStatus)url.searchParams.set('status',filterStatus);else url.searchParams.delete('status');history.replaceState(null,'',url);render(true);};filterLabel.append(filter);root.append(filterLabel);
 for(const v of data.items){const card=node('section',undefined,'panel validation-panel');card.append(node('h2',v.displayName+' · '+v.language+' · v'+v.version+' · '+v.status),node('p','Mã kiểm thử: '+v.id));for(const [key,label]of Object.entries(validationStages))card.append(node('p',label+': '+(v.steps[key]||'PENDING')));if(v.reason)card.append(node('p',validationReason(v.reason)));if(v.originalVersionId!==v.versionId)card.append(node('p','Đã chuyển sang bản trước: '+v.versionId));
 const reason=node('textarea'),label=node('label','Lý do xử lý (bắt buộc)'),notice=node('p');reason.maxLength=400;label.append(reason);notice.setAttribute('role','status');
 const act=async(action,deadline)=>{if(busy)return;if(!reason.value.trim()||reason.value.trim().length>400){notice.textContent='Nhập lý do từ 1 đến 400 ký tự.';return;}busy=true;try{await sessionRequest(base+'/final-validations/'+v.id+'/'+action,{revision:data.revision,reason:reason.value,deadline});busy=false;await render();}catch(error){notice.textContent=errorText(error.message);}finally{busy=false;}};
 const allowed=finalValidationActions(v,data);
 if(allowed.includes('retry')){const retry=node('button','Chạy lại kiểm thử bị gián đoạn');retry.onclick=()=>act('retry');card.append(label,retry);}
 if(allowed.includes('emergency')){const deadline=node('input'),deadlineLabel=node('label','Hạn nộp khẩn cấp (trước hạn kiểm thử)'),unlock=node('button','Cấp lượt nộp khẩn cấp');deadline.type='datetime-local';deadlineLabel.append(deadline);unlock.onclick=()=>{if(!deadline.value){notice.textContent='Chọn hạn nộp mới.';return;}act('emergency',new Date(deadline.value).toISOString());};card.append(label,deadlineLabel,unlock);}
 card.append(notice);root.append(card);}
 if(!data.items.length)root.append(node('p',filterStatus?'Không có bài phù hợp bộ lọc.':'Chưa có bài được đưa vào kiểm thử chính thức.'));
 const prev=node('button','Trang trước'),next=node('button','Trang sau');prev.disabled=page===0;next.disabled=data.items.length<25;prev.onclick=()=>{page--;render();};next.onclick=()=>{page++;render();};const pagination=node('div',undefined,'ui-actions');pagination.hidden=page===0&&!data.items.length;pagination.append(prev,node('span',' Trang '+(page+1)+' '),next);root.append(pagination);
 }catch(error){if(!isCurrent())return;root.replaceChildren(node('h1','Không tải được kiểm thử'),node('p',errorText(error.message)));}}
const account=await getAccount();if(!account)location.replace(loginLink());else refresh=createTournamentRefresh(readValidation,{tournamentId:id,interval:3000,connected:()=>root.isConnected});
