import test from 'node:test';
import assert from 'node:assert/strict';
import {createRegistrationDisclosure} from '../js/tournament/registration-disclosure.js';
import {installDocument,text} from './helpers/organizer-dom.js';

const settle=async()=>{for(let i=0;i<10;i++)await new Promise(done=>setImmediate(done));};
test('manual registration keeps controller/bot eligibility and uses the shared disclosure with duplicate protection',async()=>{
 const doc=installDocument('<main></main>'),root=doc.querySelector('main'),previous=globalThis.fetch;
 const t={id:'manual',status:'REGISTRATION',participantPolicy:'HUMAN_OR_BOT',botPolicy:{submissionMode:'PRE_UPLOADED_ONLY',allowedLanguages:['PYTHON']}};
 let tab='overview',posts=0,complete,registered=0;
 globalThis.fetch=async(url,options={})=>{
  let data;
  if(url==='/api/account/bots')data=[{id:'ready',name:'Ready bot',language:'PYTHON',status:'READY'},{id:'draft',name:'Draft bot',language:'PYTHON',status:'UPLOADED'}];
  else if(url==='/api/auth/csrf')data={headerName:'X-CSRF',token:'test'};
  else if(url==='/api/tournaments/manual/registrations'){posts++;assert.deepEqual(JSON.parse(options.body),{controllerType:'BOT',botId:'ready',rulesAccepted:true});await new Promise(done=>complete=done);data={};}
  else throw Error('Unexpected URL '+url);
  return{ok:true,status:200,json:async()=>data};
 };
 const view=createRegistrationDisclosure({selectTab:key=>{tab=key;paint();},refresh:()=>{},onRegistered:()=>{registered++;tab='overview';paint();}});
 function paint(){root.replaceChildren();view.attach(root,{tournament:t,account:{id:'u',username:'player'},me:{states:[]},tab});}
 try{
  paint();assert.equal(root.querySelector('form'),null);view.open();await settle();
  const form=root.querySelector('form'),[controller,bots]=form.querySelectorAll('select'),agree=form.querySelector('input'),submit=form.querySelectorAll('button').at(-1);
  assert.deepEqual(controller.options.map(option=>option.value),['HUMAN','BOT']);assert.equal(bots.options.find(option=>option.value==='draft').disabled,true);
  controller.value='BOT';controller.onchange();controller.dispatchEvent(new Event('change'));bots.value='ready';bots.onchange();agree.checked=true;agree.onchange();
  tab='rules';paint();view.open();assert.equal(root.querySelector('form'),form);assert.equal(bots.value,'ready');
  const pending=form.onsubmit({preventDefault(){}});form.onsubmit({preventDefault(){}});await settle();assert.equal(posts,1);assert.equal(submit.textContent,'Đang đăng ký…');complete();await pending;
  assert.equal(registered,1);assert.equal(root.querySelector('#player-registration-panel').hidden,true);
 }finally{globalThis.fetch=previous;}
});
