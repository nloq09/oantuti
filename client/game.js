"use strict";
const player = new URLSearchParams(location.search).get("player");
const $ = id => document.getElementById(id);
let state = null;
let selectedPiece = null;
let pending = false;
let socket = null;
const labels = { ROCK: "R", PAPER: "P", SCISSORS: "S" };

function log(text) {
  const row = document.createElement("li");
  row.textContent = text;
  $("events").prepend(row);
  while ($("events").children.length > 100) $("events").lastChild.remove();
}

function render() {
  if (!state) return;
  $("current").textContent = state.currentPlayer;
  $("turn").textContent = state.turn;
  $("sequence").textContent = state.sequence;
  $("board").replaceChildren();
  for (let row = 0; row < state.boardHeight; row++) {
    for (let col = 0; col < state.boardWidth; col++) {
      const coordinate = `${String.fromCharCode(65 + col)}${row + 1}`;
      const piece = state.pieces.find(p => p.position.row === row && p.position.col === col);
      const cell = document.createElement("button");
      cell.type = "button";
      cell.className = "cell";
      cell.disabled = true;
      cell.dataset.row = row;
      cell.dataset.col = col;
      cell.setAttribute("aria-label", `${coordinate}${piece ? ` ${piece.owner} ${piece.type}` : " trống"}`);
      const caption = document.createElement("span");
      caption.className = "coordinate";
      caption.textContent = coordinate;
      cell.append(caption);
      if (piece) {
        const token = document.createElement("span");
        token.className = `piece ${piece.owner.toLowerCase()}`;
        token.textContent = labels[piece.type] || piece.type;
        const owner = document.createElement("small");
        owner.textContent = piece.owner.slice(1);
        token.append(owner);
        cell.append(token);
      }
      $("board").append(cell);
    }
  }
}

if (player !== "P1" && player !== "P2") {
  $("status").textContent = "Chọn P1 hoặc P2 từ trang đầu.";
} else {
  $("you").textContent = player;
  $("title").textContent = `Player ${player.slice(1)} · demo-match`;
  socket = new WebSocket(`${location.protocol === "https:" ? "wss" : "ws"}://${location.host}/ws/game`);
  socket.addEventListener("open", () => {
    socket.send(JSON.stringify({ type: "JOIN", matchId: "demo-match", playerId: player }));
    log("WebSocket connected · JOIN sent");
  });
  socket.addEventListener("message", event => {
    const message = JSON.parse(event.data);
    if (message.type === "STATE") {
      state = message.payload.gameState;
      selectedPiece = null;
      pending = false;
      $("status").textContent = state.currentPlayer === player ? "Đến lượt bạn." : "Đang chờ đối thủ.";
      for (const fact of state.events || []) log(`#${fact.sequence} ${fact.type}`);
      render();
    } else if (message.type === "ACTION_REJECTED") {
      pending = false;
      $("status").textContent = `Server từ chối: ${message.payload.error}`;
      log($("status").textContent);
      render();
    }
  });
  socket.addEventListener("close", () => {
    pending = true;
    render();
    $("status").textContent = "Mất kết nối. Tải lại trang để tham gia lại.";
    log("WebSocket disconnected");
  });
  socket.addEventListener("error", () => { $("status").textContent = "Không kết nối được server."; });
}
