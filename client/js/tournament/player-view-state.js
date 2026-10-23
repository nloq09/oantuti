import {resolveTournamentViewState} from './navigation.js';
import {progressSteps} from './progress-stepper.js';
import {tournamentRoute} from './tournament-routes.js';
import {validationDetailState} from './validation-detail-state.js';

export const playerAction=(type,label,description,href=null,extra={})=>({type,label,href,enabled:Boolean(href),tone:'neutral',description,reasonDisabled:href?null:description,...extra});
export const frozenReason=reason=>({EXPLICIT_SUBMIT_REQUIRED:'Bạn chưa bấm nộp bài trước hạn.',NO_READY_VERSION_BEFORE_DEADLINE:'Bạn không có phiên bản sẵn sàng trước thời hạn.',ACCOUNT_INACTIVE:'Tài khoản không còn hoạt động.',SUBMISSION_REJECTED:'Bài đã bị từ chối hoặc loại.',ARTIFACT_CHANGED:'Bài nộp không vượt qua kiểm tra toàn vẹn.'}[reason]||'Server không ghi nhận bài nộp hợp lệ tại thời hạn.');
const closedPhases=new Set(['SUBMISSION_FROZEN','VALIDATING','CHECK_IN_OPEN','READY_TO_START','RUNNING','PAUSED','RECOVERY_REQUIRED','FINISHED']);
const validationStates={QUEUED:'VALIDATION_PENDING',PENDING:'VALIDATION_PENDING',RUNNING:'VALIDATION_RUNNING',FAILED:'VALIDATION_FAILED',INFRA_ERROR:'VALIDATION_INFRA_ERROR'};
/** Pure presentation of independent server facts. A phase never creates an artifact or permission. */
export function resolvePlayerTournamentViewState({tournament:t,flow={},workspace=null,submission=null}){
 flow=flow||{};const phase=flow.phase||'SYNCING',frozen=flow.frozenSubmission,validation=flow.finalValidation;
 const hasLockedSubmission=Boolean(frozen?.versionId&&['AUTO_SUBMITTED','EXPLICIT_SUBMIT'].includes(frozen.decision));
 const missing=frozen?.decision==='MISSING'||flow.submissionStatus==='SUBMISSION_MISSING';
 const beforeValidation=phase==='SUBMISSION_FROZEN',closed=closedPhases.has(phase);
 const emergency=(flow.canEmergencyResubmit===true||flow.emergencyCoding===true)&&flow.canAccessWorkspace===true&&flow.canModifyWorkspace===true&&phase==='CODING_OPEN';
 let submissionState=workspace?.status||submission?.status||({NO_WORKSPACE:'NO_WORKSPACE',NO_SUBMISSION:'UNKNOWN'})[flow.submissionStatus]||flow.submissionStatus||'UNKNOWN';
 if(closed){submissionState=missing?(frozen?.reason==='NO_READY_VERSION_BEFORE_DEADLINE'?'NO_READY_AT_DEADLINE':'NO_VALID_SUBMISSION_AT_DEADLINE'):hasLockedSubmission?'LOCKED':'FREEZE_PENDING';}
 const validationView=validationDetailState(validation,flow.validationDetailState);
 const showValidation=Boolean((validation||flow.validationDetailState)&&!beforeValidation&&closed);
 if(showValidation&&validation)submissionState=validation.status==='PASSED'?(validation.applied?'VALIDATION_PASSED':'VALIDATION_PENDING'):validationStates[validation.status]||'VALIDATION_PENDING';
 let primaryAction=null;
 if(emergency)primaryAction=playerAction('EMERGENCY_RESUBMIT','Nộp lại','Bạn được cấp quyền nộp lại khẩn cấp. Hoàn thành trước hạn mới do server cấp.',tournamentRoute(t.id,'code'),{tone:'primary'});
 else if(beforeValidation){
  if(missing)primaryAction=playerAction('WAIT_VALIDATION','Không có bài nộp hợp lệ','Hiện không có thao tác nào cần thực hiện. Hãy chờ giai đoạn kiểm thử chính thức.');
  else if(hasLockedSubmission)primaryAction=playerAction('VIEW_FROZEN_SUBMISSION','Xem bài đã khóa','Bạn có thể xem phiên bản đã chốt. Kiểm thử chính thức chưa bắt đầu.','#validation');
  else primaryAction=playerAction('WAIT_FREEZE','Đang chốt bài nộp','Hiện không có thao tác nào cần thực hiện. Chờ server xác nhận kết quả chốt bài.');
 }else if(showValidation&&validationView.status!=='READY'){
  primaryAction=playerAction(validationView.status==='ERROR'?'RETRY_VALIDATION':'LOADING_VALIDATION',validationView.status==='ERROR'?'Không tải được chi tiết kiểm thử':'Đang tải kết quả kiểm thử…','Chờ dữ liệu kiểm thử đầy đủ từ server.');
 }else if(showValidation&&!(phase!=='VALIDATING'&&(flow.nextAction==='CHECK_IN'||flow.nextAction==='OPEN_WAITING_ROOM'||flow.nextAction==='CHECK_IN_MISSED'))){
  if(submissionState==='VALIDATION_PASSED')primaryAction=playerAction('WAIT_CHECK_IN','Chờ check-in','Bạn không cần thực hiện thêm thao tác nào lúc này.',null,{opensAt:t.timeline?.checkInOpenAt,serverNow:flow.serverNow});
  else if(validation.status==='FAILED')primaryAction=playerAction('VIEW_VALIDATION_FAILURE','Xem bước chưa đạt','Xem bước lỗi và hướng dẫn trong kết quả kiểm thử.','#player-validation-failure');
  else if(validation.status==='INFRA_ERROR')primaryAction=playerAction('WAIT_INFRA_RETRY','Chờ xử lý kiểm thử','Kiểm thử bị gián đoạn do hạ tầng. Ban tổ chức cần xử lý; bạn không cần nộp lại.');
  else primaryAction=playerAction('WAIT_VALIDATION','Chờ kết quả kiểm thử',validation.status==='RUNNING'?'Bot đang được kiểm thử. Bạn không cần thao tác lúc này.':validation.status==='PASSED'?'Các bước đã hoàn tất; đang chờ server xác nhận kết quả.':'Bài đang chờ lượt kiểm thử chính thức.');
 }else if(closed&&missing){
  primaryAction=playerAction('NO_VALID_SUBMISSION','Không có bài nộp hợp lệ','Hiện không có thao tác nào cần thực hiện. Theo dõi kết quả và thông báo từ server.');
 }else if(phase==='VALIDATING'){
  primaryAction=playerAction('WAIT_VALIDATION','Chờ thông tin kiểm thử','Chưa có kết quả hoặc công việc kiểm thử của bạn được server xác nhận. Hiện không có thao tác nào cần thực hiện.');
 }else if(flow.nextAction==='VIEW_FROZEN_SUBMISSION'){
  primaryAction=closed&&hasLockedSubmission?playerAction('VIEW_FROZEN_SUBMISSION','Xem bài đã khóa','Xem phiên bản do server chốt.','#validation'):playerAction('WAIT_FREEZE','Chờ xác nhận bài nộp','Server chưa xác nhận bản khóa. Hiện không có thao tác nào cần thực hiện.');
 }else if(flow.nextAction==='VIEW_FINAL_VALIDATION')primaryAction=playerAction('WAIT_VALIDATION','Chờ thông tin kiểm thử','Chưa có công việc kiểm thử cá nhân được server xác nhận.');
 return {...resolveTournamentViewState(t,flow,null),phase,phaseLabel:beforeValidation?'Đã hết hạn nộp bài':null,submissionState,hasLockedSubmission,showSubmission:closed&&!showValidation,showValidation,validationView,emergency,primaryAction,primaryTitle:primaryAction?.label,primaryDescription:primaryAction?.description,reason:missing?frozenReason(frozen?.reason):null,secondaryInfo:{validationStartsAt:t.timeline?.validationStartAt,validationNotStarted:beforeValidation},stepperState:progressSteps(phase),failedStep:showValidation&&validation?.status==='FAILED'?2:null};
}
