import{tournamentRequest}from "./tournament-api.js";
import{roundLabel,formatLabel}from "./bracket-renderer.js";
import{node,link}from "./player-dom.js";
export function contextLabel(c){return `${formatLabel(c.format)}${c.stage?.number?" · Giai đoạn "+c.stage.number:""} · ${roundLabel(c.round,c.totalRounds,c.format)} · BO${c.bestOf} · Ván ${c.gameNumber}/${c.bestOf}`;}
export async function mountMatchContext(tournamentId,onlineId){
 const panel=node("section",undefined,"panel tournament-match-context");panel.setAttribute("aria-label","Ngữ cảnh trận giải đấu");document.querySelector("main")?.prepend(panel);
 let stopped=false,timer;
 async function refresh(){try{const c=await tournamentRequest(`/${encodeURIComponent(tournamentId)}/match-context?onlineMatchId=${encodeURIComponent(onlineId)}`);if(stopped)return;panel.replaceChildren(node("h2",c.tournamentName),node("p",contextLabel(c)),node("p",`${c.participantA.name} vs ${c.participantB.name}`),link("← Quay lại giải",`/tournament-lobby.html?id=${encodeURIComponent(tournamentId)}&stay=1`));if(c.series&&c.bestOf>1)panel.append(node("p",`Tỷ số series: ${c.participantA.name} ${c.series.winsA} – ${c.series.winsB} ${c.participantB.name}`));if(c.status==="FINISHED"||c.status==="FAILED"){panel.append(node("p",c.status==="FAILED"?"Trận cần được chủ giải kiểm tra.":"Ván đã kết thúc · Kết quả giải do server xác nhận."));if(c.historyMatchId)panel.append(link("Xem replay","/replay.html?match="+encodeURIComponent(c.historyMatchId)));}}catch{if(!stopped)panel.replaceChildren(node("p","Chưa tải được ngữ cảnh giải. Đang kết nối lại…"),link("Quay lại giải",`/tournament-lobby.html?id=${encodeURIComponent(tournamentId)}&stay=1`));}finally{if(!stopped)timer=setTimeout(refresh,5000);}}
 addEventListener("pagehide",()=>{stopped=true;clearTimeout(timer);},{once:true});await refresh();
}
