import {node,link,table,date} from './player-dom.js';
import {sessionRequest} from '../api/auth-api.js';
import {createTournamentRefresh} from './refresh-controller.js';
import {organizerPageShell} from './organizer-shell.js';
import {phaseLabels} from './tournament-header.js';
import {errorText} from '../ui/toast.js';

export const submissionLabels={NO_WORKSPACE:'Chưa tạo',DRAFT:'Bản nháp',BUILDING:'Đang build',BUILD_FAILED:'Build lỗi',BUILT:'Đã build',TEST_FAILED:'Kiểm thử lỗi',TESTING:'Đang kiểm thử',READY:'Sẵn sàng',SUBMITTED:'Đã nộp',LOCKED:'Đã khóa',REJECTED:'Bị từ chối',DISQUALIFIED:'Bị loại',MISSING:'Không có bài hợp lệ',PENDING:'Chờ khóa',QUEUED:'Chờ kiểm thử',RUNNING:'Đang kiểm thử',PASSED:'Đạt',FAILED:'Không đạt',INFRA_ERROR:'Lỗi hạ tầng'};
export const label=value=>submissionLabels[value]||'Chưa có';
const afterCoding=new Set(['SUBMISSION_FROZEN','VALIDATING','CHECK_IN_OPEN','READY_TO_START','RUNNING','PAUSED','RECOVERY_REQUIRED','FINISHED','CANCELLED']);
export function defaultSubmissionFilter(phase){return phase==='CODING_OPEN'?'NOT_READY':phase==='SUBMISSION_FROZEN'?'NO_VALID_SUBMISSION':'ALL';}
export function submissionPhaseModel(t,metrics,summary){
 const phase=metrics?.phase,closed=afterCoding.has(phase),valid=Number(summary.frozen||0),pending=Number(summary.freeze_pending||0);
 let text=phase==='CODING_OPEN'?`${summary.ready} / ${summary.approved} người có bản READY. Theo dõi người chưa sẵn sàng trước hạn.`:phase==='SUBMISSION_FROZEN'?`${valid} / ${summary.approved} người có bài khóa hợp lệ. ${pending?'Server đang chốt bài cho '+pending+' người.':'Tiếp theo: kiểm thử chính thức.'}`:phase==='VALIDATING'?`${summary.validation_passed} / ${summary.approved} người đã đạt kiểm thử chính thức.`:closed?'Lịch sử bài nộp chỉ đọc. Theo dõi kết quả tại phần kiểm thử chính thức.':'Chưa mở lập trình. Danh sách gồm những người đã được duyệt.';
 return {phase,closed,text,showValidation:['SUBMISSION_FROZEN','VALIDATING','CHECK_IN_OPEN','READY_TO_START','RUNNING','PAUSED','RECOVERY_REQUIRED','FINISHED'].includes(phase),lockTitle:phase==='CANCELLED'?'LỊCH SỬ BÀI NỘP':closed?(pending?'ĐANG CHỐT BÀI':'BÀI ĐÃ ĐƯỢC KHÓA'):'KHI HẾT GIỜ LẬP TRÌNH',lockText:phase==='CANCELLED'?'Giải đã hủy. Dữ liệu bài nộp được giữ để tra cứu.':closed?(pending?'Server đang hoàn tất bản khóa. Chờ đồng bộ trước khi kiểm thử.':'Không thể chỉnh sửa bản đã khóa. Bài khóa hợp lệ được chuyển sang kiểm thử chính thức.'):(t.timeline?.deadlinePolicy==='REQUIRE_EXPLICIT_SUBMIT'?'Chỉ giữ phiên bản đã bấm nộp đúng hạn.':'Server sẽ khóa bản READY mới nhất trước hạn.')+' Không thể sửa bài sau khi khóa.'};
}
export function renderSubmissionContent(root,t,metrics,data,{onPage=()=>{}}={}){
 const s=data.summary,model=submissionPhaseModel(t,metrics,s),phase=node('section',undefined,'submission-phase control-card');
 phase.append(node('h2',phaseLabels[model.phase]||'Đang đồng bộ giai đoạn'),node('p',model.text));
 if(model.showValidation)phase.append(link('Đến kiểm thử chính thức →',(location.pathname.startsWith('/admin/')?'/admin/final-validation.html':'/tournament-final-validation.html')+'?id='+encodeURIComponent(t.id)));
 const pipeline=node('ol',undefined,'submission-pipeline');for(const [key,title] of [['approved','Được duyệt'],['workspace_users','Đã tạo workspace'],['submitted','Đã nộp'],['ready','Có bản READY']]){const item=node('li');item.append(node('strong',s[key]??0),node('span',title));pipeline.append(item);}
 const issues=node('section',undefined,'submission-issues');issues.append(node('h2','Cần chú ý'));let any=false;for(const [key,title] of [['build_failed','người build lỗi'],['test_failed','người kiểm thử bản build lỗi'],['no_workspace','người chưa tạo workspace'],['submitted_not_ready','người đã nộp nhưng không có bản READY'],['rejected','người có bài bị từ chối / loại'],['freeze_missing','người không có bài hợp lệ để khóa']])if(Number(s[key])){any=true;issues.append(node('p',`${s[key]} ${title}`));}if(!any)issues.append(node('p','✓ Không có vấn đề về bài nộp'));
 const explanation=node('details');explanation.append(node('summary','ⓘ Cách tính số liệu'),node('p','Mỗi người được duyệt chỉ được đếm một lần, kể cả có nhiều ngôn ngữ hoặc phiên bản. Đã nộp: SUBMITTED / LOCKED; READY: có phiên bản READY / SUBMITTED / LOCKED đã đạt public và sample tests. Các nhóm lỗi có thể giao nhau. Bản khóa và kiểm thử dùng đúng mốc chốt hiện tại. Hàng đợi đếm job, không đếm người.'));
 const results=node('section',undefined,'submission-results');if(Number(s.approved)>0&&!Number(s.submitted))results.append(node('p','Hiện chưa có ai nộp bài trong danh sách được duyệt.'));results.append(node('h2','Bài nộp theo người chơi'),node('p',`${data.total} người khớp bộ lọc · ${s.approved} người được duyệt`));
 if(!data.items.length)results.append(node('p',!Number(s.approved)?'Chưa có người được duyệt. Danh sách bài nộp sẽ xuất hiện sau khi duyệt đăng ký.':!Number(s.submitted)&&data.filter==='ALL'?'Chưa có ai nộp bài. Người được duyệt vẫn được theo dõi trong danh sách.':'Không có người chơi khớp bộ lọc. Hãy đổi bộ lọc hoặc từ khóa.'));
 else results.append(table([
  ['Người chơi',r=>{const el=node('div');el.append(node('strong',r.display_name),node('small','@'+r.username));return el;}],
  ['Ngôn ngữ',r=>r.language],['Workspace',r=>label(r.workspace_state)+(r.workspace_count>1?' · '+r.workspace_count+' ngôn ngữ':'')],
  ['Bài nộp',r=>label(r.submission_state)],['READY',r=>r.ready?'✓ Có':'Chưa có'],
  ['Build / test',r=>r.validation_state?label(r.validation_state):['BUILD_FAILED','TEST_FAILED','BUILDING','TESTING'].includes(r.workspace_state)?label(r.workspace_state):r.ready?'Đạt kiểm thử bản build':'Chưa đạt'],
  ['Cập nhật',r=>r.updated_at?date(r.updated_at):'—'],['Chi tiết',r=>{const details=node('details');details.append(node('summary','Xem'),node('p','Workspace: '+label(r.workspace_state)),node('p','Bài nộp: '+label(r.submission_state)),node('p','Bản khóa: '+label(r.freeze_state)),node('p',r.frozen_version?'Phiên bản khóa: v'+r.frozen_version+' · '+r.frozen_language:r.freeze_state==='MISSING'?'Không có phiên bản READY để khóa.':model.closed?'Đang chờ server chốt bài.':'Chưa đến mốc khóa bài.'));return details;}]
 ],data.items));
 const controls=node('div',undefined,'ui-actions'),prev=node('button','← Trước'),next=node('button','Sau →');prev.disabled=data.page===0;next.disabled=(data.page+1)*data.size>=data.total;prev.onclick=()=>onPage(data.page-1);next.onclick=()=>onPage(data.page+1);controls.append(prev,node('span',`Trang ${data.page+1} / ${Math.max(1,Math.ceil(data.total/data.size))}`),next);if(data.total>0||data.page>0)results.append(controls);
 const lock=node('aside',undefined,'submission-lock');lock.append(node('h2',model.lockTitle),node('p',model.lockText));
 const jobs=node('p',`Build/test: ${data.jobs.active} job đang chờ / chạy · ${data.jobs.failed} job lỗi (lịch sử).`,'submission-job-summary');
 root.replaceChildren(phase,pipeline,issues,explanation,results,lock,jobs);
}
export async function mountSubmissionDashboard({id,root,t}){
 const base='/api/tournaments/'+encodeURIComponent(id),wrapper=root.parentElement;wrapper.id='submission-dashboard';wrapper.classList.add('submission-dashboard');document.querySelector('#refresh-submissions')?.remove();
 const heading=node('div',undefined,'submission-heading'),title=node('h1','BÀI NỘP'),refreshButton=node('button','↻ Cập nhật'),freshness=node('small'),filters=node('form',undefined,'submission-filters'),search=node('input'),select=node('select'),size=node('select'),submit=node('button','Lọc'),body=node('div',undefined,'submission-body'),notice=node('p');
 heading.append(title,refreshButton,freshness);search.type='search';search.maxLength=80;search.placeholder='Tên hoặc username';for(const [value,text] of [['ALL','Tất cả'],['NO_WORKSPACE','Chưa tạo workspace'],['NOT_SUBMITTED','Chưa nộp'],['NOT_READY','Chưa READY'],['READY','READY'],['BUILD_FAILED','Build lỗi'],['LOCKED','Đã khóa'],['NO_VALID_SUBMISSION','Chưa có bài khóa hợp lệ']]){const option=node('option',text);option.value=value;select.append(option);}for(const n of [25,50,100]){const option=node('option',n+' người / trang');option.value=String(n);size.append(option);}
 for(const [text,input] of [['Tìm người chơi',search],['Trạng thái',select],['Hiển thị',size]]){const field=node('label',text);field.append(input);filters.append(field);}submit.type='submit';filters.append(submit);notice.setAttribute('role','status');root.replaceChildren(heading,filters,notice,body);
 let page=0,chosen=false,appliedSearch='',lastFilter=null,controller;const refresh=()=>controller.request(true,'manual',{replace:true});filters.onsubmit=event=>{event.preventDefault();chosen=true;appliedSearch=search.value.trim();page=0;refresh();};select.onchange=()=>{chosen=true;appliedSearch=search.value.trim();page=0;refresh();};size.onchange=()=>{page=0;refresh();};refreshButton.onclick=refresh;
 await organizerPageShell(t,'submissions');
 controller=createTournamentRefresh(async({isCurrent,signal})=>{try{
  const options={signal};const [current,metrics]=await Promise.all([sessionRequest(base,undefined,undefined,options),sessionRequest(base+'/organizer-metrics',undefined,undefined,options)]);if(!isCurrent())return;if(metrics.revision!==current.revision){notice.textContent='Đang đồng bộ phiên bản giải. Chờ lần cập nhật tiếp theo.';return;}
  const filter=chosen?select.value:defaultSubmissionFilter(metrics.phase);if(lastFilter!==null&&lastFilter!==filter)page=0;lastFilter=filter;const query=new URLSearchParams({view:'participants',filter,search:appliedSearch,page:String(page),size:size.value||'25'});const data=await sessionRequest(base+'/submissions?'+query,undefined,undefined,options);if(!isCurrent())return;select.value=filter;notice.textContent='';
  // Keep an expanded participant detail stable until it is closed.
  if(body.querySelector('details[open]')){notice.textContent='Đang giữ chi tiết bạn mở. Đóng chi tiết để tiếp tục cập nhật bảng.';return;}
  renderSubmissionContent(body,current,metrics,data,{onPage:value=>{page=value;refresh();}});freshness.textContent='Tự cập nhật · '+date(metrics.serverNow);await organizerPageShell(current,'submissions');
 }catch(error){if(isCurrent())notice.textContent='Chưa cập nhật được dữ liệu: '+errorText(error.message);}}, {tournamentId:id,connected:()=>root.isConnected});
 return controller;
}
