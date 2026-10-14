import {tournamentControl,tournamentRoundPolicy,tournamentParticipantStatus,tournamentMatchOperation,tournamentRequest} from "./tournament-api.js?final-v5";
import {toast} from "../ui/toast.js";
import {confirmAction} from "../ui/modal.js";
const statusText={ACTIVE:"Đang thi đấu",WITHDRAWN:"Bỏ cuộc",DISQUALIFIED:"Bị loại"};
export function tournamentNotification(event){const labels={PARTICIPANT_APPROVED:"Bot đã được duyệt.",CHECK_IN_OPENED:"Giải đã mở check-in.",TOURNAMENT_STARTED:"Giải bắt đầu.",ROUND_STARTED:"Vòng đấu mới bắt đầu.",MATCH_STARTED:"Một trận đấu vừa bắt đầu.",TOURNAMENT_FINISHED:"Giải đã kết thúc.",TOURNAMENT_RECOVERY_REQUIRED:"Giải cần chủ giải kiểm tra để tiếp tục."};if(labels[event.type])toast(labels[event.type]);}
export function allowedControls(t){return {
  pause:t.status==="RUNNING",resume:["PAUSED","RECOVERY_REQUIRED"].includes(t.status)&&!Object.keys(t.reviews||{}).length&&!t.matches.some(m=>m.status==="FAILED"),
  "next-round":["PAUSED","RUNNING"].includes(t.status)&&!t.autoAdvanceRounds&&t.releasedRound<(t.rounds?.length||0)&&t.matches.filter(m=>m.round<=t.releasedRound).every(m=>m.status==="FINISHED"),
  finish:["RUNNING","PAUSED","RECOVERY_REQUIRED"].includes(t.status)&&!t.matches.some(m=>["PENDING","RUNNING"].includes(m.status)),
};}
function options(select,items){const key=JSON.stringify(items);if(select.dataset.options===key)return;select.dataset.options=key;const previous=select.value;select.replaceChildren(...items.map(([value,label])=>new Option(label,value)));if(items.some(([value])=>value===previous))select.value=previous;}
function card(m,live){const article=document.createElement("article");article.className="panel match-card";const title=document.createElement("h3");title.textContent=`${m.participantA.displayName} vs ${m.participantB.displayName}`;article.append(title);const text=document.createElement("p");text.textContent=`Vòng ${m.round} · ${live?"Lượt "+(m.turn??"—"):m.result||m.status}${m.decisionReason?" · Quyết định: "+m.decisionReason:""}`;article.append(text);
  const link=document.createElement("a");link.className="button primary";link.textContent=live?"Xem trực tiếp":m.decisionReason==="RESULT_OVERRIDE"?"Xem replay gốc":"Xem replay";link.href=(live?"/game.html?spectate=":"/replay.html?match=")+encodeURIComponent(live?m.onlineMatchId:m.historyMatchId);article.append(link);return article;}
