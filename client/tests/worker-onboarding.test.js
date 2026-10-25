import test from 'node:test';
import assert from 'node:assert/strict';
import {installDocument,descendants,text} from './helpers/organizer-dom.js';
installDocument('<body><main></main></body>');
globalThis.location=new URL('https://central.example/admin/worker-setup.html');
location.replace=()=>{};
const events=new EventTarget();globalThis.addEventListener=events.addEventListener.bind(events);globalThis.removeEventListener=events.removeEventListener.bind(events);
globalThis.fetch=async()=>({ok:true,status:200,json:async()=>({authenticated:false})});
const {renderWorkerSetup}=await import('../js/admin/worker-setup-view.js');
const {workerRunCommand,workerFirstRunCommand}=await import('../js/admin/worker-setup-commands.js');
function setup(){const doc=installDocument('<body><main></main></body>'),root=doc.querySelector('main');return{root,view:renderWorkerSetup(root)};}
const find=(root,label)=>descendants(root).find(n=>n.tag==='button'&&n.textContent===label);
test('onboarding keeps preparation download runtime registration and approval in one ordered page',()=>{
 const {root,view}=setup();try{
  assert.equal(root.querySelectorAll('.worker-setup-step').length,6);
  const steps=[1,2,3,4,5].map(n=>root.querySelector('#worker-setup-step-'+n));assert.ok(steps.every(Boolean));
  assert.match(text(steps[0]),/Java 21/);assert.ok(descendants(steps[1]).some(n=>n.href==='/api/admin/workers/download'));
  assert.match(text(steps[2]),/docker pull python:3.12-slim.*docker pull gcc:14.*docker pull eclipse-temurin:21-jdk/s);
  assert.equal(descendants(root).filter(n=>n.tag==='button'&&n.textContent==='Tạo token đăng ký').length,1);
  assert.ok(!descendants(steps[4]).some(n=>n.href==='/admin/workers.html#groups'));
  assert.match(text(steps[4]),/Server tự giao trận/);
  assert.match(text(steps[4]),/BOT_ONLY.*LOCKED/);assert.match(text(root.querySelector('#restart')),/không tạo token mới/i);
 }finally{view.dispose();}
});
test('changing OS and machine settings updates both commands without creating tokens',()=>{
 const original=fetch,calls=[];globalThis.fetch=async(...args)=>{calls.push(args);throw Error('No request expected');};
 const {root,view}=setup();try{
  const platform=root.querySelector('#worker-setup-platform'),name=root.querySelector('#worker-setup-name');
  platform.value='bash';name.value='Lab PC 02';platform.onchange();
  const first=root.querySelector('#worker-setup-step-4').querySelector('pre').textContent,restart=root.querySelector('#restart pre').textContent;
  assert.match(first,/read -r -s -p/);assert.match(first,/^\(\n/);assert.match(first,/--name='Lab PC 02'/);
  assert.match(restart,/--name='Lab PC 02'/);assert.doesNotMatch(restart,/WORKER_REGISTRATION_TOKEN|read -r/);
  name.value="x'; curl bad";name.oninput();
  assert.equal(root.querySelector('#worker-setup-step-4 .worker-command button').disabled,true);
  assert.equal(root.querySelector('#restart .worker-command button').disabled,true);
  assert.equal(calls.length,0);
 }finally{view.dispose();globalThis.fetch=original;}
});
test('generated startup commands distinguish first registration from restart and reject shell injection',()=>{
 for(const platform of ['powershell','bash']){
  const options={origin:'https://central.example',platform,name:'LAB-02',maxMatches:2};
  const first=workerFirstRunCommand(options),again=workerRunCommand(options);
  assert.match(first,/WORKER_REGISTRATION_TOKEN/);assert.match(first,/--max-matches=2 --max-bots=4 --serve/);
  assert.doesNotMatch(first,/--register-token=/);assert.doesNotMatch(again,/REGISTRATION_TOKEN/);
 }
 assert.match(workerFirstRunCommand(),/finally/);
 for(const name of ["bad'","bad\nname",'bad;echo','bad$(whoami)'])assert.throws(()=>workerRunCommand({name}));
 for(const maxMatches of [0,-1,1.5,257,NaN])assert.throws(()=>workerRunCommand({maxMatches}));
 assert.throws(()=>workerRunCommand({origin:'https://user:secret@central.example'}));
 assert.throws(()=>workerRunCommand({origin:'https://central.example/?token=x'}));
 assert.throws(()=>workerRunCommand({origin:'http://public.example'}));
 assert.match(workerRunCommand({origin:'http://localhost:8080'}),/--allow-insecure-http/);
});
test('creating a token sends one CSRF protected request and never embeds the secret in commands or storage',async()=>{
 const original=fetch,calls=[],secret='wrt_only-visible-once';
 globalThis.fetch=async(url,options={})=>{calls.push({url,options});return{ok:true,status:200,json:async()=>url.includes('csrf')?{headerName:'X-CSRF-TOKEN',token:'csrf'}:{id:'token-1',registrationToken:secret,expiresAt:'2026-11-01T12:00:00Z'}};};
 const {root,view}=setup();try{
  const form=root.querySelector('.worker-registration-form');assert.equal(calls.length,0);
  await form.onsubmit({preventDefault(){}});
  const post=calls.filter(c=>c.options.method==='POST');assert.equal(post.length,1);
  assert.equal(form.hidden,true);await form.onsubmit({preventDefault(){}});assert.equal(calls.filter(c=>c.options.method==='POST').length,1);
  assert.equal(post[0].options.headers['X-CSRF-TOKEN'],'csrf');assert.deepEqual(JSON.parse(post[0].options.body),{uses:1,lifetimeMinutes:30});
  const input=root.querySelector('.worker-registration-secret input');assert.equal(input.value,secret);assert.equal(input.type,'password');assert.equal(input.readOnly,true);
  assert.ok(root.querySelectorAll('pre').every(n=>!n.textContent.includes(secret)));assert.ok(!text(root).includes(secret));
  root.querySelector('#worker-setup-platform').value='bash';root.querySelector('#worker-setup-platform').onchange();assert.equal(input.value,secret);
  find(root,'Ẩn và xóa token khỏi trang').click();assert.equal(input.value,'');assert.equal(root.querySelector('.worker-registration-secret').hidden,true);assert.equal(form.hidden,false);
 }finally{view.dispose();globalThis.fetch=original;}
});
test('a late token response after page hide is erased and cannot reappear on a restored page',async()=>{
 const original=fetch;let resolve,result={registrationToken:'wrt_late',expiresAt:'2026-11-01T12:00:00Z'};
 globalThis.fetch=async url=>url.includes('csrf')?{ok:true,status:200,json:async()=>({headerName:'X-CSRF-TOKEN',token:'csrf'})}:new Promise(done=>{resolve=()=>done({ok:true,status:200,json:async()=>result});});
 const {root,view}=setup();try{
  const pending=root.querySelector('form').onsubmit({preventDefault(){}});
  for(let i=0;i<8&&!resolve;i++)await new Promise(r=>setImmediate(r));assert.ok(resolve);
  const hide=new Event('pagehide');Object.defineProperty(hide,'persisted',{value:true});events.dispatchEvent(hide);
  resolve();await pending;
  assert.equal(result.registrationToken,'');assert.equal(root.querySelector('.worker-registration-secret input').value,'');
  assert.equal(root.querySelector('.worker-registration-secret').hidden,true);
  assert.equal(find(root,'Tạo token đăng ký').disabled,false);
 }finally{view.dispose();globalThis.fetch=original;}
});
