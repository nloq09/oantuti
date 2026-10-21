import {canManageTournament} from './tournament-api.js';
import {tournamentPhase} from './tournament-header.js';
export function organizerPhaseData(t,data){if(t.timeline)return data;const phase=tournamentPhase(t),phaseEndsAt=phase==='REGISTRATION_OPEN'?t.timing?.registrationDeadline:phase==='CHECK_IN_OPEN'?t.timing?.checkInDeadline:phase==='READY_TO_START'?t.timing?.startTime:null;return {...data,phase,phaseEndsAt:phaseEndsAt??null};}
export const organizerSections=[['overview','Tổng quan'],['registration','Đăng ký'],['submissions','Bài nộp'],['validation','Kiểm thử'],['check-in','Check-in'],['matches','Trận đấu'],['bracket','Nhánh đấu'],['results','Kết quả'],['replays','Replay'],['audit','Audit'],['settings','Cài đặt']];
export const organizerPanels={overview:'overview',registration:'participants-tab','check-in':'organizer-check-in',matches:'matches',bracket:'bracket',results:'ranking',replays:'replays-tab',audit:'organizer-audit',settings:'admin-tab'};
export const organizerGroups=[{key:'overview',label:'Tổng quan',sections:['overview']},{key:'players',label:'Người chơi',sections:['registration','submissions','validation','check-in']},{key:'competition',label:'Thi đấu',sections:['matches','bracket','results','replays']},{key:'management',label:'Quản trị',sections:['audit','settings']}];
export function organizerNavigation(t){return organizerSections.filter(([key])=>!(key==='validation'&&!t.timeline)&&!(key==='submissions'&&t.participantPolicy==='HUMAN_ONLY')).map(([key,label])=>({key,label:key==='bracket'&&!['SINGLE_ELIMINATION','GROUP_KNOCKOUT'].includes(t.format)?'Hạt giống':label,group:organizerGroups.find(g=>g.sections.includes(key)).key,href:(key==='registration'&&t.timeline?'/tournament-applications.html':key==='submissions'?'/tournament-submissions.html':key==='validation'?'/tournament-final-validation.html':'/tournament.html')+'?id='+encodeURIComponent(t.id)+(key==='registration'&&t.timeline||['submissions','validation'].includes(key)?'':'&view='+encodeURIComponent(key))}));}
export function organizerSection(t,requested){return organizerNavigation(t).some(s=>s.key===requested)?requested:'overview';}
export function organizerFocus(phase){return ({REGISTRATION_OPEN:'registration',REGISTRATION_REVIEW:'registration',WAITING_FOR_CODING:'registration',CODING_OPEN:'submissions',SUBMISSION_FROZEN:'validation',VALIDATING:'validation',CHECK_IN_OPEN:'check-in',READY_TO_START:'check-in',RUNNING:'matches',PAUSED:'matches',RECOVERY_REQUIRED:'matches',FINISHED:'results',CANCELLED:'audit'})[phase]||'overview';}
export function organizerHealth(t,data,{stale=false}={}){
 if(t.status==='FINISHED'&&Object.keys(t.reviews||{}).length)return {state:'REVIEW',text:'Còn kết quả đang chờ quyết định'};
 if(['FINISHED','CANCELLED'].includes(t.status))return {state:'CLOSED',text:t.status==='FINISHED'?'Giải đã hoàn tất':'Giải đã hủy'};
 if(t.status==='RECOVERY_REQUIRED')return {state:'REVIEW',text:'Cần khôi phục giải'};
 if((t.progress?.failed||0)>0||Object.keys(t.reviews||{}).length)return {state:'REVIEW',text:'Có trận lỗi hoặc đang review'};
 if(t.status==='PAUSED')return {state:'PAUSED',text:'Giải đang tạm dừng'};
 if(stale)return {state:'UNKNOWN',text:'Chưa cập nhật được tình trạng giải'};
 if(data?.tournamentId===t.id&&data?.revision===t.revision){
  if(data.validation?.infraError||data.running?.error||data.workers?.offline)return {state:'REVIEW',text:'Có lỗi cần xử lý'};
  if(data.validation?.failed||data.workers?.degraded)return {state:'WARNING',text:'Có vấn đề cần chú ý'};
  return {state:'OK',text:'Chưa ghi nhận lỗi trong giải'};
 }
 return {state:'UNKNOWN',text:'Hạ tầng: chưa có dữ liệu tổng hợp'};
}
export function organizerAccess(t,account){return canManageTournament(t,account);}
