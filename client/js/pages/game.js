import{canResumeTournamentSeat}from "../tournament/reconnect.js";
import { GameController } from "../game/game-controller.js?v=diagonal";
import { seatKey } from "../config.js";
import { SpectatorController } from "../spectator/spectator-controller.js?v=diagonal";
const spectate = new URLSearchParams(location.search).get("spectate");
const tournamentId=new URLSearchParams(location.search).get("tournament");
let seat;
try {
  seat = JSON.parse(sessionStorage.getItem(seatKey));
} catch {}
if (spectate) new SpectatorController(spectate);
else if(tournamentId&&!canResumeTournamentSeat(seat,tournamentId,new URLSearchParams(location.search).get("matchId")))location.replace("/tournament-lobby.html?id="+encodeURIComponent(tournamentId));
else if (!seat?.matchId || !seat?.token) location.replace("/lobby.html");
else new GameController(seat);
if(tournamentId&&/^[a-f0-9-]{36}$/i.test(tournamentId)){const back=document.createElement("a");back.className="button";back.href="/tournament-lobby.html?id="+encodeURIComponent(tournamentId)+"&stay=1";back.textContent="← Quay lại giải";document.querySelector("main")?.prepend(back);}
if(tournamentId)import("../tournament/match-context.js").then(module=>module.mountMatchContext(tournamentId,spectate||new URLSearchParams(location.search).get("matchId")));
