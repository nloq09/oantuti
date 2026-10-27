import {node,date} from './admin-ui.js';
import {issueWorkerToken} from './worker-api.js';
import {field,input,copy,showError} from './worker-ops-controls.js';

export function mountWorkerRegistration(root){
 const form=node('form',undefined,'admin-form worker-registration-form'),advanced=node('details'),uses=input('number',1),minutes=input('number',30),submit=node('button','Tạo token đăng ký'),notice=node('p'),tokenBox=node('div',undefined,'worker-registration-secret'),secret=input('password'),copyToken=node('button','Sao chép token'),toggle=node('button','Hiện token'),forget=node('button','Ẩn và xóa token khỏi trang'),expiry=node('p');
 uses.min=minutes.min=1;uses.max=100;minutes.max=1440;uses.required=minutes.required=true;submit.type='submit';
 notice.setAttribute('role','status');notice.setAttribute('aria-live','polite');
 secret.readOnly=true;secret.autocomplete='off';secret.spellcheck=false;secret.setAttribute('aria-label','Token đăng ký vừa tạo');
 tokenBox.hidden=true;copyToken.type=toggle.type=forget.type='button';
 advanced.append(node('summary','Tùy chọn token'),field('Số máy được phép đăng ký',uses),field('Hết hạn sau (phút)',minutes),node('small','Mặc định một máy, có hiệu lực 30 phút. Chỉ tạo sau khi máy đã cài xong Java, Docker và tải image.'));
 form.append(advanced,submit);
 const actions=node('div',undefined,'worker-row-actions');actions.append(copyToken,toggle,forget);
 tokenBox.append(node('p','Token chỉ hiển thị trong lần tạo này. Sao chép rồi nhập vào lời nhắc ở máy worker; không gửi tài khoản admin cho người vận hành máy.'),field('Token đăng ký',secret),actions,expiry);
 root.append(form,notice,tokenBox);
 let current=null,generation=0,disposed=false;
 function erase(){if(current)current.registrationToken='';current=null;secret.value='';secret.type='password';toggle.textContent='Hiện token';tokenBox.hidden=true;}
 copyToken.onclick=()=>copy(secret.value,notice);
 toggle.onclick=()=>{secret.type=secret.type==='password'?'text':'password';toggle.textContent=secret.type==='password'?'Hiện token':'Ẩn token';};
 forget.onclick=()=>{erase();form.hidden=false;notice.textContent='Đã xóa token khỏi trang. Có thể tạo token mới nếu cần. Token cũ vẫn theo thời hạn/lượt dùng trên server; thu hồi tại tab Token đăng ký nếu không dùng nữa.';};
 form.onsubmit=async event=>{
  event.preventDefault();if(disposed||submit.disabled||form.hidden)return;
  const count=Number(uses.value),lifetime=Number(minutes.value);
  if(!Number.isInteger(count)||count<1||count>100||!Number.isInteger(lifetime)||lifetime<1||lifetime>1440){notice.textContent='Số máy từ 1–100; thời hạn từ 1–1440 phút.';return;}
  const request=++generation;submit.disabled=true;notice.textContent='Đang tạo token…';
  try{
   const result=await issueWorkerToken(count,lifetime);
   if(disposed||request!==generation){result.registrationToken='';return;}
   erase();current=result;secret.value=result.registrationToken;tokenBox.hidden=false;
   expiry.textContent='Hết hạn: '+date(result.expiresAt)+' · Tối đa '+count+' máy.';
   form.hidden=true;notice.textContent='Đã tạo token. Sao chép token, rồi chạy lệnh lần đầu ngay bên dưới.';
  }catch(error){if(!disposed&&request===generation)showError(error,notice);}
  finally{if(!disposed&&request===generation)submit.disabled=false;}
 };
 function hide(event){generation++;erase();form.hidden=false;submit.disabled=false;notice.textContent='';if(!event.persisted)dispose();}
 function dispose(){if(disposed)return;disposed=true;generation++;erase();removeEventListener('pagehide',hide);}
 addEventListener('pagehide',hide);
 return{dispose};
}
