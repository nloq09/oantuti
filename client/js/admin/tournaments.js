import {seriesViewModels,renderSeriesCards} from '../tournament/series-view.js';
import {statusText} from '../tournament/presentation.js';
import {formatLabel} from '../tournament/bracket-renderer.js';
import {presentTournamentAudit} from '../tournament/audit-presentation.js';
import {renderTeams} from "../tournament/team-view.js";
import {renderDeadlinePolicy} from '../tournament/deadline-policy-editor.js';
import {renderSwiss} from "../tournament/swiss-view.js";
import {isKnockout} from "../tournament/bracket-renderer.js";
import {node,link,date,message} from "./admin-ui.js";
import {adminRequest} from "./admin-api.js";
import {sessionRequest} from "../api/auth-api.js";
import {table,badge,details,confirmAction} from "./components.js";
import {listPage,tabs,periodFields} from "./catalog.js";
import {lifecycleActions,tournamentStatuses} from "./tournament-model.js";
import {renderBracket} from "../tournament/bracket-renderer.js";
const matchColumns=[["Vòng","round"],["P1","participantA",value=>value.displayName],["P2","participantB",value=>value.displayName],["Trạng thái","status",statusText],["Kết quả","result"],["Lỗi","failure"]];
function matchLinks(cell,m){cell.append(link("Chi tiết","/admin/match-detail.html?id="+encodeURIComponent(m.historyMatchId||m.id)));if(m.status==="RUNNING"&&m.onlineMatchId)cell.append(link("Xem trực tiếp","/admin/spectator.html?spectate="+encodeURIComponent(m.onlineMatchId)));if(m.historyMatchId&&m.status==="FINISHED")cell.append(link("Replay","/admin/replay.html?match="+encodeURIComponent(m.historyMatchId)));}
async function detail(root){
  const id=new URLSearchParams(location.search).get("id");if(!id)throw new Error("Thiếu mã giải đấu.");const t=await adminRequest("tournaments/"+encodeURIComponent(id));
  root.replaceChildren(node("h1",t.name),link("← Danh sách giải","/admin/tournaments.html"));
  root.append(link("Bài nộp bot và hàng đợi","/admin/submissions.html?id="+encodeURIComponent(id)));
  if(t.timeline)root.append(link("Duyệt đăng ký người chơi","/admin/applications.html?id="+encodeURIComponent(id)));
  if(t.timeline)root.append(link("Kiểm thử chính thức","/admin/final-validation.html?id="+encodeURIComponent(id)));
  const reload=()=>detail(root),action=(label,operation)=>{const button=node("button",label);button.onclick=()=>confirmAction(label,async reason=>{await operation(reason);await reload();});return button;};
  if(t.timeline){const policy=node('section',undefined,'panel');renderDeadlinePolicy(policy,t,reload);root.append(policy);}
  const lifecycle=(operation,reason,extra={})=>adminRequest(`tournaments/${encodeURIComponent(id)}/actions/${operation}`,{revision:t.revision,reason,...extra});
  tabs(root,[
    ["overview","Tổng quan",panel=>{panel.append(details("Giải đấu",t,[["Chủ giải","ownerName"],["Thể thức","format",formatLabel],["Trạng thái","status",statusText],["Bắt đầu","startedAt",date],["Vòng","currentRound"],["Worker tối đa","maxConcurrentMatches"]]));for(const [label,value]of [["Đang chạy",t.progress.running],["Chờ chạy",t.progress.pending],["Hoàn thành",t.progress.completed],["Trận lỗi",t.progress.failed],["Đang xem xét",Object.keys(t.reviews||{}).length]])panel.append(node("p",label+": "+value));}],
    ["participants","Người tham gia",panel=>{panel.append(table([["Tên","displayName"],["Cách thi đấu","controllerType",value=>value==="HUMAN"?"Tự điều khiển":"Bot"],["Chủ tài khoản","participantId",value=>t.participantOwners?.[value]||"Người chơi"]],t.participants,(cell,p)=>{if(p.botId)cell.append(link("Bot","/admin/bot-detail.html?id="+encodeURIComponent(p.botId)));else cell.append(node("span","Người tự chơi · "));if(p.ownerUserId)cell.append(link("User","/admin/user-detail.html?id="+encodeURIComponent(p.ownerUserId)));if(["PAUSED","RECOVERY_REQUIRED"].includes(t.status)&&!t.participantStatuses?.[p.participantId]&&!t.matches.some(m=>m.status==="RUNNING"&&[m.participantA.participantId,m.participantB.participantId].includes(p.participantId)))cell.append(action("Loại participant",reason=>sessionRequest(`/api/tournaments/${encodeURIComponent(id)}/participants/${encodeURIComponent(p.participantId)}/status`,{revision:t.revision,status:"DISQUALIFIED",reason})));}));panel.append(node("h2","Đơn đăng ký"),table([["Tên","displayName"],["Trạng thái","status",statusText],["Ngày","registeredAt",date]],t.registrations||[],(cell,r)=>{if(r.status==="PENDING"&&t.status==="REGISTRATION")for(const [label,operation]of [["Duyệt","approve"],["Từ chối","reject"]])cell.append(action(label,reason=>lifecycle(operation,reason,{registrationId:r.id})));}));}],
    ["teams","Đồng đội",panel=>{renderTeams(panel,t,null,null,()=>{});for(const a of panel.querySelectorAll("a")){const url=new URL(a.href);a.href=(url.pathname==="/game.html"?"/admin/spectator.html":"/admin/replay.html")+url.search;}}],
    ["swiss","Swiss",panel=>renderSwiss(panel,t)],
    ["stages","Giai đoạn",panel=>{for(const stage of t.stages||[])panel.append(node("p","Giai đoạn "+stage.number+" · "+formatLabel(stage.format)));if(!t.stages?.length)panel.append(node("p","Chưa có giai đoạn riêng."));}],
    ["matches","Các trận",panel=>{if(t.bestOf>1)renderSeriesCards(panel,seriesViewModels(t.matches,t.series,t.bestOf),{openGame:m=>m.historyMatchId?link("Replay","/admin/replay.html?match="+encodeURIComponent(m.historyMatchId)):null});else panel.append(table(matchColumns,t.matches,matchLinks));}],
    ["bracket","Nhánh đấu",panel=>{if(isKnockout(t.format)){const tree=node("div",undefined,"bracket-tree");panel.append(tree);renderBracket(tree,t);for(const a of tree.querySelectorAll("a")){const url=new URL(a.href);a.href=(url.pathname==="/game.html"?"/admin/spectator.html":"/admin/replay.html")+url.search;}}else panel.append(node("p","Thể thức này dùng lịch đấu và bảng điểm, không có bracket loại trực tiếp."));}],
    ["ranking","Xếp hạng",panel=>panel.append(table([["Hạng","rank"],["Tên","displayName"],["Trận","played"],["Thắng","wins"],["Hòa","draws"],["Thua","losses"],["Điểm","points"],["Quân còn","piecesRemaining"]],t.ranking))],
    ["schedule","Lịch đấu",panel=>panel.append(table([["Vòng","round"],["P1","participantA",value=>value.displayName],["P2","participantB",value=>value.displayName],["Dự kiến","scheduledAt",date],["Bắt đầu","startedAt",date],["Kết thúc","finishedAt",date]],t.matches))],
    ["reviews","Tranh chấp",panel=>{panel.append(link("Xử lý các yêu cầu xem xét","/admin/disputes.html?tournament="+encodeURIComponent(id)));for(const [match,reason]of Object.entries(t.reviews||{}))panel.append(node("p",match+" · "+reason));if(!Object.keys(t.reviews||{}).length)panel.append(node("p","Không có yêu cầu đang mở."));}],
    ["audit","Nhật ký",panel=>{for(const event of t.audit||[]){const view=presentTournamentAudit(event,t),row=node("article",undefined,"panel"),raw=node("details");row.append(node("h3",view.title),node("p",date(event.at)));raw.append(node("summary","Chi tiết kỹ thuật"),node("pre",JSON.stringify(event,null,2)));row.append(raw);panel.append(row);}}],
    ["controls","Quản trị",panel=>{panel.append(node("h2","Thao tác theo trạng thái hiện tại"));for(const [label,operation]of lifecycleActions(t))panel.append(action(label,reason=>lifecycle(operation,reason)));panel.append(node("p","Mọi thao tác cần lý do và được lưu audit. Trạng thái được kiểm tra lại ở server.","muted"));}]
  ].filter(([key])=>!(key==="teams"&&t.format!=="TEAM_ROUND_ROBIN")&&!(key==="swiss"&&t.format!=="SWISS")&&!(key==="bracket"&&(!isKnockout(t.format)||!t.bracket?.length))));
}
export async function mountTournaments(page,root){if(page==="tournament-detail.html")return detail(root);await listPage(root,{title:"Giải đấu",path:"tournaments",fields:[["Tên / chủ giải","q"],["Trạng thái","status",tournamentStatuses.map(value=>[value,statusText(value)])],["Thể thức","format",["ROUND_ROBIN","SINGLE_ELIMINATION","GROUP_KNOCKOUT","SWISS","TEAM_ROUND_ROBIN"].map(value=>[value,formatLabel(value)])],...periodFields],columns:[["Giải đấu","name"],["Chủ giải","owner"],["Thể thức","format",formatLabel],["Trạng thái","status",statusText],["Bot","participants"],["Tổng trận","total_matches"],["Hoàn thành","finished_matches"],["Đang chạy","running_matches"],["Lỗi","failed_matches"],["Bắt đầu","started_at",date]],actions:(cell,row)=>cell.append(link("Chi tiết","/admin/tournament-detail.html?id="+encodeURIComponent(row.id)))});await mountCleanup(root);}

