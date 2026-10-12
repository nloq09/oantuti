import { GameController } from "../game/game-controller.js?v=stage3";
import { openSettings } from "../ui/sound.js";
import { toast } from "../ui/toast.js";
export class SpectatorController extends GameController {
  constructor(matchId) {
    super({ matchId, playerId: "P1", role: "SPECTATOR" });
  }
  bind() {
    document.querySelector(".brand").href = "/tournaments.html";
    document.querySelector("#settings").onclick = openSettings;
    document.querySelector("#clear-log").onclick = () =>
      document.querySelector("#event-log").replaceChildren();
    document.querySelector("#leave").textContent = "Về giải đấu";
    document.querySelector("#leave").onclick = () =>
      (location.href = "/tournaments.html");
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
      "/replay.html?match=" + encodeURIComponent(snapshot.gameState.matchId);
    link.textContent = "Xem replay đầy đủ";
    const note = document.querySelector(".board-note");
    note.replaceChildren(link);
  }
}
