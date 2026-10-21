import {matchOpen,matchStatusText} from './match-execution.js';
import {tournamentRoute} from './tournament-routes.js';
import {mountMatchMonitor} from './match-monitor.js';
import {allowsPreuploaded,allowedPolicyBot,renderBotPolicy} from "./bot-policy.js";
import {mountOrganizerShell} from './organizer-shell.js';
import {matchFilter,filteredMatches,filteredRegistrations} from './organizer-filters.js';
import {renderDeadlinePolicy} from './deadline-policy-editor.js';
import {stagePlanText} from "./stage-plan.js";
import {isKnockout} from "./bracket-renderer.js";
import {renderSwiss} from "./swiss-view.js";
import {renderGroups} from "./competition-view.js";
import { refreshBots, savedBots, uploadBot } from "../api/bot-api.js";
import {
  getTournament,
  addParticipant,
  removeParticipant,
  startTournament,
  cancelTournament,
  openRegistration,
  closeRegistration,
  configureSeeding, configureRules,
  registerBot, approveRegistration, rejectRegistration, withdrawRegistration, checkInRegistration, closeCheckIn,
  watchTournament,
  canManageTournament,
} from "./tournament-api.js?v=final-v5";
import { bindAdministration, renderDashboard, tournamentNotification } from "./tournament-dashboard.js";
import { formatLabel, roundLabel, tieBreakLabels, renderBracket } from "./bracket-renderer.js";
import { statusLabels, renderRanking } from "./ranking-renderer.js";
import { toast } from "../ui/toast.js";
import { getAccount, loginLink } from "../account/account-session.js";
import { confirmAction } from "../ui/modal.js";
await refreshBots().catch(error => toast(error.message, true));
const id = new URLSearchParams(location.search).get("id");
const account = await getAccount().catch(() => null);
if (!account) {
  const notice = document.createElement("p"), link = document.createElement("a");
  link.href = loginLink(); link.textContent = "Đăng nhập để đăng ký bot của bạn vào giải."; notice.append(link);
  document.querySelector("#registration").before(notice);
}
let organizerShell,matchMonitor;
let latest = -1,
  busy = false, currentTournament;
