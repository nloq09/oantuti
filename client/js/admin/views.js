import { node, link, date, message } from "./admin-shell.js";
import { adminRequest } from "./admin-api.js";
import { mountDirectory } from "./directory.js";
function panel(title) { const element=node("section",undefined,"panel");element.append(node("h2",title));return element; }
async function dashboard(root) {
  root.replaceChildren(node("h1","Tổng quan hệ thống"));
  const live=node("p","Đang tải…","muted"), cards=node("div",undefined,"stats"), activity=panel("Hoạt động gần đây"), actions=panel("Truy cập nhanh");
  for(const [label,href] of [["Giải đang chạy","/tournaments.html?status=RUNNING"],["Bot lỗi","/admin/bots.html?status=BUILD_ERROR"],["Trận đang chạy","/admin/matches.html?status=RUNNING"],["Hệ thống","#system-summary"]]) actions.append(link(label,href),node("span"," · "));
  const system=panel("Runtime");system.id="system-summary";root.append(live,cards,actions,system,activity);
  let busy=false;
  async function refresh(){if(busy||document.hidden)return;busy=true;try{
    const data=await adminRequest("dashboard");cards.replaceChildren();
    for(const [label,key] of [["Người dùng","totalUsers"],["Đang đăng nhập","activeUsers"],["Bot đã tải","uploadedBots"],["Phòng hoạt động","activeMatches"],["Giải đang chạy","runningTournaments"],["Tranh chấp","openDisputes"],["Trận trong hàng đợi","queuedMatches"],["Trận lỗi hạ tầng","systemErrors"]]){const card=node("div",undefined,"stat-card");card.append(node("span",label),node("strong",data[key]??"Chưa hỗ trợ"));cards.append(card);}
    live.textContent="Cập nhật lúc "+date(data.checkedAt)+" · Tự làm mới mỗi 10 giây";
    system.replaceChildren(node("h2","Runtime"),node("p",`Worker ${data.workers.active}/${data.workers.maxConcurrentMatches} · Queue ${data.workers.pending}/${data.workers.maxPendingMatches} · WebSocket ${data.webSocketSessions??"—"}`),node("p","Số container Docker chưa được đo. Tranh chấp chưa có backend; không tính thành 0.","muted"));
    activity.replaceChildren(node("h2","Hoạt động gần đây"));const list=node("ul",undefined,"activity");for(const event of data.activity){const item=node("li",date(event.occurred_at)+" · "+event.event_type+" · "+(event.target_id??""));if(event.tournament_id)item.append(node("span"," · "),link("Giải đấu","/tournament.html?id="+encodeURIComponent(event.tournament_id)));list.append(item);}activity.append(list);if(!data.activity.length)activity.append(node("p","Chưa có hoạt động.","empty"));
  }catch(error){message(error,live);}finally{busy=false;}}
  await refresh();const timer=setInterval(refresh,10000);addEventListener("pagehide",()=>clearInterval(timer),{once:true});
}
export async function mount(page,root){try{if(page==="index.html")await dashboard(root);else await mountDirectory(page,root);}catch(error){message(error,root);}}
