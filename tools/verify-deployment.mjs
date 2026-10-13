// Creates dedicated test accounts/data. Run only against an isolated test deployment.
import assert from "node:assert/strict";
import {randomBytes, randomUUID} from "node:crypto";
import {readFile, writeFile} from "node:fs/promises";
import {parseArgs} from "node:util";
import tls from "node:tls";
const {values}=parseArgs({options:{url:{type:"string"},fixture:{type:"string"},seed:{type:"boolean"},verify:{type:"boolean"}}});
assert.ok(values.url && values.fixture && (values.seed !== values.verify),"Use --url --fixture and exactly one of --seed/--verify");
const base=values.url.replace(/\/$/,"");
const pause=ms=>new Promise(resolve=>setTimeout(resolve,ms));
class Browser {
  cookies=new Map();
  async fetch(path,options={}) {
    const response=await fetch(base+path,{...options,headers:{...options.headers,Cookie:[...this.cookies].map(([key,value])=>key+"="+value).join("; ")}});
    for(const cookie of response.headers.getSetCookie()) {const [pair]=cookie.split(";");const equal=pair.indexOf("=");this.cookies.set(pair.slice(0,equal),pair.slice(equal+1));}
    return response;
  }
  async csrf(){const response=await this.fetch("/api/auth/csrf");assert.equal(response.status,200);const data=await response.json();return {[data.headerName]:data.token};}
  async request(path,body,expected=200,method) {
    const response=await this.fetch(path,{method:method||(body===undefined?"GET":"POST"),headers:{"Content-Type":"application/json",...(body===undefined?{}:await this.csrf())},body:body===undefined?undefined:JSON.stringify(body)});
    assert.equal(response.status,expected,`${path}: ${await response.clone().text()}`);
    return response.status===204?undefined:response.json();
  }
  async login(account) {
    const response=await this.fetch("/api/auth/login",{method:"POST",headers:{"Content-Type":"application/json",...await this.csrf()},body:JSON.stringify({username:account.username,password:account.password})});
    assert.equal(response.status,200);
    if(base.startsWith("https:"))assert.ok(response.headers.getSetCookie().some(value=>/HttpOnly/i.test(value)&&/Secure/i.test(value)&&/SameSite=Lax/i.test(value)));
    assert.equal((await response.json()).id,account.id);
  }
  async upload(name) {
    const source=`import sys,json
for line in sys.stdin:
 q=json.loads(line); actions=q['legalActions']; goal={'row':0,'col':8} if q['player']=='P1' else {'row':8,'col':0}
 pieces={p['id']:p for p in q['state']['board']['pieces'] if p['status']=='ALIVE'}
 enemy={(p['position']['row'],p['position']['col']):p for p in pieces.values() if p['ownerId']!=q['player']}
 beats={'ROCK':'SCISSORS','SCISSORS':'PAPER','PAPER':'ROCK'}
 def score(a):
  if a['type']!='MOVE': return -10000
  p=a['to']; target=enemy.get((p['row'],p['col'])); own=pieces[a['pieceId']]
  combat=(200 if beats[own['type']]==target['type'] else -1000) if target else 0
  return combat-max(abs(p['row']-goal['row']),abs(p['col']-goal['col']))*10
 a=max(actions,key=score)
 print(json.dumps({'type':'ACTION','sequence':q['sequence'],'action':a}),flush=True)
`;
    const form=new FormData();form.append("name",name);form.append("file",new File([source],"main.py"));
    const response=await this.fetch("/api/bots",{method:"POST",headers:await this.csrf(),body:form});assert.equal(response.status,201);return response.json();
  }
}
class Seat {
  messages=[];waiters=[];
  constructor({matchId,playerId,token}){this.credentials={matchId,playerId,token};}
  async connect(){
    this.socket=new WebSocket(base.replace(/^http/,"ws")+"/ws/game");
    this.socket.onmessage=event=>{
      const message=JSON.parse(event.data);const waiting=this.waiters.find(w=>w.predicate(message));
      if(message.type==="ACTION_REJECTED") {for(const waiter of this.waiters){clearTimeout(waiter.timer);waiter.reject(new Error(message.payload.error));}this.waiters=[];return;}
      if(waiting){this.waiters.splice(this.waiters.indexOf(waiting),1);clearTimeout(waiting.timer);waiting.resolve(message);}else this.messages.push(message);
    };
    await new Promise((resolve,reject)=>{const timer=setTimeout(()=>reject(new Error("WebSocket connect timeout")),10000);this.socket.onopen=()=>{clearTimeout(timer);resolve();};this.socket.onerror=()=>{clearTimeout(timer);reject(new Error("WebSocket connect failed"));};});
  }
  state(predicate){return this.next(message=>message.type==="GAME_STATE"&&predicate(message.payload.snapshot)).then(message=>message.payload.snapshot);}
  next(predicate){
    const index=this.messages.findIndex(predicate);if(index>=0)return Promise.resolve(this.messages.splice(index,1)[0]);
    return new Promise((resolve,reject)=>{const waiter={predicate,resolve,reject,timer:setTimeout(()=>reject(new Error("WebSocket state timeout")),10000)};this.waiters.push(waiter);});
  }
  send(type,payload,sequence){this.socket.send(JSON.stringify({type,...this.credentials,payload,sequence}));}
  close(){for(const waiter of this.waiters)clearTimeout(waiter.timer);this.socket?.close();}
}
async function handshake(origin,expected) {
  const url=new URL(base);if(url.protocol!=="https:")return;
  await new Promise((resolve,reject)=>{
    const socket=tls.connect({host:url.hostname,port:Number(url.port||443),servername:url.hostname});
    const timer=setTimeout(()=>{socket.destroy();reject(new Error("Handshake timeout"));},10000);
    socket.on("secureConnect",()=>socket.write(`GET /ws/game HTTP/1.1\r\nHost: ${url.host}\r\nOrigin: ${origin}\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Key: ${randomBytes(16).toString("base64")}\r\nSec-WebSocket-Version: 13\r\n\r\n`));
    socket.once("data",data=>{clearTimeout(timer);socket.destroy();try{assert.match(data.toString().split("\r\n")[0],new RegExp(" "+expected+" "));resolve();}catch(error){reject(error);}});
    socket.on("error",error=>{clearTimeout(timer);reject(error);});
  });
}
const publicResponse=await fetch(base+"/actuator/health");assert.equal(publicResponse.status,200);assert.equal((await publicResponse.json()).status,"UP");
assert.equal((await fetch(base+"/actuator/metrics")).status,401);
await handshake(new URL(base).origin,101);await handshake("https://untrusted.example",403);
if(values.seed) {
  const a=new Browser(),b=new Browser(),suffix=randomUUID().slice(0,8),accounts=[];
  for(const [index,browser] of [a,b].entries()) {
    const username=`deploy_${suffix}_${index}`,password=randomBytes(24).toString("hex");
    const result=await browser.request("/api/auth/register",{username,email:username+"@example.com",password,displayName:"Deployment Test"},201);
    accounts.push({...result,password});await browser.login(accounts[index]);
  }
  const botA=await a.upload("Alpha test"),botB=await b.upload("Beta test");
  assert.ok((await a.request("/api/account/bots")).some(bot=>bot.id===botA.id));
  assert.ok(!(await b.request("/api/account/bots")).some(bot=>bot.id===botA.id));
  assert.ok((await b.request("/api/account/bots")).some(bot=>bot.id===botB.id));
  await a.request("/api/auth/logout",{});assert.equal((await a.request("/api/auth/me")).authenticated,false);await a.login(accounts[0]);
  await b.request("/api/account/bots/"+botA.id,{name:"stolen"},403,"PATCH");
  await b.request("/api/account/bots/"+botA.id,{},403,"DELETE");
  const capabilities=await a.request("/api/bots/capabilities");assert.ok(capabilities.languages.PYTHON,"Docker runner not available");
  const first=await a.request("/api/matches",{mode:"HUMAN_VS_HUMAN"},201);const second=await b.request(`/api/matches/${first.matchId}/join`,{});
  const p1=new Seat(first),p2=new Seat(second);
  try {
    for(const seat of [p1,p2]) {await seat.connect();seat.send("JOIN_MATCH");await seat.state(state=>state.status==="PLACEMENT");seat.send("RANDOM_PLACEMENT");await seat.state(state=>state.remaining.ROCK===0&&state.remaining.PAPER===0&&state.remaining.SCISSORS===0);}
    p1.send("READY");await p1.state(state=>state.players.find(player=>player.playerId==="P1").ready);
    p2.send("READY");await p1.state(state=>state.status==="PLAYING");p1.send("LEAVE");await p2.state(state=>state.status==="FINISHED");
  } finally {p1.close();p2.close();}
  for(const browser of [a,b]) {const history=await browser.request("/api/account/matches");assert.ok(history.matches.some(match=>match.id===first.historyId));}
  const replay=await a.request(`/api/matches/${first.historyId}/replay`);assert.equal(replay.result.winner,"P2");
  const cup=await a.request("/api/tournaments",{name:"Deployment tournament",format:"ROUND_ROBIN"},201);
  await a.request(`/api/tournaments/${cup.id}/participants`,{botId:botA.id,seed:1});await b.request(`/api/tournaments/${cup.id}/participants`,{botId:botB.id,seed:2});
  await a.request(`/api/tournaments/${cup.id}/start`,{});
  let final;const deadline=Date.now()+180000;
  while(Date.now()<deadline) {final=await a.request(`/api/tournaments/${cup.id}`);if(final.status==="FINISHED")break;await pause(300);}
  assert.equal(final.status,"FINISHED");assert.equal(final.matches[0].status,"FINISHED");assert.equal(final.matches[0].failure,null);assert.ok(final.champion);
  const tournamentReplay=await a.request(`/api/matches/${final.matches[0].historyMatchId}/replay`);assert.ok(["WIN","DRAW"].includes(tournamentReplay.result.outcome));
  await writeFile(values.fixture,JSON.stringify({accounts,bots:[botA.id,botB.id],matchId:first.historyId,tournamentId:cup.id,tournamentReplayId:final.matches[0].historyMatchId,replay,tournamentReplay},null,2),{mode:0o600});
  console.log("PASS: HTTPS cookies, same-origin/cross-origin WSS, two accounts, bot ownership, human game/history/replay, real uploaded bots and tournament ranking.");
} else {
  const fixture=JSON.parse(await readFile(values.fixture,"utf8"));const a=new Browser(),b=new Browser();
  for(const [index,browser] of [a,b].entries()) {
    await browser.login(fixture.accounts[index]);
    assert.ok((await browser.request("/api/account/bots")).some(bot=>bot.id===fixture.bots[index]));
    assert.ok((await browser.request("/api/account/matches")).matches.some(match=>match.id===fixture.matchId));
  }
  const tournament=await a.request(`/api/tournaments/${fixture.tournamentId}`);assert.equal(tournament.status,"FINISHED");assert.ok(tournament.champion);
  assert.deepEqual(await a.request(`/api/matches/${fixture.matchId}/replay`),fixture.replay);
  assert.deepEqual(await a.request(`/api/matches/${fixture.tournamentReplayId}/replay`),fixture.tournamentReplay);
  const continued=await a.request("/api/matches",{mode:"HUMAN_VS_BOT",botId:fixture.bots[0]},201);
  const seat=new Seat(continued);try {await seat.connect();seat.send("JOIN_MATCH");const advanced=await seat.state(state=>state.gameState.sequence>=2);assert.equal(advanced.botError,null);if(advanced.status!=="FINISHED"){seat.send("LEAVE");await seat.state(state=>state.status==="FINISHED");}}finally{seat.close();}
  assert.equal((await a.request(`/api/matches/${continued.historyId}/replay`)).result.outcome,"WIN");
  console.log("PASS: accounts/login, bot metadata/source, histories, ranking and exact replay payloads survive restart/restore; restored bot still executes.");
}
