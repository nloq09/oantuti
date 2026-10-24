import {node,link,date} from './player-dom.js';
const positive=value=>Number.isSafeInteger(value)&&value>0?value:0;
export function attentionItems(t,data){
 if(!data||data.tournamentId!==t.id||data.revision!==t.revision||data.phase==='CANCELLED')return [];
 if(data.phase==='FINISHED'){const count=Object.keys(t.reviews||{}).length;return count?[{key:'review',severity:'WARNING',count,text:count+' trận chờ quyết định kết quả',href:'/tournament.html?id='+encodeURIComponent(t.id)+'&view=results'}]:[];}
 const items=[],base='?id='+encodeURIComponent(t.id),local=(view,filter)=>'/tournament.html'+base+'&view='+view+(filter?'&filter='+filter:'');
 const add=(key,severity,count,text,href)=>{count=positive(count);if(count)items.push({key,severity,count,text:count+' '+text,href});};
 if(['REGISTRATION_OPEN','REGISTRATION_REVIEW','WAITING_FOR_CODING'].includes(data.phase))add('pending','INFO',data.registration?.pending,'đơn đăng ký chờ xét duyệt',t.timeline?'/tournament-applications.html'+base+'&status=PENDING':local('registration','PENDING'));
 if(t.timeline&&['SUBMISSION_FROZEN','VALIDATING','CHECK_IN_OPEN','READY_TO_START'].includes(data.phase)){
  add('validation-infra','CRITICAL',data.validation?.infraError,'bài kiểm thử bị lỗi hạ tầng','/tournament-final-validation.html'+base+'&status=INFRA_ERROR');
  add('validation-failed','WARNING',data.validation?.failed,'bài không đạt kiểm thử chính thức','/tournament-final-validation.html'+base+'&status=FAILED');
 }
 if(t.timelineStartIssue==='VALIDATION_INFRA_BLOCKED')items.push({key:'validation-blocked',severity:'CRITICAL',count:1,text:'Chưa thể bắt đầu: kiểm thử chưa hoàn tất hoặc lỗi hạ tầng. Gia hạn kiểm thử và chạy lại bài lỗi.',href:'/tournament-final-validation.html'+base});
 if(['CHECK_IN_OPEN','READY_TO_START'].includes(data.phase))add('missing-check-in','WARNING',data.checkIn?.missing,'suất đủ điều kiện chưa check-in',local('check-in','MISSING'));
 if(['RUNNING','PAUSED','RECOVERY_REQUIRED'].includes(data.phase)){
  if(data.phase==='RECOVERY_REQUIRED')items.push({key:'recovery',severity:'CRITICAL',count:1,text:'Giải cần kiểm tra để khôi phục',href:local('matches')});
  add('match-error','CRITICAL',data.running?.error,'trận lỗi cần kiểm tra',local('matches','FAILED'));
  add('review','WARNING',Object.keys(t.reviews||{}).length,'trận đang chờ review',local('results'));
  add('worker-offline','CRITICAL',data.workers?.offline,'máy đang giữ assignment của giải mất kết nối',local('matches')+'#organizer-worker-summary');
 }
 const priority={CRITICAL:0,WARNING:1,INFO:2};return items.sort((a,b)=>priority[a.severity]-priority[b.severity]);
}
export function renderTournamentAttentionPanel(root,t,data,{stale=false}={}){
 root.replaceChildren();root.dataset.empty='false';root.setAttribute('aria-label','Các vấn đề cần chủ giải xử lý');
 if(!data||data.tournamentId!==t.id||data.revision!==t.revision){root.append(node('p',stale?'Chưa cập nhật được vấn đề của giải. Đang chờ dữ liệu server.':'Đang kiểm tra dữ liệu của giải…'));return;}
 if(stale)root.append(node('p','Dữ liệu có thể đã cũ. Mở mục chi tiết và kiểm tra trạng thái mới trước khi xử lý.','metrics-warning'));
 const items=attentionItems(t,data);if(!items.length){root.dataset.empty='true';return;}
 root.append(node('h2','Cần xử lý'),node('p','Ghi nhận lúc '+date(data.serverNow)));
 const list=node('ul',undefined,'organizer-attention-list');for(const item of items){const row=node('li'),severity=node('strong',({CRITICAL:'Khẩn cấp',WARNING:'Cần chú ý',INFO:'Thông tin'})[item.severity]);row.dataset.severity=item.severity;row.append(severity,link(item.text,item.href));list.append(row);}root.append(list);
}
