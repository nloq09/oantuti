import { node, link, date, message } from "./admin-shell.js";
import { adminRequest } from "./admin-api.js";
import { sessionRequest } from "../api/auth-api.js";
import { table, badge, details, confirmAction } from "./components.js";
const columns=[["ID","id"],["Chế độ","mode"],["P1","player1"],["P2","player2"],["Trạng thái","status",badge],["Lượt","turn_count"],["Bắt đầu","started_at",date],["Thời lượng (ms)","duration_ms"],["Giải đấu","tournament_id"]];
function links(cell,row){
  if(row.live_available&&row.room_id)cell.append(link("Xem trực tiếp","/admin/spectator.html?spectate="+encodeURIComponent(row.room_id)));
  if(row.replay_available)cell.append(link("Replay","/admin/replay.html?match="+encodeURIComponent(row.id)));
}
async function list(root){
  root.replaceChildren(node("h1","Trận đấu"));const form=node("form",undefined,"filters"),result=node("section",undefined,"panel");let params=new URLSearchParams(location.search),page=Math.max(0,Number(params.get("page"))||0),version=0;
  for(const [label,key,choices,type] of [["Tìm ID / người chơi","q"],["Trạng thái","status",["RUNNING","WAITING","PLACEMENT","PENDING","FINISHED","FAILED","ERROR"]],["Tournament ID","tournament"],["User ID","user"],["Bot ID","bot"],["Từ ngày (UTC)","from",null,"date"],["Đến ngày (UTC)","to",null,"date"]]){
    const field=node("label",label),input=node(choices?"select":"input");input.name=key;if(choices){input.append(new Option("Tất cả",""));for(const choice of choices)input.append(new Option(choice,choice));}else input.type=type??"search";input.value=params.get(key)??"";field.append(input);form.append(field);
  }
  form.append(node("button","Lọc / Cập nhật"));root.append(form,result);
  async function load(){const current=++version;result.replaceChildren(node("p","Đang tải…"));try{const query=new URLSearchParams(params);query.set("page",page);query.set("size","20");const data=await adminRequest("matches?"+query);if(current!==version)return;
    history.replaceState(null,"",location.pathname+"?"+query);result.replaceChildren(node("p","Cập nhật "+date(new Date()),"muted"),table(columns,data.items,(cell,row)=>{cell.append(link("Chi tiết","/admin/match-detail.html?id="+encodeURIComponent(row.id)));links(cell,row);}));
    const navigation=node("div",undefined,"pagination"),previous=node("button","← Trước"),next=node("button","Sau →");previous.disabled=page===0;next.disabled=(page+1)*data.size>=data.total;previous.onclick=()=>{page--;load();};next.onclick=()=>{page++;load();};navigation.append(node("span",`Trang ${page+1} · ${data.total} kết quả`),previous,next);result.append(navigation);
  }catch(error){if(current===version)message(error,result);}}
  form.onsubmit=event=>{event.preventDefault();params=new URLSearchParams(new FormData(form));page=0;load();};await load();
}
async function detail(root){
  const id=new URLSearchParams(location.search).get("id");if(!id)throw new Error("Thiếu mã trận đấu.");const data=await adminRequest("matches/"+encodeURIComponent(id));
  root.replaceChildren(node("h1","Chi tiết trận đấu"),link("← Danh sách","/admin/matches.html"),details("Trận đấu",data,columns),details("Kết quả / trạng thái",data,[["Kết quả","outcome"],["Bên thắng","winner_seat"],["Rule set","rule_set"],["Kết thúc","finished_at",date],["Lượt hiện tại","current_player"],["Sequence","sequence"],["Lỗi bot (mã an toàn)","bot_error"],["Lỗi hạ tầng","infrastructure_error"],["Lỗi giải đấu","failure"]]));
  const actions=node("div",undefined,"actions"),refresh=node("button","Cập nhật trạng thái");refresh.onclick=()=>detail(root).catch(error=>message(error,root));actions.append(refresh);links(actions,data);if(data.tournament_id)actions.append(link("Các trận cùng giải","/admin/matches.html?tournament="+encodeURIComponent(data.tournament_id)));root.append(actions);
  root.append(node("p","Xem trực tiếp sử dụng spectator chỉ đọc. Quản trị không điều khiển bàn cờ hoặc sửa kết quả game.","muted"));
  const participants=node("section",undefined,"panel");participants.append(node("h2","Người chơi / Bot"),table([["Bên","seat"],["Username","username"],["Controller","controller_type"],["Bot","bot_name"]],data.participants,(cell,row)=>{if(row.seat_user_id)cell.append(link("User","/admin/user-detail.html?id="+encodeURIComponent(row.seat_user_id)));if(row.seat_bot_id)cell.append(link("Bot","/admin/bot-detail.html?id="+encodeURIComponent(row.seat_bot_id)));}));root.append(participants);
  root.append(details("Thực thi · Chỉ phiên server hiện tại",data,[["P1 số nước bot","move_count_p1"],["P1 thời gian bot (ns)","move_nanos_p1"],["P2 số nước bot","move_count_p2"],["P2 thời gian bot (ns)","move_nanos_p2"]]));
  const timeline=node("section",undefined,"panel");timeline.append(node("h2","50 sự kiện giải đấu gần đây"),table([["Sự kiện","event_type"],["Lý do","reason"],["Trước","before_value"],["Sau","after_value"],["Lúc","occurred_at",date]],data.timeline));root.append(timeline);
  if(data.tournament_id&&data.tournament_match_id){
    const tournament=await sessionRequest("/api/tournaments/"+encodeURIComponent(data.tournament_id)),status=data.status;
    for(const [label,action,enabled] of [["Đánh dấu cần xem xét","review",["FINISHED","FAILED","INFRASTRUCTURE_ERROR"].includes(status)],["Thử lại trận lỗi","retry",["FAILED","INFRASTRUCTURE_ERROR"].includes(status)&&["PAUSED","RECOVERY_REQUIRED"].includes(tournament.status)]])if(enabled){const button=node("button",label);button.onclick=()=>confirmAction(label,async reason=>{await sessionRequest(`/api/tournaments/${encodeURIComponent(data.tournament_id)}/matches/${encodeURIComponent(data.tournament_match_id)}/${action}`,{revision:tournament.revision,reason,keepResult:false});await detail(root);});actions.append(button);}
  }
}
export const mountMatches=(page,root)=>page==="matches.html"?list(root):detail(root);
