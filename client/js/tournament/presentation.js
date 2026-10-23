import {presentTournamentAudit} from './audit-presentation.js';
/** Allowlisted player wording. Unknown server values never become visible labels. */
const labels={PAUSED:'Tạm dừng',REGISTRATION:'Đang đăng ký',CHECK_IN:'Check-in',RECOVERY_REQUIRED:'Chờ khôi phục',PENDING:'Đang chờ xử lý',PENDING_APPROVAL:'Chờ duyệt',APPROVED:'Đã được duyệt',ACTIVE:'Đang tham gia',SUBMITTED:'Đã nộp bài',AUTO_SUBMITTED:'Đã tự động nộp bài',READY:'Sẵn sàng',BUILT:'Build thành công',BUILDING:'Đang build',TESTING:'Đang kiểm thử',DRAFT:'Đang chỉnh sửa',LOCKED:'Đã khóa',PASSED:'Đạt',PASS:'Đạt',FAILED:'Không đạt',FAIL:'Không đạt',BUILD_ERROR:'Build chưa thành công',TEST_FAILED:'Kiểm thử chưa đạt',RUNNING:'Đang diễn ra',FINISHED:'Đã kết thúc',CANCELLED:'Đã hủy',REJECTED:'Bị từ chối',WITHDRAWN:'Đã rút',DISQUALIFIED:'Bị loại do vi phạm',ELIMINATED:'Đã bị loại',CHAMPION:'Vô địch',CHECKED_IN:'Đã check-in',NOT_CHECKED_IN:'Chưa check-in',MISSED:'Đã lỡ hạn check-in',NOT_REQUIRED:'Không yêu cầu',QUEUED:'Đang chờ',INFRA_ERROR:'Hệ thống kiểm thử bị gián đoạn',NO_SUBMISSION:'Chưa nộp bài',NO_WORKSPACE:'Chưa tạo bài làm',SKIPPED:'Không chạy',WAITLISTED:'Danh sách chờ',WIN:'Thắng',LOSS:'Thua',DRAW:'Hòa',BYE:'Miễn đấu',FORFEIT:'Xử thua',PUBLIC_TESTS_PASSED:'Đã đạt kiểm thử công khai',FINAL_VALIDATION_PASSED:'Đã đạt kiểm thử chính thức',ONLINE:'Đang kết nối',OFFLINE:'Mất kết nối',HEALTHY:'Hoạt động bình thường',DEGRADED:'Kết nối không ổn định'};
export const statusText=(value,fallback='Đang cập nhật')=>labels[value]||fallback;
export const mapRegistrationStatus=value=>statusText(value);
export const mapSubmissionStatus=value=>statusText(value,'Chưa nộp bài');
export const mapValidationStatus=value=>statusText(value);
export const mapCheckInStatus=value=>statusText(value);
export const mapMatchStatus=value=>statusText(value);
export const languageText=value=>({PYTHON:'Python',CPP:'C++',JAVA:'Java',JAR:'Java (JAR)'})[value]||'Bot';
export function friendlyName(value,fallback='Người chơi'){
 if(!value||/^[\da-f]{8}-[\da-f-]{27,}$/i.test(value)||/^(ui_|st_|grp_)/i.test(value))return fallback;
 return String(value);
}
export const tieBreakText=value=>({POINTS:'Điểm',BUCHHOLZ:'Điểm đối thủ',WINS:'Số trận thắng',REMAINING_PIECES:'Quân còn lại',HEAD_TO_HEAD:'Đối đầu',MOVE_TIME:'Thời gian suy nghĩ',SEED:'Hạt giống',SEED_NUMBER:'Hạt giống'})[value]||'Tiêu chí phụ';
export function ruleSummary(t){const r=t.rulesSnapshot||{};const rows=[['Thể thức trận','Thắng '+Math.ceil((t.bestOf||1)/2)+' trong tối đa '+(t.bestOf||1)+' ván'],['Điểm mỗi cặp đấu','Thắng 3 · Hòa 1 · Thua 0']];
 if(r.maxTurns!=null)rows.push(['Số lượt tối đa',r.maxTurns]);if(r.turnSeconds!=null)rows.push(['Thời gian mỗi lượt',r.turnSeconds+' giây']);else if(r.moveTimeoutMs!=null)rows.push(['Thời gian mỗi lượt',r.moveTimeoutMs/1000+' giây']);
 if(r.boardRows&&r.boardColumns)rows.push(['Bàn cờ',r.boardRows+' × '+r.boardColumns]);if(r.pieceQuota)rows.push(['Quân mỗi loại',r.pieceQuota]);
 if(t.tieBreakOrder?.length)rows.push(['Xếp hạng khi bằng điểm',t.tieBreakOrder.map(tieBreakText).join(' → ')]);
 if(t.drawPolicy)rows.push(['Hòa ở vòng loại trực tiếp','So sánh quân còn lại, sau đó hạt giống']);return rows;
}

export const mapAuditAction=(event,tournament)=>presentTournamentAudit(event,tournament);
