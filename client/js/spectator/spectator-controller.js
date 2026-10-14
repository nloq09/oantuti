import { GameController } from "../game/game-controller.js?v=diagonal";
import { toast } from "../ui/toast.js";
export class SpectatorController extends GameController {
  constructor(matchId, { admin = false } = {}) {
    super({ matchId, playerId: "P1", role: "SPECTATOR", admin });
  }
  bind() {
    document.querySelector("#clear-log").onclick = () =>
      document.querySelector("#event-log").replaceChildren();
    document.querySelector("#leave").textContent = this.seat.admin ? "Về danh sách trận" : "Về giải đấu";
    document.querySelector("#leave").onclick = () =>
      (location.href = this.seat.admin ? "/admin/matches.html" : "/tournaments.html");
    document.querySelector("#copy").onclick = () =>
      toast("Mã trận: " + this.seat.matchId);
  }
  render() {
    super.render();
    if (!this.state.snapshot) return;
    document.querySelector("#identity").textContent = "ĐANG XEM · Chỉ đọc";
    document.querySelector("#placement-panel").hidden = true;
    document.querySelector("#playing-controls").hidden = true;
    document.querySelector("#leave").disabled = false;
    document.querySelector(".board-note").textContent =
      "Chế độ khán giả · Bạn không điều khiển quân trong trận này.";
    document.querySelector(
      ".left-side #own-army",
    ).previousElementSibling.textContent = "BÊN XANH";
    document.querySelector(
      ".right-side #opponent-army",
    ).previousElementSibling.textContent = "BÊN ĐỎ";
  }
  result(snapshot) {
    this.finished = true;
    const link = document.createElement("a");
    link.className = "button primary";
    link.href =
      (this.seat.admin ? "/admin/replay.html?match=" : "/replay.html?match=") + encodeURIComponent(snapshot.gameState.matchId);
    link.textContent = "Xem replay đầy đủ";
    const note = document.querySelector(".board-note");
    note.replaceChildren(link);
  }
}
