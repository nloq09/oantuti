import {node} from './player-dom.js';
const names=['Đăng ký','Lập trình','Kiểm thử','Check-in','Thi đấu','Kết quả'];
export function progressSteps(phase){
 const current=({DRAFT:0,UPCOMING:0,REGISTRATION_OPEN:0,REGISTRATION_REVIEW:0,WAITING_FOR_CODING:0,CODING_OPEN:1,SUBMISSION_FROZEN:2,VALIDATING:2,CHECK_IN_OPEN:3,READY_TO_START:3,RUNNING:4,PAUSED:4,RECOVERY_REQUIRED:4,FINISHED:5})[phase];
 return names.map((label,index)=>({label,state:phase==='CANCELLED'?'cancelled':current===undefined?'locked':index<current?'done':index===current?'current':'locked'}));
}
export function renderProgressStepper(root,phase){const nav=node('nav',undefined,'tournament-progress');nav.setAttribute('aria-label','Tiến trình giải đấu');const list=node('ol');for(const [index,step]of progressSteps(phase).entries()){const item=node('li',undefined,'progress-'+step.state);if(step.state==='current')item.setAttribute('aria-current','step');item.append(node('span',`${({done:'✓',current:'●',locked:'🔒',cancelled:'—'})[step.state]} ${index+1}. ${step.label}`));list.append(item);}nav.append(list);if(phase==='CANCELLED')nav.append(node('p','Giải đã hủy'));root.append(nav);return nav;}
