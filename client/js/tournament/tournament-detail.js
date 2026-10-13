import { refreshBots, savedBots, uploadBot } from "../api/bot-api.js";
import {
  getTournament,
  addParticipant,
  removeParticipant,
  startTournament,
  cancelTournament,
  TournamentStream,
  tournamentOwnerToken,
} from "./tournament-api.js?v=open-registration";
import { statusLabels, renderRanking } from "./ranking-renderer.js";
import { toast } from "../ui/toast.js";
import { confirmAction } from "../ui/modal.js";
await refreshBots().catch(error => toast(error.message, true));
const id = new URLSearchParams(location.search).get("id");
const owner = !!tournamentOwnerToken(id);
let latest = -1,
  busy = false;
document.querySelector("#export").href =
  "/api/tournaments/" + encodeURIComponent(id) + "/export";
async function action(operation) {
  if (busy) return;
  busy = true;
  document.querySelectorAll("#registration button").forEach(button => button.disabled = true);
  try {
    render(await operation());
  } catch (error) {
    toast(error.message, true);
  } finally {
    busy = false;
    document.querySelectorAll("#registration button").forEach(button => button.disabled = false);
  }
}
function render(t) {
  if (t.revision < latest) return;
  latest = t.revision;
  document.querySelector("#name").textContent = t.name;
  const p = t.progress;
  const draft = t.status === "DRAFT";
  document.querySelector("#invite-link").value = location.origin + "/tournament.html?id=" + encodeURIComponent(id);
  document.querySelector("#invite-code").textContent = id;
  document.querySelector("#registration").hidden = !draft || t.participants.length >= 10;
  document.querySelector("#registration-help").textContent = draft
    ? "Đăng ký bot trước khi chủ giải bắt đầu. Giải cần ít nhất 2 và tối đa 10 bot."
    : "Giải đã khóa đăng ký. Bạn vẫn có thể xem trận đấu và bảng xếp hạng.";
  document.querySelector("#start").hidden = !draft || !owner;
  document.querySelector("#start").disabled = t.participants.length < 2;
  document.querySelector("#cancel").hidden = !owner || ["FINISHED", "CANCELLED"].includes(
    t.status,
  );
  const champion = document.querySelector("#champion");
  champion.hidden = !t.champion;
  champion.textContent = t.champion
    ? `🏆 VÔ ĐỊCH · ${t.champion.displayName} · ${t.champion.points} điểm`
    : "";
  const counts = document.querySelector("#counts");
  counts.replaceChildren();
  for (const [label, value] of [
    ["Bot tham gia", t.participants.length],
    ["Tổng trận", p.total],
    ["Hoàn thành", p.completed],
    ["Đang chạy", p.running],
    ["Chờ chạy", p.pending],
    ["Lỗi hạ tầng", p.failed],
  ]) {
    const cell = document.createElement("div"),
      strong = document.createElement("strong");
    strong.textContent = value;
    cell.append(document.createTextNode(label), strong);
    counts.append(cell);
  }
  const participants = document.querySelector("#participants");
  participants.replaceChildren();
  for (const bot of t.participants) {
    const row = document.createElement("div");
    row.className = "participant-row";
    const name = document.createElement("span");
    name.textContent = bot.displayName;
    row.append(name);
    const ownedBot = savedBots().find((p) => p.id === bot.botId);
    if (draft && (owner || ownedBot)) {
      const remove = document.createElement("button");
      remove.textContent = "Gỡ";
      remove.onclick = () =>
        action(() => removeParticipant(id, bot.participantId, ownedBot));
      row.append(remove);
    }
    participants.append(row);
  }
  const available = document.querySelector("#available-bot"),
    selected = available.value;
  available.replaceChildren();
  for (const bot of savedBots().filter(
    (bot) => !t.participants.some((p) => p.botId === bot.id),
  ))
    available.append(new Option(`${bot.name} · ${bot.language}`, bot.id));
  if ([...available.options].some((o) => o.value === selected))
    available.value = selected;
  document.querySelector("#add-participant").hidden =
    !draft || !available.options.length || t.participants.length >= 10;
  const matches = document.querySelector("#match-list");
  matches.replaceChildren();
  if (!t.matches.length) {
    const text = document.createElement("p");
    text.className = "panel";
    text.textContent = "Lịch đấu được tạo khi bắt đầu giải.";
    matches.append(text);
  }
  for (const m of t.matches) {
    const card = document.createElement("article");
    card.className = "panel match-card";
    const title = document.createElement("h3");
    title.textContent = `${m.participantA.displayName} vs ${m.participantB.displayName}`;
    card.append(title);
    if (m.status !== "RUNNING") {
      const state = document.createElement("span");
      state.className = "status-badge";
      state.textContent = statusLabels[m.status];
      card.append(state);
    }
    if (m.status === "FINISHED") {
      const result = document.createElement("p");
      result.textContent = m.winnerParticipantId
        ? `Thắng: ${m.winnerParticipantId === m.participantA.participantId ? m.participantA.displayName : m.participantB.displayName}`
        : "Hòa";
      card.append(result);
    }
    if (m.failure) {
      const reason = document.createElement("p");
      reason.textContent =
        m.failure === "BOT_FAILED"
          ? "Bot lỗi hoặc quá thời gian; kết quả theo luật xử thua hiện tại."
          : "Lỗi: " + m.failure;
      card.append(reason);
    }
    if (m.onlineMatchId && ["RUNNING", "FINISHED"].includes(m.status)) {
      const link = document.createElement("a");
      link.className = "button primary";
      link.textContent =
        m.status === "RUNNING" ? "Xem trực tiếp" : "Xem replay";
      link.href =
        (m.status === "RUNNING"
          ? "/game.html?v=stage3&spectate="
          : "/replay.html?match=") + encodeURIComponent(m.onlineMatchId);
      card.append(document.createElement("br"), link);
    }
    matches.append(card);
  }
  renderRanking(document.querySelector("#ranking-rows"), t.ranking);
}
document.querySelectorAll("[data-tab]").forEach(
  (button) =>
    (button.onclick = () => {
      document.querySelectorAll("[data-tab]").forEach((item) => {
        const active = item === button;
        item.setAttribute("aria-pressed", String(active));
        document.querySelector("#" + item.dataset.tab).hidden = !active;
      });
    }),
);
document.querySelector("#start").onclick = () =>
  action(() => startTournament(id));
