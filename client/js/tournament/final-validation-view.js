import {statusBadge} from './phase-view.js';
import {node,link,date} from './player-dom.js';
export const validationStages={BUILD:'Build verification',PROTOCOL:'Giao thức',TIMEOUT:'Timeout',RESOURCE:'Giới hạn tài nguyên',LEGAL_ACTION:'Nước đi hợp lệ',HIDDEN_TESTS:'Hidden tests',TOURNAMENT_TESTS:'Đấu kiểm thử theo giải',ARTIFACT_HASH:'Toàn vẹn artifact'};
export const validationReason=reason=>({PUBLIC_TESTS_FAILED:'Bot không qua kiểm thử giao thức hoặc nước đi.',HIDDEN_TESTS_FAILED:'Bot không qua bộ kiểm thử ẩn.',TOURNAMENT_TESTS_FAILED:'Bot không hoàn tất trận kiểm thử hợp lệ.',ARTIFACT_CHANGED:'Artifact đã thay đổi.',SOURCE_CHANGED:'Hash source không khớp.',VALIDATION_DEADLINE:'Đã hết thời gian kiểm thử; cần chủ giải xem xét.',SANDBOX_UNAVAILABLE:'Sandbox chưa sẵn sàng.',FINAL_WORKER_ERROR:'Worker bị gián đoạn; cần chủ giải chạy lại.',WORKER_RECOVERY_EXHAUSTED:'Job bị gián đoạn nhiều lần.',ACCOUNT_INACTIVE:'Tài khoản không còn hoạt động.',SUBMISSION_NOT_LOCKED:'Bài không còn ở trạng thái khóa.',REGISTRATION_NOT_APPROVED:'Đăng ký không còn hợp lệ.'}[reason]||'Kiểm thử chưa đạt; theo dõi thông báo của chủ giải.');
export function renderFinalValidation(root,t,flow){const v=flow.finalValidation,panel=node('section',undefined,'panel validation-panel');root.append(panel);if(!v)return;const ready=v.status==='PASSED'&&v.applied;
 panel.append(node('h2',ready?'Bot đã được xác nhận':v.status==='FAILED'?'Kiểm thử bot chưa đạt':v.status==='INFRA_ERROR'?'Kiểm thử bị gián đoạn':'Đang kiểm thử bot chính thức'));
 panel.append(statusBadge(v.status),node('p',`${v.language} · v${v.version}`),node('p','Bài vẫn khóa chỉnh sửa; kiểm thử dùng artifact đã chốt.'));
 const stages=node('ol',undefined,'validation-stages');for(const [key,label]of Object.entries(validationStages)){const item=node('li');item.append(node('span',label),statusBadge(v.steps?.[key]||'PENDING'));stages.append(item);}panel.append(stages);
 if(v.reason)panel.append(node('p',validationReason(v.reason)));
 if(v.originalVersionId!==v.versionId)panel.append(node('p','Đang dùng phiên bản hợp lệ trước đó theo chính sách giải; phiên bản này cũng phải qua kiểm thử chính thức.'));
 if(ready)panel.append(node('p','TOURNAMENT READY · Chờ giai đoạn check-in.'));
 if(v.status==='FAILED'&&v.failurePolicy==='ALLOW_EMERGENCY_RESUBMISSION')panel.append(node('p','Chỉ chủ giải hoặc admin mới cấp lượt nộp khẩn cấp có lý do và hạn mới. Editor chưa được mở lại.'));
 if(v.emergencyDeadline)panel.append(node('p','Hạn nộp khẩn cấp: '+date(v.emergencyDeadline)));
 panel.append(node('p','Source hash: '+v.sourceHash),node('p','Artifact hash: '+v.artifactHash));if(flow.canAccessWorkspace)panel.append(link('Xem source của tôi','/tournament-code.html?id='+encodeURIComponent(t.id)+'&view=source&version='+encodeURIComponent(v.versionId)+'&language='+encodeURIComponent(v.language)));
}
