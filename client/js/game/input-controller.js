import { placementClick } from "./placement-controller.js";
import { sound } from "../ui/sound.js";
export function boardClick(state, send, position, piece, action) {
  if (!state.interactive) return;
  if (state.snapshot.status === "PLACEMENT")
    placementClick(state, send, position, piece);
  else if (state.ownTurn) {
    if (action) {
      send("ACTION", action, state.game.sequence);
      state.selected = null;
    } else if (piece?.owner === state.snapshot.playerId) {
      state.selected = piece.id;
      sound("select");
    } else state.selected = null;
  }
}
