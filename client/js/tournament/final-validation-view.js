import {validationDetailState} from './validation-detail-state.js';
import {renderFrozenSubmission} from './frozen-submission.js';
import {renderPlayerNextActionCard} from './player-shell.js';
import {resolvePlayerTournamentViewState} from './player-view-state.js';
import {node,link} from './player-dom.js';
import {playerSurface} from './player-surface.js';
export const validationStages={BUILD:'Build',PROTOCOL:'Giao thức bot',TIMEOUT:'Thời gian phản hồi',RESOURCE:'Giới hạn tài nguyên',LEGAL_ACTION:'Nước đi hợp lệ',HIDDEN_TESTS:'Kiểm thử ẩn',TOURNAMENT_TESTS:'Kiểm thử theo luật giải',ARTIFACT_HASH:'Tính toàn vẹn bài nộp'};
export const validationReason=reason=>({PUBLIC_TESTS_FAILED:'Bot không qua kiểm thử giao thức hoặc nước đi.',HIDDEN_TESTS_FAILED:'Bot không qua bộ kiểm thử ẩn.',TOURNAMENT_TESTS_FAILED:'Bot không hoàn tất trận kiểm thử hợp lệ.',ARTIFACT_CHANGED:'Bài nộp không còn toàn vẹn.',SOURCE_CHANGED:'Mã nguồn không khớp bài đã khóa.',VALIDATION_DEADLINE:'Đã hết thời gian kiểm thử; cần chủ giải xem xét.',SANDBOX_UNAVAILABLE:'Hệ thống kiểm thử chưa sẵn sàng.',FINAL_WORKER_ERROR:'Máy kiểm thử bị gián đoạn; cần ban tổ chức chạy lại.',WORKER_RECOVERY_EXHAUSTED:'Kiểm thử bị gián đoạn nhiều lần.',ACCOUNT_INACTIVE:'Tài khoản không còn hoạt động.',SUBMISSION_REJECTED:'Bài đã bị loại; kết quả kiểm thử không khôi phục quyền thi đấu.',SUBMISSION_NOT_LOCKED:'Bài không còn ở trạng thái khóa.',REGISTRATION_NOT_APPROVED:'Đăng ký không còn hợp lệ.'}[reason]||'Kiểm thử chưa đạt; theo dõi thông báo của chủ giải.');
export function validationNextStep(t,flow){
 const phase=flow.phase||t.status,check=flow.checkIn;
 if(t.status==='CANCELLED'||phase==='CANCELLED')return 'Giải đã hủy. Kết quả kiểm thử được giữ để tham khảo.';
 if(t.status==='FINISHED'||phase==='FINISHED')return 'Giải đã kết thúc. Xem kết quả và bảng xếp hạng.';
 if(t.status==='RECOVERY_REQUIRED'||phase==='RECOVERY_REQUIRED')return 'Giải đang chờ chủ giải khôi phục.';
 if(t.status==='PAUSED'||phase==='PAUSED')return 'Giải đang tạm dừng. Chờ chủ giải tiếp tục.';
 if(check?.status==='MISSED'||flow.participantStatus==='MISSED_CHECK_IN')return 'Đã lỡ hạn check-in; bạn không có trong danh sách thi đấu.';
 if(flow.participantStatus==='ELIMINATED')return 'Bạn đã bị loại. Có thể tiếp tục theo dõi giải.';
 if(t.status==='RUNNING'||phase==='RUNNING')return 'Giải đang thi đấu. Theo dõi trận đấu và bảng xếp hạng.';
 if(check?.status==='CHECKED_IN')return 'Đã check-in. Chờ giải bắt đầu.';
 if(check?.canCheckIn)return 'Hãy check-in trước hạn để tham gia thi đấu.';
 if(phase==='READY_TO_START'||phase==='CHECK_IN_OPEN')return 'Theo dõi trạng thái tham gia và danh sách thi đấu của bạn.';
 return 'Chờ giai đoạn check-in.';
}

