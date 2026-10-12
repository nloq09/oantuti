export class ClientState {
  constructor() {
    this.snapshot = null;
    this.role = "PLAYER";
    this.selected = null;
    this.type = "ROCK";
    this.connected = false;
    this.busy = false;
    this.pending = false;
  }
  get game() {
    return this.snapshot?.gameState;
  }
  get ownTurn() {
    return (
      this.role === "PLAYER" &&
      this.snapshot?.status === "PLAYING" &&
      this.game.currentPlayer === this.snapshot.playerId &&
      !this.snapshot.players.find(
        (player) => player.playerId === this.snapshot.playerId,
      )?.bot
    );
  }
  get interactive() {
    return (
      this.role === "PLAYER" && this.connected && !this.busy && !this.pending
    );
  }
}
