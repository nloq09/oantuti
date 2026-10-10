import { createMatch } from "../api/match-api.js";
import { seatKey } from "../config.js";
import { toast } from "../ui/toast.js";
let snapshot;
try {
  snapshot = JSON.parse(sessionStorage.getItem("rps-result"));
} catch {}
if (!snapshot) location.replace("/");
else {
  const g = snapshot.gameState;
  document.querySelector("#result-turns").textContent = g.turn;
  for (const owner of ["P1", "P2"])
    document.querySelector("#result-" + owner.toLowerCase()).textContent =
      g.pieces.filter((p) => p.owner === owner && p.status === "ALIVE").length;
  document.querySelector("#outcome").textContent = !g.winner
    ? "Hòa!"
    : g.winner === snapshot.playerId
      ? "Bạn thắng!"
      : "Bạn thua";
  document.querySelector("#summary").textContent =
    `Phòng ${g.matchId} · ${g.turn} lượt · ${Math.max(0, Math.round((Date.parse(snapshot.finishedAt) - Date.parse(snapshot.startedAt)) / 1000))} giây`;
  document.querySelector("#remaining").textContent = ["P1", "P2"]
    .map(
      (owner) =>
        `${owner === "P1" ? "Xanh" : "Đỏ"} còn ${g.pieces.filter((p) => p.owner === owner && p.status === "ALIVE").length} quân`,
    )
    .join(" · ");
  document.querySelector("#again").onclick = async () => {
    if (snapshot.mode !== "HUMAN_VS_BOT") {
      location.href = "/lobby.html";
      return;
    }
    try {
      sessionStorage.setItem(
        seatKey,
        JSON.stringify(
          await createMatch(
            "HUMAN_VS_BOT",
            JSON.parse(sessionStorage.getItem(seatKey) || "{}").bot,
          ),
        ),
      );
      location.href = "/game.html";
    } catch (error) {
      toast(error.message, true);
    }
  };
}
