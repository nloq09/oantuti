import {node,link,date} from './player-dom.js';
import {sessionRequest} from '../api/auth-api.js';
import {loginLink} from '../account/account-session.js';
import {errorText} from '../ui/toast.js';
import {canApply,applicationPayload,applicationLabels} from './timeline-registration-model.js';
import {renderFrozenSubmission} from './frozen-submission.js';

export function renderTimelineRegistration(root,t,account,flow,refresh){
 if(account&&flow.registrationStatus==='APPROVED'&&['FREEZE_PENDING','SUBMISSION_MISSING','SUBMISSION_FROZEN'].includes(flow.submissionStatus)){renderFrozenSubmission(root,t,flow);return;}
 const panel=node('section',undefined,'panel timeline-registration-panel');root.append(panel);
 if(flow.phase==='DRAFT'){panel.append(node('h2','Giải chưa công bố đăng ký'));return;}
 if(flow.phase==='UPCOMING'){
  panel.append(node('h2','Đăng ký chưa mở'));
  for(const [label,key]of [['Đăng ký mở','registrationOpenAt'],['Đăng ký đóng','registrationCloseAt'],['Code bắt đầu','codingOpenAt'],['Code kết thúc','codingCloseAt'],['Check-in','checkInOpenAt'],['Giải bắt đầu','tournamentStartAt']])panel.append(node('p',label+': '+date(t.timeline[key])));
  const rules=node('details');rules.append(node('summary','Xem luật'),node('p',`Bàn cờ 9 × 9 · ${t.format} · BO${t.bestOf||1}. Ngôn ngữ cho phép: ${t.botPolicy.allowedLanguages.join(', ')}.`));panel.append(rules);return;
 }
 if(!account){panel.append(node('h2','Đăng ký tham gia'),link('Đăng nhập để đăng ký',loginLink()));return;}
 panel.append(node('h2',applicationLabels[flow.registrationStatus]||'Đăng ký tham gia'));
 if(flow.registration){panel.append(node('p',flow.registration.displayName+' · '+flow.registration.intendedLanguage));if(flow.registration.affiliation)panel.append(node('p','Trường/Đội: '+flow.registration.affiliation));}
 panel.append(node('p','Phần lập trình bắt đầu: '+date(flow.codingOpenAt||t.timeline.codingOpenAt)));
 const countdown=node('p');countdown.dataset.codingStarts=flow.codingOpenAt||t.timeline.codingOpenAt;panel.append(countdown);
 if(flow.registration?.reviewReason)panel.append(node('p','Lý do xét duyệt: '+flow.registration.reviewReason));if(flow.phase!=='REGISTRATION_OPEN'){if(flow.registrationStatus==='APPROVED'&&flow.phase==='CODING_OPEN'){panel.append(node('h3','Phần lập trình đã mở'),link('Vào phần nộp bot','/tournament-code.html?id='+encodeURIComponent(t.id)));return;}if(flow.registrationStatus==='APPROVED'&&['WAITING_FOR_CODING','REGISTRATION_REVIEW'].includes(flow.phase)){panel.append(node('h3','Phần thi lập trình chưa bắt đầu'),node('p','Ngôn ngữ cho phép: '+(flow.allowedLanguages||t.botPolicy.allowedLanguages).join(', ')),link('Xem bot protocol','/bot-protocol.html'));const rules=node('details');rules.append(node('summary','Xem luật'),node('p',t.format+' · BO'+(t.bestOf||1)+' · Thắng 3, hòa 1, thua 0 điểm.'));panel.append(rules);}else panel.append(node('p',flow.registrationStatus==='APPROVED'?'Bạn đã được chấp nhận. Theo dõi giai đoạn tiếp theo của giải.':flow.registrationStatus==='PENDING'?'Đăng ký đã đóng. Đang chờ xác nhận danh sách.':flow.registrationStatus==='WAITLISTED'?'Bạn đang trong danh sách chờ. Chủ giải sẽ xác nhận trước giờ lập trình.':flow.registrationStatus==='REJECTED'?'Bạn chưa được phép tham gia phần lập trình.':'Đăng ký đã đóng. Bạn chưa có suất tham gia giải.'));return;}
 if(['PENDING','APPROVED','WAITLISTED'].includes(flow.registrationStatus)){
  const cancel=node('button','Hủy đăng ký'),notice=node('p');notice.setAttribute('role','status');cancel.onclick=()=>{
   const dialog=node('dialog'),form=node('form'),yes=node('button','Xác nhận hủy'),no=node('button','Giữ đăng ký');no.type='button';no.onclick=()=>dialog.close();form.append(node('p','Bạn muốn hủy đơn đăng ký này?'),no,yes);dialog.append(form);document.body.append(dialog);dialog.showModal();dialog.onclose=()=>dialog.remove();
   form.onsubmit=async event=>{event.preventDefault();yes.disabled=true;try{await sessionRequest('/api/tournaments/'+encodeURIComponent(t.id)+'/participant-registration',{},'DELETE');dialog.close();refresh();}catch(error){notice.textContent=errorText(error.message);yes.disabled=false;}};
  };panel.append(cancel,notice);return;
 }
 if(!canApply(flow))return;
 const rules=node('details');rules.append(node('summary','Xem luật giải'),node('p',`${t.format} · BO${t.bestOf||1} · Thắng 3, hòa 1, thua 0 điểm. Tie-break: ${(t.tieBreakOrder||[]).join(' → ')}.`),node('p','Chỉ đăng ký người chơi ở giai đoạn này. Bot phải tuân thủ giao thức và nước đi hợp lệ; kết quả do server quyết định. Luật thi đấu được khóa khi chủ giải chuẩn bị giải.'));
 for(const [key,value]of Object.entries(t.rulesSnapshot||{}))rules.append(node('p',key+': '+(typeof value==='object'?JSON.stringify(value):String(value))));panel.append(rules);
 const form=node('form',undefined,'tournament-mode-form'),language=node('select'),school=node('input'),accept=node('input'),submit=node('button','Gửi đăng ký'),notice=node('p');notice.setAttribute('role','status');school.maxLength=80;school.autocomplete='organization';accept.type='checkbox';accept.required=true;
 const field=(text,input)=>{const label=node('label',text);label.append(input);return label;};
 for(const value of flow.allowedLanguages||t.botPolicy.allowedLanguages)language.append(new Option(({CPP:'C++',PYTHON:'Python',JAVA:'Java'})[value],value));
 form.append(node('p','Tài khoản: '+account.username),node('p','Đăng ký người chơi trước. Bạn sẽ chọn bot hoặc mở môi trường code khi đến giờ làm bài.'),field('Ngôn ngữ dự kiến',language),field('Trường/Đội (không bắt buộc)',school),field('Tôi đã đọc và đồng ý luật giải',accept),notice,submit);panel.append(form);
 form.onsubmit=async event=>{event.preventDefault();if(submit.disabled)return;submit.disabled=true;form.dataset.submitting='true';try{await sessionRequest('/api/tournaments/'+encodeURIComponent(t.id)+'/participant-registration',applicationPayload(language.value,school.value,accept.checked));delete form.dataset.submitting;await refresh();}catch(error){notice.textContent=errorText(error.message);}finally{submit.disabled=false;delete form.dataset.submitting;}};
}
