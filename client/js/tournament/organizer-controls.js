import {organizerActionAvailability} from './organizer-actions.js';
import {node,date} from './player-dom.js';
import {filteredRegistrations} from './organizer-filters.js';
export function renderLifecycleControls(t,data,{stale=false,root=document}={}){
 const allowed=organizerActionAvailability(t,data,{stale});
 for(const [id,key]of [['open-registration','openRegistration'],['close-registration','closeRegistration'],['close-check-in','closeCheckIn'],['start','start'],['cancel','cancel']]){const button=root.querySelector('#'+id);if(button){button.hidden=!allowed[key];button.disabled=false;}}
 for(const button of root.querySelectorAll('[data-admin-control]')){button.hidden=!allowed[button.dataset.adminControl];button.disabled=false;}
 const editable=root.querySelector('#round-policy');if(editable)editable.hidden=!allowed.configure;
 const cancelState=root.querySelector('#cancel-state');if(cancelState)cancelState.textContent=allowed.cancel?'':!allowed.synced?'Đang đồng bộ quyền thao tác.':'Giải đã kết thúc hoặc hủy; không còn thao tác hủy.';
 return allowed;
}
export function renderOrganizerCheckIn(root,t,filter='ALL'){
 root.replaceChildren();const registrations=filteredRegistrations(t,filter==='MISSING'?'MISSING':'ALL').filter(r=>['APPROVED','CHECKED_IN','NOT_CHECKED_IN'].includes(r.status)&&(!t.timeline||r.status!=='APPROVED'||t.participants?.some(p=>p.participantId===r.id)));
 if(!registrations.length){root.append(node('p',filter==='MISSING'?'Không có người đủ điều kiện đang thiếu check-in.':'Chưa có suất hợp lệ để check-in. Với giải lập trình, bot phải đạt kiểm thử chính thức.'));return;}
 const list=node('div',undefined,'organizer-roster-list');for(const r of registrations){const row=node('article',undefined,'organizer-roster-row'),status=t.participantStatuses?.[r.id];row.append(node('strong',r.displayName),node('span',status==='DISQUALIFIED'?'Bị loại':status==='WITHDRAWN'?'Đã rút':r.status==='CHECKED_IN'?'✓ Đã check-in':r.status==='NOT_CHECKED_IN'?'! Đã lỡ hạn':'Chưa check-in'));if(r.checkedInAt)row.append(node('small',date(r.checkedInAt)));list.append(row);}root.append(list);
}
