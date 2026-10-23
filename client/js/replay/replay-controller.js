import {replayEventText} from './event-presentation.js';
import { renderBoard } from "../game/board-renderer.js?v=diagonal";
import { ClientState } from "../game/game-state.js";
import {
  replayFrames,
  replayState,
  positionForTurn,
} from "./replay-state.js?v=stage3";
import { toast } from "../ui/toast.js";
const id = new URLSearchParams(location.search).get("match");
let replay,
  frames = [],
  position = 0,
  timer;
const state = new ClientState();
state.role = "REPLAY";
function pause() {
  clearTimeout(timer);
  timer = null;
  document.querySelector("#play").textContent = "▶ Phát";
}
function render() {
  const game = replayState(replay.initialState, frames, position);
  state.snapshot = {
    playerId: "P1",
    status: game.phase,
    placementCells: [],
    gameState: game,
    players: [],
  };
  renderBoard(document.querySelector("#board"), state, () => {});
  document.querySelector("#seek").value = position;
  document.querySelector("#turn-seek").value = game.turn;
  document.querySelector("#position").textContent =
    `Lượt ${game.turn} / ${replay.finalState.turn} · Bước ${position} / ${frames.length}`;
  const log = document.querySelector("#event-log");
  log.replaceChildren();
  for (const e of game.events.slice(-20)) {
    const li = document.createElement("li");
    const text=replayEventText(e);if(!text)continue;li.textContent=text;
    log.append(li);
  }
  document.querySelector("#first").disabled = document.querySelector(
    "#previous",
  ).disabled = position === 0;
  document.querySelector("#last").disabled = document.querySelector(
    "#next",
  ).disabled = position === frames.length;
}
function seek(value) {
  pause();
  position = Math.min(frames.length, Math.max(0, value));
  render();
}
function advance() {
  if (position >= frames.length) {
    pause();
    return;
  }
  position++;
  render();
  timer = setTimeout(
    advance,
    800 / Number(document.querySelector("#speed").value),
  );
}
document.querySelector("#first").onclick = () => seek(0);
document.querySelector("#last").onclick = () => seek(frames.length);
document.querySelector("#previous").onclick = () => seek(position - 1);
document.querySelector("#next").onclick = () => seek(position + 1);
document.querySelector("#seek").oninput = (event) =>
  seek(Number(event.target.value));
document.querySelector("#play").onclick = () => {
  if (timer) {
    pause();
    return;
  }
  if (position === frames.length) position = 0;
  document.querySelector("#play").textContent = "Ⅱ Tạm dừng";
  advance();
};
window.addEventListener("pagehide", pause);
document.querySelector("#turn-seek").oninput = (event) =>
  seek(
    positionForTurn(replay.initialState, frames, Number(event.target.value)),
  );
document
  .querySelectorAll(".replay-controls button,.replay-controls input")
  .forEach((node) => (node.disabled = true));
async function load() {
  try {
    const response = await fetch(
      "/api/matches/" + encodeURIComponent(id) + "/replay",
    );
    replay = await response.json();
    if (!response.ok) throw new Error(replay.error);
    frames = replayFrames(replay.events);
    document
      .querySelectorAll(".replay-controls button,.replay-controls input")
      .forEach((node) => (node.disabled = false));
    document.querySelector("#turn-seek").max = replay.finalState.turn;
    document.querySelector("#seek").max = frames.length;
    document.querySelector("#replay-title").textContent = replay.players
      .map((p) => p.botName || (p.playerId === "P1" ? "Xanh" : "Đỏ"))
      .join(" vs ");
    document.querySelector("#replay-status").textContent =
      `${replay.result.winner === "DRAW" || !replay.result.winner ? "Hòa" : "Thắng: " + (replay.result.winner === "P1" ? "Xanh" : "Đỏ")} · Replay đầy đủ`;
    const tournamentId=new URLSearchParams(location.search).get('tournament');
    if(tournamentId){const back=document.querySelector('.replay-page > nav');if(back){const link=document.createElement('a');link.className='button';link.textContent='← Quay lại trận đấu';link.href='/tournament-detail.html?id='+encodeURIComponent(tournamentId)+'#matches';back.replaceChildren(link);}}
    render();
  } catch (error) {
    toast(error.message, true);
    document
      .querySelectorAll(".replay-controls button,.replay-controls input")
      .forEach((node) => (node.disabled = true));
    document.querySelector("#replay-status").textContent =
      "Không tải được replay.";
  }
}
load();
window.addEventListener("pageshow",event=>{if(event.persisted){pause();load();}});
