import {phaseFacts,statusBadge} from './phase-view.js';
import {node,link,date} from './player-dom.js';

export const frozenReason=reason=>({EXPLICIT_SUBMIT_REQUIRED:'Bạn chưa bấm nộp bài trước hạn.',NO_READY_VERSION_BEFORE_DEADLINE:'Không có phiên bản READY trước hạn.',ACCOUNT_INACTIVE:'Tài khoản không còn hoạt động.',SUBMISSION_REJECTED:'Bài đã bị từ chối hoặc loại.',ARTIFACT_CHANGED:'Artifact không vượt qua kiểm tra toàn vẹn.'}[reason]||'Không có bài nộp hợp lệ.');
export function renderFrozenSubmission(root,t,flow){
 const panel=node('section',undefined,'panel frozen-submission');panel.append(node('h2','Bài đã được khóa'),node('p','Phần lập trình đã kết thúc'),node('p','Đã khóa chỉnh sửa, build, kiểm thử mới và nộp lại.'));
 const frozen=flow.frozenSubmission;
 if(!frozen)panel.append(statusBadge('QUEUED'),node('p','Server đang chốt bài nộp. Trang sẽ tự cập nhật.'));
 else if(frozen.decision==='MISSING')panel.append(node('h3','Chưa có bài nộp'),node('p',frozenReason(frozen.reason)));
 else{
  panel.append(node('h3','Bài nộp đã khóa · v'+frozen.version),node('p',frozen.decision==='AUTO_SUBMITTED'?'Hệ thống tự nộp phiên bản READY trước hạn.':'Đã giữ phiên bản bạn nộp trước hạn.'));
  phaseFacts(panel,[['Phiên bản','v'+frozen.version],['Ngôn ngữ',frozen.language],['Nộp lúc',date(frozen.submittedAt)],['Đóng lập trình',date(frozen.codingCloseAt)],['Build','PASS'],['Source hash',frozen.sourceHash],['Artifact hash',frozen.artifactHash]]);
  panel.append(node('p','Bài đã khóa sẽ được kiểm thử chính thức ở giai đoạn tiếp theo.'));
 }
 if(flow.canAccessWorkspace&&frozen?.versionId)panel.append(link('Xem source của tôi','/tournament-code.html?id='+encodeURIComponent(t.id)+'&view=source'+(frozen?.versionId?'&version='+encodeURIComponent(frozen.versionId)+'&language='+encodeURIComponent(frozen.language):'')));
 root.append(panel);
}
