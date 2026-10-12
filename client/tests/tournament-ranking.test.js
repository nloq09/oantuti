import test from "node:test";
import assert from "node:assert/strict";
import {
  renderRanking,
  statusLabels,
} from "../js/tournament/ranking-renderer.js";
test("ranking renders server order and displays names as text", () => {
  const original = globalThis.document;
  globalThis.document = {
    createElement: () => ({
      children: [],
      append(value) {
        this.children.push(value);
      },
    }),
  };
  const element = {
    children: [],
    replaceChildren() {
      this.children = [];
    },
    append(value) {
      this.children.push(value);
    },
  };
  try {
    renderRanking(element, [
      {
        rank: 1,
        displayName: "<script>Bot</script>",
        played: 3,
        wins: 2,
        draws: 1,
        losses: 0,
        points: 7,
        piecesRemaining: 20,
      },
    ]);
    assert.equal(
      element.children[0].children[1].textContent,
      "<script>Bot</script>",
    );
    assert.equal(element.children[0].children[6].textContent, 7);
    for (const status of ["DRAFT", "RUNNING", "FINISHED", "CANCELLED"])
      assert.ok(statusLabels[status]);
  } finally {
    globalThis.document = original;
  }
});
