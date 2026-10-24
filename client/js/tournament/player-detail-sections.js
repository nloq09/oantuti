import {node,date} from './player-dom.js';
import {renderPhaseTimeline} from './phase-view.js';
import {renderBotPolicy} from './bot-policy.js';
import {registrationPolicyLabels} from './registration-policy.js';
import {friendlyName} from './presentation.js';
import {stagePlanText} from './stage-plan.js';

const aliases={overview:'tournament-info',rules:'tournament-rules','tournament-rules':'tournament-rules',registration:'player-registration-panel','tournament-registration-form':'player-registration-panel',coding:'tournament-phase-content',validation:'tournament-phase-content','check-in':'tournament-phase-content','my-match':'my-matches',results:'tournament-phase-content'};
export const playerSectionTarget=hash=>aliases[hash]||hash;

// Native details nodes and live views survive page repainting. Opening a section
// never creates another polling view; closing it stops that view's reads.
export function createPlayerDetailSections(){
 let scope,context;
 const entries=new Map(),active=new Set();
 function stop(entry){entry.view?.stop?.();entry.view=null;entry.snapshot=null;}
 function clear(){for(const entry of entries.values())stop(entry);entries.clear();active.clear();scope=null;}
 function paint(entry){
  if(entry.details&&!entry.element.open)return;
  if(entry.view){entry.view.update?.(context.tournament,context.me);return;}
  if(entry.snapshot===context.snapshot||entry.loading)return;
  // Keep editable forms and expanded inner details intact while the reader works.
  const activeInside=entry.body.contains(document.activeElement);
  if(entry.snapshot&&(entry.dirty||activeInside&&(entry.details||document.activeElement.matches('input,select,textarea'))||entry.body.querySelector('[data-submitting=true]')||entry.details&&entry.body.querySelector('details[open]')))return;
  const openDetails=new Set([...entry.body.querySelectorAll('details')].filter(el=>el.open).map(el=>el.id||el.querySelector('summary')?.textContent));
  entry.body.replaceChildren();entry.snapshot=context.snapshot;
  const view=entry.render(entry.body,context);
  for(const el of entry.body.querySelectorAll('details'))if(openDetails.has(el.id||el.querySelector('summary')?.textContent))el.open=true;
  if(view?.then){entry.loading=true;Promise.resolve(view).then(()=>entry.loading=false,()=>{entry.loading=false;entry.body.append(node('p','Không tải được nội dung. Hãy thử mở lại mục này.'));});}
  else if(view?.update||view?.stop)entry.view=view;
 }
 function attach(root,{id,title,render,details=true,identity=id}){
  active.add(id);let entry=entries.get(id);
  if(!entry){
   const element=node(details?'details':'section',undefined,details?'player-detail-section':'player-phase-content'),body=node('div',undefined,'player-section-body');element.id=id;
   entry={element,body,details,render,snapshot:null};entries.set(id,entry);
   if(details){const summary=node('summary',title);summary.id=id+'-summary';summary.setAttribute('aria-controls',id+'-body');body.id=id+'-body';body.setAttribute('role','region');body.setAttribute('aria-labelledby',summary.id);element.append(summary);entry.summary=summary;
    element.addEventListener('toggle',()=>{if(element.open)paint(entry);else stop(entry);});
   }
   body.addEventListener('input',()=>{entry.dirty=true;});element.append(body);
  }
  if(entry.identity!==identity){stop(entry);entry.identity=identity;entry.dirty=false;}
  entry.render=render;root.append(entry.element);paint(entry);return entry.element;
 }
 return {
  begin(next){const key=JSON.stringify([next.tournament.id,next.account?.id||null,next.account?.role||null]);if(key!==scope){clear();scope=key;}context=next;active.clear();},
  attach,
  finish(){for(const [id,entry]of entries)if(!active.has(id)){stop(entry);entry.element.remove();}},
  open(id){const entry=entries.get(id);if(!entry||!active.has(id))return null;if(entry.details){entry.element.open=true;paint(entry);}return entry.summary||entry.element;},
  clearDirty(id){for(const [key,entry]of entries)if(!id||key===id)entry.dirty=false;},
  clear,
 };
}

export function renderPlayerTournamentInfo(root,t){
 if(t.description)root.append(node('p',t.description));
 if(t.ownerName)root.append(node('p','Tổ chức: '+friendlyName(t.ownerName,'Ban tổ chức')));
 root.append(node('p',t.format==='TEAM_ROUND_ROBIN'?'Giải đồng đội · 3 vị trí mỗi cặp đội. Đội trưởng quản lý danh sách và đội hình.':`Giải cá nhân · BO${t.bestOf||1}.`));
 if(t.format==='GROUP_KNOCKOUT')root.append(node('p',stagePlanText(t.groupPlans)));
 if(t.swiss)root.append(node('p',`Swiss · ${t.swiss.totalRounds} vòng. Server ghép vòng tiếp theo khi vòng hiện tại hoàn tất và không còn yêu cầu xem xét.`));
 root.append(node('h3','Điều kiện tham gia'),node('p',({HUMAN_ONLY:'Dành cho người tự chơi.',BOT_ONLY:'Dành cho bot.',HUMAN_OR_BOT:'Cho phép người tự chơi hoặc bot.'})[t.participantPolicy]||'Tham gia theo chính sách của giải.'));
 if(t.timeline){
  root.append(node('p','Xét đăng ký: '+(registrationPolicyLabels[t.registrationPolicy?.approvalPolicy]||'Theo chính sách ban tổ chức')));
  if(t.registrationPolicy?.requireAffiliationVerification)root.append(node('p','Bắt buộc khai trường/đơn vị để xác minh.'));
 }
 root.append(node('p',t.checkInRequired?'Cần check-in trong thời gian server công bố.':'Không yêu cầu check-in.'));
 renderBotPolicy(root,t);
 if(t.timeline)renderPhaseTimeline(root,t);
 else{
  const schedule=[['Đóng đăng ký',t.timing?.registrationDeadline],['Đóng check-in',t.timing?.checkInDeadline],['Bắt đầu thi đấu',t.timing?.startTime]];
  root.append(node('h3','Lịch trình'));
  for(const [label,value]of schedule)if(value)root.append(node('p',label+': '+date(value)));
  if(!schedule.some(([,value])=>value))root.append(node('p','Lịch do ban tổ chức điều khiển; theo dõi thông báo và Việc của bạn.'));
 }
}
