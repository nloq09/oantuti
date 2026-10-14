import test from "node:test";
import assert from "node:assert/strict";
import {TournamentStream} from "../js/tournament/tournament-api.js";
test("full snapshots remain authoritative while repeated audit notifications are deduplicated",()=>{
 const originalSocket=globalThis.WebSocket,originalLocation=globalThis.location;
 const sockets=[];globalThis.location={protocol:"http:",host:"localhost"};globalThis.WebSocket=class{constructor(){sockets.push(this);}send(value){this.sent=JSON.parse(value);}close(){}};
 try{const states=[],events=[];const stream=new TournamentStream("cup",state=>states.push(state),()=>{},event=>events.push(event));const socket=sockets[0];socket.onopen();assert.equal(socket.sent.type,"WATCH_TOURNAMENT");
  socket.onmessage({data:JSON.stringify({type:"TOURNAMENT_STATE",payload:{tournament:{revision:5}}})});for(let i=0;i<2;i++)socket.onmessage({data:JSON.stringify({type:"MATCH_FINISHED",payload:{revision:5,event:{sequence:8,type:"MATCH_FINISHED"}}})});
  assert.deepEqual(states,[{revision:5}]);assert.equal(events.length,1);stream.close();
 }finally{globalThis.WebSocket=originalSocket;globalThis.location=originalLocation;}
});
