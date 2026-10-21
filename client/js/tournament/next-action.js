import {activeMatch,isRemoteMatch,matchExecutionState} from './match-execution.js';
import {tournamentRoute,playerTournamentRoute,actionDestination} from './tournament-routes.js';
import {isKnockout,roundLabel} from './bracket-renderer.js';
// Presentation of server decisions only. These links never grant admission or workspace rights.
const action=(type,label,description,href=null,extra={})=>({type,label,href,enabled:Boolean(href),tone:'neutral',description,reasonDisabled:href?null:description,...extra});
export function tournamentNextAction({tournament:t,flow,me,account}){
 const detail=tournamentRoute(t.id),code=tournamentRoute(t.id,'code'),content='#tournament-phase-content',competition=playerTournamentRoute(t);
 if(t.status==='CANCELLED')return action('TOURNAMENT_CANCELLED','Giải đã hủy','Bạn có thể tìm giải khác.','/tournaments.html');
 if(t.status==='FINISHED')return action('VIEW_RESULT','Xem kết quả','Xem thành tích, bảng điểm và replay.',detail+'#ranking');
 if(['DRAFT','UPCOMING'].includes(flow?.phase)||t.status==='DRAFT')return action('VIEW_RULES','Xem luật','Theo dõi lịch; đăng ký chưa mở.','#tournament-rules');
 if(!account)return action('LOGIN','Đăng nhập','Đăng nhập để xem việc cần làm của tài khoản.','/login.html?next='+encodeURIComponent(detail));
 const states=me?.states||[];
 if(['RUNNING','PAUSED','RECOVERY_REQUIRED'].includes(t.status)||!t.timeline){
  // Prefer an actionable human seat if a legacy account owns more than one registration.
  const matches=new Map([...(t.matches||[]),...(me?.matchMonitor?.items||[])].map(m=>[m.id,m]));
  const active=states.filter(s=>!['WITHDRAWN','DISQUALIFIED','ELIMINATED','REJECTED','MISSED_CHECK_IN'].includes(s.status));
  const item=active.find(s=>s.currentMatch&&s.registration.controllerType==='HUMAN')||active.find(s=>s.status==='READY_CHECK'&&s.registration.controllerType==='HUMAN')||active.find(s=>s.currentMatch)||active.find(s=>{const m=assignedMatch(matches,s);return m&&isRemoteMatch(m)&&activeMatch(m);})||active[0]||states[0];
  if(item&&['WITHDRAWN','DISQUALIFIED','REJECTED','MISSED_CHECK_IN'].includes(item.status))return action('INELIGIBLE','Không có suất thi đấu đang hoạt động','Xem trạng thái đăng ký hoặc liên hệ chủ giải.');
  if(item&&['CHAMPION','FINISHED'].includes(item.status))return action('VIEW_RESULT','Xem kết quả',item.status==='CHAMPION'?'Bạn đã vô địch! Xem kết quả và replay.':'Giải đã kết thúc. Xem thành tích và replay.',detail+'#ranking');
  if(item?.status==='ELIMINATED')return action('ELIMINATED','Xem bracket','Bạn đã dừng bước; theo dõi các trận còn lại.',competition+'#bracket');
  const candidate=assignedMatch(matches,item),current=item?.currentMatch;
  if(current?.onlineMatchId&&item.registration.controllerType==='HUMAN'&&(!candidate||!isRemoteMatch(candidate)&&matchExecutionState(candidate)==='RUNNING_LOCAL'))return action('ENTER_MATCH','Vào trận','Trận đã được server cấp cho bạn.',null,{enabled:true,command:'ENTER_MATCH',payload:{matchId:current.id},tone:'primary'});
  if(!['PAUSED','RECOVERY_REQUIRED'].includes(t.status)&&item?.status==='READY_CHECK'&&item.registration.controllerType==='HUMAN'&&item.nextMatch?.readyWindowId)return action('READY_CHECK','Sẵn sàng thi đấu','Xác nhận trước hạn do server cấp.',null,{enabled:true,command:'READY_CHECK',payload:{matchId:item.nextMatch.id,windowId:item.nextMatch.readyWindowId},deadline:item.nextMatch.readyDeadline,tone:'primary'});
  if(item&&candidate&&isRemoteMatch(candidate)&&activeMatch(candidate))return action('TRACK_MATCH','Theo dõi trận của tôi',({QUEUED:'Đang chờ máy thi đấu.',ASSIGNED:'Đã cấp máy chạy.',STARTING:'Đang khởi động bot.',RUNNING_REMOTE:'Trận đang chạy trên máy thi đấu; live board chưa khả dụng.',RESULT_PENDING:'Đã chạy xong, đang xác nhận kết quả.',RETRYING:'Trận đang chờ chạy lại.',RECOVERING:'Đang chờ khôi phục execution.'})[candidate.executionState]||'Đang cập nhật execution.',competition+'#my-match');
  if(current&&item.registration.controllerType==='BOT'){
   if(candidate&&!isRemoteMatch(candidate)&&candidate.status==='RUNNING'&&matchExecutionState(candidate)==='RUNNING_LOCAL'&&candidate.onlineMatchId)return action('WATCH_MATCH','Xem trận','Theo dõi trận đang diễn ra.','/game.html?spectate='+encodeURIComponent(candidate.onlineMatchId)+'&tournament='+encodeURIComponent(t.id));
   return action('TRACK_MATCH','Theo dõi trận của tôi',me?.matchMonitorError?'Chưa tải được trạng thái trận. Đang đồng bộ lại trước khi mở bàn cờ.':'Đang đồng bộ trạng thái trận; xem kết quả và khả năng mở replay trong Trận của tôi.',competition+'#my-match');
  }
  if(t.status==='RECOVERY_REQUIRED')return action('WAIT_RECOVERY','Chờ khôi phục','Chủ giải đang xử lý; không tự chạy lại trận.');
  if(t.status==='PAUSED')return action('WAIT_RESUME','Giải tạm dừng','Chờ chủ giải tiếp tục thi đấu.');
  if(item?.status==='CHECK_IN_REQUIRED')return action('CHECK_IN','Check-in','Xác nhận tham gia trước hạn.',null,{enabled:true,command:'CHECK_IN',payload:{registrationId:item.registration.id},deadline:item.deadline,tone:'primary'});
  if(item?.status==='PENDING_APPROVAL')return action('WAIT_APPROVAL','Chờ xét duyệt','Chủ giải đang xét duyệt đăng ký.');
  if(item?.status==='READY_CONFIRMED')return action('WAIT_START','Đã sẵn sàng','Chờ đối thủ xác nhận và server cấp trận.');
  if(item)return action(t.status==='RUNNING'?'WAIT_NEXT_MATCH':'WAIT_START','Chờ thi đấu',progressDescription(item,t));
  if(!t.timeline&&t.status==='REGISTRATION')return t.acceptingRegistrations===false?action('REGISTRATION_CLOSED','Đã đóng nhận đơn','Chủ giải đang xét duyệt các đăng ký đã gửi.'):action('REGISTER','Đăng ký tham gia','Chọn cách thi đấu theo luật giải.',content);
  if(t.status==='RUNNING')return action('VIEW_SCHEDULE','Xem lịch thi đấu','Bạn chưa có suất thi đấu đang hoạt động.',competition+'#schedule');
 }
 if(!flow)return action('WAIT_SERVER','Đang cập nhật trạng thái','Chờ dữ liệu quyền từ server.');
 if(t.format==='TEAM_ROUND_ROBIN'&&flow.nextAction==='OPEN_WAITING_ROOM'&&(me?.states||[]).some(s=>s.participantId&&!t.teamCompetition?.teams?.some(team=>team.roster.includes(s.participantId)&&team.roster.length>=3)))return action('MANAGE_TEAM','Hoàn tất đội của bạn','Đã check-in. Tạo hoặc gia nhập đội đủ ít nhất ba thành viên trước giờ thi.','#teams');
 switch(flow.nextAction){
  case 'REGISTER':return action('REGISTER','Đăng ký tham gia','Điền thông tin và đồng ý luật giải.','#tournament-registration-form',{tone:'primary'});
  case 'WAIT_FOR_APPROVAL':return action('WAIT_APPROVAL','Chờ xét duyệt','Đơn đăng ký đã được gửi.');
  case 'WAIT_FOR_CODING':return action('WAIT_CODING','Chờ mở lập trình','Theo dõi giờ coding, luật và bot protocol.');
  case 'OPEN_CODE_WORKSPACE':return flow.canAccessWorkspace&&flow.canModifyWorkspace?action('OPEN_CODE','Vào phòng code','Hoàn thành, kiểm thử và nộp bot trước hạn.',code,{tone:'primary'}):action('WAIT_SERVER','Đang cập nhật quyền workspace','Chờ server xác nhận cửa sổ lập trình.');
  case 'VIEW_FROZEN_SUBMISSION':return action('VIEW_FROZEN_SUBMISSION','Xem bài đã khóa','Bài được chốt theo chính sách deadline.',content);
  case 'VIEW_FINAL_VALIDATION':return action(flow.finalValidation?.status==='FAILED'?'VIEW_VALIDATION_FAILURE':'WAIT_VALIDATION',flow.finalValidation?.status==='FAILED'?'Xem kết quả kiểm thử':'Theo dõi kiểm thử',flow.finalValidation?.status==='INFRA_ERROR'?'Kiểm thử bị gián đoạn do hạ tầng; chờ chủ giải xử lý.':'Theo dõi kết quả kiểm thử chính thức.',content);
  case 'CHECK_IN':return flow.checkIn?.canCheckIn===true?action('CHECK_IN','Check-in','Bot đã đủ điều kiện; xác nhận tham gia trước hạn.',null,{enabled:true,command:'CHECK_IN',payload:{registrationId:flow.checkIn.registrationId},deadline:flow.checkIn.closesAt,tone:'primary'}):action('WAIT_SERVER','Đang cập nhật check-in','Chờ server xác nhận điều kiện tham gia.');
  case 'OPEN_WAITING_ROOM':return action('WAIT_START','Đã check-in · Chờ bắt đầu','Theo dõi lịch, thông báo và thời gian bắt đầu.');
  case 'CHECK_IN_MISSED':return action('CHECK_IN_MISSED','Đã hết hạn check-in','Bạn chưa có suất thi đấu; xem thông báo của chủ giải.');
  case 'REGISTRATION_REJECTED':return action('REGISTRATION_REJECTED','Đăng ký bị từ chối',flow.registration?.reviewReason||'Xem lý do xét duyệt bên dưới.');
  case 'WAITLISTED':return action('WAITLISTED','Đang trong danh sách chờ','Chờ chủ giải xác nhận suất tham gia.');
  default:return action('VIEW_STATUS','Theo dõi trạng thái','Chưa có thao tác được server cấp tại thời điểm này.');
 }
}

