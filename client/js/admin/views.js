import { node, link, date, message } from "./admin-shell.js";
import { adminRequest } from "./admin-api.js";
import { mountDirectory } from "./directory.js";
import { mountMatches } from "./matches.js";
import { mountViewer } from "./viewer.js";
import { mountOperations } from "./operations.js";
import { mountReviews } from "./reviews.js";
import { mountTournaments } from "./tournaments.js";
import { mountProfile } from "./profile.js";
function panel(title) { const element=node("section",undefined,"panel");element.append(node("h2",title));return element; }
async function dashboard(root) {
  root.replaceChildren(node("h1","Tổng quan hệ thống"));
  const live=node("p","Đang tải…","muted"), cards=node("div",undefined,"stats"), activity=panel("Hoạt động gần đây"), actions=panel("Truy cập nhanh");
  for(const [label,href] of [["Trận giải đang chạy","/admin/matches.html?status=RUNNING"],["Bot lỗi","/admin/bots.html?status=BUILD_ERROR"],["Hệ thống","#system-summary"]]) actions.append(link(label,href),node("span"," · "));
  actions.append(link("Tranh chấp đang mở","/admin/disputes.html?status=OPEN"));
  const system=panel("Runtime"),alerts=panel("Cảnh báo vận hành");system.id="system-summary";root.append(live,cards,actions,alerts,system,activity);
  let busy=false;
  async function refresh(){if(busy||document.hidden)return;busy=true;try{
    const data=await adminRequest("dashboard");cards.replaceChildren();
    for(const [label,key] of [["Người dùng","totalUsers"],["Đang đăng nhập","activeUsers"],["Bot đã tải","uploadedBots"],["Phòng hoạt động","activeMatches"],["Giải đang chạy","runningTournaments"],["Tranh chấp","openDisputes"],["Trận trong hàng đợi","queuedMatches"],["Trận lỗi hạ tầng","systemErrors"]]){const card=node("div",undefined,"stat-card");card.append(node("span",label),node("strong",data[key]??"Chưa hỗ trợ"));cards.append(card);}
    live.textContent="Cập nhật lúc "+date(data.checkedAt)+" · Tự làm mới mỗi 10 giây";
    alerts.replaceChildren(node("h2","Cảnh báo vận hành"));for(const alert of data.alerts||[])alerts.append(node("p",alert.severity+" · "+alert.message));if(!data.alerts?.length)alerts.append(node("p","Không có cảnh báo."));alerts.append(link("Chi tiết hệ thống","/admin/system.html"));
    system.replaceChildren(node("h2","Runtime"),node("p",`Worker ${data.workers.active}/${data.workers.maxConcurrentMatches} · Queue ${data.workers.pending}/${data.workers.maxPendingMatches} · WebSocket ${data.webSocketSessions??"—"}`),node("p","Container do ứng dụng quản lý: "+(data.dockerContainers??"Docker chưa sẵn sàng")+" · Yêu cầu xem xét trận đang mở: "+(data.openDisputes??"—"),"muted"));
    activity.replaceChildren(node("h2","Hoạt động gần đây"));const list=node("ul",undefined,"activity");for(const event of data.activity){const item=node("li",date(event.occurred_at)+" · "+event.event_type+" · "+(event.target_id??""));if(event.tournament_id)item.append(node("span"," · "),link("Giải đấu","/admin/tournament-detail.html?id="+encodeURIComponent(event.tournament_id)));list.append(item);}activity.append(list);if(!data.activity.length)activity.append(node("p","Chưa có hoạt động.","empty"));
  }catch(error){message(error,live);}finally{busy=false;}}
  await refresh();const timer=setInterval(refresh,10000);addEventListener("pagehide",()=>clearInterval(timer),{once:true});
}
export async function mount(page,root){try{if(page==="profile.html")await mountProfile(root);else if(page==="index.html")await dashboard(root);else if(["audit.html","system.html"].includes(page))await mountOperations(page,root);else if(["disputes.html","dispute-detail.html","replays.html","replay-detail.html"].includes(page))await mountReviews(page,root);else if(["tournaments.html","tournament-detail.html"].includes(page))await mountTournaments(page,root);else if(["replay.html","spectator.html"].includes(page))await mountViewer(page,root);else if(["matches.html","match-detail.html"].includes(page))await mountMatches(page,root);else await mountDirectory(page,root);}catch(error){message(error,root);}}
