import {competitionComplete} from './bracket-renderer.js';
import {tournamentPhase} from './tournament-header.js';

export function allowedControls(t){
 const matches=t.matches||[],reviews=Object.keys(t.reviews||{}).length;
 return {pause:t.status==='RUNNING',resume:['PAUSED','RECOVERY_REQUIRED'].includes(t.status)&&!reviews&&!matches.some(m=>m.status==='FAILED'),
  'next-round':['PAUSED','RUNNING'].includes(t.status)&&!reviews&&!t.autoAdvanceRounds&&t.releasedRound<(t.rounds?.length||0)&&matches.filter(m=>m.round<=t.releasedRound).every(m=>m.status==='FINISHED'),
  finish:['RUNNING','PAUSED','RECOVERY_REQUIRED'].includes(t.status)&&!reviews&&!matches.some(m=>['PENDING','RUNNING'].includes(m.status))&&competitionComplete(t)};
}
export function organizerActionAvailability(t,data,{stale=false}={}){
 const phase=tournamentPhase(t,data),synced=!stale&&data?.revision===t.revision&&data?.tournamentId===t.id,terminal=['FINISHED','CANCELLED'].includes(t.status),minimum=t.format==='TEAM_ROUND_ROBIN'?6:t.format==='GROUP_KNOCKOUT'?4:2;
 const roster=(t.participants||[]).filter(p=>!['DISQUALIFIED','WITHDRAWN'].includes(t.participantStatuses?.[p.participantId]));
 const checked=(t.registrations||[]).filter(r=>r.status==='CHECKED_IN'&&roster.some(p=>p.participantId===r.id)).length;
 const controls=allowedControls(t),serverNow=Date.parse(data?.serverNow),starts=Date.parse(t.timeline?.tournamentStartAt||t.timing?.startTime),registrationDeadline=t.timeline?.registrationCloseAt||t.timing?.registrationDeadline;
 return {phase,synced,minimum,
  openRegistration:synced&&t.status==='DRAFT'&&(!registrationDeadline||Number.isFinite(serverNow)&&serverNow<Date.parse(registrationDeadline)),
  closeRegistration:synced&&!t.timeline&&t.status==='REGISTRATION'&&roster.length>=minimum,
  closeCheckIn:synced&&!t.timeline&&t.status==='CHECK_IN'&&checked>=minimum,
  start:synced&&!t.timelineStartIssue&&roster.length>=minimum&&(t.timeline?checked>=minimum&&phase==='READY_TO_START'&&Number.isFinite(serverNow)&&serverNow>=starts&&['REGISTRATION','CHECK_IN','READY'].includes(t.status):(t.status==='READY'||t.status==='DRAFT'&&!t.checkInRequired)&&(!t.timing?.startTime||Number.isFinite(serverNow)&&serverNow>=starts)),
  cancel:synced&&!terminal,configure:synced&&['DRAFT','REGISTRATION','CHECK_IN'].includes(t.status),
  ...Object.fromEntries(Object.entries(controls).map(([key,value])=>[key,synced&&value]))};
}
export function matchOperationAvailability(t,m){
 const paused=['PAUSED','RECOVERY_REQUIRED'].includes(t.status),review=Boolean(m&&t.reviews?.[m.id]);
 const lockedResult=m&&((t.groupHistory||[]).some(s=>m.round>s.startRound&&m.round<=s.rounds)||t.groupStage?.locked&&m.round<=t.groupStage.rounds||t.format==='SWISS'&&(t.matches||[]).some(other=>other.round>m.round)||(t.bestOf||1)>1&&(t.matches||[]).some(other=>other.seriesId===m.seriesId&&other.gameNumber>m.gameNumber)||['SINGLE_ELIMINATION','GROUP_KNOCKOUT'].includes(t.format)&&(t.matches||[]).some(other=>other.round>m.round&&other.status!=='PENDING'));
 return {review:Boolean(m)&&['FINISHED','FAILED'].includes(m.status)&&t.status!=='CANCELLED'&&!review,
  keep:review&&t.status!=='CANCELLED',retry:paused&&m?.status==='FAILED'&&m.failure!=='CANCELLED',override:(paused||t.status==='FINISHED')&&review&&m?.status==='FINISHED'&&!lockedResult};
}
export function organizerNextAction(t,data,{stale=false}={}){
 const available=organizerActionAvailability(t,data,{stale}),phase=available.phase,base='/tournament.html?id='+encodeURIComponent(t.id),go=(section,label,description)=>({label,description,href:base+'&view='+section,section}),command=(name,label,description)=>({command:name,label,description});
 if(stale)return {label:'Đang đồng bộ',description:'Dữ liệu có thể đã cũ. Chờ cập nhật trước khi thao tác.'};
 if(!available.synced)return {label:'Đang tải giai đoạn',description:'Đang lấy quyền thao tác và thời hạn từ server.'};
 if(phase==='CANCELLED')return go('audit','Xem lịch sử','Giải đã hủy. Các kết quả và quyết định trước đó được giữ lại.');
 if(phase==='FINISHED')return go('results',Object.keys(t.reviews||{}).length?'Xử lý kết quả':'Xem kết quả',Object.keys(t.reviews||{}).length?'Còn trận đang chờ quyết định; kết quả vô địch chưa được xác nhận.':'Giải đã hoàn tất. Xem bảng xếp hạng và báo cáo.');
 if(available.start)return command('start','Bắt đầu giải','Danh sách hợp lệ đã sẵn sàng và đã đến thời điểm bắt đầu.');
 if(available.finish)return go('results','Hoàn tất giải','Các trận đã kết thúc. Kiểm tra kết quả và xác nhận hoàn tất.');
 if(['PAUSED','RECOVERY_REQUIRED'].includes(phase))return go('matches','Xử lý thi đấu',available.resume?'Có thể tiếp tục giải sau khi kiểm tra.':'Kiểm tra trận lỗi và các review trước khi tiếp tục.');
 if(phase==='DRAFT')return available.openRegistration?command('open-registration','Mở đăng ký','Kiểm tra lịch và luật trước khi công bố giải.'):go('settings','Kiểm tra cài đặt','Hoàn tất cấu hình và cập nhật lịch trước khi mở đăng ký.');
 if(['REGISTRATION_OPEN','REGISTRATION_REVIEW','WAITING_FOR_CODING','UPCOMING'].includes(phase)){
  if(data?.registration?.pending)return {label:'Duyệt đăng ký',description:data.registration.pending+' đơn đang chờ xét duyệt.',href:t.timeline?'/tournament-applications.html?id='+encodeURIComponent(t.id)+'&status=PENDING':base+'&view=registration&filter=PENDING',section:'registration'};
  if(available.closeRegistration)return command('close-registration','Chốt đăng ký','Đã đủ người được duyệt. Kiểm tra các đơn còn lại trước khi chốt.');
  return go('registration',phase==='UPCOMING'?'Xem lịch đăng ký':'Theo dõi đăng ký',phase==='WAITING_FOR_CODING'?'Đã xét duyệt xong. Chờ giờ mở lập trình.':'Theo dõi danh sách và các đơn được gửi vào giải.');
 }
 if(phase==='CODING_OPEN')return {label:'Xem bài nộp',description:'Theo dõi workspace, build và các phiên bản READY trước hạn.',href:'/tournament-submissions.html?id='+encodeURIComponent(t.id),section:'submissions'};
 if(['SUBMISSION_FROZEN','VALIDATING'].includes(phase))return {label:'Vào kiểm thử',description:'Bài đã được khóa. Theo dõi kiểm thử chính thức và xử lý riêng lỗi hạ tầng.',href:'/tournament-final-validation.html?id='+encodeURIComponent(t.id),section:'validation'};
 if(available.closeCheckIn)return command('close-check-in','Chốt check-in','Đã đủ người xác nhận. Kiểm tra danh sách trước khi chốt.');
 if(['CHECK_IN_OPEN','READY_TO_START'].includes(phase))return go('check-in','Xem check-in',t.timelineStartIssue?'Giải chưa đủ điều kiện bắt đầu. Kiểm tra cảnh báo và danh sách hợp lệ.':phase==='READY_TO_START'?'Chờ giờ bắt đầu và xác nhận của server.':(data?.checkIn?.missing??'Một số')+' suất chưa check-in.');
 return go('matches',available['next-round']?'Mở vòng tiếp':'Theo dõi thi đấu',available['next-round']?'Vòng hiện tại đã hoàn tất. Có thể mở vòng tiếp theo.':'Theo dõi các trận đang chạy, hàng đợi và kết quả.');
}
