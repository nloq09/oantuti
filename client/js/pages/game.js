import { GameController } from "../game/game-controller.js?v=diagonal";
import { seatKey } from "../config.js";
import { SpectatorController } from "../spectator/spectator-controller.js?v=diagonal";
const spectate = new URLSearchParams(location.search).get("spectate");
let seat;
try {
  seat = JSON.parse(sessionStorage.getItem(seatKey));
} catch {}
if (spectate) new SpectatorController(spectate);
else if (!seat?.matchId || !seat?.token) location.replace("/lobby.html");
else new GameController(seat);
