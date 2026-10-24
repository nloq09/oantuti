import {node,link} from './player-dom.js';
import {renderTimelineRegistration} from './timeline-registration.js';
import {registrationForm} from './registration-form.js';
export const registrationDisclosureAvailable=(t,flow)=>t.timeline
 ?Boolean(flow&&!['FINISHED','CANCELLED'].includes(t.status))
 :['REGISTRATION','CHECK_IN'].includes(t.status);

// Keep one mounted view per account/tournament, including its unsent inputs.
export function createRegistrationDisclosure({selectTab,refresh,onRegistered,onRules,onOpen,sectionMode=false}){
 let scope,host,snapshot,activeTab,draft={};
 const pending=()=>Boolean(host?.querySelector('[data-submitting=true]'));
 function open(){
  if(activeTab!=='registration'||!host||host.hidden)selectTab('registration');
  if(!host||host.hidden)return;
  onOpen?.();
  host.scrollIntoView?.({behavior:'smooth',block:'start'});
  (host.querySelector('select')||host.querySelector('input')||host).focus({preventScroll:true});
 }
 return {
  attach(root,next){
   const {tournament:t,account,flow,me,tab,primaryAction}=next;
   activeTab=tab;
   const key=JSON.stringify([t.id,account?.id||account?.username||null]);
   if(scope!==key){scope=key;host=null;snapshot=null;draft={};}
   const available=registrationDisclosureAvailable(t,flow);
   const card=root.querySelector('.player-next-action');
   const cta=card?.querySelector('#player-primary-action-REGISTER');
   if(cta){cta.href='#registration';cta.onclick=event=>{event.preventDefault();open();};}
   const registered=t.timeline?flow?.registration&&!['NOT_REGISTERED','CANCELLED'].includes(flow.registrationStatus):me?.states?.length;
   if(account&&registered&&available&&card){
    const info=link(sectionMode?'Xem đăng ký của tôi':'Xem thông tin đăng ký','#registration');
    info.className='registration-information-link';info.onclick=event=>{event.preventDefault();open();};card.append(info);
   }
   const visible=available&&tab==='registration';
   if(!host){host=node('section',undefined,'panel player-registration-disclosure');host.id='player-registration-panel';host.tabIndex=-1;host.setAttribute('role',sectionMode?'region':'tabpanel');if(sectionMode)host.setAttribute('aria-label','Đăng ký của tôi');else host.setAttribute('aria-labelledby','player-tab-registration');}
   root.append(host);host.hidden=!visible;
   const nextSnapshot=JSON.stringify([t.status,t.timeline,t.botPolicy,t.registrationPolicy,t.acceptingRegistrations,account,flow?.phase,flow?.registrationStatus,flow?.registration,flow?.canWithdraw,flow?.registrationControl,flow?.allowedLanguages,me?.states]);
   if(visible&&nextSnapshot!==snapshot&&!pending()){
    snapshot=nextSnapshot;host.replaceChildren();
    if(t.timeline&&flow)renderTimelineRegistration(host,t,account,flow,refresh,{
     flat:true,registrationOnly:true,draft,primaryLogin:primaryAction?.type==='LOGIN',primaryWorkspace:primaryAction?.type==='OPEN_CODE',onRegistered:result=>scope===key?onRegistered(result):undefined,
     singlePage:sectionMode,onRules:onRules||(()=>selectTab('rules')),
    });
    else if(account){
     host.append(node('h2','Đăng ký tham gia'));
     const identity=node('div',undefined,'registration-identity');identity.append(node('p','Tài khoản: '+account.username),node('p','Tên hiển thị: '+(account.displayName||account.username)));host.append(identity);
     registrationForm(host,t,me,()=>onRegistered(),{draft,onRules:onRules||(()=>selectTab('rules'))});
    }else host.append(node('p','Đăng nhập để đăng ký tham gia giải.'));
   }
   return visible;
  },
  open,
 };
}
