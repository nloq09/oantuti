import {node} from './player-dom.js';
import {formatLabel} from './format-labels.js';

export const phaseLabels={SYNCING:'Đang đồng bộ giai đoạn',DRAFT:'Bản nháp',UPCOMING:'Sắp mở đăng ký',REGISTRATION_OPEN:'Đang đăng ký',REGISTRATION_REVIEW:'Xét duyệt đăng ký',WAITING_FOR_CODING:'Chờ lập trình',CODING_OPEN:'Lập trình',SUBMISSION_FROZEN:'Đã khóa bài',VALIDATING:'Kiểm thử chính thức',CHECK_IN_OPEN:'Check-in',READY_TO_START:'Chờ bắt đầu',RUNNING:'Đang thi đấu',PAUSED:'Tạm dừng',RECOVERY_REQUIRED:'Chờ khôi phục',FINISHED:'Đã kết thúc',CANCELLED:'Đã hủy'};
export function tournamentPhase(t,flow){
 if(!flow&&!t.timeline&&t.status==='REGISTRATION'&&t.acceptingRegistrations===false)return 'REGISTRATION_REVIEW';
 if(t.timeline&&!flow&&!['DRAFT','CANCELLED','FINISHED','RUNNING','PAUSED','RECOVERY_REQUIRED'].includes(t.status))return 'SYNCING';
 return flow?.phase||({REGISTRATION:'REGISTRATION_OPEN',CHECK_IN:'CHECK_IN_OPEN',READY:'READY_TO_START'})[t.status]||t.status;
}
export function headerModel(t,flow,options={}){
 const phase=options.phase??tournamentPhase(t,flow),player=options.audience==='player';
 const approved=player?t.approvedParticipantCount:flow?.registration?.approved;
 const count=Number.isSafeInteger(approved)&&approved>=0?approved:t.timeline?(player?null:'—'):t.participantCount??t.participants?.length??0;
 return {name:t.name,format:formatLabel(t.format),phase,phaseLabel:options.phaseLabel||phaseLabels[phase]||'Đang cập nhật',status:t.status,count,countLabel:flow?.registration||t.timeline?'người được duyệt':'người tham gia',capacity:t.maxParticipants,deadline:flow?.phaseEndsAt??null,serverNow:flow?.serverNow??null};
}
export function countdownText(deadline,serverNow,elapsedMs=0){
 const end=Date.parse(deadline),now=Date.parse(serverNow);
 if(!Number.isFinite(end)||!Number.isFinite(now))return 'Thời gian do server xác nhận';
 const seconds=Math.max(0,Math.ceil((end-now-Math.max(0,elapsedMs))/1000));
 if(!seconds)return 'Đã đến mốc giờ · Chờ server cập nhật';
 return 'Còn '+[Math.floor(seconds/3600),Math.floor(seconds/60)%60,seconds%60].map(n=>String(n).padStart(2,'0')).join(':');
}
export function renderTournamentHeader(root,t,flow,options={}){
 const player=options.audience==='player',model=headerModel(t,flow,options),header=node('header',undefined,'tournament-header'+(player?' player-tournament-header':'')),meta=node('p',undefined,'tournament-header-meta');
 header.append(node('h1',model.name));
 meta.append(node('span',model.format),node('span',player&&model.count===null?'Người được duyệt: Đang tải…':`${model.count} / ${model.capacity??'—'} ${model.countLabel}`));
 const phase=node('p',model.phaseLabel,'tournament-phase-badge');phase.dataset.phase=model.phase;
 const status=node('small','Trạng thái giải: '+(phaseLabels[model.status]||({REGISTRATION:'Đăng ký',CHECK_IN:'Check-in',READY:'Sẵn sàng'})[model.status]||model.status));
 header.append(meta,phase);if(model.phase==='FINISHED')header.className+=' compact-completed';
 if(model.deadline){if(player)header.append(node('p',milestoneLabel(model.phase).replace(/ · $/,''),'player-milestone-label'));const prefix=player?'':milestoneLabel(model.phase);const timer=node('p',prefix+countdownText(model.deadline,model.serverNow),'tournament-header-timer');timer.setAttribute('role','timer');if(player){timer.dataset.timerFormat='clock';timer.textContent=timer.textContent.replace(/^Còn /,'');}timer.dataset.timerPrefix=prefix;timer.dataset.phaseDeadline=model.deadline;timer.dataset.serverNow=model.serverNow||'';timer.dataset.receivedAt=String(performance.now());header.append(timer);}
 root.append(header);return header;
}
export function updateHeaderTimers(root,now=performance.now()){
 for(const timer of root.querySelectorAll('[data-phase-deadline]')){const value=countdownText(timer.dataset.phaseDeadline,timer.dataset.serverNow,now-Number(timer.dataset.receivedAt));timer.textContent=(timer.dataset.timerPrefix||'')+(timer.dataset.timerFormat==='clock'?value.replace(/^Còn /,''):value);}
}

export function milestoneLabel(phase){return ({UPCOMING:'Đăng ký mở sau · ',REGISTRATION_OPEN:'Đăng ký kết thúc sau · ',WAITING_FOR_CODING:'Lập trình bắt đầu sau · ',CODING_OPEN:'Lập trình kết thúc sau · ',SUBMISSION_FROZEN:'Kiểm thử bắt đầu sau · ',VALIDATING:'Check-in mở sau · ',CHECK_IN_OPEN:'Check-in kết thúc sau · ',READY_TO_START:'Giải bắt đầu sau · '})[phase]||'Mốc tiếp theo · ';}
