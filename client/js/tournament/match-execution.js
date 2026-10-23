import {friendlyName,statusText} from './presentation.js';
import {node,link,date} from './player-dom.js';
export const executionLabels={WAITING_OPPONENT:'○ Chờ xếp trận / đối thủ',QUEUED:'○ Đang chờ máy thi đấu',ASSIGNED:'● Đã cấp máy chạy',STARTING:'● Đang khởi động bot',RUNNING_LOCAL:'● Trận đang chạy',RUNNING_REMOTE:'● Trận đang chạy trên máy thi đấu',RESULT_PENDING:'● Đã chạy xong · Đang xác nhận kết quả',FINISHED:'✓ Hoàn tất',RETRYING:'↻ Đang chờ chạy lại',RECOVERING:'! Đang chờ khôi phục execution',ERROR:'! Lỗi trận',CANCELLED:'○ Đã hủy',UNKNOWN:'○ Chưa xác định trạng thái'};
export function executionBadge(state){const badge=node('span',executionLabels[state]||executionLabels.UNKNOWN,'status-badge execution-badge');badge.dataset.executionState=state;return badge;}
export const isRemoteMatch=m=>m.remote===true||Boolean(m.execution)||['QUEUED','ASSIGNED','STARTING','RUNNING_REMOTE','RESULT_PENDING','RETRYING','RECOVERING'].includes(m.executionState);
export const matchExecutionState=m=>m.executionState||({RUNNING:'RUNNING_LOCAL',FINISHED:'FINISHED',FAILED:'ERROR',PENDING:'WAITING_OPPONENT'})[m.status]||'UNKNOWN';
export function matchStatusText(m){if(m.reviewRequired)return 'Kết quả đang được trọng tài xem xét';if(matchExecutionState(m)==='FINISHED'&&(m.failure==='NO_SHOW'||['FORFEIT','DOUBLE_FORFEIT'].includes(m.decisionReason)))return 'Xử thua theo luật';return executionLabels[matchExecutionState(m)]||executionLabels.UNKNOWN;}
export const activeMatch=m=>['QUEUED','ASSIGNED','STARTING','RUNNING_REMOTE','RUNNING_LOCAL','RESULT_PENDING','RETRYING','RECOVERING'].includes(matchExecutionState(m));
export function matchOpen(m,tournamentId){
 const state=matchExecutionState(m);
 if(state==='FINISHED')return m.historyMatchId?link('Xem replay','/replay.html?match='+encodeURIComponent(m.historyMatchId)+'&tournament='+encodeURIComponent(tournamentId)):null;
 if(!isRemoteMatch(m)&&state==='RUNNING_LOCAL'&&m.status==='RUNNING'&&m.onlineMatchId)return link('Xem trực tiếp','/game.html?spectate='+encodeURIComponent(m.onlineMatchId)+'&tournament='+encodeURIComponent(tournamentId));
 return null;
}
export function currentMatchOpen(t,me,id){const match=me?.matchMonitor?.items?.find(m=>m.id===id)||t.matches?.find(m=>m.id===id);return match?matchOpen(match,t.id):null;}
export function playerIdentity(side){const box=node('div',undefined,'match-identity');box.append(node('strong',friendlyName(side.displayName||side.playerName)),node('p',side.controllerType==='HUMAN'?'Tự điều khiển':side.botName||'Bot · Chưa có tên phiên bản'));return box;}
export function matchResult(m){if(m.reviewRequired)return 'Kết quả đang được trọng tài xem xét';if(m.executionState!=='FINISHED')return 'Chưa có kết quả chính thức';return m.winnerParticipantId?'Thắng: '+(m.winnerParticipantId===m.participantA.participantId?(m.participantA.playerName||m.participantA.displayName):(m.participantB.playerName||m.participantB.displayName)):['DOUBLE_FORFEIT','DOUBLE_NO_SHOW'].includes(m.decisionReason)?'Cả hai bị xử thua':'Hòa / kết quả theo quyết định của giải';}
export function renderExecutionDetails(root,m,{organizer=false,serverNow}={}){
 const e=m.execution;root.append(executionBadge(m.executionState));
 if(e){const facts=node('dl',undefined,'match-execution-facts');for(const [label,value]of [['Máy thi đấu',e.workerDisplayName||'Chưa được cấp'],['Kết nối máy',statusText(e.workerStatus,'Chưa có dữ liệu')],['Lần nhận việc',e.attempt],['Vị trí chờ',e.queuePosition??'—'],['Bắt đầu dự kiến','Chưa có ETA đáng tin cậy'],['Bắt đầu gameplay',e.startedAt?date(e.startedAt):'Chưa có tín hiệu gameplay']]){const cell=node('div');cell.append(node('dt',label),node('dd',value));facts.append(cell);}root.append(facts);
  if(m.executionState==='RUNNING_REMOTE')root.append(node('p','Live board chưa khả dụng cho trận remote. Kết quả sẽ cập nhật tự động.','notice'));
  if(e.failureCategory)root.append(node('p','Máy thi đấu bị gián đoạn; ban tổ chức đang xử lý.'));
  if(organizer){const detail=node('dl',undefined,'match-execution-facts');for(const [label,value]of [['Execution ID',e.executionId||'Chưa được cấp'],['Lease',e.leaseState],['Lease đến',e.leaseUntil?date(e.leaseUntil):'—'],['Heartbeat gần nhất',e.lastHeartbeatAt?date(e.lastHeartbeatAt):'—'],['Đã xếp hàng',e.queuedAt?date(e.queuedAt):'—'],['Nhận việc',e.claimedAt?date(e.claimedAt):'—'],['Khởi động',e.startingAt?date(e.startingAt):'—'],['Nhận kết quả',e.resultReceivedAt?date(e.resultReceivedAt):'—'],['Projection',e.projectionStatus||'—']]){const cell=node('div');cell.append(node('dt',label),node('dd',value));detail.append(cell);}root.append(detail);}
 }
 const began=e?.startedAt||(!e&&m.startedAt),end=m.finishedAt||serverNow;if(began&&end){const seconds=Math.max(0,Math.floor((Date.parse(end)-Date.parse(began))/1000));if(Number.isFinite(seconds))root.append(node('p',`Thời gian ${m.finishedAt?'trận':'đã chạy tại lần cập nhật'}: ${seconds}s`));}
}
export function renderMyMatchCards(root,t,page,{error=false,limit=3}={}){
 const section=node('section',undefined,'panel my-match-panel');section.id='my-match';section.append(node('h2','Trận của tôi'));
 if(!page){section.append(node('p',error?'Không tải được trạng thái trận. Hệ thống sẽ thử cập nhật lại.':'Đang tải trận của bạn…'));root.append(section);return;}
 section.append(node('p',page.queuePositionNote));
 if(!page.items.length)section.append(node('p','Chưa có trận được xếp cho bạn. Theo dõi trạng thái đăng ký và lịch giải.'));
 for(const m of page.items.slice(0,limit)){const card=node('article',undefined,'my-match-card');card.append(node('h3',`Vòng ${m.round} · Ván ${m.gameNumber}/${m.bestOf}`));const sides=node('div',undefined,'match-versus');sides.append(playerIdentity(m.participantA),node('span','VS'),playerIdentity(m.participantB));card.append(sides);renderExecutionDetails(card,m,{serverNow:page.serverNow});if(m.executionState==='FINISHED')card.append(node('p',matchResult(m)));const open=matchOpen(m,t.id);if(open)card.append(open);section.append(card);}
 if(page.total>limit)section.append(link('Xem tất cả trận của tôi',location.pathname+location.search+'#my-matches'));root.append(section);
}
