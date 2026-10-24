import {matchOpen,matchStatusText} from './match-execution.js';
export const isKnockout=format=>["SINGLE_ELIMINATION","GROUP_KNOCKOUT"].includes(format);
export const competitionComplete=t=>t.format==="SWISS"?Boolean(t.swiss?.complete):t.format==="TEAM_ROUND_ROBIN"?Boolean(t.teamCompetition?.fixtures?.length)&&t.teamCompetition.fixtures.every(f=>f.score.complete):t.format==="ROUND_ROBIN"||Boolean(t.bracket?.at(-1)?.resolution);
export const formatLabel = format => format==="SWISS"?"Swiss":format==="TEAM_ROUND_ROBIN"?"Đồng đội / Trường học": format === "GROUP_KNOCKOUT" ? "Vòng bảng → Loại trực tiếp" : format === "SINGLE_ELIMINATION" ? "Loại trực tiếp" : "Vòng tròn";
export const tieBreakLabels = {BUCHHOLZ:"Buchholz (điểm đối thủ)",POINTS:"Điểm",WINS:"Số trận thắng",HEAD_TO_HEAD:"Đối đầu trong nhóm bằng điểm",REMAINING_PIECES:"Quân còn lại",AVERAGE_MOVE_TIME:"Thời gian tính nước trung bình"};
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
      a:players.get(n.slotA)?.displayName || (n.sourceA ? "Chờ người thắng" : "Miễn đấu"),
      b:players.get(n.slotB)?.displayName || (n.sourceB ? "Chờ người thắng" : "Miễn đấu"),
      reviewRequired:Boolean(t.series?.find(s=>s.id===n.matchId)?.reviewRequired||t.reviews?.[n.matchId]||t.series?.find(s=>s.id===n.matchId)?.gameIds?.some(id=>t.reviews?.[id]||games.get(id)?.reviewRequired)||t.matches.some(m=>(m.seriesId===n.matchId||m.id===n.matchId)&&m.reviewRequired)),
      winner:players.get(n.winnerParticipantId)?.displayName,series:t.series?.find(s=>s.id===n.matchId),game:games.get(t.series?.find(s=>s.id===n.matchId)?.gameIds?.at(-1)||n.matchId),
    })),
  }));
}
export function renderBracket(element,t,{flatCards=false}={}) {
  element.replaceChildren();
  const rounds=bracketRounds(t);
  if(!rounds.length){element.textContent="Nhánh đấu được tạo sau khi chốt người đi tiếp hoặc bắt đầu giải loại trực tiếp.";return;}
  for(const round of rounds) {
    const column=document.createElement("section");column.className="bracket-round";
    const title=document.createElement("h3");title.textContent=round.label;column.append(title);
    const slots=document.createElement("div");slots.className="bracket-slots";column.append(slots);
    for(const n of round.nodes) {
      const card=document.createElement("article");card.className=flatCards?"bracket-node player-bracket-node":"panel bracket-node";if(n.matchId)card.dataset.matchId=n.matchId;
      for(const [id,name] of [[n.slotA,n.a],[n.slotB,n.b]]) {
        const row=document.createElement("p");row.textContent=name;
        if(id && id===n.winnerParticipantId&&!n.reviewRequired){row.className="bracket-winner";row.prepend("✓ ");}card.append(row);
      }
      if(n.series?.bestOf>1){const score=document.createElement("p");score.textContent=`BO${n.series.bestOf} · ${n.series.winsA} – ${n.series.winsB} · Ván ${n.series.currentGame}`;card.append(score);}
      if(n.resolution&&!n.reviewRequired) {const note=document.createElement("small");note.textContent=({BYE:"Miễn đấu",WALKOVER:"Đi tiếp do nhánh đối thủ trống",NO_ELIGIBLE_PARTICIPANT:"Không còn người tham gia đủ điều kiện",DOUBLE_FORFEIT:"Cả hai bị xử thua",WIN:"Đã có kết quả",DRAW_REMAINING_PIECES:"Hòa · đi tiếp nhờ quân còn lại",DRAW_SEED:"Hòa · đi tiếp theo hạt giống"})[n.resolution];card.append(note);}
      if(n.reviewRequired){const note=document.createElement('p');note.textContent='Kết quả đang được trọng tài xem xét · Tỉ số tạm thời';card.append(note);}
      if(n.game?.failure){const note=document.createElement("small");note.textContent="Lỗi trận: "+n.game.failure;card.append(note);}
      if(n.game){const state=document.createElement('small');state.textContent=matchStatusText(n.reviewRequired?{...n.game,reviewRequired:true}:n.game);card.append(state);const open=matchOpen(n.game,t.id);if(open)card.append(open);}
      slots.append(card);
    }
    element.append(column);
  }
}
