import {node,date} from './player-dom.js';
import {validateCreateStep} from './tournament-create-validation.js';
import {localStamp} from './create-timeline-model.js';
import {sessionRequest} from '../api/auth-api.js';
import {errorText} from '../ui/toast.js';
const fields=[['registrationDeadline','Hạn đăng ký'],['checkInDeadline','Hạn check-in'],['startTime','Giờ bắt đầu']];
const local=value=>{if(!value)return '';const d=new Date(value);return new Date(d.getTime()-d.getTimezoneOffset()*60000).toISOString().slice(0,16);};
export function renderManualTiming(root,t,refresh){
 if(t.timeline)return;
 const editable=t.status==='DRAFT'&&!t.registrations?.length,box=node('details'),title=node('summary','Lịch giải thủ công');box.append(title);root.append(box);
 if(!editable){for(const [key,label]of fields)box.append(node('p',label+': '+date(t.timing?.[key])));box.append(node('p','Lịch đã khóa sau khi mở đăng ký.'));return;}
 const form=node('form'),inputs={},save=node('button','Lưu lịch thủ công'),notice=node('p');notice.setAttribute('role','status');
 for(const [key,label]of fields){const input=node('input'),field=node('label',label);input.type='datetime-local';input.value=local(t.timing?.[key]);field.append(input);form.append(field);inputs[key]=input;}
 form.append(node('p','Các mốc có thể để trống. Chủ giải chuyển giai đoạn khi đủ điều kiện.'),notice,save);box.append(form);
 form.onsubmit=async event=>{event.preventDefault();if(save.disabled)return;const values=Object.fromEntries(fields.map(([key])=>[key,inputs[key].value]));try{validateCreateStep(3,{timing:values});}catch(error){notice.textContent=error.message;return;}save.disabled=true;form.dataset.submitting='true';try{await sessionRequest('/api/tournaments/'+encodeURIComponent(t.id)+'/timing',{revision:t.revision,...Object.fromEntries(fields.map(([key])=>[key,values[key]?localStamp(values[key]):null]))});await refresh();}catch(error){notice.textContent=errorText(error.message);}finally{save.disabled=false;delete form.dataset.submitting;}};
}
