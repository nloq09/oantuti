import test from 'node:test';
import assert from 'node:assert/strict';
import {Element,descendants,setup} from './helpers/tournament-dom.js';
import {createPlayerTabView} from '../js/tournament/player-tab-view.js';
import {renderMatchPages} from '../js/tournament/match-pages.js';
import {mountPublicBracket} from '../js/tournament/public-bracket.js';
import {mountMatchMonitor} from '../js/tournament/match-monitor.js';

const cup={id:'retained-cup',revision:4,rounds:[],stages:[],format:'SINGLE_ELIMINATION',participants:[],matches:[],bracket:[]},account={id:'reader',role:'USER'};
const tick=()=>new Promise(resolve=>setImmediate(resolve));
const reply=data=>({ok:true,json:async()=>data});
const text=root=>descendants(root).map(n=>n.textContent).join(' ');
function harness(){
 const root=setup();root.connectedRoot=true;globalThis.Node=Element;
 Object.defineProperty(Element.prototype,'isConnected',{configurable:true,get(){return !!this.connectedRoot||!!this.parent?.isConnected;}});
 Element.prototype.append=function(...nodes){for(const n of nodes){if(n instanceof Element){if(n.parent)n.parent.children=n.parent.children.filter(c=>c!==n);n.parent=this;}this.children.push(n);}};
 Element.prototype.prepend=function(...nodes){this.append(...nodes);this.children=[...nodes,...this.children.filter(n=>!nodes.includes(n))];};
 Element.prototype.replaceChildren=function(...nodes){for(const n of this.children)if(n instanceof Element)n.parent=null;this.children=[];this.append(...nodes);};
 Element.prototype.contains=function(n){return descendants(this).includes(n);};
 Element.prototype.querySelectorAll=function(selector){return descendants(this).slice(1).filter(n=>selector==='[data-match-id]'?!!n.dataset.matchId:selector.startsWith('details')&&n.tag==='details'&&(!selector.includes('[open]')||n.open));};
 Element.prototype.querySelector=function(selector){return this.querySelectorAll(selector)[0];};
 globalThis.Option=class extends Element{constructor(label,value){super('option');this.textContent=label;this.value=String(value);}};
 globalThis.location={pathname:'/tournament-detail.html',search:'?id=retained-cup'};
 const listeners=new Map(),timers=new Map();let serial=0;const add=(key,fn)=>{if(!listeners.has(key))listeners.set(key,new Set());listeners.get(key).add(fn);},remove=(key,fn)=>listeners.get(key)?.delete(fn);
 globalThis.addEventListener=add;globalThis.removeEventListener=remove;document.addEventListener=add;document.removeEventListener=remove;document.hidden=false;
 const originalSet=setTimeout,originalClear=clearTimeout;globalThis.setTimeout=fn=>{timers.set(++serial,fn);return serial;};globalThis.clearTimeout=id=>timers.delete(id);
 const owner=createPlayerTabView();function panel(){const p=new Element('section');root.replaceChildren(p);return p;}
 return{root,owner,panel,listeners,timers,cleanup(){owner.clear();globalThis.setTimeout=originalSet;globalThis.clearTimeout=originalClear;}};
}

