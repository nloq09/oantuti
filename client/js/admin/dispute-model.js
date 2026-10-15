export function disputeDecisions(data){
  if(!["OPEN","UNDER_REVIEW"].includes(data.status))return [];
  const t=data.tournament,m=data.match,paused=["PAUSED","RECOVERY_REQUIRED"].includes(t.status),result=[];
  if(t.status!=="CANCELLED")result.push(["Nhận xử lý","assign"],["Giữ nguyên kết quả","uphold"]);
  result.push(["Từ chối yêu cầu","reject"]);
  if(paused&&m.status==="FAILED"&&m.failure!=="CANCELLED")result.push(["Thử lại trận lỗi","retry"]);
  if(["PAUSED","RECOVERY_REQUIRED","FINISHED"].includes(t.status)&&m.status==="FINISHED"&&(t.format!=="SINGLE_ELIMINATION"||t.matches.filter(other=>other.round>m.round).every(other=>other.status==="PENDING")))result.push(["Sửa quyết định kết quả","override"]);
  if(paused&&[m.participantA,m.participantB].some(p=>!t.participantStatuses?.[p.participantId]&&!t.matches.some(game=>game.status==="RUNNING"&&[game.participantA.participantId,game.participantB.participantId].includes(p.participantId))))result.push(["Loại participant","disqualify"]);
  return result;
}
