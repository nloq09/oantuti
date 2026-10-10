import { GameController } from "../game/game-controller.js";
import { seatKey } from "../config.js";
let seat;
try {
  seat = JSON.parse(sessionStorage.getItem(seatKey));
} catch {}
if (!seat?.matchId || !seat?.token) location.replace("/lobby.html");
else new GameController(seat);
