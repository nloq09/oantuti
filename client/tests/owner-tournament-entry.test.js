import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {installDocument,text} from './helpers/organizer-dom.js';
test('owner opening a direct player detail or lobby URL redirects before rendering personal actions',async()=>{
 const original=globalThis.fetch;
 try{for(const page of ['tournament-detail.html','tournament-lobby.html']){
  const doc=installDocument(readFileSync(new URL('../'+page,import.meta.url),'utf8'));doc.hidden=false;
  const events=new EventTarget();globalThis.addEventListener=events.addEventListener.bind(events);globalThis.removeEventListener=events.removeEventListener.bind(events);globalThis.CustomEvent=class extends Event{constructor(type,options){super(type);this.detail=options.detail;}};
  let destination;globalThis.location=new URL('http://example.test/'+page+'?id=cup&stay=1#matches');location.replace=url=>destination=url;
  globalThis.history={replaceState(){}};globalThis.WebSocket=class{send(){}close(){}};
  const calls=[];
  globalThis.fetch=async(url,options={})=>{calls.push({url,options});let data;
   if(url==='/api/auth/me')data={authenticated:true,user:{id:'owner',role:'USER'}};
   else if(url.endsWith('/summary'))data={id:'cup',ownerUserId:'owner',status:'RUNNING',timeline:{},revision:3};
   else if(url.endsWith('/me'))data={tournamentId:'cup',revision:3,states:[]};
   else throw Error('Unexpected participant request '+url);
   return {ok:true,status:200,json:async()=>data};
  };
  await import('../js/tournament/player-detail.js?owner-entry='+page);
  assert.equal(destination,'/tournament.html?id=cup');
  assert.ok(!calls.some(c=>/participant-flow|my-matches|\/entry|\/ready/.test(c.url)));
  assert.doesNotMatch(text(doc.querySelector('#tournament-player')),/Quản lý giải|Việc của bạn/);
  events.dispatchEvent(new Event('pagehide'));
 }}finally{globalThis.fetch=original;}
});
