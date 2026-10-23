import {node} from './player-dom.js';
import {tournamentRequest} from './tournament-api.js';
import {errorText} from '../ui/toast.js';

export const canWithdraw=(t,item)=>['READY','RUNNING','PAUSED','RECOVERY_REQUIRED'].includes(t.status)&&item.participantId&&!['WITHDRAWN','DISQUALIFIED'].includes(item.status);
export function withdrawalControl(root,t,item,reload){
 if(!canWithdraw(t,item))return;
 const button=node('button','Rút khỏi giải');button.type='button';root.append(button);
 button.onclick=()=>{
  const dialog=node('dialog'),form=node('form'),label=node('label','Tôi hiểu việc rút không thể hoàn tác và có thể khiến đội của tôi mất điểm.'),check=node('input'),confirm=node('button','Xác nhận rút khỏi giải'),cancel=node('button','Ở lại giải'),message=node('p');
  check.type='checkbox';check.required=true;label.prepend(check);cancel.type='button';cancel.onclick=()=>dialog.close();message.setAttribute('role','status');
  form.append(node('h2','Rút khỏi giải?'),node('p','Ván đang chơi sẽ được xử lý đầu hàng. Các trận chưa chơi sẽ bị xử thua; kết quả và replay đã hoàn tất được giữ lại.'),label,message,cancel,confirm);dialog.append(form);document.body.append(dialog);dialog.showModal();
  form.onsubmit=async e=>{e.preventDefault();if(confirm.disabled)return;confirm.disabled=true;form.dataset.submitting='true';try{await tournamentRequest('/'+encodeURIComponent(t.id)+'/withdraw',{revision:t.revision,participantId:item.participantId,confirmed:check.checked});dialog.close();await reload(true);}catch(error){message.textContent=errorText(error.message);}finally{delete form.dataset.submitting;confirm.disabled=false;}};
  dialog.onclose=()=>dialog.remove();
 };
}

export function renderWithdrawalActions(root,t,me,reload){
 const entries=(me?.states||[]).filter(item=>canWithdraw(t,item));
 for(const item of entries){const group=node('div',undefined,'actions');if(entries.length>1)group.append(node('span',item.registration.displayName));withdrawalControl(group,t,item,reload);root.append(group);}
}
