const destinations = new Set(["/", "/lobby.html", "/bots.html", "/tournaments.html", "/tournament.html", "/profile.html", "/history.html", "/game.html", "/replay.html", "/result.html", "/admin/index.html", "/admin/users.html", "/admin/user-detail.html", "/admin/bots.html", "/admin/bot-detail.html", "/admin/matches.html", "/admin/match-detail.html", "/admin/replay.html", "/admin/spectator.html", "/admin/tournaments.html", "/admin/tournament-detail.html", "/admin/disputes.html", "/admin/dispute-detail.html", "/admin/replays.html", "/admin/replay-detail.html", "/admin/audit.html", "/admin/system.html", "/admin/profile.html", "/tournament-detail.html", "/tournament-lobby.html", "/tournament-code.html", "/tournament-submissions.html", "/admin/submissions.html", "/tournament-applications.html", "/admin/applications.html", "/tournament-final-validation.html", "/admin/final-validation.html"]);
export function safeNext(value, origin = "https://rps.invalid") {
  try {
    const url = new URL(value || "/", origin);
    return url.origin === origin && !url.username && !url.password && destinations.has(url.pathname)
      ? url.pathname + url.search + url.hash : "/";
  } catch { return "/"; }
}
export function accountLinks(account,home,loginHref,registerHref) {
  if(account?.role==="ADMIN")return [["Quản trị","/admin/index.html"]];
  const links=home?[]:[["Trang chủ","/"]];
  if(!account)return [...links,["Đăng nhập",loginHref],["Đăng ký",registerHref]];
  if(!home)links.push(["Vào trận","/lobby.html"],["Bot của tôi","/bots.html"],["Giải đấu","/tournaments.html"]);
  links.push(["Lịch sử","/history.html"],["Hồ sơ","/profile.html"]);
  return links;
}
export function adminDestination(account,pathname,search="") {
  if(account?.role!=="ADMIN"||pathname.startsWith("/admin/"))return null;
  const query=new URLSearchParams(search);
  if(pathname==="/replay.html")return "/admin/replay.html"+search;
  if(pathname==="/game.html"&&query.get("spectate"))return "/admin/spectator.html?spectate="+encodeURIComponent(query.get("spectate"));
  if(["/tournament.html","/tournament-detail.html","/tournament-lobby.html"].includes(pathname)&&query.get("id"))return "/admin/tournament-detail.html?id="+encodeURIComponent(query.get("id"));
  if(pathname==="/tournament-applications.html")return "/admin/applications.html"+search;
  if(pathname==="/tournament-submissions.html")return "/admin/submissions.html"+search;
  if(pathname==="/tournament-final-validation.html")return "/admin/final-validation.html"+search;
  if(pathname==="/profile.html")return "/admin/profile.html";
  return "/admin/index.html";
}
export function accountLanding(account,next,origin="https://rps.invalid") {
  const safe=safeNext(next,origin),url=new URL(safe,origin);
  return adminDestination(account,url.pathname,url.search)??safe;
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
