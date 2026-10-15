export class GameSocket {
  constructor(seat, onMessage, onStatus) {
    this.role = seat.role || "PLAYER";
    this.seat=this.role==="PLAYER"?{matchId:seat.matchId,playerId:seat.playerId,token:seat.token}:{matchId:seat.matchId};
    this.onMessage = onMessage;
    this.onStatus = onStatus;
    this.attempt = 0;
    this.stopped = false;
    this.connect(false);
  }
  connect(resume) {
    this.onStatus("connecting");
    let synchronized = false;
    this.socket = new WebSocket(
      `${location.protocol === "https:" ? "wss:" : "ws:"}//${location.host}/ws/game`,
    );
    this.socket.onopen = () => {
      this.send(
        this.role === "SPECTATOR"
          ? "SPECTATE_MATCH"
          : resume
            ? "RESUME_MATCH"
            : "JOIN_MATCH",
      );
    };
    this.socket.onmessage = (event) => {
      try {
        const message = JSON.parse(event.data);
        if (message.type === "GAME_STATE" && !synchronized) {
          message.resync = true;
          synchronized = true;
          this.attempt = 0;
          this.onStatus("connected");
        }
        this.onMessage(message);
      } catch {
        this.onStatus("error");
      }
    };
    this.socket.onclose = () => {
      if (this.stopped) return;
      this.onStatus("disconnected");
      const delay = [1, 2, 3, 5][Math.min(this.attempt++, 3)];
      this.retry = setTimeout(() => this.connect(true), delay * 1000);
    };
    this.socket.onerror = () => this.socket.close();
  }
  send(type, payload, sequence) {
    if (this.role === "SPECTATOR" && !["SPECTATE_MATCH", "PING"].includes(type))
      return false;
    if (this.socket.readyState !== WebSocket.OPEN) return false;
    this.socket.send(JSON.stringify({ type, ...this.seat, payload, sequence }));
    return true;
  }
  close() {
    this.stopped = true;
    clearTimeout(this.retry);
    this.socket.close();
  }
}
