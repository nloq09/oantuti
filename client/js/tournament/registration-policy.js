import {node} from './player-dom.js';
import {sessionRequest} from '../api/auth-api.js';
import {errorText} from '../ui/toast.js';

export const registrationPolicyLabels={AUTO_APPROVE:'Tự động duyệt',MANUAL_REVIEW:'Duyệt thủ công',HYBRID:'Tự động + xử lý ngoại lệ'};
export const eligibilityReasonLabels={MANUAL_REVIEW_REQUIRED:'Giải yêu cầu ban tổ chức xác nhận',AFFILIATION_NOT_VERIFIED:'Đơn vị tự khai cần được xác minh',MISSING_REQUIRED_FIELD:'Thiếu thông tin bắt buộc',LANGUAGE_NOT_ALLOWED:'Ngôn ngữ không được phép',ACCOUNT_NOT_ELIGIBLE:'Tài khoản không đủ điều kiện',REGISTRATION_CLOSED:'Đã hết thời gian đăng ký',DUPLICATE_REGISTRATION:'Đã có đơn đăng ký',CAPACITY_FULL:'Đã đủ số chỗ',TEAM_INVALID:'Đội chưa hợp lệ'};

export function registrationPolicyEditor(root,policy={approvalPolicy:'HYBRID',requireAffiliationVerification:false}){
 const box=node('fieldset'),mode=node('select'),verify=node('input');verify.type='checkbox';verify.checked=Boolean(policy.requireAffiliationVerification);
 mode.setAttribute('aria-label','Chính sách duyệt đăng ký');
 for(const [value,text]of Object.entries(registrationPolicyLabels)){const option=node('option',text);option.value=value;mode.append(option);}mode.value=policy.approvalPolicy;
 const label=node('label','Cách duyệt đơn');label.append(mode);const verification=node('label','Yêu cầu xác minh trường/đơn vị');verification.prepend(verify);
 box.append(node('legend','Chính sách đăng ký người chơi'),label,verification,node('p','Danh sách chờ: đăng ký trước được xếp trước (FCFS).'),node('p','Đơn hợp lệ được duyệt theo chính sách. Nếu yêu cầu xác minh đơn vị, ban tổ chức phải xác nhận thông tin tự khai.'));root.append(box);
 return {box,read:()=>({approvalPolicy:mode.value,requireAffiliationVerification:verify.checked}),setEnabled:enabled=>{box.hidden=!enabled;box.disabled=!enabled;}};
}

export function renderRegistrationPolicy(root,t,refresh){
 if(!t.timeline)return;
 const section=node('section'),policy=t.registrationPolicy||{approvalPolicy:'MANUAL_REVIEW',requireAffiliationVerification:false};
 section.append(node('h3','Chính sách đăng ký'),node('p',registrationPolicyLabels[policy.approvalPolicy]||'Duyệt thủ công'));root.append(section);
 if(t.status!=='DRAFT'){section.append(node('p','Chính sách được cố định sau khi mở đăng ký.'));return;}
 const form=node('form'),editor=registrationPolicyEditor(form,policy),reason=node('input'),label=node('label','Lý do thay đổi (bắt buộc)'),save=node('button','Lưu chính sách đăng ký'),notice=node('p');reason.required=true;reason.maxLength=500;label.append(reason);notice.setAttribute('role','status');form.append(label,notice,save);section.append(form);
 form.onsubmit=async event=>{event.preventDefault();if(save.disabled)return;save.disabled=true;try{await sessionRequest('/api/tournaments/'+encodeURIComponent(t.id)+'/registration-policy',{revision:t.revision,policy:editor.read(),reason:reason.value},'PUT');await refresh();}catch(error){notice.textContent=errorText(error.message);}finally{save.disabled=false;}};
}
