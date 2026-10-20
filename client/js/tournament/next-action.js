// Presentation of server decisions only. These links never grant admission or workspace rights.
const action=(type,label,description,href=null,extra={})=>({type,label,href,enabled:Boolean(href),tone:'neutral',description,reasonDisabled:href?null:description,...extra});
export function tournamentNextAction({tournament:t,flow,me,account}){
 const detail='/tournament-detail.html?id='+encodeURIComponent(t.id),code='/tournament-code.html?id='+encodeURIComponent(t.id),content='#tournament-phase-content',competition=t.status==='RUNNING'?'/tournament-lobby.html?id='+encodeURIComponent(t.id):detail;
 if(t.status==='CANCELLED')return action('TOURNAMENT_CANCELLED','Giải đã hủy','Bạn có thể tìm giải khác.','/tournaments.html');
 if(t.status==='FINISHED')return action('VIEW_RESULT','Xem kết quả','Xem thành tích, bảng điểm và replay.',detail+'#ranking');
 if(['DRAFT','UPCOMING'].includes(flow?.phase)||t.status==='DRAFT')return action('VIEW_RULES','Xem luật','Theo dõi lịch; đăng ký chưa mở.','#tournament-rules');
 if(!account)return action('LOGIN','Đăng nhập','Đăng nhập để xem việc cần làm của tài khoản.','/login.html?next='+encodeURIComponent(detail));
 const states=me?.states||[];
 if(['RUNNING','PAUSED','RECOVERY_REQUIRED'].includes(t.status)||!t.timeline){
  // Prefer an actionable human seat if a legacy account owns more than one registration.
  const active=states.filter(s=>!['WITHDRAWN','DISQUALIFIED','ELIMINATED','REJECTED','MISSED_CHECK_IN'].includes(s.status));
  const item=active.find(s=>s.currentMatch&&s.registration.controllerType==='HUMAN')||active.find(s=>s.status==='READY_CHECK')||active.find(s=>s.currentMatch)||active[0]||states[0];
  if(item&&['WITHDRAWN','DISQUALIFIED','REJECTED','MISSED_CHECK_IN'].includes(item.status))return action('INELIGIBLE','Không có suất thi đấu đang hoạt động','Xem trạng thái đăng ký hoặc liên hệ chủ giải.');
  if(item?.status==='ELIMINATED')return action('ELIMINATED','Xem bracket','Bạn đã dừng bước; theo dõi các trận còn lại.',competition+'#bracket');
  if(item?.currentMatch?.onlineMatchId&&item.registration.controllerType==='HUMAN')return action('ENTER_MATCH','Vào trận','Trận đã được server cấp cho bạn.',null,{enabled:true,command:'ENTER_MATCH',payload:{matchId:item.currentMatch.id},tone:'primary'});
  if(item?.currentMatch?.onlineMatchId&&item.registration.controllerType==='BOT')return action('WATCH_MATCH','Xem trận','Theo dõi trận đang diễn ra.','/game.html?spectate='+encodeURIComponent(item.currentMatch.onlineMatchId));
  if(t.status==='RECOVERY_REQUIRED')return action('WAIT_RECOVERY','Chờ khôi phục','Chủ giải đang xử lý; không tự chạy lại trận.');
  if(t.status==='PAUSED')return action('WAIT_RESUME','Giải tạm dừng','Chờ chủ giải tiếp tục thi đấu.');
  if(item?.status==='READY_CHECK'&&item.registration.controllerType==='HUMAN'&&item.nextMatch?.readyWindowId)return action('READY_CHECK','Sẵn sàng thi đấu','Xác nhận trước hạn do server cấp.',null,{enabled:true,command:'READY_CHECK',payload:{matchId:item.nextMatch.id,windowId:item.nextMatch.readyWindowId},deadline:item.nextMatch.readyDeadline,tone:'primary'});
  if(item?.status==='CHECK_IN_REQUIRED')return action('CHECK_IN','Check-in','Xác nhận tham gia trước hạn.',null,{enabled:true,command:'CHECK_IN',payload:{registrationId:item.registration.id},deadline:item.deadline,tone:'primary'});
  if(item?.status==='PENDING_APPROVAL')return action('WAIT_APPROVAL','Chờ xét duyệt','Chủ giải đang xét duyệt đăng ký.');
  if(item?.status==='READY_CONFIRMED')return action('WAIT_START','Đã sẵn sàng','Chờ đối thủ xác nhận và server cấp trận.');
  if(item)return action(t.status==='RUNNING'?'WAIT_NEXT_MATCH':'WAIT_START','Chờ thi đấu','Theo dõi lịch và trạng thái trận của bạn.');
  if(!t.timeline&&t.status==='REGISTRATION')return action('REGISTER','Đăng ký tham gia','Chọn cách thi đấu theo luật giải.',content);
  if(t.status==='RUNNING')return action('VIEW_SCHEDULE','Xem lịch thi đấu','Bạn chưa có suất thi đấu đang hoạt động.',competition+'#schedule');
 }
 if(!flow)return action('WAIT_SERVER','Đang cập nhật trạng thái','Chờ dữ liệu quyền từ server.');
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
