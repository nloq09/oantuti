const errors = {
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