test('parent repaint retains the mounted schedule, selected filter and single poller',async()=>{
 const h=harness();let calls=0;globalThis.fetch=async()=>{calls++;return reply({tournamentId:cup.id,revision:4,total:0,items:[]});};
 try{h.owner.reconcile(cup,'schedule',account,'RUNNING');const view=h.owner.attach(h.panel(),host=>renderMatchPages(host,cup,{states:[{}]}));await view.refresh();const select=descendants(h.root).find(n=>n.tag==='select');select.value='2';select.onchange();await view.refresh();const before=calls;
  h.owner.reconcile({...cup,matches:[{executionState:'RUNNING_REMOTE'}]},'schedule',account,'RUNNING');const reused=h.owner.attach(h.panel(),()=>{throw Error('unexpected remount');});assert.equal(reused,view);assert.equal(calls,before);assert.ok(descendants(h.root).includes(select));assert.equal(select.value,'2');assert.equal(h.listeners.get('online').size,1);assert.equal(h.timers.size,1);
 }finally{h.cleanup();}assert.equal(h.listeners.get('online').size,0);assert.equal(h.timers.size,0);
});
test('parent repaint retains bracket zoom and reuses the existing refresh controller',async()=>{
 const h=harness();globalThis.fetch=async()=>reply(cup);
 try{h.owner.reconcile(cup,'bracket',account,'RUNNING');const view=h.owner.attach(h.panel(),host=>mountPublicBracket(host,cup,null));await view.refresh();const zoom=descendants(h.root).find(n=>n.tag==='select'),tree=descendants(h.root).find(n=>n.className==='bracket-tree');tree.style={};zoom.value='1.5';zoom.onchange();
  h.owner.reconcile(cup,'bracket',account,'RUNNING');h.owner.attach(h.panel(),()=>{throw Error('unexpected remount');});assert.equal(descendants(h.root).find(n=>n.tag==='select'),zoom);assert.equal(tree.style.fontSize,'1.5em');assert.equal(h.listeners.get('visibilitychange').size,1);
 }finally{h.cleanup();}
});
test('leaving a tab immediately aborts its read and a late response cannot replace the next tab',async()=>{
 const h=harness();let resolve,signal;globalThis.fetch=(_url,options)=>{signal=options.signal;return new Promise(done=>resolve=done);};
 try{h.owner.reconcile(cup,'schedule',account,'RUNNING');h.owner.attach(h.panel(),host=>renderMatchPages(host,cup,null));await tick();assert.equal(signal.aborted,false);h.owner.reconcile(cup,'overview',account,'RUNNING');assert.equal(signal.aborted,true);const overview=h.panel();overview.append(new Element('h2'));overview.children[0].textContent='Overview';resolve(reply({tournamentId:cup.id,revision:4,total:0,items:[]}));await tick();assert.equal(text(h.root).trim(),'Overview');assert.equal(h.listeners.get('online').size,0);assert.equal(h.timers.size,0);
 }finally{h.cleanup();}
});
test('parent repaint keeps an expanded personal match detail instead of mounting another monitor',async()=>{
 const h=harness(),side={participantId:'p',playerName:'Player',controllerType:'HUMAN'};let calls=0;globalThis.fetch=async()=>{calls++;return reply({tournamentId:cup.id,revision:4,total:1,items:[{id:'m',round:1,gameNumber:1,bestOf:1,status:'PENDING',executionState:'QUEUED',participantA:side,participantB:side}],queuePositionNote:'Scoped queue'});};
 try{h.owner.reconcile(cup,'my-matches',account,'RUNNING');const monitor=h.owner.attach(h.panel(),host=>mountMatchMonitor(host,cup,{mine:true}));await monitor.refresh();const detail=descendants(h.root).find(n=>n.tag==='details');detail.open=true;const before=calls;h.owner.reconcile(cup,'my-matches',account,'RUNNING');h.owner.attach(h.panel(),()=>{throw Error('unexpected remount');});assert.ok(descendants(h.root).includes(detail));assert.equal(detail.open,true);assert.equal(calls,before);assert.equal(h.timers.size,1);
 }finally{h.cleanup();}
});
test('phase or account changes rebuild a view and retire the old request',async()=>{
 for(const [next,nextAccount,phase]of [[cup,account,'FINISHED'],[cup,{id:'someone-else',role:'USER'},'RUNNING'],[cup,null,'RUNNING']]){
  const h=harness();let signal;globalThis.fetch=(_url,options)=>{signal=options.signal;return new Promise(()=>{});};
  try{h.owner.reconcile(cup,'my-matches',account,'RUNNING');h.owner.attach(h.panel(),host=>mountMatchMonitor(host,cup,{mine:true}));await tick();h.owner.reconcile(next,'my-matches',nextAccount,phase);assert.equal(signal.aborted,true);assert.equal(h.listeners.get('online').size,0);const container=h.panel();let mounts=0;h.owner.attach(container,()=>{mounts++;return{stop(){}};});assert.equal(mounts,1);
  }finally{h.cleanup();}
 }
});
test('clearing a view before the initial microtask prevents any obsolete network call',async()=>{
 const h=harness();let calls=0;globalThis.fetch=async()=>{calls++;return reply(cup);};try{h.owner.reconcile(cup,'bracket',account,'RUNNING');h.owner.attach(h.panel(),host=>mountPublicBracket(host,cup,null));h.owner.clear();await tick();assert.equal(calls,0);assert.equal(h.timers.size,0);assert.equal(h.listeners.get('pagehide').size,0);}finally{h.cleanup();}
});
test('a newer revision updates round choices without resetting schedule filter or page controls',async()=>{
 const h=harness();let revision=4;globalThis.fetch=async()=>reply({tournamentId:cup.id,revision,total:0,items:[]});
 try{h.owner.reconcile(cup,'schedule',account,'RUNNING',{states:[{}]});const view=h.owner.attach(h.panel(),host=>renderMatchPages(host,cup,{states:[{}]}));await view.refresh();const selects=descendants(h.root).filter(n=>n.tag==='select'),sort=selects.at(-1);sort.value='time';const next={...cup,revision:5,rounds:[{number:1}]};revision=5;h.owner.reconcile(next,'schedule',account,'RUNNING',{states:[{}]});h.owner.attach(h.panel(),()=>{throw Error('unexpected remount');});await view.refresh();assert.equal(descendants(h.root).filter(n=>n.tag==='select').at(-1),sort);assert.equal(sort.value,'time');assert.ok(selects[0].children.some(n=>n.value==='1'));assert.equal(h.listeners.get('online').size,1);
 }finally{h.cleanup();}
});
