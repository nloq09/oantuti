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
let renderVersion=0;
let stopped = false,
  busy = false,
  timer;
async function load() {
  const requestVersion=++renderVersion;clearTimeout(timer);
  try {
    const tournaments = await tournamentRequest();
    if (stopped||requestVersion!==renderVersion) return;
    const list = document.querySelector("#tournament-list");
    list.replaceChildren();
    if (!tournaments.length) {
      const empty = document.createElement("p");
      empty.className = "panel";
      empty.textContent = "Chưa có giải đấu. Hãy tạo giải đấu đầu tiên.";
      list.append(empty);
    }
    const ownerFilter=document.querySelector("#filter-owner"),previous=ownerFilter.value;
    const owners=[...new Map(tournaments.filter(t=>t.ownerUserId).map(t=>[t.ownerUserId,t.ownerName||t.ownerUserId])).entries()];
    if(ownerFilter.dataset.owners!==JSON.stringify(owners)){ownerFilter.dataset.owners=JSON.stringify(owners);ownerFilter.replaceChildren(new Option("Tất cả",""),...owners.map(([id,name])=>new Option(name,id)));ownerFilter.value=previous;}
    const filtered=filterTournaments(tournaments,{format:document.querySelector("#filter-format").value,status:document.querySelector("#filter-status").value,owner:ownerFilter.value});
    let group;
    for (const t of filtered.sort((a,b)=>tournamentGroup(a.status).localeCompare(tournamentGroup(b.status),"vi"))) {
      const groupName=tournamentGroup(t.status);if(group!==groupName){group=groupName;const heading=document.createElement("h3");heading.textContent=groupName;list.append(heading);}
      const card = document.createElement("article");
      card.className = "panel tournament-card";
      const name = document.createElement("h3");
      name.textContent = t.name;
      const status = document.createElement("span");
      status.className = "status-badge";
      status.textContent = statusLabels[t.status];
      const info = document.createElement("p");
      info.textContent = `${formatLabel(t.format)} · ${t.participants.length} bot · ${t.progress.completed}/${t.progress.total} trận`;
      const link = document.createElement("a");
      link.className = "button primary";
      link.href = "/tournament.html?id=" + encodeURIComponent(t.id);
      link.textContent = ["DRAFT", "REGISTRATION"].includes(t.status) ? "Tham gia giải" : "Xem giải đấu";
      card.append(name, status, info, link);
      list.append(card);
    }
  } catch (error) {
    if (!stopped&&requestVersion===renderVersion) toast(error.message, true);
  }
  if (!stopped&&requestVersion===renderVersion) timer = setTimeout(load, 5000);
}
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
  stopped = true;
  clearTimeout(timer);
});
load();

for(const id of ['filter-format','filter-status','filter-owner'])document.querySelector('#'+id).onchange=load;
