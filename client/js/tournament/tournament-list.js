import { refreshBots, savedBots } from "../api/bot-api.js";
import {
  tournamentRequest,
  createTournament,
  addParticipant,
} from "./tournament-api.js?v=open-registration";
import { statusLabels } from "./ranking-renderer.js";
import { toast } from "../ui/toast.js";
await refreshBots().catch(error => toast(error.message, true));
const bots = savedBots();
const options = document.querySelector("#bot-options");
for (const bot of bots) {
  const row = document.createElement("label");
  row.className = "bot-option";
  const input = document.createElement("input");
  input.type = "checkbox";
  input.value = bot.id;
  row.append(input, document.createTextNode(`${bot.name} · ${bot.language}`));
  options.append(row);
}
document.querySelector("#bot-empty").hidden = bots.length > 0;
let stopped = false,
  busy = false,
  timer;
async function load() {
  try {
    const tournaments = await tournamentRequest();
    if (stopped) return;
    const list = document.querySelector("#tournament-list");
    list.replaceChildren();
    if (!tournaments.length) {
      const empty = document.createElement("p");
      empty.className = "panel";
      empty.textContent = "Chưa có giải đấu. Hãy tạo giải đấu đầu tiên.";
      list.append(empty);
    }
    for (const t of tournaments) {
      const card = document.createElement("article");
      card.className = "panel tournament-card";
      const name = document.createElement("h3");
      name.textContent = t.name;
      const status = document.createElement("span");
      status.className = "status-badge";
      status.textContent = statusLabels[t.status];
      const info = document.createElement("p");
      info.textContent = `Round Robin · ${t.participants.length} bot · ${t.progress.completed}/${t.progress.total} trận`;
      const link = document.createElement("a");
      link.className = "button primary";
      link.href = "/tournament.html?id=" + encodeURIComponent(t.id);
      link.textContent = t.status === "DRAFT" ? "Tham gia giải" : "Xem giải đấu";
      card.append(name, status, info, link);
      list.append(card);
    }
  } catch (error) {
    if (!stopped) toast(error.message, true);
  }
  if (!stopped) timer = setTimeout(load, 5000);
}
document.querySelector("#create-tournament").onsubmit = async (event) => {
  event.preventDefault();
  if (busy) return;
  const chosen = [...options.querySelectorAll("input:checked")].map((input) =>
    bots.find((bot) => bot.id === input.value),
  );
  if (chosen.length > 10) {
    toast("Mỗi giải tối đa 10 bot.", true);
    return;
  }
  busy = true;
  document.querySelector("#create").disabled = true;
  let t;
  try {
    t = await createTournament(
      document.querySelector("#tournament-name").value.trim(),
    );
    for (const bot of chosen) await addParticipant(t.id, bot);
    location.href = "/tournament.html?id=" + encodeURIComponent(t.id);
  } catch (error) {
    toast(error.message, true);
    if (t) location.href = "/tournament.html?id=" + encodeURIComponent(t.id);
    else {
      busy = false;
      document.querySelector("#create").disabled = false;
    }
  }
};
document.querySelector("#join-tournament").onsubmit = (event) => {
  event.preventDefault();
  const value = document.querySelector("#tournament-code").value.trim();
  let target = value;
  try { target = new URL(value).searchParams.get("id") || ""; } catch {}
  if (!/^[\da-f]{8}-[\da-f]{4}-[\da-f]{4}-[\da-f]{4}-[\da-f]{12}$/i.test(target)) {
    toast("Nhập mã giải đấu hoặc link mời hợp lệ.", true);
    return;
  }
  location.href = "/tournament.html?id=" + encodeURIComponent(target);
};
window.addEventListener("pagehide", () => {
  stopped = true;
  clearTimeout(timer);
});
load();
