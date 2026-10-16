import {stagePlanEditor,validateStagePlan} from "./stage-plan.js";
import{sessionRequest}from "../api/auth-api.js";
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
const stageBox=document.createElement("fieldset");const legend=document.createElement("legend");legend.textContent="Các giai đoạn vòng bảng";stageBox.append(legend);document.querySelector("#format-help").after(stageBox);const readStages=stagePlanEditor(stageBox);const syncStages=()=>stageBox.hidden=document.querySelector("#tournament-format").value!=="GROUP_KNOCKOUT";document.querySelector("#tournament-format").addEventListener("change",syncStages);syncStages();
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
    const plans=document.querySelector("#tournament-format").value==="GROUP_KNOCKOUT"?validateStagePlan(readStages(),maxParticipants):null;
    t = await createTournament(
      document.querySelector("#tournament-name").value.trim(),
      {bestOf:Number(document.querySelector("#tournament-best-of").value),participantPolicy:document.querySelector("#participant-policy").value,maxParticipants, format:document.querySelector("#tournament-format").value, maxConcurrentMatches:Number(document.querySelector("#match-concurrency").value), checkInRequired: document.querySelector("#check-in-required").checked},
    );
    if(plans)t=await sessionRequest("/api/tournaments/"+encodeURIComponent(t.id)+"/stage-plan",{revision:t.revision,stages:plans});
    const stamp=id=>document.querySelector("#"+id).value?new Date(document.querySelector("#"+id).value).toISOString():null;const timing={revision:t.revision,registrationDeadline:stamp("registration-deadline"),checkInDeadline:stamp("check-in-deadline"),startTime:stamp("scheduled-start")};if(timing.registrationDeadline||timing.checkInDeadline||timing.startTime)await sessionRequest("/api/tournaments/"+encodeURIComponent(t.id)+"/timing",timing);
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

mountHub(document.querySelector("#tournament-list"));
document.querySelector("#participant-policy").onchange=()=>{const humanOnly=document.querySelector("#participant-policy").value==="HUMAN_ONLY";for(const input of options.querySelectorAll("input")){input.disabled=humanOnly;if(humanOnly)input.checked=false;}};

const formatControl=document.querySelector("#tournament-format"),formatHelp=document.querySelector("#format-help"),capacityControl=document.querySelector("#max-participants");
function showFormatHelp(){const format=formatControl.value;formatHelp.textContent=({ROUND_ROBIN:"Mỗi cặp gặp nhau một lần; thắng 3, hòa 1, thua 0 điểm.",SINGLE_ELIMINATION:"Thua một cặp đấu sẽ dừng bước; người thắng đi tiếp theo nhánh.",GROUP_KNOCKOUT:"Cần ít nhất 4 người/bot. Chọn số bảng và top N cho từng giai đoạn bên dưới; sau đó thi đấu loại trực tiếp.",SWISS:"Ghép các đối thủ gần điểm qua nhiều vòng, không gặp lại. BYE được 3 điểm và không tạo phòng đấu.",TEAM_ROUND_ROBIN:"Cần ít nhất 2 đội đủ 3 người/bot. Sau khi được duyệt, vào Chi tiết giải → Đồng đội để tạo/gia nhập đội. Đội trưởng nộp đội hình trước mỗi cặp đấu."})[format];capacityControl.min=format==="TEAM_ROUND_ROBIN"?6:format==="GROUP_KNOCKOUT"?4:2;if(Number(capacityControl.value)<Number(capacityControl.min))capacityControl.value=capacityControl.min;}
formatControl.addEventListener("change",showFormatHelp);showFormatHelp();
