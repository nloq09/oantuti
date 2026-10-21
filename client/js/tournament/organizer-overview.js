import {node,link,date} from './player-dom.js';
import {phaseLabels,tournamentPhase} from './tournament-header.js';
import {organizerNextAction} from './organizer-actions.js';
import {renderOrganizerMetrics} from './organizer-metrics.js';
import {renderTournamentAttentionPanel} from './organizer-attention.js';

const activityLabels={TOURNAMENT_CREATED:'Đã tạo giải',TIMELINE_CONFIGURED:'Đã cập nhật lịch giải',REGISTRATION_OPENED:'Đã mở đăng ký',PARTICIPANT_APPLICATION_SUBMITTED:'Đã nhận đơn đăng ký',PARTICIPANT_APPLICATION_REVIEWED:'Đã xét duyệt đăng ký',PARTICIPANT_APPROVED:'Đã duyệt người tham gia',CODING_FREEZE_APPLIED:'Đã khóa bài',FINAL_VALIDATION_QUEUED:'Đã đưa bài vào kiểm thử',FINAL_VALIDATION_PASSED:'Bot đã đạt kiểm thử',FINAL_VALIDATION_FAILED:'Bot chưa đạt kiểm thử',CHECK_IN:'Người chơi đã check-in',CHECK_IN_OPENED:'Đã mở check-in',TOURNAMENT_READY:'Giải đã sẵn sàng',TOURNAMENT_STARTED:'Giải bắt đầu',TOURNAMENT_PAUSED:'Giải tạm dừng',TOURNAMENT_RESUMED:'Giải tiếp tục',ROUND_RELEASED:'Đã mở vòng tiếp',MATCH_STARTED:'Trận đấu bắt đầu',MATCH_FINISHED:'Trận đấu hoàn tất',TOURNAMENT_FINISHED:'Giải đã hoàn tất',TOURNAMENT_CANCELLED:'Giải đã hủy',MATCH_RESULT_OVERRIDDEN:'Đã có quyết định sửa kết quả'};
export function recentOrganizerActivity(t){return [...(t.audit||[])].reverse().slice(0,8).map(event=>({at:event.at,label:activityLabels[event.type]||'Đã cập nhật giải đấu',reason:event.reason}));}
export function renderOrganizerOverview(root,t,data,{stale=false,onCommand}={}){
 root.replaceChildren();const valid=data?.tournamentId===t.id&&data?.revision===t.revision,snapshot=valid?data:null,phase=tournamentPhase(t,snapshot),top=node('div',undefined,'organizer-overview-top');
 const current=node('section',undefined,'control-card current-phase');current.setAttribute('aria-label','Giai đoạn hiện tại');current.append(node('p','GIAI ĐOẠN HIỆN TẠI','control-kicker'),node('h2',t.timeline&&!snapshot?'Đang đồng bộ':phaseLabels[phase]||'Đang cập nhật'));
 if(snapshot){const group=phase==='CODING_OPEN'?snapshot.coding:['SUBMISSION_FROZEN','VALIDATING'].includes(phase)?snapshot.validation:['CHECK_IN_OPEN','READY_TO_START'].includes(phase)?snapshot.checkIn:['RUNNING','PAUSED','RECOVERY_REQUIRED','FINISHED'].includes(phase)?snapshot.running:snapshot.registration;
  const text=phase==='CODING_OPEN'?`${group?.submitted??'—'} bài đã nộp · ${group?.ready??'—'} người có bản READY`:['SUBMISSION_FROZEN','VALIDATING'].includes(phase)?`${group?.total??'—'} bài đã khóa · ${group?.passed??'—'} đạt · ${group?.running??'—'} đang kiểm thử`:['CHECK_IN_OPEN','READY_TO_START'].includes(phase)?`${group?.checkedIn??'—'} / ${group?.eligible??'—'} suất đã check-in`:['RUNNING','PAUSED','RECOVERY_REQUIRED','FINISHED'].includes(phase)?`${group?.completed??'—'} / ${group?.total??'—'} trận hoàn tất`:`${group?.registered??'—'} đơn · ${group?.pending??'—'} chờ duyệt`;
  current.append(node('p',text));if(snapshot.phaseEndsAt)current.append(node('p','Mốc tiếp theo: '+date(snapshot.phaseEndsAt)));
 }else current.append(node('p',stale?'Chưa cập nhật được trạng thái. Hệ thống sẽ thử lại.':'Đang lấy thống kê từ server…'));
 const next=node('section',undefined,'control-card organizer-next-action'),action=organizerNextAction(t,snapshot,{stale});next.append(node('p','VIỆC TIẾP THEO','control-kicker'),node('h2',action.label),node('p',action.description));
 if(action.href){const a=link(action.label,action.href);a.className='button primary';next.append(a);}
 if(action.command&&onCommand){const button=node('button',action.label,'primary');button.dataset.organizerCommand=action.command;button.onclick=()=>onCommand(action.command);next.append(button);}
 top.append(current,next);root.append(top);
 const attention=node('section',undefined,'organizer-attention');renderTournamentAttentionPanel(attention,t,snapshot,{stale});root.append(attention);
 const metrics=node('section',undefined,'control-card organizer-phase-metrics');renderOrganizerMetrics(metrics,t,snapshot,{section:'overview',stale});root.append(metrics);
 const bottom=node('div',undefined,'organizer-overview-bottom'),timeline=node('section',undefined,'control-card organizer-schedule');timeline.append(node('h2','Lịch giải đấu'));
 const rows=t.timeline?[['Đăng ký đóng',t.timeline.registrationCloseAt],['Lập trình kết thúc',t.timeline.codingCloseAt],['Hạn kiểm thử',t.timeline.validationDeadline],['Check-in đóng',t.timeline.checkInCloseAt],['Bắt đầu thi đấu',t.timeline.tournamentStartAt]]:[['Đăng ký đóng',t.timing?.registrationDeadline],['Check-in đóng',t.timing?.checkInDeadline],['Bắt đầu thi đấu',t.timing?.startTime]];
 const scheduled=rows.filter(([,stamp])=>stamp);if(!scheduled.length)timeline.append(node('p','Giải vận hành thủ công. Chủ giải chuyển giai đoạn khi đủ điều kiện.'));
 else {const list=node('dl',undefined,'organizer-schedule-list');for(const [label,stamp]of scheduled)list.append(node('dt',label),node('dd',date(stamp)));timeline.append(list);}
 timeline.append(link('Xem cài đặt lịch','/tournament.html?id='+encodeURIComponent(t.id)+'&view=settings#deadline-policy'));
 const recent=node('section',undefined,'control-card organizer-recent');recent.append(node('h2','Hoạt động gần đây'));const events=recentOrganizerActivity(t);
 if(!events.length)recent.append(node('p','Chưa có hoạt động quản lý.'));else{const list=node('ol');for(const event of events){const item=node('li');item.append(node('time',date(event.at)),node('span',event.label));if(event.reason)item.append(node('small',event.reason));list.append(item);}recent.append(list);}
 recent.append(link('Xem toàn bộ Audit','/tournament.html?id='+encodeURIComponent(t.id)+'&view=audit'));bottom.append(timeline,recent);root.append(bottom);
}
