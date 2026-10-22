import {node,link,date} from './player-dom.js';
import {sessionRequest} from '../api/auth-api.js';
import {confirmOrganizerDecision} from './organizer-decision-dialog.js';

export function renderRegistrationLifecycle(root,t,summary,refresh,{compact=false}={}){
 const state=summary.control,counts=summary.counts;
 root.append(node('h2','Lịch đăng ký'));
 if(!compact)root.append(node('p',`${counts.approved} đã duyệt · ${counts.needsReview} cần xử lý · ${counts.waitlisted} đang chờ · ${counts.rejected} từ chối`));
 const action=(text,path,description)=>{const button=node('button',text);button.type='button';button.onclick=()=>confirmOrganizerDecision(text,description,async reason=>{await sessionRequest('/api/tournaments/'+encodeURIComponent(t.id)+path,{revision:summary.revision,reason});await refresh();},{danger:false});root.append(button);};
 if(t.status==='DRAFT'||Date.parse(summary.serverNow)<Date.parse(t.timeline?.registrationOpenAt)){
  root.append(node('p','Giải chưa mở đăng ký. Server sẽ tự mở lúc '+date(t.timeline?.registrationOpenAt)+'.'));
  if(summary.canOpen)action('Mở đăng ký ngay','/open-registration','Mở nhận đăng ký sớm từ bây giờ. Giờ đóng đăng ký và các mốc lập trình giữ nguyên. Nếu không mở sớm, server sẽ tự mở theo lịch.');return;
 }
 root.append(node('p',summary.registrationOpen?'Đang nhận đăng ký. Server tự đóng lúc '+date(summary.registrationCloseAt)+'.':'Đã đóng nhận đăng ký mới.'));
 root.append(node('p','Phần lập trình tự mở lúc '+date(summary.codingOpenAt)+'. Người đã được duyệt có thể vào code mà không cần chốt danh sách thủ công.'));
 if(counts.needsReview)root.append(node('p',summary.canReview?`${counts.needsReview} đơn cần xử lý trước giờ lập trình. Các đơn chưa được duyệt không được vào code.`:`${counts.needsReview} đơn chưa được duyệt; không được vào phần lập trình.`),link('Xem đơn cần xử lý','/tournament-applications.html?id='+encodeURIComponent(t.id)+'&status=NEEDS_REVIEW'));
 if(state.lockedAt)root.append(node('p',`Danh sách đã được chốt trước đó: ${state.lockedCount} người · ${date(state.lockedAt)} · ${state.lockedByName}`));
 if(summary.canClose&&!state.lockedAt)action('Đóng cửa đăng ký','/registration/close','Đóng nhận đơn mới sớm. Bạn vẫn xét duyệt được các đơn đã gửi đến giờ lập trình; không cần chốt danh sách để người đã được duyệt vào code.');
}
