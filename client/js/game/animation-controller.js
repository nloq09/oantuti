import { settings, sound } from "../ui/sound.js";
const wait = (ms) => new Promise((resolve) => setTimeout(resolve, ms));
export class AnimationController {
  constructor(board) {
    this.board = board;
  }
  async play(events, bot = false) {
    if (bot) await wait(400);
    for (const event of events) {
      const p = event.payload;
      if (event.type === "PIECE_MOVED") {
        sound("move");
        const image = [...this.board.querySelectorAll(".piece")].find(
          (n) => n.dataset.id === p.pieceId,
        );
        const target = this.board.querySelector(
          `[data-row="${p.to.row}"][data-col="${p.to.col}"]`,
        );
        if (image && target && settings.animation) {
          const from = image.getBoundingClientRect(),
            to = target.getBoundingClientRect();
          await image.animate(
            [
              { transform: "translate(0,0)" },
              {
                transform: `translate(${to.x - from.x + (to.width - from.width) / 2}px,${to.y - from.y + (to.height - from.height) / 2}px)`,
              },
            ],
            { duration: 250, fill: "forwards", easing: "ease-in-out" },
          ).finished;
        }
      } else if (event.type === "COMBAT_OCCURRED") {
        sound("combat");
        if (settings.animation) {
          const target = this.board.querySelector(
            `[data-row="${p.defender.position.row}"][data-col="${p.defender.position.col}"]`,
          );
          if (target) {
            target.dataset.effect =
              p.attacker.type === p.defender.type
                ? "rock"
                : p.attacker.type.toLowerCase();
            target.classList.add("combat");
            await wait(300);
            target.classList.remove("combat");
          }
        }
      } else if (event.type === "PIECE_REMOVED" && settings.animation) {
        const image = [...this.board.querySelectorAll(".piece")].find(
          (n) => n.dataset.id === p.piece.id,
        );
        if (image)
          await image.animate(
            [{ opacity: 1 }, { opacity: 0, transform: "scale(.25)" }],
            { duration: 180, fill: "forwards" },
          ).finished;
      }
    }
  }
}