async function action(operation) {
  if (busy) return;
  busy = true;
  document.querySelectorAll("#registration button").forEach(button => button.disabled = true);
  try {
    render(await operation());
  } catch (error) {
    toast(error.message, true);
  } finally {
    busy = false, currentTournament;
    document.querySelectorAll("#registration button").forEach(button => button.disabled = false);
  }
}
function render(t) {
  if (t.revision < latest) return;
  latest = t.revision;currentTournament = t;
  const owner = canManageTournament(t, account);
  if(t.timeline&&!owner){location.replace("/tournament-detail.html?id="+encodeURIComponent(t.id));return;}
  document.querySelector("#name").textContent = t.name;
  const p = t.progress;
  const draft = ["DRAFT", "REGISTRATION"].includes(t.status);
  document.querySelector("#invite-link").value = location.origin+tournamentRoute(id);
  document.querySelector("#invite-code").textContent = id;
  document.querySelector("#registration").hidden = !account || !draft || (t.registrations || []).filter(r => ["PENDING","APPROVED","CHECKED_IN"].includes(r.status)).length >= t.maxParticipants;
  document.querySelector("#registration-help").textContent = draft
    ? `Giải tối đa ${t.maxParticipants} bot. Đăng ký trong giai đoạn mở sẽ chờ chủ giải duyệt.`
    : t.status === "CHECK_IN" ? "Check-in đã mở. Chủ bot cần xác nhận có mặt trước khi chủ giải hoàn tất." : "Giải đã khóa đăng ký. Bạn vẫn có thể xem trận đấu và bảng xếp hạng.";
  document.querySelector("#open-registration").hidden = !owner || t.status !== "DRAFT";
  document.querySelector("#close-registration").hidden = !owner || Boolean(t.timeline) || t.status !== "REGISTRATION";
  document.querySelector("#close-registration").disabled = t.participants.length < (t.format==="GROUP_KNOCKOUT"?4:t.format==="TEAM_ROUND_ROBIN"?6:2);
  document.querySelector("#start").hidden = !owner || Boolean(t.timeline) || !(["DRAFT", "READY"].includes(t.status) && !(t.status === "DRAFT" && t.checkInRequired));
  document.querySelector("#start").textContent = t.status === "DRAFT" ? "Bắt đầu nhanh" : "Bắt đầu giải";
  document.querySelector("#start").disabled = t.participants.length < (t.format==="GROUP_KNOCKOUT"?4:t.format==="TEAM_ROUND_ROBIN"?6:2);
  document.querySelector("#cancel").hidden = !owner || ["FINISHED", "CANCELLED"].includes(
    t.status,
  );
  document.querySelector("#close-check-in").hidden = !owner || Boolean(t.timeline) || t.status !== "CHECK_IN";
  document.querySelector("#close-check-in").disabled = (t.registrations || []).filter(r => r.status === "CHECKED_IN").length < (t.format==="GROUP_KNOCKOUT"?4:t.format==="TEAM_ROUND_ROBIN"?6:2);
  let submissionLink=document.querySelector("#submission-dashboard-link");if(!submissionLink){submissionLink=document.createElement("a");submissionLink.id="submission-dashboard-link";submissionLink.className="button";submissionLink.textContent="Bài nộp bot và hàng đợi →";document.querySelector("#admin-tab").prepend(submissionLink);}submissionLink.hidden=!owner;submissionLink.href="/tournament-submissions.html?id="+encodeURIComponent(id);
  let applicationLink=document.querySelector("#application-review-link");if(!applicationLink){applicationLink=document.createElement("a");applicationLink.id="application-review-link";applicationLink.className="button";applicationLink.textContent="Duyệt đăng ký người chơi →";document.querySelector("#admin-tab").prepend(applicationLink);}applicationLink.hidden=!owner||!t.timeline;applicationLink.href="/tournament-applications.html?id="+encodeURIComponent(id);
  let deadlinePolicy=document.querySelector('#deadline-policy');if(!deadlinePolicy){deadlinePolicy=document.createElement('section');deadlinePolicy.id='deadline-policy';document.querySelector('#admin-tab').prepend(deadlinePolicy);}if(owner)renderDeadlinePolicy(deadlinePolicy,t,async()=>render(await getTournament(id)));else deadlinePolicy.hidden=true;
  let finalLink=document.querySelector('#final-validation-link');if(!finalLink){finalLink=document.createElement('a');finalLink.id='final-validation-link';finalLink.className='button';finalLink.textContent='Kiểm thử chính thức →';document.querySelector('#admin-tab').prepend(finalLink);}finalLink.hidden=!owner||!t.timeline;finalLink.href='/tournament-final-validation.html?id='+encodeURIComponent(id);
  renderRegistrations(t, owner);
  renderSeeding(t, owner);renderRules(t,owner);
  document.querySelector("#format-heading").textContent="Tổng quan · "+formatLabel(t.format);
  let botPolicySummary=document.querySelector("#bot-policy-summary");if(!botPolicySummary){botPolicySummary=document.createElement("div");botPolicySummary.id="bot-policy-summary";document.querySelector("#format-heading").after(botPolicySummary);}botPolicySummary.replaceChildren();renderBotPolicy(botPolicySummary,t);if(!allowsPreuploaded(t)||t.submissionValidationRequired)document.querySelector("#registration").hidden=true;
  let stageSummary=document.querySelector("#stage-plan-summary");if(!stageSummary){stageSummary=document.createElement("p");stageSummary.id="stage-plan-summary";document.querySelector("#format-heading").after(stageSummary);}stageSummary.hidden=t.format!=="GROUP_KNOCKOUT";stageSummary.textContent=stagePlanText(t.groupPlans);
  let teamActions=document.querySelector("#manage-team-link");if(!teamActions){teamActions=document.createElement("a");teamActions.id="manage-team-link";teamActions.className="button";document.querySelector("#format-heading").after(teamActions);}teamActions.hidden=t.format!=="TEAM_ROUND_ROBIN";teamActions.href="/tournament-detail.html?id="+encodeURIComponent(t.id)+"#teams";teamActions.textContent="Đội, đội hình và kết quả đồng đội →";
  document.querySelector("#competition-policy").textContent=t.format==="SWISS"?"Swiss: ghép theo điểm, không gặp lại đối thủ; BYE +3 điểm, không tạo phòng. Buchholz là tổng điểm các đối thủ đã gặp.":t.format==="TEAM_ROUND_ROBIN"?"Hai đội đấu 3 vị trí theo đội hình đã khóa. Điểm đội tính theo kết quả cả cặp đội; không lấy thứ hạng cá nhân làm vô địch đồng đội.":isKnockout(t.format) ? "Hòa: bot còn nhiều quân hơn đi tiếp; nếu bằng nhau, ưu tiên hạt giống cao hơn. BYE được đi tiếp, không cộng điểm." : "Mỗi cặp gặp nhau một lần. Số bot lẻ: mỗi vòng có một bot nghỉ (BYE).";
  document.querySelector("#ranking-policy").textContent=(isKnockout(t.format)?"Ưu tiên vòng đi tới và kết quả bracket. Sau đó: ":"")+(t.tieBreakOrder||[]).map(rule=>tieBreakLabels[rule]).join(" → ")+" → ID bot trong giải. Thời gian tính nước gồm khởi chạy sandbox; chưa có dữ liệu được xếp sau.";
  document.querySelector('[data-tab="bracket"]').hidden=!isKnockout(t.format);
  renderBracket(document.querySelector("#bracket-tree"),t);
  const champion = document.querySelector("#champion");
  champion.hidden = !t.champion;
  champion.textContent = t.champion
    ? `🏆 VÔ ĐỊCH · ${t.champion.displayName} · ${t.champion.points} điểm`
    : "";
  const counts = document.querySelector("#counts");
  counts.replaceChildren();
  for (const [label, value] of [
    ["Vòng hiện tại", t.currentRound || "—"],
    ["Người/bot tham gia", t.participants.length],
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
    name.textContent = `${bot.seedNumber ? "#"+bot.seedNumber+" · " : ""}${bot.displayName}${bot.artifactHash ? " · Đã khóa bot" : ""}`;
    row.append(name);
    const ownedBot = savedBots().find((p) => p.id === bot.botId);
    if (draft && (owner || ownedBot || (account?.id && account.id === bot.ownerUserId))) {
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
    (bot) => !t.participants.some((p) => p.botId === bot.id) && !(t.registrations || []).some(r => r.botId === bot.id && ["PENDING","APPROVED","CHECKED_IN"].includes(r.status)),
  ))
    available.append(new Option(`${bot.name} · ${bot.language}`, bot.id));
  if ([...available.options].some((o) => o.value === selected))
    available.value = selected;
  document.querySelector("#add-participant").hidden =
    t.participantPolicy==="HUMAN_ONLY" || !draft || !available.options.length || (t.registrations || []).filter(r => ["PENDING","APPROVED","CHECKED_IN"].includes(r.status)).length >= t.maxParticipants;
  const monitorParent=document.querySelector('#matches');let monitorRoot=document.querySelector('#organizer-match-monitor');
  document.querySelector('#organizer-match-filter').hidden=owner;document.querySelector('label[for="organizer-match-filter"]').hidden=owner;
  if(owner){if(!monitorRoot){monitorRoot=document.createElement('div');monitorRoot.id='organizer-match-monitor';monitorParent.prepend(monitorRoot);matchMonitor=mountMatchMonitor(monitorRoot,t);}else matchMonitor?.update(t);}else{matchMonitor?.stop();monitorRoot?.remove();matchMonitor=null;}
  const matches = document.querySelector("#match-list");
  matches.hidden=owner;
  matches.replaceChildren();
  const matchFilterControl=document.querySelector('#organizer-match-filter');matchFilterControl.value=matchFilter(new URLSearchParams(location.search).get('filter'));const visibleMatches=filteredMatches(t,matchFilterControl.value);
  if (!visibleMatches.length) {
    const text = document.createElement("p");
    text.className = "panel";
    text.textContent = t.matches.length?'Không có trận phù hợp với bộ lọc.':"Lịch đấu được tạo khi bắt đầu giải.";
    matches.append(text);
  }
  let currentRound;
  for (const m of [...visibleMatches].sort((a,b)=>(a.round||1)-(b.round||1))) {
    if(currentRound!==m.round) {
      currentRound=m.round;const heading=document.createElement("h3");heading.textContent=roundLabel(m.round||1,t.rounds?.length||1,t.format);matches.append(heading);
      const byes=t.rounds?.find(r=>r.number===m.round)?.byeParticipantIds||[];
      if(byes.length){const note=document.createElement("p");note.className="round-bye";note.textContent="BYE · "+byes.map(id=>t.participants.find(p=>p.participantId===id)?.displayName||id).join(", ");matches.append(note);}
    }
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
    if (m.status === "FINISHED" && !m.reviewRequired) {
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
    card.append(document.createTextNode(matchStatusText(m)));const open=matchOpen(m,t.id);if(open)card.append(document.createElement('br'),open);
    matches.append(card);
  }
  let groups=document.querySelector("#group-standings");if(!groups){groups=document.createElement("div");groups.id="group-standings";document.querySelector("#ranking-policy").after(groups);}groups.replaceChildren();renderSwiss(groups,t);renderGroups(groups,t);if(t.teamCompetition){const teamLink=document.createElement("a");teamLink.href="/tournament-detail.html?id="+encodeURIComponent(t.id)+"#teams";teamLink.textContent="Đội, đội hình và bảng điểm đồng đội →";groups.append(teamLink);}
  renderRanking(document.querySelector("#ranking-rows"), t.ranking);renderDashboard(t,owner);
  if(owner){
    if(!organizerShell){
      document.querySelector('#name').hidden=true;document.querySelector('.tab-bar').hidden=true;
      document.querySelector('#overview').prepend(document.querySelector('.tournament-invite'));
      document.querySelector('#ranking').prepend(document.querySelector('#champion'));
      const selectPanel=target=>{for(const key of ['overview','participants-tab','matches','bracket','ranking','replays-tab','admin-tab','organizer-audit'])document.getElementById(key).hidden=key!==target;document.querySelectorAll('[data-tab]').forEach(button=>button.setAttribute('aria-pressed',String(button.dataset.tab===target)));};
      organizerShell=mountOrganizerShell(document.querySelector('main'),{tournament:t,account,section:new URLSearchParams(location.search).get('view'),selectPanel,onSnapshot:render});
    }else organizerShell.update(t);
  }
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
document.querySelector("#open-registration").onclick = () => action(() => openRegistration(id));
document.querySelector("#close-registration").onclick = () =>
  confirmAction("Khóa đăng ký?", "Đơn chưa duyệt sẽ bị từ chối. Không thể đăng ký bot mới. Nếu giải yêu cầu check-in, chủ bot phải xác nhận có mặt trước khi bắt đầu.", () => action(() => closeRegistration(id)));
document.querySelector("#close-check-in").onclick = () => confirmAction("Hoàn tất check-in?", "Bot chưa check-in sẽ không được đưa vào lịch đấu. Cần ít nhất hai bot đã check-in.", () => action(() => closeCheckIn(id)));

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
  if (bot) action(() => currentTournament.status === "REGISTRATION" ? registerBot(id, bot) : addParticipant(id, bot));
};
document.querySelector("#upload-participant").onsubmit = (event) => {
  event.preventDefault();
  action(async () => {
    const bot = await uploadBot(document.querySelector("#participant-name").value.trim(),
      document.querySelector("#participant-file").files[0]);
    const t = await (currentTournament.status === "REGISTRATION" ? registerBot(id, bot) : addParticipant(id, bot));
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
  watchTournament(id,tournamentNotification);

}

function renderRegistrations(t, owner) {
  const list = document.querySelector("#registration-list"); list.replaceChildren();
  const control=document.querySelector('#organizer-registration-filter'),params=new URLSearchParams(location.search),requested=params.get('filter');control.hidden=!owner;control.previousElementSibling.hidden=!owner;control.value=owner&&['PENDING','MISSING'].includes(requested)?requested:'ALL';const visible=filteredRegistrations(t,control.value);document.querySelector('#organizer-registration-filter-note').textContent=control.value==='MISSING'?'Danh sách dựa trên roster hợp lệ hiện có và các suất được ghi nhận lỡ hạn; không gồm ứng viên chưa final PASS.':'';
  const labels = {PENDING:"Chờ duyệt",APPROVED:"Đã duyệt",REJECTED:"Bị từ chối",CANCELLED:"Đã rút",CHECKED_IN:"Đã check-in",NOT_CHECKED_IN:"Không check-in"};
  for (const r of visible) {
    const row = document.createElement("div"); row.className="participant-row";
    const text = document.createElement("span"); text.textContent=`${r.displayName} · ${labels[r.status] || r.status}`; row.append(text);
    const buttons = document.createElement("div");buttons.className="actions";
    const add = (label, operation) => {const button=document.createElement("button");button.textContent=label;button.onclick=()=>action(operation);buttons.append(button);};
    if(owner && t.status === "REGISTRATION" && r.status === "PENDING")add("Duyệt",()=>approveRegistration(id,r.id));
    if(owner && t.status === "REGISTRATION" && ["PENDING","APPROVED"].includes(r.status))add("Từ chối",()=>rejectRegistration(id,r.id));
    if(account?.id === r.userId && t.status === "CHECK_IN" && r.status === "APPROVED")add("Check-in",()=>checkInRegistration(id,r.id));
    if((account?.id === r.userId || owner) && ["REGISTRATION","CHECK_IN"].includes(t.status) && ["PENDING","APPROVED","CHECKED_IN"].includes(r.status))add("Rút đăng ký",()=>withdrawRegistration(id,r.id));
    row.append(buttons);list.append(row);
  }
  if(!list.childElementCount)list.textContent=control.value==='ALL'?"Chưa có đơn đăng ký.":'Không có đơn phù hợp với bộ lọc.';
}

for(const [selector,view]of [['#organizer-match-filter','matches'],['#organizer-registration-filter','check-in']])document.querySelector(selector).onchange=event=>{const url=new URL(location.href);url.searchParams.set('view',view);if(event.target.value==='ALL')url.searchParams.delete('filter');else url.searchParams.set('filter',event.target.value);history.replaceState(null,'',url);if(currentTournament){organizerShell?.select(view);render(currentTournament);}};

function renderSeeding(t, owner) {
  const section=document.querySelector("#seeding");section.hidden=!owner;
  const editable=["DRAFT","REGISTRATION","CHECK_IN"].includes(t.status);
  const strategy=document.querySelector("#seeding-strategy"), seed=document.querySelector("#seeding-seed");
  if(document.activeElement!==strategy)strategy.value=t.seedingStrategy;
  if(document.activeElement!==seed)seed.value=t.seedingSeed;
  document.querySelector("#save-seeding").disabled=!editable;
  strategy.disabled=!editable;seed.disabled=!editable;
  const rows=document.querySelector("#manual-seeds");
  const previous=[...rows.querySelectorAll("input")];
  if(editable && rows.contains(document.activeElement) && previous.map(input=>input.dataset.participantId).join(",")===t.participants.map(p=>p.participantId).join(","))return;
  rows.replaceChildren();
  t.participants.forEach((p,index)=>{const label=document.createElement("label");label.textContent=p.displayName;
    const input=document.createElement("input");input.type="number";input.min="1";input.max=String(t.participants.length);input.value=String(p.manualSeed ?? index+1);input.dataset.participantId=p.participantId;input.disabled=!editable;label.append(input);rows.append(label);});
  rows.hidden=strategy.value!=="MANUAL";
}
document.querySelector("#seeding-strategy").onchange=()=>document.querySelector("#manual-seeds").hidden=document.querySelector("#seeding-strategy").value!=="MANUAL";
document.querySelector("#seeding-form").onsubmit=event=>{
  event.preventDefault();const strategy=document.querySelector("#seeding-strategy").value,seed=document.querySelector("#seeding-seed").value.trim();
  if(!/^-?[0-9]+$/.test(seed) || BigInt(seed)<-9223372036854775808n || BigInt(seed)>9223372036854775807n){toast("Seed ngẫu nhiên phải là số nguyên 64-bit.",true);return;}
  const manualSeeds=Object.fromEntries([...document.querySelectorAll("#manual-seeds input")].map(input=>[input.dataset.participantId,Number(input.value)]));
  if(strategy==="MANUAL" && new Set(Object.values(manualSeeds)).size!==Object.keys(manualSeeds).length){toast("Seed thủ công không được trùng nhau.",true);return;}
  action(()=>configureSeeding(id,{strategy,seed,manualSeeds:strategy==="MANUAL"?manualSeeds:null}));
};

bindAdministration(()=>currentTournament,action);
const ruleInputs=document.querySelector("#tie-break-inputs");
for(let index=0;index<4;index++) {
  const label=document.createElement("label");label.textContent=`Tiêu chí phụ ${index+1}`;
  const select=document.createElement("select");select.append(new Option("Không sử dụng",""));
  for(const [value,text] of Object.entries(tieBreakLabels))if(value!=="POINTS")select.append(new Option(text,value));
  label.append(select);ruleInputs.append(label);
}
function renderRules(t,owner) {
  const section=document.querySelector("#competition-rules");section.hidden=!owner;
  const editable=["DRAFT","REGISTRATION","CHECK_IN"].includes(t.status);
  const concurrency=document.querySelector("#rule-concurrency");
  if(!section.contains(document.activeElement)) {
    concurrency.value=t.maxConcurrentMatches;
    section.querySelectorAll("select").forEach((select,index)=>select.value=t.tieBreakOrder?.[index+1]||"");
  }
  section.querySelectorAll("input,select,button").forEach(input=>input.disabled=!editable);
}
document.querySelector("#rules-form").onsubmit=event=>{
  event.preventDefault();const tieBreakOrder=["POINTS",...[...ruleInputs.querySelectorAll("select")].map(select=>select.value).filter(Boolean)];
  if(new Set(tieBreakOrder).size!==tieBreakOrder.length){toast("Các tiêu chí phụ không được trùng nhau.",true);return;}
  action(()=>configureRules(id,{maxConcurrentMatches:Number(document.querySelector("#rule-concurrency").value),tieBreakOrder}));
};