document.querySelector("#cancel").onclick = () =>
  confirmAction(
    "Hủy giải đấu?",
    "Các trận đang chạy sẽ bị dừng. Kết quả đã hoàn tất vẫn được giữ.",
    () => action(() => cancelTournament(id)),
  );
document.querySelector("#add-participant").onsubmit = (event) => {
  event.preventDefault();
  const bot = savedBots().find(
    (bot) => bot.id === document.querySelector("#available-bot").value,
  );
  if (bot) action(() => addParticipant(id, bot));
};
document.querySelector("#upload-participant").onsubmit = (event) => {
  event.preventDefault();
  action(async () => {
    const bot = await uploadBot(document.querySelector("#participant-name").value.trim(),
      document.querySelector("#participant-file").files[0]);
    const t = await addParticipant(id, bot);
    document.querySelector("#upload-participant").reset();
    toast("Bot đã đăng ký vào giải đấu.");
    return t;
  });
};
document.querySelector("#copy-invite").onclick = async () => {
  const link = document.querySelector("#invite-link");
  try { await navigator.clipboard.writeText(link.value); toast("Đã sao chép link mời."); }
  catch { link.focus(); link.select(); toast("Hãy sao chép link mời trong ô."); }
};
if (!id) location.replace("/tournaments.html");
else {
  getTournament(id)
    .then(render)
    .catch((error) => toast(error.message, true));
  const stream = new TournamentStream(id, render);
  window.addEventListener("pagehide", () => stream.close());
}
