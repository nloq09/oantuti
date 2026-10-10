import { icon, labels } from "../config.js";
export const key = (p) => `${p.row},${p.col}`;
export function renderBoard(element, state, click) {
  element.replaceChildren();
  const s = state.snapshot,
    g = s.gameState;
  const targets = new Map(
    (g.legalActions || [])
      .filter((a) => a.type === "MOVE" && a.pieceId === state.selected)
      .map((a) => [key(a.to), a]),
  );
  const placement = new Set(s.placementCells.map(key));
  const pieces = new Map(
    g.pieces
      .filter((p) => p.status === "ALIVE")
      .map((p) => [key(p.position), p]),
  );
  element.append(document.createElement("span"));
  for (let c = 0; c < g.boardWidth; c++) {
    const label = document.createElement("span");
    label.className = "coordinate";
    label.textContent = String.fromCharCode(65 + c);
    element.append(label);
  }
  for (let r = 0; r < g.boardHeight; r++) {
    const label = document.createElement("span");
    label.className = "coordinate";
    label.textContent = r + 1;
    element.append(label);
    for (let c = 0; c < g.boardWidth; c++) {
      const position = { row: r, col: c },
        piece = pieces.get(key(position));
      const cell = document.createElement("button");
      cell.className =
        "cell " + (r < 3 ? "red-zone" : r >= 6 ? "blue-zone" : "");
      cell.dataset.row = r;
      cell.dataset.col = c;
      cell.setAttribute(
        "aria-label",
        `${String.fromCharCode(65 + c)}${r + 1}${piece ? " " + labels[piece.type] + " " + piece.owner : ""}`,
      );
      cell.classList.toggle("selected", piece?.id === state.selected);
      cell.classList.toggle("legal", targets.has(key(position)));
      cell.classList.toggle("attack", targets.has(key(position)) && !!piece);
      cell.classList.toggle(
        "placement",
        s.status === "PLACEMENT" && placement.has(key(position)),
      );
      cell.disabled = !state.interactive;
      cell.onclick = () => click(position, piece, targets.get(key(position)));
      if (piece) {
        const img = document.createElement("img");
        img.src = icon(piece.type, piece.owner);
        img.alt = labels[piece.type];
        img.className = "piece";
        img.dataset.id = piece.id;
        cell.append(img);
      }
      element.append(cell);
    }
  }
}
