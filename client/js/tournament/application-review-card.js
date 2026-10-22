import {node,date} from './player-dom.js';
import {reviewDecisions} from './application-review-model.js';
import {eligibilityReasonLabels} from './registration-policy.js';
import {applicationLabels} from './timeline-registration-model.js';
export const decisionLabels={APPROVED:'Chấp nhận',WAITLISTED:'Đưa vào danh sách chờ',REJECTED:'Từ chối'};
export function renderApplicationCard(entry,{reviewOpen,checked=false,onSelect,onDecision,onDetails}){
 const card=node('article',undefined,'panel application-review-card'),decisions=reviewDecisions(entry.status,reviewOpen);
 if(decisions.length){const select=node('input'),label=node('label','Chọn đơn '+entry.displayName);select.type='checkbox';select.checked=checked;select.dataset.applicationSelection=entry.id;select.onchange=()=>onSelect(entry,select.checked);label.prepend(select);card.append(label);}
 card.append(node('h3',entry.displayName),node('p',(applicationLabels[entry.status]||entry.status)+' · '+({CPP:'C++',PYTHON:'Python',JAVA:'Java'}[entry.intendedLanguage]||entry.intendedLanguage)),node('p','Trường / Đơn vị: '+(entry.affiliation||'—')));
 for(const code of entry.reasonCodes||[])card.append(node('p',(entry.status==='NEEDS_REVIEW'?'Cần kiểm tra: ':'Khi gửi đơn: ')+(eligibilityReasonLabels[code]||'Cần ban tổ chức kiểm tra'),'application-review-reason'));
 if(entry.waitlistPosition)card.append(node('p','Vị trí danh sách chờ: #'+entry.waitlistPosition));
 card.append(node('p','Đăng ký lúc: '+date(entry.registeredAt)));
 if(entry.reviewReason)card.append(node('p','Lý do gần nhất: '+entry.reviewReason),node('p','Xét duyệt lúc: '+date(entry.reviewedAt)));
 const actions=node('div',undefined,'actions');
 if(decisions.length){const explanation=node('textarea'),label=node('label','Ghi chú nội bộ / Lý do quyết định (bắt buộc)');explanation.maxLength=500;explanation.setAttribute('aria-label','Lý do xét duyệt '+entry.displayName);label.append(explanation);const publicText=node('textarea'),publicLabel=node('label','Lời nhắn cho người đăng ký (không bắt buộc)');publicText.maxLength=500;publicText.setAttribute('aria-label','Lời nhắn cho người đăng ký '+entry.displayName);publicLabel.append(publicText);card.append(label,publicLabel);for(const decision of decisions){const button=node('button',decisionLabels[decision]);button.type='button';button.onclick=()=>onDecision(entry,decision,explanation.value,publicText.value);actions.append(button);}}
 const details=node('button','Xem chi tiết');details.type='button';details.onclick=()=>onDetails(entry);actions.append(details);card.append(actions);return card;
}
