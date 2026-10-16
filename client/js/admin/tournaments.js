import {renderTeams} from "../tournament/team-view.js";
import {renderSwiss} from "../tournament/swiss-view.js";
import {isKnockout} from "../tournament/bracket-renderer.js";
import {node,link,date,message} from "./admin-shell.js";
import {adminRequest} from "./admin-api.js";
import {sessionRequest} from "../api/auth-api.js";
import {table,badge,details,confirmAction} from "./components.js";
import {listPage,tabs,periodFields} from "./catalog.js";
import {lifecycleActions,tournamentStatuses} from "./tournament-model.js";
import {renderBracket} from "../tournament/bracket-renderer.js";
const matchColumns=[["ID","id"],["Vòng","round"],["P1","participantA",value=>value.displayName],["P2","participantB",value=>value.displayName],["Trạng thái","status",badge],["Kết quả","result"],["Lỗi","failure"]];
function matchLinks(cell,m){cell.append(link("Chi tiết","/admin/match-detail.html?id="+encodeURIComponent(m.historyMatchId||m.id)));if(m.status==="RUNNING"&&m.onlineMatchId)cell.append(link("Xem trực tiếp","/admin/spectator.html?spectate="+encodeURIComponent(m.onlineMatchId)));if(m.historyMatchId&&m.status==="FINISHED")cell.append(link("Replay","/admin/replay.html?match="+encodeURIComponent(m.historyMatchId)));}
async function detail(root){
  const id=new URLSearchParams(location.search).get("id");if(!id)throw new Error("Thiếu mã giải đấu.");const t=await adminRequest("tournaments/"+encodeURIComponent(id));
  root.replaceChildren(node("h1",t.name),link("← Danh sách giải","/admin/tournaments.html"));
  root.append(link("Bài nộp bot và hàng đợi","/admin/submissions.html?id="+encodeURIComponent(id)));
  const reload=()=>detail(root),action=(label,operation)=>{const button=node("button",label);button.onclick=()=>confirmAction(label,async reason=>{await operation(reason);await reload();});return button;};
  const lifecycle=(operation,reason,extra={})=>adminRequest(`tournaments/${encodeURIComponent(id)}/actions/${operation}`,{revision:t.revision,reason,...extra});
  tabs(root,[
    ["overview","Tổng quan",panel=>{panel.append(details("Giải đấu",t,[["ID","id"],["Chủ giải","ownerName"],["Thể thức","format"],["Trạng thái","status"],["Bắt đầu","startedAt",date],["Vòng","currentRound"],["Worker tối đa","maxConcurrentMatches"]]));for(const [label,value]of [["Đang chạy",t.progress.running],["Chờ chạy",t.progress.pending],["Hoàn thành",t.progress.completed],["Trận lỗi",t.progress.failed],["Đang xem xét",Object.keys(t.reviews||{}).length]])panel.append(node("p",label+": "+value));}],
    ["participants","Người tham gia",panel=>{panel.append(table([["Tên","displayName"],["Bot","botId"],["Chủ tài khoản","ownerUserId"]],t.participants,(cell,p)=>{if(p.botId)cell.append(link("Bot","/admin/bot-detail.html?id="+encodeURIComponent(p.botId)));else cell.append(node("span","Người tự chơi · "));if(p.ownerUserId)cell.append(link("User","/admin/user-detail.html?id="+encodeURIComponent(p.ownerUserId)));if(["PAUSED","RECOVERY_REQUIRED"].includes(t.status)&&!t.participantStatuses?.[p.participantId]&&!t.matches.some(m=>m.status==="RUNNING"&&[m.participantA.participantId,m.participantB.participantId].includes(p.participantId)))cell.append(action("Loại participant",reason=>sessionRequest(`/api/tournaments/${encodeURIComponent(id)}/participants/${encodeURIComponent(p.participantId)}/status`,{revision:t.revision,status:"DISQUALIFIED",reason})));}));panel.append(node("h2","Đơn đăng ký"),table([["Tên","displayName"],["Trạng thái","status",badge],["Ngày","registeredAt",date]],t.registrations||[],(cell,r)=>{if(r.status==="PENDING"&&t.status==="REGISTRATION")for(const [label,operation]of [["Duyệt","approve"],["Từ chối","reject"]])cell.append(action(label,reason=>lifecycle(operation,reason,{registrationId:r.id})));}));}],
    ["teams","Đồng đội",panel=>{renderTeams(panel,t,null,null,()=>{});for(const a of panel.querySelectorAll("a")){const url=new URL(a.href);a.href=(url.pathname==="/game.html"?"/admin/spectator.html":"/admin/replay.html")+url.search;}}],
    ["swiss","Swiss",panel=>renderSwiss(panel,t)],
    ["stages","Giai đoạn",panel=>{panel.append(node("p",t.stages?.length?JSON.stringify(t.stages,null,2):"Giải này chưa có giai đoạn riêng."));}],
    ["matches","Các trận",panel=>panel.append(table(matchColumns,t.matches,matchLinks))],
    ["bracket","Bracket",panel=>{if(isKnockout(t.format)){const tree=node("div",undefined,"bracket-tree");panel.append(tree);renderBracket(tree,t);for(const a of tree.querySelectorAll("a")){const url=new URL(a.href);a.href=(url.pathname==="/game.html"?"/admin/spectator.html":"/admin/replay.html")+url.search;}}else panel.append(node("p","Thể thức này dùng lịch đấu và bảng điểm, không có bracket loại trực tiếp."));}],
    ["ranking","Xếp hạng",panel=>panel.append(table([["Hạng","rank"],["Tên","displayName"],["Trận","played"],["Thắng","wins"],["Hòa","draws"],["Thua","losses"],["Điểm","points"],["Quân còn","piecesRemaining"]],t.ranking))],
    ["schedule","Lịch đấu",panel=>panel.append(table([["Vòng","round"],["P1","participantA",value=>value.displayName],["P2","participantB",value=>value.displayName],["Dự kiến","scheduledAt",date],["Bắt đầu","startedAt",date],["Kết thúc","finishedAt",date]],t.matches))],
    ["reviews","Tranh chấp",panel=>{panel.append(link("Xử lý các yêu cầu xem xét","/admin/disputes.html?tournament="+encodeURIComponent(id)));for(const [match,reason]of Object.entries(t.reviews||{}))panel.append(node("p",match+" · "+reason));if(!Object.keys(t.reviews||{}).length)panel.append(node("p","Không có yêu cầu đang mở."));}],
    ["audit","Audit",panel=>panel.append(table([["Thời gian","at",date],["Actor","actor"],["Sự kiện","type"],["Target","target"],["Lý do","reason"]],t.audit||[]))],
    ["controls","Quản trị",panel=>{panel.append(node("h2","Thao tác theo trạng thái hiện tại"));for(const [label,operation]of lifecycleActions(t))panel.append(action(label,reason=>lifecycle(operation,reason)));panel.append(node("p","Mọi thao tác cần lý do và được lưu audit. Trạng thái được kiểm tra lại ở server.","muted"));}]
  ]);
}
export async function mountTournaments(page,root){if(page==="tournament-detail.html")return detail(root);return listPage(root,{title:"Giải đấu",path:"tournaments",fields:[["Tên / chủ giải","q"],["Trạng thái","status",tournamentStatuses],["Thể thức","format",["ROUND_ROBIN","SINGLE_ELIMINATION","GROUP_KNOCKOUT","SWISS","TEAM_ROUND_ROBIN"]],...periodFields],columns:[["Giải đấu","name"],["Chủ giải","owner"],["Thể thức","format"],["Trạng thái","status",badge],["Bot","participants"],["Tổng trận","total_matches"],["Hoàn thành","finished_matches"],["Đang chạy","running_matches"],["Lỗi","failed_matches"],["Bắt đầu","started_at",date]],actions:(cell,row)=>cell.append(link("Chi tiết","/admin/tournament-detail.html?id="+encodeURIComponent(row.id)))});}
