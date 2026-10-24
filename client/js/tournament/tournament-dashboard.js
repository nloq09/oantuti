import {presentTournamentAudit} from './audit-presentation.js';
import {activeMatch,matchOpen,matchStatusText} from './match-execution.js';
import {tournamentControl,tournamentRoundPolicy,tournamentMatchOperation,tournamentRequest} from './tournament-api.js';
import {toast} from '../ui/toast.js';
import {allowedControls,matchOperationAvailability} from './organizer-actions.js';
import {confirmOrganizerDecision,organizerDecisionReason} from './organizer-decision-dialog.js';
import {renderParticipantDiscipline} from './organizer-discipline.js';
import {node} from './player-dom.js';
export {allowedControls} from './organizer-actions.js';
const statusText={ACTIVE:'Đang thi đấu',WITHDRAWN:'Bỏ cuộc',DISQUALIFIED:'Bị loại'};
export function tournamentNotification(event){const labels={PARTICIPANT_APPROVED:'Người tham gia đã được duyệt.',CHECK_IN_OPENED:'Giải đã mở check-in.',TOURNAMENT_STARTED:'Giải bắt đầu.',ROUND_STARTED:'Vòng đấu mới bắt đầu.',MATCH_STARTED:'Một trận đấu vừa bắt đầu.',TOURNAMENT_FINISHED:'Giải đã kết thúc.',TOURNAMENT_RECOVERY_REQUIRED:'Giải cần chủ giải kiểm tra để tiếp tục.'};if(labels[event.type])toast(labels[event.type]);}
function options(select,items){const key=JSON.stringify(items);if(select.dataset.options===key)return;select.dataset.options=key;const previous=select.value;select.replaceChildren(...items.map(([value,label])=>new Option(label,value)));if(items.some(([value])=>value===previous))select.value=previous;}
function card(m,id){const article=node('article',undefined,'panel match-card');article.append(node('h3',`${m.participantA.displayName} vs ${m.participantB.displayName}`),node('p',`Vòng ${m.round} · ${matchStatusText(m)}`));const open=matchOpen(m,id);if(open)article.append(open);return article;}
export function renderDashboard(t,owner){
 document.querySelector('.tournament-invite').hidden=!['DRAFT','REGISTRATION','CHECK_IN'].includes(t.status);
 const adminButton=document.querySelector('[data-tab="admin-tab"]');adminButton.hidden=!owner;
 if(!owner&&adminButton.getAttribute('aria-pressed')==='true')document.querySelector('[data-tab="overview"]').click();
 document.querySelector('#tournament-phase').textContent=`${t.status} · ${t.autoAdvanceRounds?'Tự chuyển vòng':'Chuyển vòng thủ công'}`;
 document.querySelector('#ranking-empty').textContent=t.ranking?.length?'':'Chưa có kết quả xếp hạng. Kết quả sẽ cập nhật khi các trận hoàn tất.';
 document.querySelector('#export-tournament').href=`/api/tournaments/${encodeURIComponent(t.id)}/export`;
 const live=document.querySelector('#live-matches'),replays=document.querySelector('#replay-list');live.replaceChildren();replays.replaceChildren();
 for(const m of t.matches){if(activeMatch(m))live.append(card(m,t.id));if(m.status==='FINISHED'&&m.historyMatchId)replays.append(card(m,t.id));}
 if(!live.childElementCount)live.textContent='Chưa có trận đang diễn ra.';if(!replays.childElementCount)replays.textContent='Chưa có replay. Trận xử thua hành chính không tạo replay giả.';
 const rows=document.querySelectorAll('#participants .participant-row');t.participants.forEach((p,index)=>{const r=t.registrations?.find(r=>r.id===p.participantId);rows[index]?.append(node('small',`${t.participantOwners?.[p.participantId]||'Người chơi'} · ${statusText[t.participantStatuses?.[p.participantId]||'ACTIVE']} · ${r?.status||'—'}`));});
 const snapshot=document.querySelector('#rules-snapshot');snapshot.replaceChildren();for(const [label,value]of [['Số ván mỗi cặp','BO'+(t.rulesSnapshot?.bestOf||t.bestOf||1)],['Chế độ check-in',t.checkInRequired?'Bắt buộc':'Không bắt buộc']])snapshot.append(node('dt',label),node('dd',value));
 document.querySelector('#series-setting').textContent=`BO${t.bestOf||1} · ${t.maxParticipants} suất · ${t.participantPolicy==='HUMAN_ONLY'?'Người tự chơi':t.participantPolicy==='HUMAN_OR_BOT'?'Người hoặc bot':'Bot'} · Số ván được chọn khi tạo giải.`;

 const editable=['DRAFT','REGISTRATION','CHECK_IN'].includes(t.status),auto=document.querySelector('#auto-rounds');if(document.activeElement!==auto)auto.checked=t.autoAdvanceRounds;auto.disabled=!editable;
 document.querySelector('#round-policy').hidden=!owner||!editable;
 const controls=allowedControls(t);document.querySelectorAll('[data-admin-control]').forEach(button=>{button.hidden=!owner||!controls[button.dataset.adminControl];button.disabled=false;});document.querySelector('#finish-operation').hidden=!owner||!controls.finish;
 document.querySelector('#organizer-match-operations').hidden=!owner;
 options(document.querySelector('#admin-match'),t.matches.filter(m=>['FINISHED','FAILED'].includes(m.status)).map(m=>[m.id,`V${m.round} · ${m.participantA.displayName} / ${m.participantB.displayName} · ${m.status}`]));
 options(document.querySelector('#result-match'),t.matches.filter(m=>t.reviews?.[m.id]).map(m=>[m.id,`V${m.round} · ${m.participantA.displayName} / ${m.participantB.displayName}`]));
 renderMatchReview(t);renderResultReview(t);
 const discipline=document.querySelector('#participant-operations');if(t.timeline)discipline.hidden=true;else renderParticipantDiscipline(discipline,t,()=>document.dispatchEvent(new Event('organizer-request-refresh')));
}
export function renderMatchReview(t){
 const m=t.matches.find(m=>m.id===document.querySelector('#admin-match').value),allowed=matchOperationAvailability(t,m);
 document.querySelector('#match-review-content').hidden=!m;document.querySelector('#match-review-empty').textContent=m?'':'Chưa có trận đã kết thúc hoặc lỗi để review.';
 document.querySelector('#review-details').textContent=m?`Kết quả: ${m.result||'Chưa có'} · ${t.reviews?.[m.id]?'Đang chờ quyết định ở Kết quả':'Chưa đánh dấu review'}${m.failure?' · Lỗi: '+m.failure:''}`:'';
 const logs=document.querySelector('#match-execution-log');logs.replaceChildren();for(const e of (t.audit||[]).filter(e=>e.target===m?.id)){const p=presentTournamentAudit(e,t);logs.append(node('li',`${p.title} · ${new Date(e.at).toLocaleString('vi-VN')}${p.changeSummary?' · '+p.changeSummary:''}`));};
 document.querySelector('#request-review').hidden=!allowed.review;document.querySelector('#retry-match').hidden=!allowed.retry;document.querySelector('#match-review-actions').hidden=!allowed.review&&!allowed.retry;
}
export function renderResultReview(t){
 const m=t.matches.find(m=>m.id===document.querySelector('#result-match').value),allowed=matchOperationAvailability(t,m),select=document.querySelector('#override-winner');
 options(select,m?[['','Hòa'],...[m.participantA,m.participantB].filter(p=>!['DISQUALIFIED','WITHDRAWN'].includes(t.participantStatuses?.[p.participantId])).map(p=>[p.participantId,p.displayName+' thắng'])]:[]);
 document.querySelector('#result-content').hidden=!m;document.querySelector('#result-empty').textContent=m?'':'Không có trận đang chờ quyết định.';
 document.querySelector('#result-details').textContent=m?(!allowed.keep?'Giải đã hủy. Chỉ xem lịch sử quyết định.':allowed.override?'Có thể giữ hoặc sửa kết quả; quyết định được ghi audit.':'Có thể giữ kết quả. Sửa kết quả chỉ khả dụng khi điều kiện của giải cho phép.') : '';
 document.querySelector('#keep-result').hidden=!allowed.keep;document.querySelector('#override-result').hidden=!allowed.override;document.querySelector('#override-fields').hidden=!allowed.override;
}
export function bindAdministration(current,action){
 const reason=selector=>organizerDecisionReason(document.querySelector(selector).value);
 document.querySelectorAll('[data-admin-control]').forEach(button=>button.onclick=()=>{const kind=button.dataset.adminControl;if(kind==='finish'){confirmOrganizerDecision('Hoàn tất giải?', 'Xác nhận các kết quả đã được kiểm tra. Quyết định được lưu trong audit.',text=>action(()=>{const t=current();return tournamentControl(t.id,kind,t.revision,text);}));return;}action(()=>{const t=current();return tournamentControl(t.id,kind,t.revision);});});
 document.querySelector('#save-round-policy').onclick=()=>action(()=>{const t=current();return tournamentRoundPolicy(t.id,t.revision,document.querySelector('#auto-rounds').checked);});
 document.querySelector('#admin-match').onchange=()=>renderMatchReview(current());document.querySelector('#result-match').onchange=()=>renderResultReview(current());
 for(const [id,operation,extra,isResult]of [['request-review','review',{},false],['keep-result','review',{keepResult:true},true],['retry-match','retry',{},false],['override-result','override',{},true]])document.querySelector('#'+id).onclick=()=>{
  const matchId=document.querySelector(isResult?'#result-match':'#admin-match').value;
  const invoke=text=>action(()=>{const t=current();return tournamentMatchOperation(t.id,matchId,operation,{revision:t.revision,reason:text,...extra,...(operation==='override'?{winnerParticipantId:document.querySelector('#override-winner').value||null}:{})});});
  if(operation==='override')confirmOrganizerDecision('Sửa kết quả trận?', 'Quyết định mới được lưu riêng; lịch sử và replay gốc không thay đổi.',invoke);
  else {try{invoke(reason(isResult?'#result-reason':'#admin-reason'));}catch(error){toast(error.message,true);}}
 };
 document.querySelector('#load-report').onclick=async()=>{try{const report=await tournamentRequest(`/${encodeURIComponent(current().id)}/report`),target=document.querySelector('#tournament-report');target.replaceChildren();for(const [label,value]of [['Vô địch',report.champion?.displayName||'Chưa có'],['Á quân',report.runnerUp?.displayName||'Chưa có'],['Số trận',report.totalMatches],['Thời gian trung bình (ms)',Math.round(report.averageMatchDurationMillis)],['Lỗi bot',report.totalBotErrors],['Lỗi hạ tầng',report.infrastructureErrors]])target.append(node('p',label+': '+value));}catch(error){toast(error.message,true);}};
}
