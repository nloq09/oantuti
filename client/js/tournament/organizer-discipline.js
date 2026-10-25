import {node} from './player-dom.js';
import {matchExecutionState} from './match-execution.js';
import {tournamentParticipantStatus} from './tournament-api.js';
import {confirmOrganizerDecision} from './organizer-decision-dialog.js';
export function disciplinaryParticipants(t){return ['PAUSED','RECOVERY_REQUIRED'].includes(t.status)?(t.participants||[]).filter(p=>!['DISQUALIFIED','WITHDRAWN'].includes(t.participantStatuses?.[p.participantId])&&!(t.matches||[]).some(m=>(m.status==='RUNNING'||['ASSIGNED','STARTING','RUNNING_REMOTE','RESULT_PENDING','RECOVERING'].includes(matchExecutionState(m)))&&[m.participantA?.participantId,m.participantB?.participantId].includes(p.participantId))):[];}
export function renderParticipantDiscipline(root,t,refresh){
 root.replaceChildren();const participants=disciplinaryParticipants(t);if(!participants.length){root.hidden=true;return;}root.hidden=false;const section=node('details'),title=node('summary','Xử lý suất thi đấu'),select=node('select'),label=node('label','Người chơi / bot');for(const p of participants)select.append(new Option(p.displayName,p.participantId));label.append(select);section.append(title,node('p','Giải đang tạm dừng. Chỉ xử lý suất không có trận đang chạy; quyết định được lưu trong audit.'),label);
 for(const [status,text]of [['WITHDRAWN','Xử bỏ cuộc'],['DISQUALIFIED','Loại khỏi giải']]){const button=node('button',text,'danger');button.onclick=()=>{const target=select.value;confirmOrganizerDecision(text+'?', 'Các trận chưa chơi của suất này sẽ được xử lý theo luật giải. Không sửa replay đã có.',async reason=>{await tournamentParticipantStatus(t.id,target,t.revision,status,reason);await refresh();});};section.append(button);}root.append(section);
}
