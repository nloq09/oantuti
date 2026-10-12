/** Reconstructs recorded facts, never evaluates movement, combat or victory rules. */
export function positionForTurn(initial, frames, target) {
  if (target <= initial.turn) return 0;
  for (let index = 0; index < frames.length; index++) {
    if (
      frames[index].some(
        (event) =>
          ["TURN_CHANGED", "TURN_ENDED"].includes(event.type) &&
          event.payload.turn >= target,
      )
    )
      return index + 1;
  }
  return frames.length;
}
export function replayFrames(events) {
  const frames = [];
  for (const event of events) {
    let group = frames.at(-1);
    if (!group || group[0].sequence !== event.sequence) {
      group = [];
      frames.push(group);
    }
    group.push(event);
  }
  return frames;
}
export function replayState(initial, frames, position) {
  const state = structuredClone(initial);
  state.legalActions = [];
  state.events = [];
  for (const group of frames.slice(0, Math.max(0, position)))
    for (const event of group) {
      const p = event.payload;
      state.sequence = event.sequence;
      if (event.type === "PIECE_PLACED") {
        state.pieces = state.pieces.filter((piece) => piece.id !== p.piece.id);
        state.pieces.push(structuredClone(p.piece));
      } else if (event.type === "PIECE_MOVED") {
        const piece = state.pieces.find((piece) => piece.id === p.pieceId);
        if (piece) piece.position = structuredClone(p.to);
      } else if (event.type === "PIECE_REMOVED") {
        state.pieces = state.pieces.filter((piece) => piece.id !== p.piece.id);
      } else if (["TURN_CHANGED", "TURN_ENDED"].includes(event.type)) {
        state.currentPlayer = p.currentPlayer;
        state.turn = p.turn;
      } else if (event.type === "MATCH_STARTED") state.phase = "PLAYING";
      else if (["GAME_FINISHED", "MATCH_FINISHED"].includes(event.type)) {
        state.phase = "FINISHED";
        state.winner = p.winner === "DRAW" ? null : p.winner;
        state.outcome = p.outcome;
      }
      state.events.push(event);
    }
  return state;
}
