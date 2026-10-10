export class ClientState {
  constructor() {
    this.snapshot = null;
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
      this.snapshot?.status === "PLAYING" &&
      this.game.currentPlayer === this.snapshot.playerId &&
      !this.snapshot.players.find(
        (player) => player.playerId === this.snapshot.playerId,
      )?.bot
    );
  }
  get interactive() {
    return this.connected && !this.busy && !this.pending;
  }
}
