import { key } from "./board-renderer.js";
export function placementClick(state, send, position, piece) {
  if (piece?.owner === state.snapshot.playerId) {
    state.selected = state.selected === piece.id ? null : piece.id;
    return;
  }
  if (!state.snapshot.placementCells.some((p) => key(p) === key(position)))
    return;
  const selected = state.game.pieces.find((p) => p.id === state.selected);
  if (!selected && state.snapshot.remaining[state.type] <= 0) return;
  send("PLACE", {
    type: selected?.type || state.type,
    position,
    pieceId: selected?.id,
  });
  state.selected = null;
}
