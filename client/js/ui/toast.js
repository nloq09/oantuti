const errors = {
  BOT_ARTIFACT_CHANGED: "File bot bị thiếu hoặc không khớp phiên bản đã khóa. Giải chưa thể chạy bot này.",
  INVALID_MANUAL_SEEDS: "Mỗi bot cần một seed thủ công riêng từ 1 đến số bot đã duyệt.",
  BOT_NOT_READY: "Bot bị lỗi, đã xóa hoặc thiếu file source. Hãy tải lại bot.",
  BOT_BUILD_ERROR: "Bot không qua được kiểm tra cú pháp/biên dịch trong Docker. Hãy sửa source rồi tải lại.",
  REGISTRATION_NOT_FOUND: "Không tìm thấy đơn đăng ký trong giải này.",
  INVALID_REGISTRATION_TRANSITION: "Đơn đăng ký đã thay đổi trạng thái. Hãy tải lại giải.",
  CHECK_IN_NOT_OPEN: "Giải chưa mở check-in hoặc đã khóa check-in.",
  CHECK_IN_REQUIRED: "Cần mở đăng ký và hoàn tất check-in trước khi bắt đầu giải.",
  NO_APPROVED_REGISTRATION: "Bạn chưa có bot được duyệt để check-in.",
  NOT_ENOUGH_CHECKED_IN_BOTS: "Cần ít nhất hai bot đã check-in để hoàn tất.",
  INVALID_PARTICIPANT_LIMIT: "Số bot tối đa phải từ 2 đến 10.",
  INVALID_TOURNAMENT_TRANSITION: "Trạng thái giải đã thay đổi hoặc không cho phép thao tác này. Hãy tải lại giải.",
  SERVER_BUSY: "Server đang bận hoặc đang dừng. Hãy thử lại sau; giải đấu chưa được bắt đầu nếu hàng đợi đã đầy.",
  RATE_LIMITED: "Bạn thao tác quá nhanh hoặc đã đăng nhập sai nhiều lần. Hãy chờ rồi thử lại.",
  INVALID_CREDENTIALS: "Tên đăng nhập hoặc mật khẩu không đúng.",
  INVALID_USERNAME: "Tên đăng nhập gồm 3–32 chữ cái, chữ số hoặc dấu gạch dưới.",
  INVALID_EMAIL: "Email không hợp lệ.",
  INVALID_DISPLAY_NAME: "Tên hiển thị cần từ 1 đến 60 ký tự.",
  INVALID_PASSWORD: "Mật khẩu cần ít nhất 12 ký tự, tối đa 72 byte UTF-8.",
  PASSWORD_MISMATCH: "Mật khẩu xác nhận không khớp.",
  ACCOUNT_ALREADY_EXISTS: "Tên đăng nhập hoặc email đã được sử dụng.",
  CURRENT_PASSWORD_INVALID: "Mật khẩu hiện tại không đúng.",
  PASSWORD_UNCHANGED: "Mật khẩu mới phải khác mật khẩu hiện tại.",
  DATABASE_UNAVAILABLE: "Chưa kết nối được database. Hãy thử lại sau.",
  MATCH_PERSISTENCE_UNAVAILABLE: "Chưa lưu được lịch sử trận. Hãy thử lại sau.",
  TOURNAMENT_PERSISTENCE_UNAVAILABLE: "Chưa lưu được dữ liệu giải đấu. Hãy thử lại khi database hoạt động.",
  UNSUPPORTED_TOURNAMENT_VISIBILITY: "Hiện chỉ hỗ trợ giải đấu công khai.",
  UNAUTHENTICATED: "Hãy đăng nhập để quản lý bot hoặc tạo/tham gia giải đấu.",
  CSRF_INVALID: "Phiên thao tác hết hạn. Hãy thử lại.",
  BOT_ALREADY_OWNED: "Bot đã thuộc một tài khoản khác; không thể nhập lại.",
  BOT_ARTIFACT_MISSING: "Thiếu file bot trên server. Liên hệ quản trị viên để khôi phục.",
  TOURNAMENT_NOT_FOUND: "Không tìm thấy giải đấu.",
  TOURNAMENT_LOCKED:
    "Giải đấu đã bắt đầu hoặc kết thúc; không thể sửa danh sách bot.",
  NOT_ENOUGH_BOTS: "Cần ít nhất 2 bot để bắt đầu giải.",
  DUPLICATE_PARTICIPANT: "Bot này đã tham gia giải.",
  PARTICIPANT_LIMIT: "Mỗi giải có tối đa 10 bot.",
  INVALID_TOURNAMENT_NAME: "Tên giải cần từ 1 đến 80 ký tự.",
  REPLAY_NOT_READY: "Replay chỉ sẵn sàng sau khi trận kết thúc.",
  REPLAY_FILE_MISSING: "Thiếu file replay trên server. Cần khôi phục từ bản sao lưu.",
  REPLAY_INVALID: "Replay không hợp lệ hoặc đã bị hỏng; không thể phát lại.",
  REPLAY_PERSISTENCE_UNAVAILABLE: "Chưa lưu được replay. Hãy thử lại khi server hoạt động ổn định.",
  SPECTATOR_READ_ONLY: "Khán giả chỉ được xem trận.",
  BOT_REQUIRED: "Hãy chọn bot của bạn trước khi tạo trận bot đấu bot.",
  BOT_SANDBOX_UNAVAILABLE:
    "Chưa có Docker Linux hoặc image cho ngôn ngữ này. Xem hướng dẫn bot để thiết lập.",
  BOT_CONTROLLED: "Bot đang điều khiển đội quân này.",
  BOT_FAILED:
    "Một bot bị lỗi, quá thời gian hoặc trả nước đi không hợp lệ và đã bị xử thua.",
  BOT_ACCESS_DENIED: "Bạn không có quyền sử dụng bot này.",
  BOT_NOT_FOUND: "Không tìm thấy bot đã tải lên.",
  BOT_FILE_SIZE: "File rỗng hoặc quá lớn: source 512 KB, JAR 5 MB.",
  INVALID_BOT_NAME: "Tên bot cần từ 1 đến 60 ký tự.",
  INVALID_BOT_FILE: "File bot không đúng định dạng.",
  UNSUPPORTED_BOT_LANGUAGE: "Hỗ trợ .cpp, .py, .java và .jar.",
  BOT_STORAGE_FULL: "Kho bot trên server đã đầy.",
  BOT_STORAGE_ERROR: "Server không thể lưu bot.",
  MATCH_NOT_FOUND: "Không tìm thấy phòng.",
  MATCH_FULL: "Phòng đã đủ người.",
  INVALID_PLACEMENT: "Ô này không thể đặt quân.",
  PLACEMENT_QUOTA: "Đã đủ 6 quân loại này.",
  PLACEMENT_INCOMPLETE: "Hãy đặt đủ 18 quân trước khi sẵn sàng.",
  INVALID_SEQUENCE: "Bàn cờ đã thay đổi. Đã tải trạng thái mới.",
  NOT_YOUR_TURN: "Chưa đến lượt bạn.",
  INVALID_MOVE: "Nước đi không hợp lệ.",
  PLAYER_READY: "Hãy hủy sẵn sàng để sửa đội hình.",
  PLAYER_NOT_FOUND: "Thông tin vào phòng không hợp lệ.",
  SEAT_TAKEN: "Vị trí đang được mở ở cửa sổ khác.",
  MATCH_FINISHED: "Ván chơi đã kết thúc.",
  INVALID_PHASE: "Thao tác chưa khả dụng.",
  INVALID_MESSAGE: "Dữ liệu gửi không hợp lệ.",
};
export const errorText = message => errors[message] || (message === "Failed to fetch" ? "Không kết nối được server." : "Không thể thực hiện thao tác. Hãy thử lại.");
export function toast(message, error = false) {
  let box = document.querySelector("#toasts");
  if (!box) {
    box = document.createElement("div");
    box.id = "toasts";
    box.setAttribute("role", "status");
    document.body.append(box);
  }
  const text = errors[message] || message;
  if ([...box.children].some((item) => item.textContent === text)) return;
  while (box.children.length >= 3) box.firstElementChild.remove();
  const item = document.createElement("div");
  item.className = "toast" + (error ? " error" : "");
  item.textContent = text;
  box.append(item);
  setTimeout(() => item.remove(), 4500);
}
