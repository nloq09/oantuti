import {node} from './player-dom.js';
import {formatLabel} from './bracket-renderer.js';

export const phaseLabels={DRAFT:'Bản nháp',UPCOMING:'Sắp mở đăng ký',REGISTRATION_OPEN:'Đang đăng ký',REGISTRATION_REVIEW:'Xét duyệt đăng ký',WAITING_FOR_CODING:'Chờ lập trình',CODING_OPEN:'Lập trình',SUBMISSION_FROZEN:'Đã khóa bài',VALIDATING:'Kiểm thử chính thức',CHECK_IN_OPEN:'Check-in',READY_TO_START:'Chờ bắt đầu',RUNNING:'Đang thi đấu',PAUSED:'Tạm dừng',RECOVERY_REQUIRED:'Chờ khôi phục',FINISHED:'Đã kết thúc',CANCELLED:'Đã hủy'};
export function tournamentPhase(t,flow){
 if(!flow&&!t.timeline&&t.status==='REGISTRATION'&&t.acceptingRegistrations===false)return 'REGISTRATION_REVIEW';
 return flow?.phase||({REGISTRATION:'REGISTRATION_OPEN',CHECK_IN:'CHECK_IN_OPEN',READY:'READY_TO_START'})[t.status]||t.status;
}
export function headerModel(t,flow){
 const phase=tournamentPhase(t,flow);
 return {name:t.name,format:formatLabel(t.format),phase,phaseLabel:phaseLabels[phase]||'Đang cập nhật',status:t.status,count:t.participants?.length??0,capacity:t.maxParticipants,deadline:flow?.phaseEndsAt??null,serverNow:flow?.serverNow??null};
}
export function countdownText(deadline,serverNow,elapsedMs=0){
 const end=Date.parse(deadline),now=Date.parse(serverNow);
 if(!Number.isFinite(end)||!Number.isFinite(now))return 'Thời gian do server xác nhận';
 const seconds=Math.max(0,Math.ceil((end-now-Math.max(0,elapsedMs))/1000));
 if(!seconds)return 'Đã đến mốc giờ · Chờ server cập nhật';
 return 'Còn '+[Math.floor(seconds/3600),Math.floor(seconds/60)%60,seconds%60].map(n=>String(n).padStart(2,'0')).join(':');
}
export function renderTournamentHeader(root,t,flow){
 const model=headerModel(t,flow),header=node('header',undefined,'tournament-header'),meta=node('p',undefined,'tournament-header-meta');
 header.append(node('h1',model.name));
 meta.append(node('span',model.format),node('span',`${model.count} / ${model.capacity??'—'} suất thi đấu`));
 const phase=node('p',model.phaseLabel,'tournament-phase-badge');phase.dataset.phase=model.phase;
 const status=node('small','Trạng thái giải: '+(phaseLabels[model.status]||({REGISTRATION:'Đăng ký',CHECK_IN:'Check-in',READY:'Sẵn sàng'})[model.status]||model.status));
 header.append(meta,phase,status);
 if(model.deadline){const timer=node('p',countdownText(model.deadline,model.serverNow),'tournament-header-timer');timer.setAttribute('role','timer');timer.dataset.phaseDeadline=model.deadline;timer.dataset.serverNow=model.serverNow||'';timer.dataset.receivedAt=String(performance.now());header.append(timer);}
 root.append(header);return header;
}
export function updateHeaderTimers(root,now=performance.now()){
 for(const timer of root.querySelectorAll('[data-phase-deadline]'))timer.textContent=countdownText(timer.dataset.phaseDeadline,timer.dataset.serverNow,now-Number(timer.dataset.receivedAt));
}
