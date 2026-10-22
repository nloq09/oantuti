import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {TournamentClientState,playerFlowCapabilities} from '../js/tournament/player-state.js';
import {readSnapshotKey} from '../js/tournament/read-snapshot.js';

// Execute the real page reader with injected transports/rendering. Importing the entire page
// also starts navigation, timers and sockets, which are outside this read-recovery test.
const source=await readFile(new URL('../js/tournament/player-detail.js',import.meta.url),'utf8');
const start=source.indexOf('async function readPlayer('),end=source.indexOf('\nfunction render(',start);
assert.ok(start>=0&&end>start,'page reader must be available for the integration harness');
const readerSource=source.slice(start,end);
function harness(){
 let failed=false,snapshot={id:'cup',revision:7,status:'RUNNING',matches:[]},screen='empty',renders=0,clears=0;
 const dependencies={account:null,getAccount:async()=>null,tournamentRequest:async()=>{if(failed)throw Error('NETWORK');return snapshot;},id:'cup',state:new TournamentClientState(),playerFlowCapabilities,readSnapshotKey,flow:null,serverClock:{update(){}},timelineLobbyDestination:()=>null,playerRefresh:{dispose(){}},location:{},renderPending:false,onRender(){renders++;screen='content';},currentHumanMatch:()=>null,document:{hidden:false},tabView:{clear(){clears++;}},root:{replaceChildren(){screen='error';}},node:()=>({}),link:()=>({}),matchEntry:{}};
 const read=new Function(...Object.keys(dependencies),readerSource+';function render(){renderPending=false;onRender();}return readPlayer;')(...Object.values(dependencies));
 return{read:(isCurrent=()=>true)=>read({force:false,isCurrent,signal:new AbortController().signal}),fail(value){failed=value;},revision(value){snapshot={...snapshot,revision:value};},get screen(){return screen;},get renders(){return renders;},get clears(){return clears;}};
}
test('successful polling restores an errored player page even at the same tournament revision',async()=>{
 const h=harness();await h.read();assert.equal(h.renders,1);h.fail(true);await h.read();assert.equal(h.screen,'error');assert.equal(h.clears,1);h.fail(false);await h.read();assert.equal(h.screen,'content');assert.equal(h.renders,2);await h.read();assert.equal(h.renders,2);
});
test('failed initial load and repeated transient failures recover on the next valid snapshot',async()=>{
 const h=harness();h.fail(true);await h.read();await h.read();assert.equal(h.screen,'error');assert.equal(h.renders,0);h.fail(false);await h.read();assert.equal(h.screen,'content');assert.equal(h.renders,1);
});
test('an obsolete failed request cannot replace a newer page or schedule a redundant repaint',async()=>{
 const h=harness();await h.read();h.fail(true);await h.read(()=>false);assert.equal(h.screen,'content');assert.equal(h.clears,0);h.fail(false);await h.read();assert.equal(h.renders,1);
});
test('a stale revision cannot consume the pending recovery of the player page',async()=>{
 const h=harness();await h.read();h.fail(true);await h.read();h.fail(false);h.revision(6);await h.read();assert.equal(h.screen,'error');h.revision(7);await h.read();assert.equal(h.screen,'content');assert.equal(h.renders,2);
});