function progressDescription(item,t){
 if(t.format==='TEAM_ROUND_ROBIN'&&!item.currentMatch&&!item.nextMatch)return 'Mở tab Đồng đội để xem đội và đội hình đã được server ghi nhận.';
 if(item.qualified&&item.nextMatch)return '✓ Đã vượt qua vòng bảng. Chờ vòng tiếp theo được mở.';
 if(isKnockout(t.format)&&item.nextMatch&&(t.bestOf>1?t.series||[]:t.matches||[]).some(m=>m.status==='FINISHED'&&m.winnerParticipantId===item.participantId))return '✓ Đã đi tiếp vào '+roundLabel(item.nextMatch.round,t.rounds?.length||1,t.format)+'. Chờ vòng được mở và xác nhận sẵn sàng.';
 return 'Theo dõi lịch và trạng thái trận của bạn.';
}
// Discovery lacks personal capabilities. Only a fresh participant flow can enable coding/check-in.
export function tournamentNavigation(t,context={}){
 if(context.flow||context.me){const resolved=tournamentNextAction({tournament:t,...context});return{...resolved,enabled:true,command:undefined,payload:undefined,href:actionDestination(resolved,t)};}
 if(t.status==='FINISHED')return action('VIEW_RESULT','Xem kết quả','Xem kết quả do server công bố.',tournamentRoute(t.id,'detail',{tab:'ranking'}));
 if(t.status==='CANCELLED')return action('TOURNAMENT_CANCELLED','Giải đã hủy','Xem thông tin giải đã hủy.',tournamentRoute(t.id));
 return action('VIEW_STATUS',t.registered?'Xem trạng thái':'Xem giải','Mở giải để lấy giai đoạn và quyền hiện tại từ server.',playerTournamentRoute(t));
}

// Prefer the selected registration's current assignment. Never let another owned bot hijack a human seat.
function assignedMatch(rows,item){
 if(!item)return null;
 const id=item.currentMatch?.id||item.nextMatch?.id;if(id)return rows.get(id)||null;
 return item.participantId?[...rows.values()].find(m=>activeMatch(m)&&[m.participantA?.participantId,m.participantB?.participantId].includes(item.participantId))||null:null;
}
