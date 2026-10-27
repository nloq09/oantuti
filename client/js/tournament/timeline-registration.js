import {languageText} from './player-labels.js';
import {resolvePlayerTournamentViewState} from './player-view-state.js';
import {statusBadge,phaseFacts,phaseCountdown,renderPhaseRules,renderPhaseTimeline} from './phase-view.js';
import {checkInScreen,renderTimelineCheckIn} from './timeline-check-in.js';
import {node,link,date} from './player-dom.js';
import {sessionRequest} from '../api/auth-api.js';
import {loginLink} from '../account/account-session.js';
import {errorText} from '../ui/toast.js';
import {canApply,applicationPayload,registrationPresentation,canWithdrawRegistration} from './timeline-registration-model.js';
import {renderFrozenSubmission} from './frozen-submission.js';
import {renderFinalValidation} from './final-validation-view.js';
import {playerSurface} from './player-surface.js';

export function renderTimelineRegistration(root,t,account,flow,refresh,options={}){
 const view=resolvePlayerTournamentViewState({tournament:t,flow});
 if(!options.registrationOnly){
  if(account&&['CHECK_IN_OPEN','READY_TO_START'].includes(flow.phase)&&checkInScreen(flow)){renderTimelineCheckIn(root,t,flow,refresh,options);return;}
  if(account&&view.showValidation&&view.validationView.status!=='READY'){renderFinalValidation(root,t,flow,{refresh});return;}
  if(account&&view.showValidation){renderFinalValidation(root,t,flow,{refresh});return;}
  if(account&&flow.registrationStatus==='APPROVED'&&view.showSubmission){renderFrozenSubmission(root,t,flow);return;}
 }
 const panel=options.flat?root:playerSurface(root,'timeline-registration-panel');if(!options.flat)root.append(panel);
 if(flow.phase==='DRAFT'){panel.append(node('h2','Giải chưa công bố đăng ký'));return;}
 if(flow.phase==='UPCOMING'){
  panel.append(node('h2','Đăng ký chưa mở'));
  phaseCountdown(panel,'Đăng ký mở sau',t.timeline.registrationOpenAt,flow);
  if(!options.singlePage){renderPhaseTimeline(panel,t);renderPhaseRules(panel,t);}return;
 }
 if(!account){panel.append(node('h2','Thông tin giải'),node('p','Đăng nhập để xem trạng thái và điều kiện tham gia của tài khoản.'));if(!options.primaryLogin)panel.append(link('Đăng nhập',loginLink()));if(!options.singlePage){renderPhaseTimeline(panel,t);renderPhaseRules(panel,t);}return;}
 const presentation=registrationPresentation(flow);panel.dataset.registrationStatus=presentation.status;
 panel.append(node('h2',presentation.title));if(!options.flat||!['NOT_REGISTERED','CANCELLED'].includes(flow.registrationStatus))panel.append(node('p',presentation.description,'registration-player-description'));
 if(flow.registration){panel.append(statusBadge(flow.registrationStatus));phaseFacts(panel,[['Người chơi',flow.registration.displayName],['Ngôn ngữ dự kiến',({CPP:'C++',PYTHON:'Python',JAVA:'Java'})[flow.registration.intendedLanguage]||flow.registration.intendedLanguage],['Trường/Đội',flow.registration.affiliation||'—']]);}
 if(presentation.publicMessage)panel.append(node('p','Lời nhắn từ ban tổ chức: '+presentation.publicMessage,'registration-public-message'));
 if(flow.registrationStatus==='WAITLISTED'&&Number.isSafeInteger(flow.registration?.waitlistPosition)&&flow.registration.waitlistPosition>0)panel.append(node('p','Vị trí danh sách chờ: #'+flow.registration.waitlistPosition));
 if(['REGISTRATION_OPEN','REGISTRATION_REVIEW','WAITING_FOR_CODING'].includes(flow.phase))panel.append(node('p','Lập trình bắt đầu: '+date(flow.codingOpenAt||t.timeline.codingOpenAt),'registration-coding-time'));
 if(flow.registrationStatus==='APPROVED'&&flow.phase==='CODING_OPEN'){panel.append(node('h3','Phần lập trình đã mở'));if(!options.primaryWorkspace)panel.append(link('Vào phần nộp bot','/tournament-code.html?id='+encodeURIComponent(t.id)));return;}
 if(flow.registrationStatus==='APPROVED'&&['WAITING_FOR_CODING','REGISTRATION_REVIEW'].includes(flow.phase)){panel.append(node('h3','Phần thi lập trình chưa bắt đầu'),node('p','Ngôn ngữ cho phép: '+(flow.allowedLanguages||t.botPolicy.allowedLanguages).map(languageText).join(', ')),link('Xem bot protocol','/bot-protocol.html'));if(!options.singlePage)renderPhaseRules(panel,t);}
 const applyResponse=result=>{if(!result?.registrationStatus||!result.phase)return;if(options.flat){panel.replaceChildren();renderTimelineRegistration(panel,t,account,result,refresh,options);return;}const host=node('div');renderTimelineRegistration(host,t,account,result,refresh,options);const next=host.children[0];if(next){panel.before(...host.childNodes);panel.remove();}};
 if(canWithdrawRegistration(flow)){
  const cancel=node('button','Hủy đăng ký'),notice=node('p');notice.setAttribute('role','status');cancel.onclick=()=>{if(cancel.disabled)return;
   const dialog=node('dialog'),form=node('form'),yes=node('button','Xác nhận hủy'),no=node('button','Giữ đăng ký');no.type='button';no.onclick=()=>dialog.close();form.append(node('p','Bạn muốn hủy đơn đăng ký này?'),no,yes);dialog.append(form);document.body.append(dialog);dialog.showModal();dialog.addEventListener('close',()=>dialog.remove());
   form.onsubmit=async event=>{event.preventDefault();if(yes.disabled)return;yes.disabled=true;cancel.disabled=true;panel.dataset.submitting='true';try{const result=await sessionRequest('/api/tournaments/'+encodeURIComponent(t.id)+'/participant-registration',{},'DELETE');dialog.close();applyResponse(result);await refresh();}catch(error){notice.textContent=errorText(error.message);}finally{yes.disabled=false;cancel.disabled=false;delete panel.dataset.submitting;}};
  };panel.append(cancel,notice);return;
 }
 if(!canApply(flow)&&!['NOT_REGISTERED','CANCELLED'].includes(flow.registrationStatus))return;
 const draft=options.draft||{};
 const form=node('form',undefined,'tournament-mode-form registration-application'),language=node('select'),school=node('input'),accept=node('input'),submit=node('button','Xác nhận đăng ký','primary'),notice=node('p');form.id='tournament-registration-form';notice.id='registration-submit-notice';notice.setAttribute('role','status');notice.setAttribute('aria-live','polite');school.required=Boolean(t.registrationPolicy?.requireAffiliationVerification);school.maxLength=80;school.autocomplete='organization';school.name='affiliation';language.name='intendedLanguage';accept.type='checkbox';accept.required=true;accept.name='rulesAccepted';submit.type='submit';submit.setAttribute('aria-describedby',notice.id);
 const field=(text,input)=>{const label=node('label',text);label.append(input);return label;};
 for(const value of flow.allowedLanguages||t.botPolicy.allowedLanguages)language.append(new Option(({CPP:'C++',PYTHON:'Python',JAVA:'Java'})[value],value));
 if(draft.intendedLanguage&&(flow.allowedLanguages||t.botPolicy.allowedLanguages).includes(draft.intendedLanguage))language.value=draft.intendedLanguage;
 school.value=draft.affiliation||'';accept.checked=Boolean(draft.rulesAccepted);
 language.onchange=()=>{draft.intendedLanguage=language.value;};school.oninput=()=>{draft.affiliation=school.value;};accept.onchange=()=>{draft.rulesAccepted=accept.checked;};
 const identity=node('div',undefined,'registration-identity');identity.append(node('p','Tài khoản: '+account.username),node('p','Tên hiển thị: '+(account.displayName||account.username)));
 const languageField=field('Ngôn ngữ dự kiến',language);
 if(t.botPolicy.submissionMode!=='PRE_UPLOADED_ONLY')languageField.append(node('small','Khi lập trình, bạn có thể đổi sang ngôn ngữ khác được giải cho phép.'));
 const agreement=node('div',undefined,'registration-agreement'),acceptLabel=node('label',undefined,'registration-acceptance'),rules=link('Xem luật giải','#rules');rules.className='registration-rules-link';if(options.onRules)rules.onclick=event=>{event.preventDefault();options.onRules();};acceptLabel.append(accept,node('span','Tôi đã đọc và đồng ý luật giải'));agreement.append(acceptLabel,rules);
 form.append(identity,languageField,field(t.registrationPolicy?.requireAffiliationVerification?'Trường/Đơn vị (bắt buộc xác minh)':'Trường/Đội (không bắt buộc)',school),agreement,submit,notice);panel.append(form);
 const closedReason=!canApply(flow)?'Đăng ký đã đóng hoặc chưa mở. Bạn không thể gửi đơn tại thời điểm này.':null;
 if(closedReason){submit.disabled=true;notice.textContent=closedReason;}
 form.onsubmit=async event=>{
  event.preventDefault();if(submit.disabled)return;let payload;
  try{payload=applicationPayload(language.value,school.value,accept.checked);if(school.required&&!payload.affiliation)throw new Error('MISSING_REQUIRED_FIELD');}
  catch(error){notice.textContent=errorText(error.message);return;}
  submit.disabled=true;form.dataset.submitting='true';form.setAttribute('aria-busy','true');submit.textContent='Đang đăng ký…';notice.textContent='';
  let result;
  try{result=await sessionRequest('/api/tournaments/'+encodeURIComponent(t.id)+'/participant-registration',payload);}
  catch(error){notice.textContent=errorText(error.message);if(['REGISTRATION_NOT_OPEN','ACCOUNT_NOT_ELIGIBLE','DUPLICATE_REGISTRATION'].includes(error.message)){draft.blockedReason=notice.textContent;submit.disabled=true;}else submit.disabled=false;}
  finally{delete form.dataset.submitting;form.setAttribute('aria-busy','false');submit.textContent='Xác nhận đăng ký';}
  if(result){
   // A confirmed POST must never look like a failed registration because a subsequent read failed.
   if(options.onRegistered)await options.onRegistered(result);
   else{applyResponse(result);await refresh();}
  }
 };
 if(draft.blockedReason){notice.textContent=draft.blockedReason;submit.disabled=true;}
}
