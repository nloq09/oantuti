import test from "node:test";
import assert from "node:assert/strict";
import {
  replayFrames,
  replayState,
  positionForTurn,
} from "../js/replay/replay-state.js";
test("replay seeking reapplies facts without changing initial board or evaluating combat", () => {
  const initial = {
    pieces: [
      { id: "A", position: { row: 6, col: 0 }, status: "ALIVE" },
      { id: "B", position: { row: 5, col: 0 }, status: "ALIVE" },
    ],
    phase: "READY",
    turn: 0,
    legalActions: [],
  };
  const events = [
    { sequence: 1, type: "MATCH_STARTED", payload: {} },
    { sequence: 2, type: "PIECE_REMOVED", payload: { piece: { id: "B" } } },
    {
      sequence: 2,
      type: "PIECE_MOVED",
      payload: { pieceId: "A", to: { row: 5, col: 0 } },
    },
    {
      sequence: 2,
      type: "TURN_CHANGED",
      payload: { currentPlayer: "P2", turn: 1 },
    },
    {
      sequence: 3,
      type: "GAME_FINISHED",
      payload: { outcome: "WIN", winner: "P1" },
    },
  ];
  const frames = replayFrames(events);
  assert.equal(frames.length, 3);
  assert.equal(positionForTurn(initial, frames, 1), 2);
  assert.equal(positionForTurn(initial, frames, 0), 0);
  const finished = replayState(initial, frames, 3);
  assert.equal(finished.winner, "P1");
  assert.deepEqual(
    finished.pieces.map((p) => p.id),
    ["A"],
  );
  assert.equal(finished.turn, 1);
  assert.deepEqual(replayState(initial, frames, 0).pieces, initial.pieces);
  assert.equal(initial.pieces.length, 2);
  assert.deepEqual(replayState(initial, frames, 2).pieces[0].position, {
    row: 5,
    col: 0,
  });
});
