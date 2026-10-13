import { requireAccount, loginLink } from "../account/account-session.js";
import { accountMatches, accountStats } from "../api/account-api.js";
import { dateLabel, showStats, outcomeLabels, matchStatus } from "../account/account-ui.js";
import { historyLoader } from "../account/history-loader.js";
import { errorText } from "../ui/toast.js";
const list = document.querySelector("#history-list"), status = document.querySelector("#history-status"), select = document.querySelector("#history-filter");
const previous = document.querySelector("#previous-page"), next = document.querySelector("#next-page");
let page = 0, total = 0;
function fail(error) {
  if (error.message === "UNAUTHENTICATED") location.replace(loginLink());
  else status.textContent = errorText(error.message);
}
function render(result) {
  total = result.total; page = result.page; list.replaceChildren();
  status.textContent = total ? "" : "Chưa có trận nào phù hợp. Vào trận để bắt đầu chơi.";
  for (const match of result.matches) {
    const card = document.createElement("article"), title = document.createElement("h2"), outcome = document.createElement("span"), info = document.createElement("p"), teams = document.createElement("p");
    card.className = "panel history-card";
    title.textContent = match.mode === "HUMAN_VS_BOT" ? "Đấu với bot của game" : "Đấu với người chơi";
    outcome.className = "match-outcome " + (match.outcome || "");
    outcome.textContent = outcomeLabels[match.outcome] || matchStatus[match.status] || "Chưa hoàn tất";
    info.textContent = `${dateLabel(match.createdAt)} · ${match.turnCount} lượt${match.durationMs == null ? "" : " · " + Math.round(match.durationMs / 1000) + " giây"}`;
    teams.textContent = match.participants.map(p => `${p.seat === "P1" ? "Xanh" : "Đỏ"}: ${p.controllerType === "HUMAN" ? "người chơi" : p.controllerType === "BUILTIN_BOT" ? "bot của game" : "bot của người chơi"}`).join(" · ");
    card.append(title, outcome, info, teams);
    if (match.status === "FINISHED") {
      const replay = document.createElement("a"); replay.className = "button primary"; replay.textContent = "Xem replay"; replay.href = "/replay.html?match=" + encodeURIComponent(match.id); card.append(replay);
    } else { const note = document.createElement("p"); note.textContent = "Replay chỉ có khi trận kết thúc. Trận đang chơi không được tiếp tục sau khi server restart."; card.append(note); }
    list.append(card);
  }
  document.querySelector("#page-label").textContent = `Trang ${page + 1} / ${Math.max(1, Math.ceil(total / 20))} · ${total} trận`;
}
const load = historyLoader(accountMatches, render, error => { list.replaceChildren(); document.querySelector("#page-label").textContent = ""; total = 0; fail(error); }, loading => {
  list.setAttribute("aria-busy", String(loading)); previous.disabled = loading || page === 0; next.disabled = loading || (page + 1) * 20 >= total;
  document.querySelector("#refresh-history").disabled = loading;
  if (loading) status.textContent = "Đang tải lịch sử…";
});
select.onchange = () => { page = 0; load(select.value, page); };
previous.onclick = () => load(select.value, page - 1); next.onclick = () => load(select.value, page + 1);
document.querySelector("#refresh-history").onclick = () => load(select.value, page);
try {
  if (await requireAccount()) {
    document.querySelector("[data-protected-content]").hidden = false; document.querySelector("#account-load-status").textContent = "";
    accountStats().then(stats => showStats(document.querySelector("#account-stats"), stats)).catch(error => { document.querySelector("#stats-status").textContent = errorText(error.message); });
    await load("all", 0);
  }
} catch (error) { document.querySelector("#account-load-status").textContent = errorText(error.message); }
