import {node} from './player-dom.js';
import {sessionRequest} from '../api/auth-api.js';
import {errorText} from '../ui/toast.js';
export function renderDeadlinePolicy(root,t,refresh){
 root.replaceChildren();root.hidden=!t.timeline;if(!t.timeline)return;
 const current=t.timeline.deadlinePolicy||'AUTO_SUBMIT_LATEST_READY';
 root.append(node('h3','Khi hết giờ lập trình'),node('p',current==='AUTO_SUBMIT_LATEST_READY'?'Tự nộp phiên bản READY mới nhất trước hạn.':'Chỉ giữ bài người chơi đã bấm nộp trước hạn.'),node('p','Bài chưa READY trước hạn không được tự nộp. Hết giờ luôn khóa chỉnh sửa.'));
 if(t.status!=='DRAFT')return;
 const select=node('select'),label=node('label','Chính sách deadline'),save=node('button','Lưu chính sách'),notice=node('p');notice.setAttribute('role','status');
 select.append(new Option('Tự nộp bản READY mới nhất','AUTO_SUBMIT_LATEST_READY'),new Option('Bắt buộc bấm nộp trước hạn','REQUIRE_EXPLICIT_SUBMIT'));select.value=current;label.append(select);root.append(label,save,notice);
 save.onclick=async()=>{save.disabled=true;try{await sessionRequest('/api/tournaments/'+encodeURIComponent(t.id)+'/timeline',{revision:t.revision,timeline:{...t.timeline,deadlinePolicy:select.value}},'PUT');await refresh();}catch(error){notice.textContent=errorText(error.message);}finally{save.disabled=false;}};
}
