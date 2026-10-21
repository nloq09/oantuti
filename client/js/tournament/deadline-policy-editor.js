import {renderRegistrationPolicy} from './registration-policy.js';
import {timelineEditor} from './create-timeline-editor.js';
import {renderTimelineOverrides} from './timeline-overrides.js';
import {node} from './player-dom.js';
import {sessionRequest} from '../api/auth-api.js';
import {errorText} from '../ui/toast.js';
import {renderManualTiming} from './organizer-manual-timing.js';
const rendered=new WeakMap();
export function renderDeadlinePolicy(root,t,refresh){
 const key=JSON.stringify([t.id,t.revision,t.status,t.timeline,t.participantPolicy,t.participants?.length,t.registrations?.length]);if(rendered.get(root)===key)return;rendered.set(root,key);
 root.replaceChildren();renderRegistrationPolicy(root,t,refresh);const editable=(t.participantPolicy||'BOT_ONLY')==='BOT_ONLY'&&t.status==='DRAFT'&&!t.participants?.length&&!t.registrations?.length;
 root.hidden=false;if(!t.timeline)renderManualTiming(root,t,refresh);
 if(editable){const details=node('details'),title=node('summary',t.timeline?'Sửa lịch đầy đủ':'Chuyển sang giải theo timeline'),form=node('form'),box=node('div'),save=node('button','Lưu timeline'),notice=node('p');notice.setAttribute('role','status');details.append(title,form);form.append(box,notice,save);root.append(details);const editor=timelineEditor(box);if(t.timeline)editor.setValue(t.timeline);form.onsubmit=async event=>{event.preventDefault();if(save.disabled)return;let schedule;try{schedule=editor.read();}catch{notice.textContent='Kiểm tra các mốc giờ trong timeline.';return;}save.disabled=true;form.dataset.submitting='true';try{await sessionRequest('/api/tournaments/'+encodeURIComponent(t.id)+'/timeline',{revision:t.revision,timeline:schedule},'PUT');await refresh();}catch(error){notice.textContent=errorText(error.message);}finally{save.disabled=false;delete form.dataset.submitting;}};}
 if(!t.timeline)return;
 const current=t.timeline.deadlinePolicy||'AUTO_SUBMIT_LATEST_READY';
 const failures={DISQUALIFY:'Loại bài không đạt',USE_LAST_VALID_VERSION:'Thử lại bằng bản hợp lệ trước đó',ALLOW_EMERGENCY_RESUBMISSION:'Chủ giải có thể cấp lượt nộp khẩn cấp'};
 root.append(node('h3','Lịch và chính sách giải'));
 renderTimelineOverrides(root,t,refresh);if(t.status!=='DRAFT'){root.append(node('p','Chính sách đã chốt: '+(current==='AUTO_SUBMIT_LATEST_READY'?'Tự nộp bản READY mới nhất':'Bắt buộc bấm nộp trước hạn')+' · '+failures[t.timeline.validationFailurePolicy||'DISQUALIFY']));return;}
 const select=node('select'),label=node('label','Chính sách deadline'),save=node('button','Lưu chính sách'),notice=node('p');notice.setAttribute('role','status');
 const failure=node('select'),failureLabel=node('label','Xử lý bài không qua kiểm thử');for(const [value,text]of Object.entries(failures))failure.append(new Option(text,value));failure.value=t.timeline.validationFailurePolicy||'DISQUALIFY';failureLabel.append(failure);
 select.append(new Option('Tự nộp bản READY mới nhất','AUTO_SUBMIT_LATEST_READY'),new Option('Bắt buộc bấm nộp trước hạn','REQUIRE_EXPLICIT_SUBMIT'));select.value=current;label.append(select);root.append(label,failureLabel,save,notice);
 save.onclick=async()=>{save.disabled=true;try{await sessionRequest('/api/tournaments/'+encodeURIComponent(t.id)+'/timeline',{revision:t.revision,timeline:{...t.timeline,deadlinePolicy:select.value,validationFailurePolicy:failure.value}},'PUT');await refresh();}catch(error){notice.textContent=errorText(error.message);}finally{save.disabled=false;}};
}
