import {languageText} from './player-labels.js';
import {phaseFacts,statusBadge} from './phase-view.js';
import {node,link,date} from './player-dom.js';
import {resolvePlayerTournamentViewState} from './player-view-state.js';
import {playerSurface} from './player-surface.js';
export {frozenReason} from './player-view-state.js';

export function renderFrozenSubmission(root,t,flow){
 const view=resolvePlayerTournamentViewState({tournament:t,flow}),frozen=flow.frozenSubmission;
 const panel=playerSurface(root,'frozen-submission');panel.id='player-submission-detail';panel.dataset.submissionState=view.submissionState;
 panel.append(node('h2','Trạng thái bài nộp'));
 if(view.hasLockedSubmission){
  panel.append(node('h3','✓ Bài đã được khóa'),node('p',frozen.decision==='AUTO_SUBMITTED'?'Hệ thống tự nộp phiên bản sẵn sàng mới nhất hợp lệ trước hạn.':'Đã giữ phiên bản bạn nộp trước hạn.'));
  phaseFacts(panel,[['Phiên bản','v'+frozen.version],['Ngôn ngữ',languageText(frozen.language)],['Nộp lúc',date(frozen.submittedAt)],['Đóng lập trình',date(frozen.codingCloseAt)]]);
  if(flow.canAccessWorkspace)panel.append(link('XEM SOURCE ĐÃ KHÓA','/tournament-code.html?id='+encodeURIComponent(t.id)+'&view=source&version='+encodeURIComponent(frozen.versionId)+'&language='+encodeURIComponent(frozen.language)));
 }else if(view.reason)panel.append(node('h3',view.submissionState==='NO_READY_AT_DEADLINE'?'✕ Không có phiên bản sẵn sàng trước hạn':'✕ Không có bài nộp hợp lệ'),node('p',view.reason));
 else panel.append(statusBadge('QUEUED'),node('p','Server đang chốt bài nộp. Trang sẽ tự cập nhật.'));
 panel.append(node('p','Phần lập trình đã kết thúc'),node('p','Không thể chỉnh sửa source, build mới, kiểm thử mới hoặc nộp bài mới.'));
 if(view.secondaryInfo.validationNotStarted){const next=node('div',undefined,'submission-next-milestone');next.append(node('h3','Bước tiếp theo'),node('p','Kiểm thử chính thức chưa bắt đầu.'));if(view.secondaryInfo.validationStartsAt)next.append(node('p','Kiểm thử bắt đầu lúc: '+date(view.secondaryInfo.validationStartsAt)));panel.append(next);}
 else panel.append(node('p','Kết quả và quyền tham gia tiếp theo do server xác nhận.'));
 root.append(panel);
}
