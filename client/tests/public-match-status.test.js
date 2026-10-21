import test from 'node:test';
import assert from 'node:assert/strict';
import {setup,Element,descendants} from './helpers/tournament-dom.js';
import {matchOpen,matchStatusText,activeMatch,currentMatchOpen} from '../js/tournament/match-execution.js';
import {renderBracket} from '../js/tournament/bracket-renderer.js';
import {renderLobbyMatches} from '../js/tournament/lobby-matches.js';
import {renderMatchPages} from '../js/tournament/match-pages.js';
import {mountPublicBracket} from '../js/tournament/public-bracket.js';

Element.prototype.replaceChildren=function(...nodes){this.children=nodes;};
Element.prototype.prepend=function(...nodes){this.children.unshift(...nodes);};
Element.prototype.contains=function(node){return descendants(this).includes(node);};
Element.prototype.querySelectorAll=function(selector){return descendants(this).filter(n=>selector==='[data-match-id]'&&n.dataset.matchId);};
const a={participantId:'a',displayName:'Alpha'},b={participantId:'b',displayName:'Beta'};
const match={id:'m',round:1,stageNumber:1,participantA:a,participantB:b,status:'PENDING',remote:true,executionState:'QUEUED',onlineMatchId:'stale-room'};
const cup={id:'public-status',revision:7,rounds:[{number:1},{number:2}],stages:[],participants:[a,b],matches:[match],format:'SINGLE_ELIMINATION',bracket:[{round:1,position:0,matchId:'m',slotA:'a',slotB:'b'}]};
const text=root=>descendants(root).map(n=>n.textContent).join(' ');
const tick=()=>new Promise(resolve=>setImmediate(resolve));
function env(){const root=setup();root.isConnected=true;globalThis.Node=Element;globalThis.Option=class extends Element{constructor(label,value){super('option');this.textContent=label;this.value=String(value);}};globalThis.location={pathname:'/tournament-lobby.html',search:'?id=public-status'};globalThis.addEventListener=globalThis.removeEventListener=()=>{};document.addEventListener=document.removeEventListener=()=>{};document.hidden=false;return root;}
const page=(items=[match],total=items.length)=>({tournamentId:cup.id,revision:7,items,total});
function response(data,ok=true){return{ok,json:async()=>data};}

