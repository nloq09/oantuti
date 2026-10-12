export async function tournamentRequest(path = "", body, method) {
  const response = await fetch("/api/tournaments" + path, {
    method: method || (body === undefined ? "GET" : "POST"),
    headers: { "Content-Type": "application/json" },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const data = await response.json();
  if (!response.ok) throw new Error(data.error || "SERVER_ERROR");
  return data;
}
export const getTournament = (id) =>
  tournamentRequest("/" + encodeURIComponent(id));
export const createTournament = (name) =>
  tournamentRequest("", { name, format: "ROUND_ROBIN" });
export const addParticipant = (id, bot) =>
  tournamentRequest("/" + encodeURIComponent(id) + "/participants", {
    botId: bot.id,
    botToken: bot.token,
    displayName: bot.name,
  });
export const removeParticipant = (id, participantId) =>
  tournamentRequest(
    "/" +
      encodeURIComponent(id) +
      "/participants/" +
      encodeURIComponent(participantId),
    undefined,
    "DELETE",
  );
export const startTournament = (id) =>
  tournamentRequest("/" + encodeURIComponent(id) + "/start", {});
export const cancelTournament = (id) =>
  tournamentRequest("/" + encodeURIComponent(id) + "/cancel", {});
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
