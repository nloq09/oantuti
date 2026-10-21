import { csrfHeaders } from "../api/auth-api.js";
export const canManageTournament = (tournament, account) => Boolean(account && (account.role === "ADMIN" || (tournament.ownerUserId && tournament.ownerUserId === account.id)));
export async function tournamentRequest(path = "", body, method, extraHeaders = {}, {signal} = {}) {
  const mutation = (method || (body === undefined ? "GET" : "POST")) !== "GET";
  const response = await fetch("/api/tournaments" + path, {
    method: method || (body === undefined ? "GET" : "POST"), ...(signal?{signal}:{}),
    credentials: "same-origin", cache: "no-store",
    headers: { "Content-Type": "application/json", ...(mutation ? await csrfHeaders() : {}), ...extraHeaders },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const data = await response.json();
  if (!response.ok) throw new Error(data.error || "SERVER_ERROR");
  return data;
}
export const getTournament = (id) =>
  tournamentRequest("/" + encodeURIComponent(id));
export async function createTournament(name, settings = {}) {
  const t = await tournamentRequest("", { name, format: "ROUND_ROBIN", ...settings });
  return t;
}
export const addParticipant = (id, bot) =>
  tournamentRequest("/" + encodeURIComponent(id) + "/participants", {
    botId: bot.id,
    displayName: bot.name,
  });
export const removeParticipant = (id, participantId, bot) =>
  tournamentRequest(
    "/" +
      encodeURIComponent(id) +
      "/participants/" +
      encodeURIComponent(participantId),
    undefined,
    "DELETE",
    {},
  );
export const startTournament = (id) =>
  tournamentRequest("/" + encodeURIComponent(id) + "/start", {});
export const cancelTournament = (id) =>
  tournamentRequest("/" + encodeURIComponent(id) + "/cancel", {});
export const openRegistration = id => tournamentRequest("/" + encodeURIComponent(id) + "/open-registration", {});
export const closeRegistration = id => tournamentRequest("/" + encodeURIComponent(id) + "/close-registration", {});
const registrationPath = (id, registrationId) => "/" + encodeURIComponent(id) + "/registrations/" + encodeURIComponent(registrationId);
export const registerBot = (id, bot) => tournamentRequest("/" + encodeURIComponent(id) + "/registrations", {botId:bot.id});
export const approveRegistration = (id, registrationId) => tournamentRequest(registrationPath(id,registrationId) + "/approve", {});
export const rejectRegistration = (id, registrationId) => tournamentRequest(registrationPath(id,registrationId) + "/reject", {});
export const withdrawRegistration = (id, registrationId) => tournamentRequest(registrationPath(id,registrationId), undefined, "DELETE");
export const checkInRegistration = (id, registrationId) => tournamentRequest("/" + encodeURIComponent(id) + "/check-in", {registrationId});
export const closeCheckIn = id => tournamentRequest("/" + encodeURIComponent(id) + "/close-check-in", {});
export const configureRules = (id, settings) => tournamentRequest("/" + encodeURIComponent(id) + "/rules", settings);
export const configureSeeding = (id, settings) => tournamentRequest("/" + encodeURIComponent(id) + "/seeding", settings);
export class TournamentStream {
 constructor(id,update,status=()=>{},notification=()=>{},updatesOnly=false){Object.assign(this,{id,update,status,notification,updatesOnly});this.lastEvent=0;this.lastRevision=-1;this.lastStateRevision=-1;this.stopped=false;this.attempt=0;this.connect();}
 invalidate(revision,first=false){if(!Number.isSafeInteger(revision)||revision<0||revision<this.lastRevision)return;if(revision===this.lastRevision&&!first)return;this.lastRevision=revision;if(typeof dispatchEvent==='function'&&typeof CustomEvent==='function')dispatchEvent(new CustomEvent('rps-tournament-update',{detail:{id:this.id,revision}}));}
 connect(){
  if(this.stopped)return;this.status('connecting');const socket=new WebSocket(`${location.protocol==='https:'?'wss:':'ws:'}//${location.host}/ws/game`);this.socket=socket;let first=true;
  socket.onopen=()=>{if(!this.stopped&&this.socket===socket)socket.send(JSON.stringify({type:'WATCH_TOURNAMENT',tournamentId:this.id,...(this.updatesOnly?{updatesOnly:true}:{})}));};
  socket.onmessage=event=>{if(this.stopped||this.socket!==socket)return;try{const message=JSON.parse(event.data),payload=message.payload||{},t=payload.tournament;if(payload.tournamentId&&payload.tournamentId!==this.id||t?.id&&t.id!==this.id)return;
   if(message.type==='ACTION_REJECTED'){this.status(payload.error);this.close();return;}
   const revision=t?.revision??payload.revision;if(Number.isSafeInteger(revision)&&revision<this.lastRevision)return;
   if(message.type==='TOURNAMENT_STATE'){this.attempt=0;this.status('connected');if(Number.isSafeInteger(revision)&&revision>=this.lastStateRevision){this.lastStateRevision=revision;this.update(t);}this.invalidate(revision,first);first=false;}
   else if(payload.event){if(payload.event.sequence>this.lastEvent){this.lastEvent=payload.event.sequence;this.notification(payload.event);this.invalidate(revision);}}
   else if(['TOURNAMENT_UPDATED','STANDINGS_UPDATED','BRACKET_UPDATED'].includes(message.type))this.invalidate(revision);
  }catch{this.status('error');}};
  socket.onclose=()=>{if(!this.stopped&&this.socket===socket){this.status('disconnected');this.retry=setTimeout(()=>this.connect(),[1000,2000,5000][Math.min(this.attempt++,2)]);}};socket.onerror=()=>socket.close();
 }
 close(){this.stopped=true;clearTimeout(this.retry);this.socket?.close();}
}
// Actual pages subscribe to compact invalidations and always reload their authorized HTTP projections.
export function watchTournament(id,notification=()=>{}){
 let stream=null,disposed=false;
 const start=()=>{if(!disposed&&!document.hidden&&!stream)stream=new TournamentStream(id,()=>{},()=>{},notification,true);};
 const stop=()=>{stream?.close();stream=null;};const visible=()=>document.hidden?stop():start();
 const hide=event=>{stop();if(!event.persisted)dispose();},show=event=>{if(event.persisted)start();};
 function dispose(){disposed=true;stop();document.removeEventListener('visibilitychange',visible);removeEventListener('pagehide',hide);removeEventListener('pageshow',show);}
 document.addEventListener('visibilitychange',visible);addEventListener('pagehide',hide);addEventListener('pageshow',show);start();return{close:dispose};
}

export const tournamentControl=(id,action,revision,reason)=>tournamentRequest(`/${encodeURIComponent(id)}/control/${encodeURIComponent(action)}`,{revision,reason});
export const tournamentRoundPolicy=(id,revision,autoAdvanceRounds)=>tournamentRequest(`/${encodeURIComponent(id)}/round-policy`,{revision,autoAdvanceRounds});
export const tournamentParticipantStatus=(id,participantId,revision,status,reason)=>tournamentRequest(`/${encodeURIComponent(id)}/participants/${encodeURIComponent(participantId)}/status`,{revision,status,reason});
export const tournamentMatchOperation=(id,matchId,operation,body)=>tournamentRequest(`/${encodeURIComponent(id)}/matches/${encodeURIComponent(matchId)}/${encodeURIComponent(operation)}`,body);
