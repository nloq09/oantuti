const errors = {
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
export function toast(message, error = false) {
  let box = document.querySelector("#toasts");
  if (!box) {
    box = document.createElement("div");
    box.id = "toasts";
    box.setAttribute("role", "status");
    document.body.append(box);
  }
  const item = document.createElement("div");
  item.className = "toast" + (error ? " error" : "");
  item.textContent = errors[message] || message;
  box.append(item);
  setTimeout(() => item.remove(), 4500);
}
