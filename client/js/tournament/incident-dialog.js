import {node} from './player-dom.js';
import {modal} from '../ui/modal.js';
import {tournamentRequest} from './tournament-api.js';
import {errorText} from '../ui/toast.js';
const reasons=[['BOT','Bot không phản hồi'],['RESULT','Kết quả bất thường'],['OTHER','Replay lỗi'],['OTHER','Hiển thị sai'],['NETWORK','Kết nối'],['OTHER','Khác']];
export function incidentReportDialog(t,items,refresh,trigger){const form=node('form'),game=node('select'),reason=node('select'),description=node('textarea'),notice=node('p'),submit=node('button','Gửi báo cáo'),cancel=node('button','Hủy');
 items=[...items].sort((a,b)=>(a.gameNumber||1)-(b.gameNumber||1));for(const item of items)game.append(new Option('Ván '+(item.gameNumber||items.indexOf(item)+1),item.matchId));for(const [i,[,label]]of reasons.entries())reason.append(new Option(label,i));
 const field=(label,input)=>{const box=node('label',label);box.append(input);return box;};description.required=true;description.maxLength=400;description.rows=4;notice.setAttribute('role','status');cancel.type='button';submit.type='submit';const actions=node('div',undefined,'actions');actions.append(cancel,submit);form.append(node('p','Trận: '+items[0].name),field('Ván đấu',game),field('Loại sự cố',reason),field('Mô tả',description),notice,actions);
 const dialog=modal('Báo cáo sự cố',form);dialog.classList.add('incident-dialog');cancel.onclick=()=>dialog.close();dialog.addEventListener('close',()=>trigger?.focus());
 form.onsubmit=async event=>{event.preventDefault();if(submit.disabled)return;submit.disabled=true;form.dataset.submitting='true';try{await tournamentRequest('/'+encodeURIComponent(t.id)+'/disputes',{matchId:game.value,reason:reasons[Number(reason.value)][0],description:description.value.trim()});dialog.close();await refresh(true);}catch(error){notice.textContent=errorText(error.message);}finally{submit.disabled=false;delete form.dataset.submitting;}};
}
