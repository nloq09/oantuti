import { csrfHeaders } from "../api/auth-api.js";
export const canManageTournament = (tournament, account) => Boolean(account && (account.role === "ADMIN" || (tournament.ownerUserId && tournament.ownerUserId === account.id)));
export async function tournamentRequest(path = "", body, method, extraHeaders = {}) {
  const mutation = (method || (body === undefined ? "GET" : "POST")) !== "GET";
  const response = await fetch("/api/tournaments" + path, {
    method: method || (body === undefined ? "GET" : "POST"),
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
  constructor(id, update, status = () => {}, notification = () => {}, updatesOnly = false) {
    this.id = id;this.updatesOnly=updatesOnly;
    this.update = update;
    this.status = status;this.notification=notification;this.lastEvent=0;
    this.stopped = false;
    this.attempt = 0;
    this.connect();
  }
  connect() {
    this.status("connecting");
    this.socket = new WebSocket(
      `${location.protocol === "https:" ? "wss:" : "ws:"}//${location.host}/ws/game`,
    );
    this.socket.onopen = () =>
      this.socket.send(
        JSON.stringify({ type: "WATCH_TOURNAMENT", tournamentId: this.id, ...(this.updatesOnly?{updatesOnly:true}:{}) }),
      );
    this.socket.onmessage = (event) => {
      try {
        const message = JSON.parse(event.data);
        if (message.type === "TOURNAMENT_STATE") {
          this.attempt = 0;
          this.status("connected");
          this.update(message.payload.tournament);
          const revision=message.payload.tournament.revision;
          if(this.notificationRevision!==revision){this.notificationRevision=revision;if(typeof dispatchEvent==="function"&&typeof CustomEvent==="function")dispatchEvent(new CustomEvent("rps-tournament-update",{detail:{id:this.id,revision}}));}
        } else if(message.payload?.event) {
          if(message.payload.event.sequence>this.lastEvent){this.lastEvent=message.payload.event.sequence;this.notification(message.payload.event);}
        } else if (message.type === "ACTION_REJECTED") {
          this.status(message.payload.error);
          this.close();
        }
      } catch {
        this.status("error");
      }
    };
    this.socket.onclose = () => {
      if (!this.stopped) {
        this.status("disconnected");
        this.retry = setTimeout(
          () => this.connect(),
          [1000, 2000, 5000][Math.min(this.attempt++, 2)],
        );
      }
    };
    this.socket.onerror = () => this.socket.close();
  }
  close() {
    this.stopped = true;
    clearTimeout(this.retry);
    this.socket?.close();
  }
}

export const tournamentControl=(id,action,revision,reason)=>tournamentRequest(`/${encodeURIComponent(id)}/control/${encodeURIComponent(action)}`,{revision,reason});
export const tournamentRoundPolicy=(id,revision,autoAdvanceRounds)=>tournamentRequest(`/${encodeURIComponent(id)}/round-policy`,{revision,autoAdvanceRounds});
export const tournamentParticipantStatus=(id,participantId,revision,status,reason)=>tournamentRequest(`/${encodeURIComponent(id)}/participants/${encodeURIComponent(participantId)}/status`,{revision,status,reason});
export const tournamentMatchOperation=(id,matchId,operation,body)=>tournamentRequest(`/${encodeURIComponent(id)}/matches/${encodeURIComponent(matchId)}/${encodeURIComponent(operation)}`,body);
