const destinations = new Set(["/", "/lobby.html", "/bots.html", "/tournaments.html", "/tournament.html", "/profile.html", "/history.html", "/game.html", "/replay.html", "/result.html"]);
export function safeNext(value, origin = "https://rps.invalid") {
  try {
    const url = new URL(value || "/", origin);
    return url.origin === origin && !url.username && !url.password && destinations.has(url.pathname)
      ? url.pathname + url.search + url.hash : "/";
  } catch { return "/"; }
}
export function validatePassword(password, confirmation) {
  if (password !== confirmation) throw new Error("PASSWORD_MISMATCH");
  if (!password.trim() || [...password].length < 12 || new TextEncoder().encode(password).length > 72) throw new Error("INVALID_PASSWORD");
}
export const dateLabel = value => value ? new Date(value).toLocaleString("vi-VN") : "—";
export const outcomeLabels = { WIN: "Thắng", LOSS: "Thua", DRAW: "Hòa", SELF_PLAY: "Hai bên cùng tài khoản", UNKNOWN: "Chưa xác định" };
export const matchStatus = { FINISHED: "Hoàn tất", WAITING: "Chờ đối thủ", PLACEMENT: "Đang xếp quân", PLAYING: "Đang chơi", INFRASTRUCTURE_ERROR: "Lỗi chạy bot" };
export function showStats(target, stats) {
  target.replaceChildren();
  for (const [label, value] of [["Trận đã chơi", stats.matchesPlayed], ["Thắng", stats.wins], ["Thua", stats.losses], ["Hòa", stats.draws], ["Tỷ lệ thắng", stats.winRate + "%"]]) {
    const card = document.createElement("div"), number = document.createElement("strong"), title = document.createElement("span");
    card.className = "stat-card"; number.textContent = value; title.textContent = label; card.append(number, title); target.append(card);
  }
}
