import {getAccount,loginLink} from '../account/account-session.js';
import {sessionRequest} from '../api/auth-api.js';
import {node,link} from './player-dom.js';
import {validationStages,validationReason} from './final-validation-view.js';
import {errorText} from '../ui/toast.js';
const isAdmin=location.pathname.startsWith('/admin/');
const root=document.querySelector('#final-validation-dashboard'),id=new URLSearchParams(location.search).get('id'),base='/api/tournaments/'+encodeURIComponent(id);let page=0,busy=false,stopped=false,timer;
async function render(){if(busy||stopped)return;busy=true;try{const [t,data]=await Promise.all([sessionRequest(base),sessionRequest(base+'/final-validations?page='+page)]);if(stopped)return;if(root.contains(document.activeElement)&&document.activeElement.matches('input,textarea,select'))return;root.replaceChildren(node('h1',t.name+' · Kiểm thử chính thức'),link('← Quản lý giải',(isAdmin?'/admin/tournament-detail.html?id=':'/tournament.html?id=')+encodeURIComponent(id)),node('p','Chỉ kiểm thử bài đã khóa. Không mở lại editor tự động; lỗi hạ tầng có thể chạy lại trước hạn kiểm thử.'));
 for(const v of data.items){const card=node('section',undefined,'panel validation-panel');card.append(node('h2',v.displayName+' · '+v.language+' · v'+v.version+' · '+v.status),node('p','Mã kiểm thử: '+v.id));for(const [key,label]of Object.entries(validationStages))card.append(node('p',label+': '+(v.steps[key]||'PENDING')));if(v.reason)card.append(node('p',validationReason(v.reason)));if(v.originalVersionId!==v.versionId)card.append(node('p','Đã chuyển sang bản trước: '+v.versionId));
 const reason=node('textarea'),label=node('label','Lý do xử lý (bắt buộc)'),notice=node('p');reason.maxLength=400;label.append(reason);notice.setAttribute('role','status');
 const act=async(action,deadline)=>{if(busy)return;busy=true;try{await sessionRequest(base+'/final-validations/'+v.id+'/'+action,{revision:data.revision,reason:reason.value,deadline});busy=false;await render();}catch(error){notice.textContent=errorText(error.message);}finally{busy=false;}};
 if(v.canRetry){const retry=node('button','Chạy lại kiểm thử bị gián đoạn');retry.onclick=()=>act('retry');card.append(label,retry);}
 if(v.canGrantEmergency){const deadline=node('input'),deadlineLabel=node('label','Hạn nộp khẩn cấp (trước hạn kiểm thử)'),unlock=node('button','Cấp lượt nộp khẩn cấp');deadline.type='datetime-local';deadlineLabel.append(deadline);unlock.onclick=()=>{if(!deadline.value){notice.textContent='Chọn hạn nộp mới.';return;}act('emergency',new Date(deadline.value).toISOString());};card.append(label,deadlineLabel,unlock);}
 card.append(notice);root.append(card);}
 if(!data.items.length)root.append(node('p','Chưa có bài được đưa vào kiểm thử chính thức.'));
 const prev=node('button','Trang trước'),next=node('button','Trang sau');prev.disabled=page===0;next.disabled=data.items.length<25;prev.onclick=()=>{page--;render();};next.onclick=()=>{page++;render();};root.append(prev,node('span',' Trang '+(page+1)+' '),next);
 }catch(error){root.replaceChildren(node('h1','Không tải được kiểm thử'),node('p',errorText(error.message)));}finally{busy=false;clearTimeout(timer);if(!stopped)timer=setTimeout(render,3000);}}
const account=await getAccount();if(!account)location.replace(loginLink());else await render();addEventListener('pagehide',()=>{stopped=true;clearTimeout(timer);});addEventListener('pageshow',event=>{if(event.persisted){stopped=false;render();}});
