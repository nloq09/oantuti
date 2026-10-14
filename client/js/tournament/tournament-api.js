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
export class TournamentStream {
  constructor(id, update, status = () => {}) {
    this.id = id;
    this.update = update;
    this.status = status;
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
        JSON.stringify({ type: "WATCH_TOURNAMENT", tournamentId: this.id }),
      );
    this.socket.onmessage = (event) => {
      try {
        const message = JSON.parse(event.data);
        if (message.type === "TOURNAMENT_STATE") {
          this.attempt = 0;
          this.status("connected");
          this.update(message.payload.tournament);
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
