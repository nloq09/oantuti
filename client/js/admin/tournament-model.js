export const tournamentStatuses=["DRAFT","REGISTRATION","CHECK_IN","READY","RUNNING","PAUSED","FINISHED","CANCELLED","RECOVERY_REQUIRED"];
export function lifecycleActions(t){
  const minimum=t.format==="GROUP_KNOCKOUT"?4:2,enough=t.participants.length>=minimum,review=Object.keys(t.reviews||{}).length>0,failed=t.matches.some(m=>m.status==="FAILED"),active=t.matches.some(m=>["RUNNING","PENDING"].includes(m.status));
  const result=[];
  if(t.status==="DRAFT")result.push(["Mở đăng ký","open-registration"]);
  if(t.status==="REGISTRATION"&&enough)result.push([t.checkInRequired?"Đóng đăng ký / Mở check-in":"Đóng đăng ký","close-registration"]);
  if(t.status==="CHECK_IN"&&(t.registrations||[]).filter(r=>r.status==="CHECKED_IN").length>=minimum)result.push(["Hoàn tất check-in","close-check-in"]);
  if((t.status==="READY"||t.status==="DRAFT"&&!t.checkInRequired)&&enough)result.push(["Bắt đầu giải","start"]);
  if(t.status==="RUNNING")result.push(["Tạm dừng","pause"]);
  if(["PAUSED","RECOVERY_REQUIRED"].includes(t.status)&&!review&&!failed)result.push(["Tiếp tục","resume"]);
  if(["PAUSED","RUNNING"].includes(t.status)&&!t.autoAdvanceRounds&&!review&&t.releasedRound<(t.rounds?.length||0)&&t.matches.filter(m=>m.round<=t.releasedRound).every(m=>m.status==="FINISHED"))result.push(["Cho chạy vòng tiếp","next-round"]);
  if(["RUNNING","PAUSED","RECOVERY_REQUIRED"].includes(t.status)&&!active&&!review&&(t.format==="ROUND_ROBIN"||t.bracket?.at(-1)?.resolution))result.push(["Hoàn tất giải","finish"]);
  if(!["FINISHED","CANCELLED"].includes(t.status))result.push(["Hủy giải","cancel"]);
  return result;
}
