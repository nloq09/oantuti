import {node,link,date} from './player-dom.js';
import {sessionRequest} from '../api/auth-api.js';
import {confirmOrganizerDecision} from './organizer-decision-dialog.js';

export function renderRegistrationLifecycle(root,t,summary,refresh,{compact=false}={}){
 const state=summary.control,counts=summary.counts;
 root.append(node('h2',state.lockedAt?'Danh sách thi đấu đã chốt':compact?'Danh sách thi đấu':'Đăng ký và danh sách thi đấu'));
 if(!compact)root.append(node('p',`${counts.approved} đã duyệt · ${counts.needsReview} cần xử lý · ${counts.waitlisted} đang chờ · ${counts.rejected} từ chối`));
 if(t.status==='DRAFT'||Date.parse(summary.serverNow)<Date.parse(t.timeline?.registrationOpenAt)){root.append(node('p','Giải chưa mở đăng ký.'));return;}
 if(!state.enabled&&!summary.canClose&&!summary.canLock){root.append(node('p','Giải dùng luồng đăng ký cũ, không yêu cầu chốt danh sách riêng.'));return;}
 if(state.lockedAt){root.append(node('p',`${state.lockedCount} người · Chốt lúc ${date(state.lockedAt)} · ${state.lockedByName}`));return;}
 root.append(node('p',summary.registrationOpen?'Đang nhận đăng ký đến '+date(summary.registrationCloseAt):'Đã đóng nhận đơn mới. Danh sách thi đấu chưa được chốt.'));
 if(counts.needsReview)root.append(node('p',`Không thể chốt danh sách: ${counts.needsReview} đơn vẫn cần xử lý.`),link('Xử lý ngay','/tournament-applications.html?id='+encodeURIComponent(t.id)+'&status=NEEDS_REVIEW'));
 const until=Date.parse(summary.codingOpenAt)-Date.parse(summary.serverNow);
 if(!summary.canLock&&!counts.needsReview&&!summary.registrationOpen&&until>0&&t.status==='REGISTRATION')root.append(node('p','Chưa đủ người được duyệt để chốt danh sách. Hãy kiểm tra các đơn và danh sách chờ.'));
 if(state.enabled&&until<=600000)root.append(node('p',until>0?'Coding bắt đầu trong không quá 10 phút nhưng danh sách chưa được chốt.':'Đã đến giờ coding. Hãy điều chỉnh lịch trước khi chốt; phần lập trình vẫn bị khóa.'));
 const action=(text,path,description)=>{const button=node('button',text);button.type='button';button.onclick=()=>confirmOrganizerDecision(text,description,async reason=>{await sessionRequest('/api/tournaments/'+encodeURIComponent(t.id)+path,{revision:summary.revision,reason});await refresh();},{danger:false});root.append(button);};
 if(summary.canClose)action('Đóng cửa đăng ký','/registration/close','Sau khi đóng, người chơi mới không thể đăng ký. Bạn vẫn xử lý được các đơn đã gửi; thao tác này chưa chốt danh sách thi đấu.');
 if(summary.canLock)action('Chốt danh sách thi đấu','/roster/lock',`${counts.approved} đã duyệt, ${counts.waitlisted} chờ, ${counts.rejected} từ chối, ${counts.needsReview} cần xử lý. Sau khi chốt, danh sách sẽ cố định; không thể xét duyệt, rút đơn hoặc tự đôn waitlist.`);
}
