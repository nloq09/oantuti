export const formatLabel = format => format === "SINGLE_ELIMINATION" ? "Loại trực tiếp" : "Vòng tròn";
export const tieBreakLabels = {POINTS:"Điểm",WINS:"Số trận thắng",HEAD_TO_HEAD:"Đối đầu trong nhóm bằng điểm",REMAINING_PIECES:"Quân còn lại",AVERAGE_MOVE_TIME:"Thời gian tính nước trung bình"};
export function roundLabel(round, total, format) {
  if (format !== "SINGLE_ELIMINATION") return `Vòng ${round}`;
  return ({0:"Chung kết",1:"Bán kết",2:"Tứ kết"})[total-round] || `Vòng ${round}`;
}
export function bracketRounds(t) {
  const players = new Map(t.participants.map(p => [p.participantId,p]));
  const games = new Map(t.matches.map(m => [m.id,m]));
  const total = Math.max(0,...(t.bracket || []).map(n => n.round));
  return Array.from({length:total},(_,index)=>({
    number:index+1,label:roundLabel(index+1,total,t.format),
    nodes:t.bracket.filter(n=>n.round===index+1).sort((a,b)=>a.position-b.position).map(n=>({...n,
      a:players.get(n.slotA)?.displayName || (n.sourceA ? "Chờ người thắng" : "BYE"),
      b:players.get(n.slotB)?.displayName || (n.sourceB ? "Chờ người thắng" : "BYE"),
      winner:players.get(n.winnerParticipantId)?.displayName,game:games.get(n.matchId),
    })),
  }));
}
export function renderBracket(element,t) {
  element.replaceChildren();
  const rounds=bracketRounds(t);
  if(!rounds.length){element.textContent="Bracket được tạo khi bắt đầu giải loại trực tiếp.";return;}
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
