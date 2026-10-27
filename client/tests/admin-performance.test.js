import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { installDocument, text } from "./helpers/organizer-dom.js";
import { readSystem, subscribeSystem, publishSystem } from "../js/admin/system-status.js";
import { watchNotifications } from "../js/admin/notifications.js";
import { dashboard } from "../js/admin/dashboard.js";
import { mountOperations } from "../js/admin/operations.js";
import { preload } from "../js/admin/views.js";

function environment() {
  const previous = {fetch:globalThis.fetch, setInterval:globalThis.setInterval, clearInterval:globalThis.clearInterval, addEventListener:globalThis.addEventListener};
  const doc=installDocument('<body><details></details><main></main></body>');doc.hidden=false;
  const intervals=new Set(), events=new EventTarget();
  globalThis.setInterval=fn=>{intervals.add(fn);return fn;};globalThis.clearInterval=fn=>intervals.delete(fn);
  globalThis.addEventListener=events.addEventListener.bind(events);
  return {doc, intervals, events, close(){events.dispatchEvent(new Event('pagehide'));Object.assign(globalThis,previous);}};
}
const systemData=()=>({alerts:[],checkedAt:'2026-10-27T00:00:00Z',application:'UP',postgresql:'UP',docker:{status:'UP',managedContainers:0},workers:{state:'ACCEPTING',active:0,maxConcurrentMatches:4,pending:0,maxPendingMatches:256},queue:[],queueCount:0,queueCapacity:256,storage:{bots:{status:'UP'},replays:{status:'UP'}},jvmMemoryUsedBytes:1024,pendingAudit:0,activity:[]});

test('parallel system readers share one request and failed requests can be retried',async()=>{
  const env=environment(),updates=[];let complete,calls=0;
  const unsubscribe=subscribeSystem((data,error)=>updates.push({data,error}));
  try {
    globalThis.fetch=()=>{calls++;return new Promise(resolve=>complete=resolve);};
    const first=readSystem(),second=readSystem();assert.equal(first,second);assert.equal(calls,1);
    complete({ok:true,status:200,json:async()=>systemData()});await first;assert.equal(updates.length,1);
    globalThis.fetch=async()=>({ok:false,status:401,json:async()=>({error:'UNAUTHENTICATED'})});
    await assert.rejects(readSystem(),error=>error.status===401);assert.equal(updates.at(-1).error.status,401);
    globalThis.fetch=async()=>({ok:true,status:200,json:async()=>systemData()});await readSystem();assert.ok(updates.at(-1).data);
  }finally{unsubscribe();env.close();}
});

test('dashboard publishes alerts to the header without an extra system request on load or refresh',async()=>{
  const env=environment(),calls=[];
  try {
    globalThis.fetch=async url=>{calls.push(url);return{ok:true,status:200,json:async()=>systemData()};};
    const header=env.doc.querySelector('details');watchNotifications(header,{poll:false});
    await dashboard(env.doc.querySelector('main'));
    assert.deepEqual(calls,['/api/admin/dashboard']);assert.match(text(header),/Cảnh báo · 0/);
    assert.equal(env.intervals.size,1);await [...env.intervals][0]();
    assert.deepEqual(calls,['/api/admin/dashboard','/api/admin/dashboard']);
    env.events.dispatchEvent(new Event('pagehide'));assert.equal(env.intervals.size,0);
    publishSystem({alerts:[{severity:'INFO',message:'Disposed listener must not update'}]});assert.doesNotMatch(text(header),/Disposed listener/);
  }finally{env.close();}
});

test('system page and header share the same request and error state',async()=>{
  const env=environment(),calls=[];
  try {
    globalThis.fetch=async url=>{calls.push(url);return{ok:true,status:200,json:async()=>({...systemData(),alerts:[{severity:'WARNING',message:'Worker capacity'}]})};};
    const header=env.doc.querySelector('details');watchNotifications(header,{poll:false});
    await mountOperations('system.html',env.doc.querySelector('main'));
    assert.deepEqual(calls,['/api/admin/system']);assert.equal(env.intervals.size,1);
    assert.match(text(header),/Worker capacity/);assert.match(text(env.doc.querySelector('main')),/Worker capacity/);
    globalThis.fetch=async()=>({ok:false,status:503,json:async()=>({error:'SERVER_UNAVAILABLE'})});
    await [...env.intervals][0]();assert.match(text(header),/Chưa cập nhật/);
  }finally{env.close();}
});

test('ordinary admin pages poll once and overlapping refreshes do not multiply requests',async()=>{
  const env=environment();let calls=0,complete;
  try {
    globalThis.fetch=()=>{calls++;return new Promise(resolve=>complete=resolve);};
    watchNotifications(env.doc.querySelector('details'));
    assert.equal(env.intervals.size,1);[...env.intervals][0]();const same=readSystem();assert.equal(calls,1);
    complete({ok:true,status:200,json:async()=>systemData()});await same;
    assert.match(text(env.doc.querySelector('details')),/Cảnh báo · 0/);
  }finally{env.close();}
});

test('initial admin graph excludes unrelated screens and preloading never calls private APIs',async()=>{
  const env=environment();let calls=0;
  try {
    globalThis.fetch=async()=>{calls++;throw new Error('A code preload must not fetch account data');};
    const seen=new Set();
    function visit(file){file=resolve(file);if(seen.has(file))return;seen.add(file);const source=readFileSync(file,'utf8');for(const match of source.matchAll(/^import\s+(?:[^;\n]*?from\s*)?["'](\.[^"']+)["']/gm))visit(resolve(dirname(file),match[1]));}
    visit(fileURLToPath(new URL('../js/admin/admin-shell.js',import.meta.url)));
    assert.ok(seen.size<=12,`Unexpected startup graph: ${seen.size} files`);
    for(const name of ['workers.js','audit-view.js','tournaments.js','directory.js','matches.js'])assert.ok(![...seen].some(file=>file.endsWith('/'+name)||file.endsWith('\\'+name)));
    const first=preload('index.html');assert.equal(first,preload('index.html'));assert.equal(typeof (await first).dashboard,'function');
    assert.equal(typeof (await preload('matches.html')).mountMatches,'function');assert.equal(calls,0);
  }finally{env.close();}
});
