import test from 'node:test';
import assert from 'node:assert/strict';
class Element {
 constructor(tag){this.tag=tag;this.children=[];this.textContent='';this.value='';this.dataset={};this.attributes={};}
 append(...children){this.children.push(...children);}
 replaceChildren(...children){this.children=children;}
 setAttribute(name,value){this.attributes[name]=value;}
 focus(){} select(){}
}
const all=node=>[node,...node.children.flatMap(all)];
test('admin worker form masks one-time token, clears it on close and cleans polling on page leave',async()=>{
 const saved={fetch:globalThis.fetch,document:globalThis.document,Node:globalThis.Node,location:globalThis.location,addEventListener:globalThis.addEventListener,setInterval:globalThis.setInterval,clearInterval:globalThis.clearInterval};
 const root=new Element('main'),listeners={},calls=[],secret='only-this-response';let cleared=false;
 globalThis.Node=Element;globalThis.document={createElement:tag=>new Element(tag),querySelector:()=>root,hidden:false};globalThis.location={pathname:'/admin/workers.html',search:'',origin:'https://central.example',protocol:'https:',replace(){}};globalThis.addEventListener=(type,callback)=>{listeners[type]=callback;};globalThis.setInterval=()=>123;globalThis.clearInterval=id=>{cleared=id===123;};
 globalThis.fetch=async(url,options)=>{calls.push({url,options});return{ok:true,status:200,json:async()=>url.endsWith('/auth/me')?{authenticated:false}:url.includes('/auth/csrf')?{headerName:'X-CSRF-TOKEN',token:'csrf'}:url.includes('registration-tokens')&&options.method==='POST'?{registrationToken:secret,expiresAt:'2026-11-01T00:00:00Z'}:url.includes('registration-tokens')?{items:[]}:{items:[],total:0,size:20}};};
 try {
  const {mountWorkers}=await import('../js/admin/workers.js');await mountWorkers('workers.html',root);let nodes=all(root);
  assert.equal(nodes.filter(n=>n.tag==='form').length,1);const form=nodes.find(n=>n.tag==='form');assert.ok(form);nodes.find(n=>n.textContent==='Thêm worker').onclick();await form.onsubmit({preventDefault(){}});
  nodes=all(root);const token=nodes.find(n=>n.value===secret);assert.ok(token);assert.equal(token.type,'password');assert.equal(token.readOnly,true);assert.ok(nodes.every(n=>!n.textContent.includes(secret)));
  nodes.find(n=>n.textContent==='Đóng').onclick();assert.ok(!all(root).some(n=>n.value===secret));listeners.pagehide();assert.equal(cleared,true);
  assert.ok(calls.filter(c=>c.options.method==='POST'&&c.url.includes('/api/admin/')).every(c=>c.options.headers['X-CSRF-TOKEN']==='csrf'));
  await mountWorkers('worker-setup.html',root);nodes=all(root);assert.ok(nodes.some(n=>n.href==='/api/admin/workers/download'));assert.ok(nodes.some(n=>n.tag==='pre'&&n.textContent.includes('--central=https://central.example')));assert.ok(nodes.filter(n=>n.tag==='pre').every(n=>!n.textContent.includes('--register-token')));
 }finally{Object.assign(globalThis,saved);}
});