export function renderDashboard(t,owner){
  document.querySelector(".tournament-invite").hidden=!["DRAFT","REGISTRATION","CHECK_IN"].includes(t.status);
  const adminButton=document.querySelector('[data-tab="admin-tab"]');adminButton.hidden=!owner;
  if(!owner&&adminButton.getAttribute("aria-pressed")==="true")document.querySelector('[data-tab="overview"]').click();
  document.querySelector("#tournament-phase").textContent=`${t.status} · ${t.autoAdvanceRounds?"Tự chuyển vòng":"Chuyển vòng thủ công"} · Bắt đầu: ${t.startedAt?new Date(t.startedAt).toLocaleString("vi-VN"):"Chưa bắt đầu"}`;
  document.querySelector("#export-tournament").href=`/api/tournaments/${encodeURIComponent(t.id)}/export`;
  const live=document.querySelector("#live-matches"),replays=document.querySelector("#replay-list");live.replaceChildren();replays.replaceChildren();
  for(const m of t.matches){if(m.status==="RUNNING"&&m.onlineMatchId)live.append(card(m,true));if(m.status==="FINISHED"&&m.historyMatchId)replays.append(card(m,false));}
  if(!live.childElementCount)live.textContent="Chưa có trận đang diễn ra.";if(!replays.childElementCount)replays.textContent="Chưa có replay. Trận xử thua hành chính không tạo replay giả.";
  const rows=document.querySelectorAll("#participants .participant-row");t.participants.forEach((p,index)=>{const description=document.createElement("small");const registration=t.registrations?.find(r=>r.id===p.participantId);description.textContent=`${t.participantOwners?.[p.participantId]||"Người chơi"} · ${statusText[t.participantStatuses?.[p.participantId]||"ACTIVE"]} · ${registration?.status||"—"}`;rows[index]?.append(description);});
  document.querySelector("#rules-snapshot").textContent=JSON.stringify(t.rulesSnapshot||{},null,2);
  const audit=document.querySelector("#tournament-audit");if(audit.dataset.count!==String(t.audit?.length||0)){audit.dataset.count=String(t.audit?.length||0);audit.replaceChildren();for(const event of [...(t.audit||[])].reverse()){const row=document.createElement("li");row.textContent=`#${event.sequence} · ${event.type} · ${new Date(event.at).toLocaleString("vi-VN")} · ${event.actor||"Server"}${event.reason?" · "+event.reason:""}${event.beforeValue?" · Trước: "+event.beforeValue:""}${event.afterValue?" · Sau: "+event.afterValue:""}`;audit.append(row);}}
  const editable=["DRAFT","REGISTRATION","CHECK_IN"].includes(t.status);const auto=document.querySelector("#auto-rounds");if(document.activeElement!==auto)auto.checked=t.autoAdvanceRounds;auto.disabled=!editable;document.querySelector("#save-round-policy").disabled=!editable;
  const allowed=allowedControls(t);document.querySelectorAll("[data-admin-control]").forEach(button=>button.disabled=!allowed[button.dataset.adminControl]);
  options(document.querySelector("#admin-participant"),t.participants.map(p=>[p.participantId,p.displayName+" · "+statusText[t.participantStatuses?.[p.participantId]||"ACTIVE"]]));
  options(document.querySelector("#admin-match"),t.matches.map(m=>[m.id,`V${m.round} · ${m.participantA.displayName} / ${m.participantB.displayName} · ${m.status} · lần ${m.attempt||1}`]));
  renderMatchReview(t);
}
function renderMatchReview(t){const m=t.matches.find(m=>m.id===document.querySelector("#admin-match").value),select=document.querySelector("#override-winner");
  options(select,m?[["","Hòa"],[m.participantA.participantId,m.participantA.displayName+" thắng"],[m.participantB.participantId,m.participantB.displayName+" thắng"]]:[]);
  document.querySelector("#review-details").textContent=m?`Kết quả: ${m.result||"—"} · Lỗi: ${m.failure||"Không"} · ${t.reviews?.[m.id]?"Đang review: "+t.reviews[m.id]:"Chưa đánh dấu review"}`:"Chọn một trận.";
  const logs=document.querySelector("#match-execution-log");logs.replaceChildren();for(const event of (t.audit||[]).filter(e=>e.target===m?.id)){const row=document.createElement("li");row.textContent=`${event.type} · ${new Date(event.at).toLocaleString("vi-VN")} · ${event.reason||""}`;logs.append(row);}
  const paused=["PAUSED","RECOVERY_REQUIRED"].includes(t.status);document.querySelector("#retry-match").disabled=!paused||m?.status!=="FAILED";
  document.querySelector("#request-review").disabled=!["FINISHED","FAILED"].includes(m?.status)||t.status==="CANCELLED";
  document.querySelector("#keep-result").disabled=!t.reviews?.[m?.id];document.querySelector("#override-result").disabled=!t.reviews?.[m?.id]||m?.status!=="FINISHED";
  document.querySelectorAll("[data-participant-status]").forEach(button=>button.disabled=!paused);
}
export function bindAdministration(current,action){
  const explanation=()=>{const reason=document.querySelector("#admin-reason").value.trim();if(!reason)throw new Error("Hãy nhập lý do cho thao tác này.");return reason;};
  const run=(operation,confirm=false)=>{try{const t=current();if(!t)return;if(confirm)confirmAction("Xác nhận quyết định giải đấu","Thao tác được lưu trong audit. Kết quả game và replay gốc vẫn được giữ.",()=>action(()=>operation(t)));else action(()=>operation(t));}catch(error){toast(error.message,true);}};
  document.querySelectorAll("[data-admin-control]").forEach(button=>button.onclick=()=>run(t=>tournamentControl(t.id,button.dataset.adminControl,t.revision,button.dataset.adminControl==="finish"?explanation():undefined)));
  document.querySelector("#save-round-policy").onclick=()=>run(t=>tournamentRoundPolicy(t.id,t.revision,document.querySelector("#auto-rounds").checked));
  document.querySelectorAll("[data-participant-status]").forEach(button=>button.onclick=()=>run(t=>tournamentParticipantStatus(t.id,document.querySelector("#admin-participant").value,t.revision,button.dataset.participantStatus,explanation()),true));
  document.querySelector("#admin-match").onchange=()=>renderMatchReview(current());
  for(const [id,operation,extra] of [["request-review","review",{}],["keep-result","review",{keepResult:true}],["retry-match","retry",{}],["override-result","override",{}]])document.querySelector("#"+id).onclick=()=>run(t=>tournamentMatchOperation(t.id,document.querySelector("#admin-match").value,operation,{revision:t.revision,reason:explanation(),...extra,...(operation==="override"?{winnerParticipantId:document.querySelector("#override-winner").value||null}:{})}),operation==="override");
  document.querySelector("#load-report").onclick=async()=>{try{const report=await tournamentRequest(`/${encodeURIComponent(current().id)}/report`);const target=document.querySelector("#tournament-report");target.replaceChildren();for(const [label,value] of [["Vô địch",report.champion?.displayName||"Chưa có"],["Á quân",report.runnerUp?.displayName||"Chưa có"],["Số trận",report.totalMatches],["Thời gian trung bình (ms)",Math.round(report.averageMatchDurationMillis)],["Lỗi bot",report.totalBotErrors],["Lỗi hạ tầng",report.infrastructureErrors]]){const row=document.createElement("p");row.textContent=label+": "+value;target.append(row);}}catch(error){toast(error.message,true);}};
}