const cleanupReasons={EMPTY_ABANDONED_DRAFT:'Nháp trống bỏ dở ít nhất 14 ngày',EMPTY_CANCELLED:'Giải hủy trống ít nhất 7 ngày'};
async function mountCleanup(root){
 const box=node('section',undefined,'panel');root.prepend(box);
 async function load(){box.replaceChildren(node('h2','Dọn giải bỏ dở'),node('p','Chỉ xét giải nháp hoặc đã hủy, không có đăng ký, đội, bài nộp hay trận đấu. Sau 72 giờ chờ, server kiểm tra lại rồi đưa vào thùng rác. Giải trong thùng rác vẫn có thể khôi phục.'));
 try{const data=await adminRequest('tournament-cleanup');if(!data.items.length)box.append(node('p','Chưa có giải cần dọn hoặc trong thùng rác.'));else box.append(table([['Giải','name'],['Chủ giải','owner'],['Lý do','reason',value=>cleanupReasons[value]||value],['Đánh dấu','candidate_at',date],['Đưa vào thùng rác','trashed_at',value=>value?date(value):'Đang chờ xác minh']],data.items,(cell,row)=>{
 const button=node('button',row.trashed_at?'Khôi phục':'Giữ lại');button.onclick=()=>confirmAction(button.textContent,async reason=>{if(row.trashed_at)await adminRequest(`tournament-cleanup/${encodeURIComponent(row.id)}/restore`,{reason});else await sessionRequest(`/api/tournaments/${encodeURIComponent(row.id)}/keep`,{reason,revision:row.candidate_revision});await load();});cell.append(button);
 }));}
 catch(error){message(error,box);}const reload=node('button','Cập nhật danh sách dọn giải');reload.onclick=load;box.append(reload);}
 await load();
}
