import {node} from './player-dom.js';


export const registrationDisclosureAvailable=(t,flow)=>t.timeline
 ?Boolean(flow&&!['FINISHED','CANCELLED'].includes(t.status))
 :['REGISTRATION','CHECK_IN'].includes(t.status);

// Keep one mounted view per account/tournament, including its unsent inputs.
export function createRegistrationDisclosure({selectTab,refresh,onRegistered,onRules,onOpen,onClose,sectionMode=false,renderTimelineRegistration,registrationForm,loadRenderers}){
 let scope,host,snapshot,activeTab,draft={},loading,latest,focusAfterLoad=false;
 const pending=()=>Boolean(host?.querySelector('[data-submitting=true]'));
 function open(){
  if(activeTab!=='registration'||!host||host.hidden)selectTab('registration');
  if(!host||host.hidden)return;
  if(loading)focusAfterLoad=true;
  onOpen?.();
  host.scrollIntoView?.({behavior:'smooth',block:'start'});
  (host.querySelector('select')||host.querySelector('input')||host).focus({preventScroll:true});
 }
 const api = {
  attach(root,next){
   latest={root,next};
   const {tournament:t,account,flow,me,tab,primaryAction}=next;
   activeTab=tab;
   const key=JSON.stringify([t.id,account?.id||account?.username||null]);
   if(scope!==key){scope=key;host=null;snapshot=null;draft={};}
   const available=registrationDisclosureAvailable(t,flow);
   const visible=available&&tab==='registration';
   if(!visible)focusAfterLoad=false;
   const card=root.querySelector('.player-next-action');
   const cta=card?.querySelector('#player-primary-action-REGISTER');
   if(cta){cta.href='#registration';cta.onclick=event=>{event.preventDefault();open();};}
   const registered=t.timeline?flow?.registration&&!['NOT_REGISTERED','CANCELLED'].includes(flow.registrationStatus):me?.states?.length;
   if(account&&registered&&available&&card){
    card.querySelector('#player-registration-toggle')?.remove();
    const info=node('button',sectionMode?'Xem đăng ký của tôi':'Xem thông tin đăng ký');
    info.type='button';info.id='player-registration-toggle';info.className='registration-information-link';
    info.setAttribute('aria-controls','player-registration-panel');info.setAttribute('aria-expanded',String(visible));
    info.onclick=()=>{if(!host||host.hidden){open();return;}onClose?.();selectTab('overview');root.querySelector('#player-registration-toggle')?.focus({preventScroll:true});};card.append(info);
   }
   if(!host){host=node('section',undefined,'panel player-registration-disclosure');host.id='player-registration-panel';host.tabIndex=-1;host.setAttribute('role',sectionMode?'region':'tabpanel');if(sectionMode)host.setAttribute('aria-label','Đăng ký của tôi');else host.setAttribute('aria-labelledby','player-tab-registration');}
   if(host.parentElement!==root)root.append(host);host.hidden=!visible;
   const nextSnapshot=JSON.stringify([t.status,t.timeline,t.botPolicy,t.registrationPolicy,t.acceptingRegistrations,account,flow?.phase,flow?.registrationStatus,flow?.registration,flow?.canWithdraw,flow?.registrationControl,flow?.allowedLanguages,me?.states]);
   if(visible&&account&&!(t.timeline?renderTimelineRegistration:registrationForm)&&loadRenderers){
    if(!loading){
     host.replaceChildren(node('p','Đang tải thông tin đăng ký…'));host.setAttribute('aria-busy','true');
     loading=Promise.resolve().then(()=>loadRenderers(Boolean(t.timeline))).then(renderers=>{
      renderTimelineRegistration=renderers.renderTimelineRegistration||renderTimelineRegistration;registrationForm=renderers.registrationForm||registrationForm;loading=null;
      if(latest?.root.isConnected){host?.removeAttribute('aria-busy');api.attach(latest.root,latest.next);if(focusAfterLoad&&!host.hidden){focusAfterLoad=false;api.open();}}
     },()=>{loading=null;if(host?.isConnected&&!host.hidden){host.removeAttribute('aria-busy');const retry=node('button','Thử lại');retry.type='button';retry.onclick=()=>api.attach(latest.root,latest.next);host.replaceChildren(node('p','Không tải được thông tin đăng ký. Dữ liệu đã nhập được giữ.'),retry);}});
    }
    return visible;
   }
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
 return api;
}
