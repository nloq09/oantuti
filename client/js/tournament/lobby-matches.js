import {node,link,date} from './player-dom.js';
import {activeMatch,isRemoteMatch,matchOpen,matchStatusText} from './match-execution.js';
export function renderLobbyMatches(root,t,states=[]){
 const panel=node('section',undefined,'panel next-match');panel.append(node('h2','Trận tiếp theo'));let count=0;
 for(const item of states){const match=item.currentMatch||item.nextMatch;if(!match)continue;count++;panel.append(node('h3',item.registration.displayName+' vs '+match.opponent),node('p',`Vòng ${match.round} · Dự kiến: ${date(match.scheduledAt)}`));if(!t.autoAdvanceRounds&&match.round>t.releasedRound)panel.append(node('p','Chờ chủ giải mở vòng tiếp theo.'));}
 if(!count)panel.append(node('p','Chưa có trận tiếp theo. Theo dõi trạng thái của bạn và lịch giải.'));
 const live=node('section',undefined,'panel'),matches=t.matches.filter(activeMatch);live.append(node('h2','Trận đang xử lý'));
 for(const m of matches){live.append(node('h3',m.participantA.displayName+' vs '+m.participantB.displayName),node('p',matchStatusText(m)));const open=matchOpen(m,t.id);if(open)live.append(open);else if(isRemoteMatch(m))live.append(node('p','Trận remote chưa có bàn cờ trực tiếp. Kết quả và replay xuất hiện sau khi server xác nhận.'));}
 if(!matches.length)live.append(node('p','Hiện chưa có trận đang xử lý.'));
 if(t.activeMatchesTruncated)live.append(link('Xem đầy đủ lịch và trạng thái trận',location.pathname+location.search+'#schedule'));
 root.append(panel,live);
}
