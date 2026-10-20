import {canManageTournament} from './tournament-api.js';
export const organizerSections=[['overview','Tổng quan'],['registration','Đăng ký'],['submissions','Bài nộp'],['validation','Kiểm thử'],['check-in','Check-in'],['matches','Trận đấu'],['bracket','Nhánh đấu'],['results','Kết quả'],['replays','Replay'],['audit','Audit'],['settings','Cài đặt']];
export const organizerPanels={overview:'overview',registration:'participants-tab','check-in':'participants-tab',matches:'matches',bracket:'bracket',results:'ranking',replays:'replays-tab',audit:'organizer-audit',settings:'admin-tab'};
export function organizerNavigation(t){return organizerSections.filter(([key])=>!(key==='validation'&&!t.timeline)&&!(key==='submissions'&&t.participantPolicy==='HUMAN_ONLY')&&!(key==='bracket'&&!['SINGLE_ELIMINATION','GROUP_KNOCKOUT'].includes(t.format))).map(([key,label])=>({key,label,href:(key==='registration'&&t.timeline?'/tournament-applications.html':key==='submissions'?'/tournament-submissions.html':key==='validation'?'/tournament-final-validation.html':'/tournament.html')+'?id='+encodeURIComponent(t.id)+(key==='registration'&&t.timeline||['submissions','validation'].includes(key)?'':'&view='+encodeURIComponent(key))}));}
export function organizerSection(t,requested){return organizerNavigation(t).some(s=>s.key===requested)?requested:'overview';}
export function organizerFocus(phase){return ({REGISTRATION_OPEN:'registration',REGISTRATION_REVIEW:'registration',WAITING_FOR_CODING:'registration',CODING_OPEN:'submissions',SUBMISSION_FROZEN:'validation',VALIDATING:'validation',CHECK_IN_OPEN:'check-in',READY_TO_START:'check-in',RUNNING:'matches',PAUSED:'matches',RECOVERY_REQUIRED:'matches',FINISHED:'results',CANCELLED:'audit'})[phase]||'overview';}
export function organizerHealth(t){
 if(t.status==='RECOVERY_REQUIRED')return {state:'REVIEW',text:'Cần khôi phục giải'};
 if((t.progress?.failed||0)>0||Object.keys(t.reviews||{}).length)return {state:'REVIEW',text:'Có trận lỗi hoặc đang review'};
 if(t.status==='PAUSED')return {state:'PAUSED',text:'Giải đang tạm dừng'};
 return {state:'UNKNOWN',text:'Hạ tầng: chưa có dữ liệu tổng hợp'};
}
export function organizerAccess(t,account){return canManageTournament(t,account);}
