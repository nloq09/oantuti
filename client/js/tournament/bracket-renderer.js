export const formatLabel = format => format === "GROUP_KNOCKOUT" ? "Vòng bảng → Loại trực tiếp" : format === "SINGLE_ELIMINATION" ? "Loại trực tiếp" : "Vòng tròn";
export const tieBreakLabels = {POINTS:"Điểm",WINS:"Số trận thắng",HEAD_TO_HEAD:"Đối đầu trong nhóm bằng điểm",REMAINING_PIECES:"Quân còn lại",AVERAGE_MOVE_TIME:"Thời gian tính nước trung bình"};
export function roundLabel(round, total, format) {
  if (!["SINGLE_ELIMINATION","GROUP_KNOCKOUT"].includes(format)) return `Vòng ${round}`;
  return ({0:"Chung kết",1:"Bán kết",2:"Tứ kết"})[total-round] || `Vòng ${round}`;
}
export function bracketRounds(t) {
  const players = new Map(t.participants.map(p => [p.participantId,p]));
  const games = new Map(t.matches.map(m => [m.id,m]));
  const total = Math.max(0,...(t.bracket || []).map(n => n.round));
  return [...new Set((t.bracket||[]).map(n=>n.round))].sort((a,b)=>a-b).map(number=>({
    number,label:roundLabel(number,total,t.format),
    nodes:t.bracket.filter(n=>n.round===number).sort((a,b)=>a.position-b.position).map(n=>({...n,
      a:players.get(n.slotA)?.displayName || (n.sourceA ? "Chờ người thắng" : "BYE"),
      b:players.get(n.slotB)?.displayName || (n.sourceB ? "Chờ người thắng" : "BYE"),
      winner:players.get(n.winnerParticipantId)?.displayName,series:t.series?.find(s=>s.id===n.matchId),game:games.get(t.series?.find(s=>s.id===n.matchId)?.gameIds?.at(-1)||n.matchId),
    })),
  }));
}
export function renderBracket(element,t) {
  element.replaceChildren();
  const rounds=bracketRounds(t);
  if(!rounds.length){element.textContent="Bracket được tạo sau khi chốt người đi tiếp hoặc bắt đầu giải loại trực tiếp.";return;}
  for(const round of rounds) {
    const column=document.createElement("section");column.className="bracket-round";
    const title=document.createElement("h3");title.textContent=round.label;column.append(title);
    const slots=document.createElement("div");slots.className="bracket-slots";column.append(slots);
    for(const n of round.nodes) {
      const card=document.createElement("article");card.className="panel bracket-node";if(n.matchId)card.dataset.matchId=n.matchId;
      for(const [id,name] of [[n.slotA,n.a],[n.slotB,n.b]]) {
        const row=document.createElement("p");row.textContent=name;
        if(id && id===n.winnerParticipantId){row.className="bracket-winner";row.prepend("✓ ");}card.append(row);
      }
      if(n.series?.bestOf>1){const score=document.createElement("p");score.textContent=`BO${n.series.bestOf} · ${n.series.winsA} – ${n.series.winsB} · Ván ${n.series.currentGame}`;card.append(score);}
      if(n.resolution) {const note=document.createElement("small");note.textContent=({BYE:"Miễn đấu",WALKOVER:"Đi tiếp do nhánh đối thủ trống",NO_ELIGIBLE_PARTICIPANT:"Không còn người tham gia đủ điều kiện",DOUBLE_FORFEIT:"Cả hai bị xử thua",WIN:"Đã có kết quả",DRAW_REMAINING_PIECES:"Hòa · đi tiếp nhờ quân còn lại",DRAW_SEED:"Hòa · đi tiếp theo hạt giống"})[n.resolution];card.append(note);}
      if(n.game?.failure){const note=document.createElement("small");note.textContent="Lỗi trận: "+n.game.failure;card.append(note);}
      if(n.game?.onlineMatchId && ["RUNNING","FINISHED"].includes(n.game.status)) {
        const live=n.game.status==="RUNNING",link=document.createElement("a");link.className="button";link.textContent=live?"Xem trực tiếp":"Xem replay";
        link.href=(live?"/game.html?spectate=":"/replay.html?match=")+encodeURIComponent(live?n.game.onlineMatchId:(n.game.historyMatchId||n.game.onlineMatchId));card.append(link);
      }
      slots.append(card);
    }
    element.append(column);
  }
}
