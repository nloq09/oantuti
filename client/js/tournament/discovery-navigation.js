import {tournamentRoute,ownerTournamentRoute,playerTournamentRoute} from './tournament-routes.js';
const action=(type,label,description,href=null)=>({type,label,href,enabled:Boolean(href),tone:'neutral',description,reasonDisabled:href?null:description});
export function discoveryNavigation(t,context={}){
 if(t.owned===true||ownerTournamentRoute(t,context.account))return action('MANAGE_TOURNAMENT','Quản lý giải','Mở trang quản lý giải của bạn.',tournamentRoute(t.id,'organizer'));
 if(t.status==='FINISHED')return action('VIEW_RESULT','Xem kết quả','Xem kết quả do server công bố.',tournamentRoute(t.id,'detail',{tab:'ranking'}));
 if(t.status==='CANCELLED')return action('TOURNAMENT_CANCELLED','Giải đã hủy','Xem thông tin giải đã hủy.',tournamentRoute(t.id));
 if(t.registered&&t.applicationStatus==='APPROVED'&&t.phase==='CODING_OPEN')return action('OPEN_CODE','Tiếp tục lập trình','Mở giải để xác nhận quyền lập trình hiện tại.',tournamentRoute(t.id));
 if(t.registered&&t.phase==='CHECK_IN_OPEN')return action('CHECK_IN','Check-in','Xác nhận tham gia theo trạng thái server.',tournamentRoute(t.id));
 if(!t.registered&&(t.phase==='REGISTRATION_OPEN'||t.acceptingRegistrations===true))return action('REGISTER','Đăng ký','Xem điều kiện và đăng ký tham gia.',tournamentRoute(t.id));
 return action('VIEW_STATUS',t.registered?'Xem trạng thái':'Xem giải','Mở giải để lấy giai đoạn và quyền hiện tại từ server.',playerTournamentRoute(t));
}
