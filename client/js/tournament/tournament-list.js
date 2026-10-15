import {mountHub} from "./tournament-hub.js";
import { refreshBots, savedBots } from "../api/bot-api.js";
import {
  tournamentRequest,
  createTournament,
  addParticipant,
} from "./tournament-api.js?v=final-v5";
import { filterTournaments, tournamentGroup } from "./tournament-list-model.js";
import { formatLabel } from "./bracket-renderer.js";
import { statusLabels } from "./ranking-renderer.js";
import { toast } from "../ui/toast.js";
import { getAccount, loginLink } from "../account/account-session.js";
const account = await getAccount().catch(error => { toast(error.message, true); return null; });
if (!account) {
  document.querySelectorAll("#create-tournament input,#create-tournament select,#create-tournament button").forEach(element => element.disabled = true);
  const link = document.createElement("a"); link.href = loginLink(); link.textContent = "Đăng nhập để tạo giải và đăng ký bot của bạn.";
  document.querySelector("#tournament-account-notice").append(link);
}
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
let busy=false;
document.querySelector("#create-tournament").onsubmit = async (event) => {
  event.preventDefault();
  if (busy) return;
  const chosen = [...options.querySelectorAll("input:checked")].map((input) =>
    bots.find((bot) => bot.id === input.value),
  );
  const maxParticipants = Number(document.querySelector("#max-participants").value);
  if (chosen.length > maxParticipants) {
    toast(`Giải này tối đa ${maxParticipants} bot.`, true);
    return;
  }
  busy = true;
  document.querySelector("#create").disabled = true;
  let t;
  try {
    t = await createTournament(
      document.querySelector("#tournament-name").value.trim(),
      {maxParticipants, format:document.querySelector("#tournament-format").value, maxConcurrentMatches:Number(document.querySelector("#match-concurrency").value), checkInRequired: document.querySelector("#check-in-required").checked},
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


});
mountHub(document.querySelector("#tournament-list"));

