import {tournamentNavigation} from './next-action.js';
export const hubTabs=[["live","Đang diễn ra"],["open","Đang mở đăng ký"],["upcoming","Sắp diễn ra"],["joined","Đã tham gia"],["owned","Giải của tôi"],["finished","Đã kết thúc"]];
export const tournamentCta=(t,context)=>tournamentNavigation(t,context).label;
export const tournamentHref=(t,context)=>tournamentNavigation(t,context).href;
