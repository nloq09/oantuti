import {normalizeValidationStep} from './validation-detail-state.js';
import {node,date} from './player-dom.js';
import {finalValidationActions} from './organizer-validation-actions.js';
export const validationLabels={QUEUED:'Đang chờ',RUNNING:'Đang kiểm thử',PASSED:'Đạt',FAILED:'Không đạt',INFRA_ERROR:'Lỗi hạ tầng',EMERGENCY_OPEN:'Đang nộp lại khẩn cấp'};
export const stageLabels={BUILD:'Build',PROTOCOL:'Giao thức bot',TIMEOUT:'Thời gian phản hồi',RESOURCE:'Giới hạn tài nguyên',LEGAL_ACTION:'Nước đi hợp lệ',HIDDEN_TESTS:'Kiểm thử ẩn',TOURNAMENT_TESTS:'Kiểm thử theo luật giải',ARTIFACT_HASH:'Tính toàn vẹn bài nộp'};
const reasons={PUBLIC_TESTS_FAILED:'Bot chưa qua kiểm tra giao thức hoặc nước đi.',HIDDEN_TESTS_FAILED:'Bot chưa qua bộ kiểm thử ẩn.',TOURNAMENT_TESTS_FAILED:'Bot chưa hoàn tất kiểm thử theo luật giải.',ARTIFACT_CHANGED:'Bài nộp không còn toàn vẹn.',SOURCE_CHANGED:'Mã nguồn không khớp bài đã khóa.',SANDBOX_UNAVAILABLE:'Hệ thống kiểm thử chưa sẵn sàng.',FINAL_WORKER_ERROR:'Máy kiểm thử chưa hoàn thành công việc.',WORKER_RECOVERY_EXHAUSTED:'Máy kiểm thử bị gián đoạn nhiều lần.',VALIDATION_DEADLINE:'Đã hết hạn kiểm thử. Cần xem xét lịch giải.',ACCOUNT_INACTIVE:'Tài khoản không còn hoạt động.',SUBMISSION_REJECTED:'Bài đã bị loại; kết quả kiểm thử không khôi phục quyền thi đấu.',SUBMISSION_NOT_LOCKED:'Bài không còn ở trạng thái khóa.',REGISTRATION_NOT_APPROVED:'Đăng ký không còn hợp lệ.'};
export const safeValidationReason=reason=>reasons[reason]||'Chưa hoàn tất kiểm thử. Xem trạng thái từng bước và kiểm tra thông báo của hệ thống.';
export function validationSummaryModel(summary={}){
 const n=key=>Number.isSafeInteger(summary[key])&&summary[key]>=0?summary[key]:0;
 const total=n('total'),queued=n('queued'),running=n('running'),passed=n('passed'),failed=n('failed'),infra=n('infraError'),emergency=n('emergencyOpen'),unapplied=n('unapplied');
 return {total,queued,running,passed,failed,infra,emergency,unapplied,completed:passed+failed+infra,allComplete:total>0&&queued===0&&running===0&&emergency===0&&unapplied===0&&passed+failed===total,issues:failed+infra};
}
function metricGroup(title,fields){const group=node('section',undefined,'validation-metric-group');group.append(node('h2',title));const dl=node('dl',undefined,'organizer-metrics-grid');for(const [label,value]of fields){const cell=node('div');cell.append(node('dt',label),node('dd',value));dl.append(cell);}group.append(dl);return group;}
export function renderValidationSummary(root,t,data,metrics,{filter=()=>{}}={}){
 const s=validationSummaryModel(data.summary),progress=node('section',undefined,'control-card validation-progress');
 progress.append(metricGroup('Tiến độ',[['Tổng bài',s.total],['Đang chờ',s.queued],['Đang chạy',s.running],['Hoàn tất',s.completed]]),metricGroup('Kết quả',[['Đạt',s.passed],['Không đạt',s.failed],['Lỗi hạ tầng',s.infra]]));
 if(s.emergency)progress.append(node('p','Nộp lại khẩn cấp: '+s.emergency));
 const help=node('details');help.append(node('summary','ⓘ Cách tính số liệu'),node('p','Số liệu chỉ tính lần khóa bài hiện tại, không thay đổi theo bộ lọc hoặc lịch sử. Hoàn tất gồm kết quả đạt, không đạt và lỗi hạ tầng; lỗi hạ tầng vẫn cần xử lý. Kết quả phải được server áp dụng trước khi đủ điều kiện check-in.'));progress.append(help);root.append(progress);
 const next=node('section',undefined,'control-card validation-next-action');next.append(node('h2','Bước tiếp theo'));
 const phase=metrics?.phase;
 if(t.status==='CANCELLED'||t.status==='FINISHED')next.append(node('p',t.status==='CANCELLED'?'Giải đã hủy. Kết quả kiểm thử được giữ để tra cứu.':'Giải đã kết thúc. Kết quả kiểm thử được giữ để tra cứu.'));
 else if(phase&&['RUNNING','PAUSED','RECOVERY_REQUIRED','CHECK_IN_OPEN','READY_TO_START'].includes(phase))next.append(node('p',phase==='CHECK_IN_OPEN'?'Check-in đã mở. Theo dõi người tham gia tại tab Check-in.':phase==='READY_TO_START'?'Đã đến giai đoạn chuẩn bị thi đấu. Theo dõi tab Check-in.':'Theo dõi giai đoạn hiện tại trong menu quản lý giải.'));
 else if(s.issues)next.append(node('p',s.issues+' bài cần xem xét trước Check-in.'));
 else if(s.running||s.queued)next.append(node('p',(s.running+s.queued)+' bài vẫn đang chờ hoặc đang kiểm thử.'),node('p','Hệ thống sẽ tự cập nhật kết quả.'));
 else if(s.emergency)next.append(node('p',s.emergency+' người đang trong thời gian nộp lại khẩn cấp.'));
 else if(s.unapplied)next.append(node('p','Đang chờ server áp dụng kết quả kiểm thử.'));
 else if(s.allComplete)next.append(node('p','✓ Tất cả bài hiện tại đã kiểm thử xong.'),node('p','Hiện không có thao tác nào cần thực hiện.'));
 else next.append(node('p','Chưa có bài nào được đưa vào kiểm thử chính thức.'));
 if(t.timeline?.checkInOpenAt&&!['CHECK_IN_OPEN','READY_TO_START','RUNNING','FINISHED','CANCELLED','PAUSED','RECOVERY_REQUIRED'].includes(phase||t.status))next.append(node('p','Check-in mở lúc: '+date(t.timeline.checkInOpenAt)));
 if(s.issues){const attention=node('div',undefined,'validation-attention');attention.append(node('h3','Cần xử lý'));for(const [count,label,status]of [[s.failed,'bài không đạt','FAILED'],[s.infra,'lỗi hạ tầng','INFRA_ERROR']])if(count){const button=node('button',status==='FAILED'?'Xem bài không đạt':'Xem lỗi hạ tầng');button.onclick=()=>filter(status);attention.append(node('p','! '+count+' '+label),button);}next.append(attention);}
 else next.append(node('small','✓ Không có vấn đề cần xử lý'));
 root.append(next);
}
export function renderValidationCard(root,v,data,{act=async()=>{},remember=()=>{},saved={}}={}){
 const card=node('article',undefined,'control-card organizer-validation-card');card.dataset.validationId=v.id;
 const steps=Object.entries(stageLabels),complete=steps.filter(([key])=>['PASSED','FAILED','INFRA_ERROR','SKIPPED'].includes(normalizeValidationStep(v.steps?.[key]))).length,passed=steps.filter(([key])=>normalizeValidationStep(v.steps?.[key])==='PASSED').length;
 card.append(node('h3',v.displayName||'Người chơi'),node('p',(({PYTHON:'Python',CPP:'C++',JAVA:'Java'})[v.language]||'Bot')+' · v'+v.version));
 const result=node('strong',(v.status==='PASSED'?'✓ ':v.status==='FAILED'?'✕ ':v.status==='RUNNING'?'● ':'')+(validationLabels[v.status]||'Đang cập nhật'),'validation-result');result.dataset.status=v.status;card.append(result,node('p',complete+' / '+steps.length+' bước kiểm thử hoàn tất'+(v.status==='PASSED'?' · '+passed+' / '+steps.length+' đạt':'')));
 if(v.status==='PASSED'&&v.applied!==true)card.append(node('small','Đang chờ server áp dụng kết quả.'));
 if(['FAILED','INFRA_ERROR'].includes(v.status))card.append(node('p',safeValidationReason(v.reason),'validation-safe-reason'));
 const detail=node('details',undefined,'validation-detail');detail.open=saved.open===true;detail.append(node('summary','Xem chi tiết'));
 const list=node('ul',undefined,'organizer-validation-stages');
 for(const [key,label]of steps){
  const state=normalizeValidationStep(v.steps?.[key])||'PENDING',row=node('li');
  row.dataset.status=state;
  const suffix=({RUNNING:' — Đang chạy',FAILED:' — Không đạt',INFRA_ERROR:' — Gián đoạn',PENDING:' — Chờ kiểm thử',SKIPPED:' — Không chạy'})[state]||(state==='PASSED'?'':' — Chờ cập nhật');
  row.append(node('span',(state==='PASSED'?'✓ ':state==='FAILED'?'✕ ':state==='RUNNING'?'● ':'○ ')+label+suffix));list.append(row);
 }
 detail.append(list);
 if(v.emergencyDeadline)detail.append(node('p','Hạn nộp lại khẩn cấp: '+date(v.emergencyDeadline)));
 if(v.originalVersionId&&v.originalVersionId!==v.versionId)detail.append(node('p','Đang dùng phiên bản hợp lệ trước đó theo chính sách giải.'));
 if(v.history?.length){const history=node('details');history.open=saved.historyOpen===true;history.ontoggle=()=>remember(v.id,{historyOpen:history.open});history.append(node('summary','Lịch sử kiểm thử'));for(const entry of v.history){const state=validationLabels[entry.status]||'Đã cập nhật kiểm thử';history.append(node('p',state+(entry.reason?' · '+safeValidationReason(entry.reason):'')));}detail.append(history);}
 const technical=node('details',undefined,'validation-technical');technical.open=saved.technicalOpen===true;technical.ontoggle=()=>remember(v.id,{technicalOpen:technical.open});technical.append(node('summary','Thông tin kỹ thuật'));for(const [label,value]of [['Mã kiểm thử',v.id],['Source hash',v.sourceHash],['Artifact hash',v.artifactHash],['Tạo lúc',date(v.createdAt)],['Hoàn tất lúc',date(v.finishedAt)]])if(value)technical.append(node('p',label+': '+value));detail.append(technical);
 const actions=data.history?[]:finalValidationActions(v,data);if(actions.length){const form=node('form',undefined,'validation-action-form'),reason=node('textarea'),label=node('label','Lý do xử lý (bắt buộc)'),notice=node('p');reason.maxLength=400;reason.required=true;reason.value=saved.reason||'';label.append(reason);notice.setAttribute('role','status');form.append(label);let deadline;
 if(actions.includes('emergency')){deadline=node('input');deadline.type='datetime-local';deadline.required=true;deadline.value=saved.deadline||'';const label=node('label','Hạn nộp khẩn cấp (trước hạn kiểm thử)');label.append(deadline);form.append(label);}
 const button=node('button',actions[0]==='retry'?'Chạy lại':'Cấp lượt nộp khẩn cấp');button.type='submit';form.append(button,notice);form.onsubmit=async event=>{event.preventDefault();if(button.disabled)return;const why=reason.value.trim(),end=deadline?new Date(deadline.value):null;if(!why||why.length>400||end&&!Number.isFinite(end.getTime())){notice.textContent='Nhập lý do và hạn giờ hợp lệ.';return;}button.disabled=true;form.dataset.submitting='true';try{await act(v,actions[0],why,end?.toISOString());}catch(error){notice.textContent=error.message;}finally{button.disabled=false;delete form.dataset.submitting;}};
 reason.oninput=()=>remember(v.id,{reason:reason.value});if(deadline)deadline.oninput=()=>remember(v.id,{deadline:deadline.value});detail.append(form);}
 detail.ontoggle=()=>remember(v.id,{open:detail.open});card.append(detail);root.append(card);return card;
}
export function renderValidationPagination(root,data,onPage){if(data.total<=data.size&&data.page===0)return;const nav=node('nav',undefined,'ui-actions validation-pagination');nav.setAttribute('aria-label','Trang kết quả kiểm thử');const prev=node('button','Trang trước'),next=node('button','Trang sau');prev.disabled=data.page===0;next.disabled=(data.page+1)*data.size>=data.total;prev.onclick=()=>onPage(data.page-1);next.onclick=()=>onPage(data.page+1);nav.append(prev,node('span','Trang '+(data.page+1)+' / '+Math.max(1,Math.ceil(data.total/data.size))),next);root.append(nav);}
