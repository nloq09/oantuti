export const statusLabels = {
  RECOVERY_REQUIRED: "Cần phục hồi",
  DRAFT: "○ Bản nháp",
  READY: "✓ Sẵn sàng",
  RUNNING: "● Đang chạy",
  FINISHED: "✓ Hoàn thành",
  CANCELLED: "× Đã hủy",
  PENDING: "◷ Chờ chạy",
  FAILED: "! Lỗi hạ tầng",
};
export function renderRanking(element, rows) {
  element.replaceChildren();
  for (const row of rows) {
    const tr = document.createElement("tr");
    for (const value of [
      row.rank,
      row.displayName,
      row.played,
      row.wins,
      row.draws,
      row.losses,
      row.points,
      row.piecesRemaining,
    ]) {
      const td = document.createElement("td");
      td.textContent = value;
      tr.append(td);
    }
    element.append(tr);
  }
}