// Explicit allowlist: technical identifiers and hashes never enter the presentation model.
export function playerValidationModel(v){
 const detail=validationDetailState(v);
 const steps=detail.status==='READY'?Object.entries(validationStages).map(([key,label])=>({label,status:detail.steps[key]})):[];
 return {detailState:detail.status,status:v.status,ready:detail.status==='READY'&&v.status==='PASSED'&&v.applied===true,language:({CPP:'C++',PYTHON:'Python',JAVA:'Java'})[v.language]||'Bot',version:v.version,
 steps,passed:detail.status==='READY'?detail.passed:null,total:detail.total??null,reason:v.reason?validationReason(v.reason):null,
 fallback:Boolean(v.originalVersionId&&v.versionId&&v.originalVersionId!==v.versionId)};
}
export function renderFinalValidation(root,t,flow,{refresh}={}){
 if(flow.phase==='SUBMISSION_FROZEN'){renderFrozenSubmission(root,t,flow);return;}
 const v=flow.finalValidation,detail=validationDetailState(v,flow.validationDetailState);
 if(detail.status==='IDLE')return;
 if(detail.status!=='READY'){
  const panel=playerSurface(root,'validation-panel');panel.id='player-submission-detail';panel.dataset.validationLoadState=detail.status;
  panel.append(node('h2','Kiểm thử chính thức'),node('p',detail.status==='LOADING'?'Đang tải kết quả kiểm thử…':'Không thể tải đầy đủ chi tiết kiểm thử.'));
  if(detail.status==='ERROR'){
   if(detail.code==='VALIDATION_STATE_INCONSISTENT')console.warn('VALIDATION_STATE_INCONSISTENT');
   const retry=node('button','Thử lại');retry.onclick=()=>refresh?refresh():globalThis.dispatchEvent(new CustomEvent('rps-tournament-update',{detail:{id:t.id}}));panel.append(retry);
  }
  root.append(panel);return;
 }
 const model=playerValidationModel(v),panel=playerSurface(root,'validation-panel');
 panel.id='player-submission-detail';panel.dataset.validationStatus=model.status;
 panel.append(node('p','Bài nộp đã được khóa và đang ở chế độ chỉ đọc.','validation-readonly'));
 panel.append(node('h2',model.ready?'✓ KIỂM THỬ HOÀN TẤT':model.status==='FAILED'?'Kiểm thử bot chưa đạt':model.status==='INFRA_ERROR'?'Kiểm thử bị gián đoạn':model.status==='RUNNING'?'Đang kiểm thử bot chính thức':model.status==='PASSED'?'Đang xác nhận kết quả':'Chờ kiểm thử chính thức'));
 if(model.ready)panel.append(node('p','Bot của bạn đã vượt qua toàn bộ kiểm thử chính thức.'));
 panel.append(node('p',model.language+(Number.isInteger(model.version)?' · v'+model.version:'')),node('strong',model.passed+' / '+model.steps.length+' kiểm thử đạt','validation-summary'));
 const stages=node('ul',undefined,'validation-stages player-validation-stages');
 for(const step of model.steps){
  const item=node('li',undefined,'validation-step');item.dataset.status=step.status;
  const suffix=({RUNNING:' — Đang chạy',FAILED:' — Không đạt',INFRA_ERROR:' — Gián đoạn',SKIPPED:' — Không chạy',PENDING:' — Chờ kiểm thử',QUEUED:' — Chờ kiểm thử'})[step.status]||(step.status==='PASSED'?'':' — Chờ cập nhật');
  item.append(node('span',(step.status==='PASSED'?'✓ ':step.status==='FAILED'?'✕ ':step.status==='RUNNING'?'● ':'○ ')+step.label+suffix));stages.append(item);
 }
 panel.append(stages);
 if(model.reason){const reason=node('p',model.reason,'validation-failure');reason.id='player-validation-failure';panel.append(reason);}
 else if(model.status==='FAILED'){const reason=node('p','Xem bước không đạt ở trên. Theo dõi hướng dẫn của ban tổ chức.','validation-failure');reason.id='player-validation-failure';panel.append(reason);}
 if(model.status==='FAILED'&&v.failurePolicy==='ALLOW_EMERGENCY_RESUBMISSION')panel.append(node('p','Chỉ ban tổ chức mới có thể cấp lượt nộp lại. Editor chưa được mở lại.'));
 if(model.fallback)panel.append(node('p','Đang dùng phiên bản hợp lệ trước đó theo chính sách giải.'));
 if(model.ready&&flow.phase!=='VALIDATING')panel.append(node('p',validationNextStep(t,flow)));
 if(flow.canAccessWorkspace===true&&v.versionId)panel.append(link('XEM SOURCE ĐÃ KHÓA','/tournament-code.html?id='+encodeURIComponent(t.id)+'&view=source&version='+encodeURIComponent(v.versionId)+'&language='+encodeURIComponent(v.language),'button'));
 root.append(panel);
 if(flow.phase==='VALIDATING'){const action=resolvePlayerTournamentViewState({tournament:t,flow}).primaryAction;if(action)renderPlayerNextActionCard(root,action);}
}
