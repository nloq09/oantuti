import {node,date} from './player-dom.js';
import {checkInRegistration} from './tournament-api.js';
import {errorText} from '../ui/toast.js';
import {renderRules,renderSchedule} from './competition-view.js';
import {notificationText} from './notifications.js';
import {sessionRequest} from '../api/auth-api.js';

export function checkInScreen(flow){
 if(flow.nextAction==='OPEN_WAITING_ROOM')return 'waiting';
 if(flow.nextAction==='CHECK_IN_MISSED')return 'missed';
 if(flow.nextAction==='CHECK_IN')return 'open';
 return null;
}
export function renderTimelineCheckIn(root,t,flow,refresh,{primaryCheckIn=false}={}){
 const screen=checkInScreen(flow),panel=node('section',undefined,'panel timeline-check-in');root.append(panel);
 panel.append(node('h2',screen==='waiting'?'Phòng chờ giải đấu':screen==='missed'?'Đã hết hạn check-in':'Check-in đã mở'));
 panel.append(node('p',flow.registration.displayName));
 const validation=flow.finalValidation;
 if(validation)panel.append(node('p',`Bot · ${validation.language} · Version ${validation.version} · ${validation.status==='PASSED'?'Đã qua kiểm thử chính thức':'Chưa hợp lệ'}`));
 if(screen==='open'){
  panel.append(node('strong','BOT READY ✓'),node('p','Check-in đóng: '+date(flow.checkIn.closesAt)));
  const deadline=node('p');deadline.dataset.deadline=flow.checkIn.closesAt;
  const button=node('button','Check-in ngay'),notice=node('p');notice.setAttribute('role','status');
  button.onclick=async()=>{if(button.disabled)return;button.disabled=true;try{await checkInRegistration(t.id,flow.checkIn.registrationId);await refresh();}catch(error){notice.textContent=errorText(error.message);button.disabled=false;}};
  if(primaryCheckIn)panel.append(node('p','Dùng nút Check-in trong “Việc của bạn” để xác nhận tham gia.'));else panel.append(deadline,button,notice);return;
 }
 if(screen==='missed'){panel.append(node('p','Bạn chưa check-in trong thời gian quy định nên không có tên trong danh sách thi đấu.'),node('p','Check-in kết thúc: '+date(flow.checkIn.closesAt)));return;}
 panel.append(node('strong','✓ Đã check-in'),node('strong',validation?.status==='PASSED'&&validation.applied!==false?'Bot Ready ✓':'Đang xác nhận trạng thái bot'),node('p','Xác nhận lúc: '+date(flow.checkIn.checkedInAt)),node('p',`${flow.checkIn.checkedInCount} người đã check-in`));
 const countdown=node('p');countdown.dataset.tournamentStarts=flow.checkIn.startsAt;panel.append(countdown,node('p','Giải bắt đầu: '+date(flow.checkIn.startsAt)));
 const announcements=node('section',undefined,'panel');announcements.append(node('h2','Thông báo'));const notice=node('p','Đang tải thông báo…');notice.setAttribute('role','status');announcements.append(notice);root.append(announcements);
 sessionRequest('/api/tournaments/'+encodeURIComponent(t.id)+'/notifications').then(data=>{if(announcements.isConnected){notice.textContent=data.items?.length?'Thông báo của giải':'Chưa có thông báo mới.';for(const item of data.items||[])announcements.append(node('p',notificationText(item)+' · '+date(item.createdAt)));}}).catch(()=>{if(announcements.isConnected)notice.textContent='Chưa tải được thông báo. Trang sẽ tự thử lại khi cập nhật.';});
 const rules=node('section',undefined,'panel');renderRules(rules,t);root.append(rules);
 const schedule=node('section',undefined,'panel');schedule.append(node('h2','Lịch thi đấu'));
 if(t.matches?.length)renderSchedule(schedule,t,null);else schedule.append(node('p','Chưa ghép trận. Lịch sẽ xuất hiện khi chủ giải chuẩn bị thi đấu.'));
 root.append(schedule);
}
