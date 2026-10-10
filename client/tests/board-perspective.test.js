import test from "node:test";
import assert from "node:assert/strict";
import { renderBoard } from "../js/game/board-renderer.js";

class Element {
  constructor(tag) {
    this.tag = tag;
    this.children = [];
    this.dataset = {};
    this.attributes = {};
    this.classes = new Set();
    this.classList = {
      toggle: (name, enabled) => enabled
        ? this.classes.add(name) : this.classes.delete(name),
    };
  }
  append(child) { this.children.push(child); }
  replaceChildren() { this.children = []; }
  setAttribute(name, value) { this.attributes[name] = value; }
}

for (const playerId of ["P1", "P2"]) {
  test(`${playerId}: own deployment below, clicks and highlights use server coordinates`, () => {
    const originalDocument = globalThis.document;
    globalThis.document = { createElement: tag => new Element(tag) };
    try {
      const position = { row: playerId === "P1" ? 6 : 2, col: 0 };
      const destination = { row: playerId === "P1" ? 5 : 3, col: 0 };
      const action = { type: "MOVE", pieceId: "own", from: position, to: destination };
      const board = new Element("div");
      const state = {
        interactive: true, selected: "own",
        snapshot: {
          playerId, status: "PLAYING", placementCells: [position],
          gameState: {
            boardWidth: 9, boardHeight: 9,
            pieces: [{ id: "own", owner: playerId, type: "ROCK", position, status: "ALIVE" }],
            legalActions: [action],
          },
        },
      };
      let clicked;
      renderBoard(board, state, (...args) => { clicked = args; });
      const cells = board.children.filter(element => element.tag === "button");
      const ownCell = cells.find(cell => cell.children.some(img => img.dataset.id === "own"));
      assert.ok(cells.indexOf(ownCell) >= 54, "own starting zone is in bottom three rows");
      assert.equal(board.children[1].textContent, playerId === "P1" ? "A" : "I");
      assert.equal(cells[0].dataset.row, playerId === "P1" ? 0 : 8);
      assert.equal(cells[0].dataset.col, playerId === "P1" ? 0 : 8);
      const target = cells.find(cell => cell.dataset.row === destination.row && cell.dataset.col === destination.col);
      assert.ok(target.classes.has("legal"));
      target.onclick();
      assert.deepEqual(clicked[0], destination);
      assert.equal(clicked[2], action, "send the original server action without rotating its coordinates");
      state.snapshot.status = "PLACEMENT";
      renderBoard(board, state, () => {});
      assert.ok(board.children.find(cell => cell.dataset.row === position.row && cell.dataset.col === position.col).classes.has("placement"));
    } finally {
      globalThis.document = originalDocument;
    }
  });
}
