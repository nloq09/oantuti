import{roundLabel}from "./bracket-renderer.js";
export function participantNextAction(item,t){
 if(item.status==="CHAMPION")return "Bạn đã vô địch! Xem kết quả và replay bên dưới.";
 if(item.status==="ELIMINATED")return "Bạn đã dừng bước. Xem bracket hoặc theo dõi những trận còn lại.";
 if(item.status==="FINISHED")return "Giải đã kết thúc. Xem thành tích và replay bên dưới.";
 if(item.status==="TOURNAMENT_CANCELLED")return "Giải đã hủy. Quay lại trang khám phá để tìm giải khác.";
 if(["WITHDRAWN","DISQUALIFIED","MISSED_CHECK_IN","REJECTED"].includes(item.status))return "Bạn không có suất thi đấu đang hoạt động. Xem trạng thái hoặc liên hệ chủ giải.";
 if(t.status==="RECOVERY_REQUIRED")return "Chờ chủ giải khôi phục. Hệ thống không tự tạo lại trận đã chạy.";
 if(t.status==="PAUSED")return "Giải đang tạm dừng. Trận đang chạy vẫn có thể hoàn tất; chờ chủ giải tiếp tục.";
 if(item.status==="READY_CHECK")return "Xác nhận sẵn sàng trước hạn để vào trận.";
 if(item.status==="READY_CONFIRMED")return "Đã xác nhận. Bạn sẽ tự vào trận khi đối thủ sẵn sàng.";
 if(item.status==="PLAYING")return "Trận đang diễn ra. Bấm Vào trận để tiếp tục.";
 if(item.status==="PENDING_APPROVAL")return "Chờ chủ giải duyệt đăng ký.";
 if(item.status==="CHECK_IN_REQUIRED")return "Check-in trước hạn để giữ suất thi đấu.";
 if(item.qualified&&item.nextMatch)return "✓ Đã vượt qua vòng bảng. Chờ bán kết và xác nhận sẵn sàng.";
 if(t.format!=="ROUND_ROBIN"&&item.nextMatch&&(t.bestOf>1?t.series||[]:t.matches).some(m=>m.status==="FINISHED"&&m.winnerParticipantId===item.participantId))return "✓ Đã đi tiếp vào "+roundLabel(item.nextMatch.round,t.rounds.length,t.format)+". Chờ vòng được mở và xác nhận sẵn sàng.";
 return "Theo dõi lịch và thông báo để biết đối thủ và bước tiếp theo.";
}
export function podium(t){if(t.status!=="FINISHED"||!t.champion)return[];const champion=t.participants.find(p=>p.participantId===t.champion.participantId);if(!champion)return[];
 if(t.format!=="ROUND_ROBIN"){const final=t.bracket?.slice().sort((a,b)=>b.round-a.round)[0];const runner=final&&[final.slotA,final.slotB].find(id=>id&&id!==champion.participantId);return[champion,t.participants.find(p=>p.participantId===runner)].filter(Boolean);}
 return [champion,...t.ranking.filter(row=>row.participantId!==champion.participantId&&row.played>0&&!t.participantStatuses?.[row.participantId]).map(row=>t.participants.find(p=>p.participantId===row.participantId)).filter(Boolean)].slice(0,3);
}
