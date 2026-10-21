import {node,link,date} from './player-dom.js';
import {countdownText} from './tournament-header.js';
import {registrationMetrics,registrationDashboardState} from './registration-dashboard-model.js';
import {registrationPolicyLabels} from './registration-policy.js';
import {renderRegistrationLifecycle} from './registration-lifecycle.js';

const number=value=>new Intl.NumberFormat('vi-VN').format(value??0);
export function renderRegistrationDashboard(root,t,summary,{status='',selectFilter,refresh,focusRoster}={}){
 root.replaceChildren();const state=registrationDashboardState(t,summary),heading=node('header',undefined,'registration-heading');
 heading.append(node('h1','Đăng ký'),node('p',state.label,'registration-admission-state'));
 if(state.deadline){const timer=node('p',countdownText(state.deadline,summary.serverNow),'registration-countdown');timer.setAttribute('role','timer');timer.dataset.phaseDeadline=state.deadline;timer.dataset.serverNow=summary.serverNow;timer.dataset.receivedAt=String(performance.now());heading.append(node('p','Mốc giờ: '+date(state.deadline)),timer);}
 root.append(heading);
 const metrics=node('div',undefined,'registration-summary-grid');metrics.setAttribute('aria-label','Số liệu toàn bộ đơn đăng ký');
 for(const [key,label,filter]of registrationMetrics){const card=node('button',undefined,'registration-stat');card.type='button';card.dataset.registrationMetric=key;card.dataset.registrationFocus='metric-'+key;card.setAttribute('aria-pressed',String(status===filter));card.setAttribute('aria-label',`${label}: ${number(summary.counts[key])}. Lọc danh sách.`);card.append(node('strong',number(summary.counts[key])),node('span',label));card.onclick=()=>selectFilter?.(filter);metrics.append(card);}
 root.append(metrics,node('p',`${number(summary.counts.approved)} / ${number(summary.capacity)} suất đã duyệt · Còn ${number(summary.remainingSlots)} suất · ${number(summary.counts.cancelled)} đơn đã hủy`,'registration-capacity'));
 const attention=node('section',undefined,'control-card registration-primary-action');attention.setAttribute('aria-label','Việc cần xử lý');
 if(summary.counts.needsReview>0){
  attention.append(node('h2',summary.canReview?'Cần bạn xử lý':'Đơn chưa được xử lý'),node('p',`${number(summary.counts.needsReview)} đơn cần ban tổ chức xác nhận. ${summary.canReview?'Các đơn rõ ràng hợp lệ được xử lý theo chính sách của giải.':'Phần xét duyệt hiện đã khóa; chỉ có thể xem danh sách.'}`));
  const review=node('button',`${summary.canReview?'Xử lý':'Xem'} ${number(summary.counts.needsReview)} đơn`,'primary');review.type='button';review.dataset.registrationFocus='review-queue';review.onclick=()=>selectFilter?.('NEEDS_REVIEW');attention.append(review);
 }else{
  attention.append(node('h2','Không có đơn cần xử lý'),node('p',summary.canLock?'Đã đóng nhận đơn và không còn đơn cần xác nhận. Kiểm tra danh sách trước khi chốt.':summary.control.lockedAt?'Danh sách đã chốt. Bạn vẫn có thể tra cứu các đơn đăng ký.':'Theo dõi đăng ký; không cần duyệt từng đơn đã được hệ thống chấp nhận.'));
  const action=node('button',summary.canLock?'Kiểm tra và chốt danh sách':'Xem tất cả đơn');action.type='button';action.dataset.registrationFocus='registration-next';action.onclick=()=>summary.canLock?focusRoster?.():selectFilter?.('');attention.append(action);
 }
 root.append(attention);
 const columns=node('div',undefined,'registration-dashboard-columns'),policy=node('section',undefined,'control-card registration-policy-card'),facts=node('dl'),config=summary.policy;
 policy.append(node('h2','Chính sách đăng ký'),facts);
 for(const [label,value]of [['Cách duyệt',registrationPolicyLabels[config.approvalPolicy]],['Giới hạn',number(summary.capacity)+' người'],['Danh sách chờ','Đăng ký trước được xếp trước'],['Xác minh trường/đơn vị',config.requireAffiliationVerification?'Bắt buộc ban tổ chức xác nhận':'Không yêu cầu']])facts.append(node('dt',label),node('dd',value));
 if(summary.canEditPolicy){const edit=link('Chỉnh chính sách','/tournament.html?id='+encodeURIComponent(t.id)+'&view=settings#deadline-policy');edit.dataset.registrationFocus='edit-policy';policy.append(edit);}else policy.append(node('p','Chính sách chỉ được sửa khi giải còn nháp và chưa có đơn đăng ký.'));
 const roster=node('section',undefined,'control-card registration-roster');roster.id='registration-roster';renderRegistrationLifecycle(roster,t,summary,refresh,{compact:true});columns.append(policy,roster);root.append(columns);
}
