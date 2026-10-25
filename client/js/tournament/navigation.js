import {tournamentPhase,phaseLabels} from './tournament-header.js';
export const hasTournamentReplays=t=>Number.isFinite(t.replayCount)?t.replayCount>0:Boolean(t.matches?.some(m=>m.historyMatchId));
export const supportsBracket=format=>['SINGLE_ELIMINATION','DOUBLE_ELIMINATION','GROUP_KNOCKOUT'].includes(format);
export const supportsStandings=format=>['ROUND_ROBIN','SWISS','GROUP_KNOCKOUT','TEAM_ROUND_ROBIN','SINGLE_ELIMINATION','DOUBLE_ELIMINATION'].includes(format);
export const supportsSeries=bestOf=>Number(bestOf)>1;
export const mapTournamentPhase=value=>phaseLabels[value]||'Đang cập nhật';
export function resolveTournamentNavigation({tournamentFormat,tournamentPhase:phase,viewerRole='PLAYER',participantState,matchAvailability={},activeTab='overview',checkInRequired=true,timelineEnabled=false,botSubmissionEnabled=true}){
 const running=['RUNNING','PAUSED','RECOVERY_REQUIRED'].includes(phase),finished=phase==='FINISHED',closed=finished||phase==='CANCELLED',competing=running||closed;
 const matches=matchAvailability.hasMatches===true,replays=matchAvailability.hasReplays===true;
 if(['ORGANIZER','ADMIN'].includes(viewerRole)){
  const codingOrLater=['CODING_OPEN','SUBMISSION_FROZEN','VALIDATING','CHECK_IN_OPEN','READY_TO_START','RUNNING','PAUSED','RECOVERY_REQUIRED','FINISHED'].includes(phase);
  const validationOrLater=codingOrLater&&phase!=='CODING_OPEN';
  const seedSetup=!competing&&phase!=='SYNCING';
  const options=[['overview','Tổng quan',true],['registration','Đăng ký',true],['submissions','Bài nộp',botSubmissionEnabled&&(!timelineEnabled||codingOrLater)],['validation','Kiểm thử',timelineEnabled&&validationOrLater],['check-in','Check-in',checkInRequired&&['CHECK_IN_OPEN','READY_TO_START','RUNNING','PAUSED','RECOVERY_REQUIRED','FINISHED'].includes(phase)],['matches','Trận đấu',matches],['bracket',seedSetup?'Hạt giống':'Nhánh đấu',seedSetup||supportsBracket(tournamentFormat)&&matchAvailability.hasBracket===true],['results','Kết quả',finished||competing&&matches],['replays','Replay',replays],['workers','Worker riêng',botSubmissionEnabled],['audit','Audit',true],['settings','Cài đặt',true]];
  const selected=options.some(([key,,show])=>key===activeTab&&show)?activeTab:'overview';
  return options.map(([id,label,visible])=>({id,label,visible,enabled:visible,active:visible&&id===selected,viewerRole}));
 }
 const options=[['overview',finished?'Kết quả':'Tổng quan',true],['registration','Đăng ký',['REGISTRATION_OPEN','REGISTRATION_REVIEW','UPCOMING','WAITING_FOR_CODING'].includes(phase)],['coding','Lập trình',phase==='CODING_OPEN'],['validation','Kiểm thử',['SUBMISSION_FROZEN','VALIDATING'].includes(phase)],['check-in','Check-in',checkInRequired&&['CHECK_IN_OPEN','READY_TO_START'].includes(phase)],['my-matches','Trận của tôi',running&&Boolean(participantState)],['teams','Đồng đội',tournamentFormat==='TEAM_ROUND_ROBIN'&&!closed],['participants','Người tham gia',competing&&!finished],['schedule',tournamentFormat==='SWISS'?'Ghép cặp':'Lịch thi đấu',running&&matches],['bracket','Nhánh đấu',competing&&supportsBracket(tournamentFormat)&&matchAvailability.hasBracket===true],['ranking','Bảng xếp hạng',competing&&supportsStandings(tournamentFormat)],['matches','Trận đấu',competing&&matches],['replays','Replay',competing&&replays],['rules','Luật giải',!finished],['disputes','Báo cáo sự cố',competing&&matches&&Boolean(participantState)]];
 const visible=options.filter(([, ,show])=>show);const focus=phase==='CODING_OPEN'?'coding':['SUBMISSION_FROZEN','VALIDATING'].includes(phase)?'validation':['CHECK_IN_OPEN','READY_TO_START'].includes(phase)?'check-in':['REGISTRATION_OPEN','REGISTRATION_REVIEW','WAITING_FOR_CODING'].includes(phase)?'registration':'overview';const selected=visible.some(([id])=>id===activeTab)?activeTab:visible.some(([id])=>id===focus)?focus:'overview';
 return options.map(([id,label,show])=>({id,label,visible:show,enabled:show,active:show&&selected===id,viewerRole}));
}
export function resolveTournamentViewState(t,flow,me,activeTab='overview',viewerRole='PLAYER'){
 const phase=tournamentPhase(t,flow),hasMatches=(t.progress?.total??t.matches?.length??0)>0;
 const navigation=resolveTournamentNavigation({tournamentFormat:t.format,tournamentPhase:phase,viewerRole,participantState:me?.states?.length?me.states[0]:flow?.registration,checkInRequired:t.checkInRequired,matchAvailability:{hasMatches,hasBracket:(t.bracket?.length||0)>0,hasReplays:hasTournamentReplays(t)},activeTab});
 return {phase,phaseLabel:mapTournamentPhase(phase),compact:phase==='FINISHED',navigation,activeTab:navigation.find(x=>x.active)?.id||'overview',loadState:'loaded'};
}
export const resolveMatchViewState=m=>({status:!m?'loading':m.reviewRequired?'review':m.status==='FINISHED'?'finished':m.status==='FAILED'?'error':'active',hasReplay:Boolean(m?.historyMatchId)});

export const resolveOrganizerTournamentViewState=(t,flow,tab='overview')=>resolveTournamentViewState(t,flow,null,tab,'ORGANIZER');