test('public remote labels never turn stale room IDs into live or pending replay links',()=>{
 env();for(const state of ['QUEUED','ASSIGNED','STARTING','RUNNING_REMOTE','RESULT_PENDING','RETRYING','RECOVERING']){const m={...match,executionState:state,historyMatchId:'unconfirmed'};assert.equal(activeMatch(m),true);assert.equal(matchOpen(m,cup.id),null);assert.notEqual(matchStatusText(m),matchStatusText({status:'PENDING'}));}
 assert.equal(matchOpen({...match,status:'FINISHED',executionState:'FINISHED'},cup.id),null);
 assert.equal(matchOpen({...match,status:'FINISHED',executionState:'FINISHED',historyMatchId:'real/history'},cup.id).href,'/replay.html?match=real%2Fhistory');
 assert.match(matchOpen({...match,remote:false,status:'RUNNING',executionState:'RUNNING_LOCAL'},cup.id).href,/spectate=stale-room/);
 assert.equal(matchOpen({...match,remote:false,status:'RUNNING',executionState:'CANCELLED'},cup.id),null);
});
test('bracket shows remote progress and review notice without a false winner or live board',()=>{
 const root=env();renderBracket(root,{...cup,matches:[{...match,executionState:'RESULT_PENDING'}]});assert.match(text(root),/Đang xác nhận kết quả/);assert.equal(descendants(root).some(n=>n.tag==='a'),false);
 renderBracket(root,{...cup,bracket:[{...cup.bracket[0],resolution:'WIN',winnerParticipantId:'a'}],matches:[{...match,status:'FINISHED',executionState:'FINISHED',reviewRequired:true,historyMatchId:'h'}]});assert.match(text(root),/trọng tài xem xét/);assert.equal(descendants(root).some(n=>n.className==='bracket-winner'),false);assert.match(descendants(root).find(n=>n.tag==='a').href,/match=h/);
});
test('guest lobby includes active remote games, an honest live limitation and bounded-list navigation',()=>{
 const root=env();renderLobbyMatches(root,{...cup,activeMatchesTruncated:true});assert.match(text(root),/Alpha vs Beta/);assert.match(text(root),/Đang chờ máy thi đấu/);assert.match(text(root),/chưa có bàn cờ trực tiếp/);assert.doesNotMatch(text(root),/chưa có trận đang xử lý/);assert.equal(descendants(root).filter(n=>n.tag==='a').length,1);assert.match(descendants(root).find(n=>n.tag==='a').href,/#schedule$/);
});
test('personal status falls back only to an authorized public projection when monitor loading fails',()=>{
 env();assert.equal(currentMatchOpen(cup,null,'m'),null);assert.equal(currentMatchOpen({...cup,matches:[]},null,'m'),null);
 const local={...match,remote:false,executionState:'RUNNING_LOCAL',status:'RUNNING'};assert.match(currentMatchOpen({...cup,matches:[local]},null,'m').href,/spectate=/);
 assert.equal(currentMatchOpen({...cup,matches:[local]},{matchMonitor:{items:[match]}},'m'),null);
});
test('public bracket polls independently at the same revision and retains its zoom controls',async()=>{
 const root=env();let data={...cup};globalThis.fetch=async()=>response(data);const mounted=mountPublicBracket(root,cup,null);try{await mounted.refresh();const zoom=descendants(root).find(n=>n.tag==='select'),tree=descendants(root).find(n=>n.className==='bracket-tree');tree.style={};zoom.value='1.5';zoom.onchange();data={...cup,matches:[{...match,executionState:'RUNNING_REMOTE'}]};await mounted.refresh();assert.match(text(root),/Trận đang chạy trên máy thi đấu/);assert.equal(descendants(root).find(n=>n.tag==='select'),zoom);assert.equal(tree.style.fontSize,'1.5em');const previousTreeChild=tree.children[0];await mounted.refresh();assert.equal(tree.children[0],previousTreeChild);
 }finally{mounted.stop();}
});
test('schedule refreshes execution state at the same revision without rebuilding unchanged rows',async()=>{
 const root=env();let current=page();globalThis.fetch=async()=>response(current);const mounted=renderMatchPages(root,cup,null);try{await mounted.refresh();assert.match(text(root),/Đang chờ máy thi đấu/);const initialTable=descendants(root).find(n=>n.tag==='table');await mounted.refresh();assert.equal(descendants(root).find(n=>n.tag==='table'),initialTable);
  current=page([{...match,executionState:'RUNNING_REMOTE'}]);await mounted.refresh();assert.match(text(root),/Trận đang chạy trên máy thi đấu/);assert.equal(descendants(root).some(n=>n.href?.includes('spectate=')),false);
 }finally{mounted.stop();}
});
test('schedule keeps keyboard focus until it leaves the table and then applies pending progress',async()=>{
 const root=env();let current=page();globalThis.fetch=async()=>response(current);const mounted=renderMatchPages(root,{...cup,id:'focused'},null);try{current.tournamentId='focused';await mounted.refresh();const table=descendants(root).find(n=>n.tag==='table');document.activeElement=table;current={...page([{...match,executionState:'RUNNING_REMOTE'}]),tournamentId:'focused'};await mounted.refresh();assert.equal(descendants(root).find(n=>n.tag==='table'),table);assert.match(text(root),/giữ vị trí bàn phím/);document.activeElement=null;await mounted.refresh();assert.match(text(root),/Trận đang chạy trên máy thi đấu/);
 }finally{mounted.stop();}
});
test('schedule cancels a replaced filter request and ignores its late response',async()=>{
 const root=env();let pending,lateSignal;globalThis.fetch=async(_url,options)=>{lateSignal=options.signal;return new Promise(resolve=>pending=resolve);};const t={...cup,id:'filter-race'},mounted=renderMatchPages(root,t,null);try{await tick();const selects=descendants(root).filter(n=>n.tag==='select');globalThis.fetch=async url=>{assert.equal(new URL(url,'http://localhost').searchParams.get('round'),'2');return response({...page([{...match,round:2,executionState:'RUNNING_REMOTE'}]),tournamentId:t.id});};selects[0].value='2';selects[0].onchange();await mounted.refresh();assert.equal(lateSignal.aborted,true);pending(response({...page([{...match,executionState:'STARTING'}]),tournamentId:t.id}));await tick();assert.match(text(root),/Trận đang chạy trên máy thi đấu/);assert.doesNotMatch(text(root),/Đang khởi động bot/);
 }finally{mounted.stop();}
});
test('schedule preserves filters and page on refresh and returns to the first page after shrink',async()=>{
 const root=env(),t={...cup,id:'paging'};let total=23;const urls=[];globalThis.fetch=async url=>{const query=new URL(url,'http://localhost').searchParams;urls.push(query);return response({...page(query.get('page')==='1'&&total>20?[{...match,id:'last'}]:[match],total),tournamentId:t.id});};const mounted=renderMatchPages(root,t,null);try{await mounted.refresh();const next=descendants(root).find(n=>n.tag==='button'&&n.textContent==='Sau →');next.onclick();await mounted.refresh();await mounted.refresh();assert.equal(urls.at(-1).get('page'),'1');assert.match(text(root),/Trang 2/);total=1;await mounted.refresh();await mounted.refresh();assert.equal(urls.at(-1).get('page'),'0');assert.match(text(root),/Trang 1/);
 }finally{mounted.stop();}
});
test('schedule keeps its last snapshot on transient error but clears it when private access expires',async()=>{
 const root=env(),t={...cup,id:'auth'};globalThis.fetch=async()=>response({...page(),tournamentId:t.id});const mounted=renderMatchPages(root,t,{states:[{}]});try{await mounted.refresh();globalThis.fetch=async()=>{throw new Error('network');};await mounted.refresh();assert.match(text(root),/có thể đã cũ/);assert.match(text(root),/Alpha vs Beta/);globalThis.fetch=async()=>response({error:'UNAUTHENTICATED'},false);await mounted.refresh();assert.match(text(root),/quyền truy cập đã thay đổi/);assert.doesNotMatch(text(root),/Alpha vs Beta/);
 }finally{mounted.stop();}
});
