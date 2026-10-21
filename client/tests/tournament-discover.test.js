import test from 'node:test';import assert from 'node:assert/strict';
import {mountHub} from '../js/tournament/tournament-hub.js';
import {setupPage,descendants,tick} from './helpers/tournament-page-dom.js';
const text=root=>descendants(root).map(n=>n.textContent).join(' ');
test('empty discovery offers Create without a reload and preserves the selected main tab in URL',async()=>{
 const {root}=setupPage('http://localhost/tournaments.html?tab=discover&category=open');let create=0;globalThis.fetch=async()=>({ok:true,json:async()=>({items:[],total:0,size:12})});const hub=mountHub(root,{onCreate:()=>create++});try{await tick();assert.match(text(root),/Không tìm thấy giải đấu phù hợp/);descendants(root).find(n=>n.tag==='button'&&n.textContent==='Tạo giải đấu').onclick();assert.equal(create,1);assert.equal(location.search.includes('tab=discover'),true);}finally{hub.dispose();}
});
test('discovery keeps filters and pagination and only submits edited search on Find',async()=>{
 const {root}=setupPage('http://localhost/tournaments.html?tab=discover&category=open&page=1&q=Alpha&format=SWISS');const calls=[];globalThis.fetch=async url=>{calls.push(new URL(url,'http://localhost'));return{ok:true,json:async()=>({items:[],total:30,size:12})};};const hub=mountHub(root);try{await tick();assert.equal(calls[0].searchParams.get('page'),'1');assert.match(text(root),/Trang 2 \/ 3/);const search=descendants(root).find(n=>n.tag==='input'),form=descendants(root).find(n=>n.tag==='form');search.value='Beta';await hub.request();assert.equal(calls.at(-1).searchParams.get('q'),'Alpha');form.onsubmit({preventDefault(){}});await tick();assert.equal(calls.at(-1).searchParams.get('q'),'Beta');assert.equal(calls.at(-1).searchParams.get('page'),'0');assert.equal(calls.at(-1).searchParams.get('format'),'SWISS');}finally{hub.dispose();}
});
test('Create tab skips discovery network reads and ignores an abandoned response',async()=>{
 const {root}=setupPage('http://localhost/tournaments.html?tab=create');let active=false,calls=0,finish;globalThis.fetch=()=>{calls++;return new Promise(resolve=>finish=resolve);};const hub=mountHub(root,{active:()=>active});try{await tick();assert.equal(calls,0);active=true;const old=hub.request(true,'tab',{replace:true});await tick();assert.equal(calls,1);active=false;await hub.request(true,'tab',{replace:true});finish({ok:true,json:async()=>({items:[],total:12,size:12})});await old;await tick();assert.equal(new URL(location.href).searchParams.get('tab'),'create');assert.doesNotMatch(text(root),/12 giải đấu/);}finally{hub.dispose();}
});

test('discovery restores pagination after a transient failure with an unchanged snapshot',async()=>{
 const {root}=setupPage();let fail=false;globalThis.fetch=async()=>{if(fail)throw new Error('offline');return{ok:true,json:async()=>({items:[],total:30,size:12})};};const hub=mountHub(root);try{await tick();assert.match(text(root),/Trang 1 \/ 3/);fail=true;await hub.request(true,'query');assert.match(text(root),/Thử lại/);fail=false;await hub.request(true,'query');assert.match(text(root),/Trang 1 \/ 3/);assert.doesNotMatch(text(root),/Thử lại/);}finally{hub.dispose();}
});
