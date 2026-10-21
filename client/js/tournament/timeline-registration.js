import {statusBadge,phaseFacts,phaseCountdown,renderPhaseRules,renderPhaseTimeline} from './phase-view.js';
import {checkInScreen,renderTimelineCheckIn} from './timeline-check-in.js';
import {node,link,date} from './player-dom.js';
import {sessionRequest} from '../api/auth-api.js';
import {loginLink} from '../account/account-session.js';
import {errorText} from '../ui/toast.js';
import {canApply,applicationPayload,applicationLabels} from './timeline-registration-model.js';
import {renderFrozenSubmission} from './frozen-submission.js';
import {renderFinalValidation} from './final-validation-view.js';

export function renderTimelineRegistration(root,t,account,flow,refresh,options={}){
 if(account&&checkInScreen(flow)){renderTimelineCheckIn(root,t,flow,refresh,options);return;}
 if(account&&flow.finalValidation){renderFinalValidation(root,t,flow);return;}
 if(account&&flow.registrationStatus==='APPROVED'&&['FREEZE_PENDING','SUBMISSION_MISSING','SUBMISSION_FROZEN'].includes(flow.submissionStatus)){renderFrozenSubmission(root,t,flow);return;}
 const panel=node('section',undefined,'panel timeline-registration-panel');root.append(panel);
 if(flow.phase==='DRAFT'){panel.append(node('h2','Giải chưa công bố đăng ký'));return;}
 if(flow.phase==='UPCOMING'){
  panel.append(node('h2','Đăng ký chưa mở'));
  phaseCountdown(panel,'Đăng ký mở sau',t.timeline.registrationOpenAt,flow);
  renderPhaseTimeline(panel,t);renderPhaseRules(panel,t);return;
 }
 if(!account){panel.append(node('h2','Thông tin giải'),node('p','Đăng nhập để xem trạng thái và điều kiện tham gia của tài khoản.'));if(!options.primaryLogin)panel.append(link('Đăng nhập',loginLink()));renderPhaseTimeline(panel,t);renderPhaseRules(panel,t);return;}
 panel.append(node('h2',applicationLabels[flow.registrationStatus]||'Đăng ký tham gia'));
 if(flow.registration){panel.append(statusBadge(flow.registrationStatus));phaseFacts(panel,[['Người chơi',flow.registration.displayName],['Ngôn ngữ dự kiến',flow.registration.intendedLanguage],['Trường/Đội',flow.registration.affiliation||'—']]);}
 if(flow.registrationStatus==='WAITLISTED'&&Number.isSafeInteger(flow.registration?.waitlistPosition)&&flow.registration.waitlistPosition>0)panel.append(node('p','Vị trí danh sách chờ: #'+flow.registration.waitlistPosition));
 if(['REGISTRATION_OPEN','REGISTRATION_REVIEW','WAITING_FOR_CODING'].includes(flow.phase))phaseCountdown(panel,'Phần lập trình bắt đầu: '+date(flow.codingOpenAt||t.timeline.codingOpenAt),flow.codingOpenAt||t.timeline.codingOpenAt,flow,'codingStarts');
 if(flow.registration?.reviewReason)panel.append(node('p','Lý do xét duyệt: '+flow.registration.reviewReason));if(flow.phase!=='REGISTRATION_OPEN'){if(flow.registrationStatus==='APPROVED'&&flow.phase==='CODING_OPEN'){panel.append(node('h3','Phần lập trình đã mở'));if(!options.primaryWorkspace)panel.append(link('Vào phần nộp bot','/tournament-code.html?id='+encodeURIComponent(t.id)));return;}if(flow.registrationStatus==='APPROVED'&&['WAITING_FOR_CODING','REGISTRATION_REVIEW'].includes(flow.phase)){panel.append(node('h3','Phần thi lập trình chưa bắt đầu'),node('p','Ngôn ngữ cho phép: '+(flow.allowedLanguages||t.botPolicy.allowedLanguages).join(', ')),link('Xem bot protocol','/bot-protocol.html'));renderPhaseRules(panel,t);}else panel.append(node('p',flow.registrationStatus==='APPROVED'?'Bạn đã được chấp nhận. Theo dõi giai đoạn tiếp theo của giải.':['PENDING','NEEDS_REVIEW'].includes(flow.registrationStatus)?'Đăng ký đã đóng. Đang chờ xác nhận danh sách.':flow.registrationStatus==='WAITLISTED'?'Bạn đang trong danh sách chờ. Chủ giải sẽ xác nhận trước giờ lập trình.':flow.registrationStatus==='REJECTED'?'Bạn chưa được phép tham gia phần lập trình.':'Đăng ký đã đóng. Bạn chưa có suất tham gia giải.'));return;}
 if(['PENDING','NEEDS_REVIEW','APPROVED','WAITLISTED'].includes(flow.registrationStatus)){
  const cancel=node('button','Hủy đăng ký'),notice=node('p');notice.setAttribute('role','status');cancel.onclick=()=>{
   const dialog=node('dialog'),form=node('form'),yes=node('button','Xác nhận hủy'),no=node('button','Giữ đăng ký');no.type='button';no.onclick=()=>dialog.close();form.append(node('p','Bạn muốn hủy đơn đăng ký này?'),no,yes);dialog.append(form);document.body.append(dialog);dialog.showModal();dialog.onclose=()=>dialog.remove();
   form.onsubmit=async event=>{event.preventDefault();yes.disabled=true;try{await sessionRequest('/api/tournaments/'+encodeURIComponent(t.id)+'/participant-registration',{},'DELETE');dialog.close();refresh();}catch(error){notice.textContent=errorText(error.message);yes.disabled=false;}};
  };panel.append(cancel,notice);return;
 }
 if(!canApply(flow))return;
 renderPhaseRules(panel,t);
 const form=node('form',undefined,'tournament-mode-form'),language=node('select'),school=node('input'),accept=node('input'),submit=node('button','Gửi đăng ký'),notice=node('p');form.id='tournament-registration-form';notice.setAttribute('role','status');school.required=Boolean(t.registrationPolicy?.requireAffiliationVerification);school.maxLength=80;school.autocomplete='organization';accept.type='checkbox';accept.required=true;
 const field=(text,input)=>{const label=node('label',text);label.append(input);return label;};
 for(const value of flow.allowedLanguages||t.botPolicy.allowedLanguages)language.append(new Option(({CPP:'C++',PYTHON:'Python',JAVA:'Java'})[value],value));
 form.append(node('p','Tài khoản: '+account.username),node('p','Tên hiển thị: '+(account.displayName||account.username)),node('p','Đăng ký người chơi trước. Bạn sẽ chọn bot hoặc mở môi trường code khi đến giờ làm bài.'),field('Ngôn ngữ dự kiến',language),field(t.registrationPolicy?.requireAffiliationVerification?'Trường/Đơn vị (bắt buộc xác minh)':'Trường/Đội (không bắt buộc)',school),field('Tôi đã đọc và đồng ý luật giải',accept),notice,submit);panel.append(form);
 form.onsubmit=async event=>{event.preventDefault();if(submit.disabled)return;submit.disabled=true;form.dataset.submitting='true';try{await sessionRequest('/api/tournaments/'+encodeURIComponent(t.id)+'/participant-registration',applicationPayload(language.value,school.value,accept.checked));delete form.dataset.submitting;await refresh();}catch(error){notice.textContent=errorText(error.message);}finally{submit.disabled=false;delete form.dataset.submitting;}};
}
