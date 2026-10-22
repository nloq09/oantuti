import {node,date} from './player-dom.js';
import {sessionRequest} from '../api/auth-api.js';
import {eligibilityReasonLabels,registrationPolicyLabels} from './registration-policy.js';
import {applicationLabels} from './timeline-registration-model.js';
import {errorText} from '../ui/toast.js';

export function openApplicationDetails(base,entry,{request=sessionRequest,onClose=()=>{}}={}){
 const dialog=node('dialog',undefined,'organizer-decision-dialog application-details-dialog'),close=node('button','Đóng chi tiết'),body=node('div'),abort=new AbortController();
 close.type='button';close.onclick=()=>dialog.close();dialog.setAttribute('aria-label','Chi tiết đơn đăng ký');
 dialog.append(node('h2','Chi tiết đơn đăng ký'),close,body);body.textContent='Đang tải…';
 dialog.addEventListener('close',()=>{abort.abort();dialog.remove();onClose();});document.body.append(dialog);dialog.showModal();close.focus();
 const loaded=request(base+'/participant-applications/'+encodeURIComponent(entry.id),undefined,undefined,{signal:abort.signal}).then(data=>{
  if(abort.signal.aborted)return;
  const {details,policy}=data,application=details.application;
  body.replaceChildren(node('h3',application.displayName),node('p','Tài khoản: '+details.username),node('p',applicationLabels[application.status]||application.status),node('p','Trường / Đơn vị: '+(application.affiliation||'—')),node('p','Ngôn ngữ: '+application.intendedLanguage),node('p','Gửi đơn: '+date(application.registeredAt)),node('p','Chấp nhận luật: '+date(details.rulesAcceptedAt)));
  body.append(node('h3','Kiểm tra điều kiện'),node('p',details.accountActive?'Tài khoản đang hoạt động':'Tài khoản không còn hoạt động'),node('p',data.languageAllowed?'Ngôn ngữ nằm trong danh sách cho phép':'Ngôn ngữ không được phép'),node('p','Chính sách: '+registrationPolicyLabels[policy.approvalPolicy]),node('p',policy.requireAffiliationVerification?'Cần ban tổ chức xác minh đơn vị tự khai':'Không yêu cầu xác minh đơn vị'));
  for(const code of application.reasonCodes||[])body.append(node('p',(eligibilityReasonLabels[code]||'Cần kiểm tra')+' · '+code));
  if(application.waitlistPosition)body.append(node('p','Vị trí chờ: #'+application.waitlistPosition));
  if(application.reviewReason)body.append(node('p','Quyết định gần nhất: '+application.reviewReason),node('p','Xét duyệt: '+date(application.reviewedAt)));
  body.append(node('h3','Lịch sử xét duyệt'));
  if(!data.history.length)body.append(node('p','Chưa có quyết định xét duyệt.'));
  const list=node('ol');for(const event of data.history){const row=node('li');row.append(node('p',date(event.at)+' · '+(applicationLabels[event.beforeValue]||event.beforeValue||'—')+' → '+(applicationLabels[event.afterValue]||event.afterValue||'—')),node('p',event.reason||'Tự động theo chính sách'),node('small','Người xử lý: '+(event.actor||'Hệ thống')));list.append(row);}body.append(list);
  if(data.moreHistory)body.append(node('p','Hiển thị 100 quyết định mới nhất. Xem Nhật ký quản lý để tra cứu đầy đủ.'));
  if(!data.canReview)body.append(node('p','Xét duyệt đã khóa. Chi tiết chỉ để tra cứu.'));
 }).catch(error=>{if(!abort.signal.aborted)body.replaceChildren(node('p',errorText(error.message)));});
 return {dialog,loaded};
}
