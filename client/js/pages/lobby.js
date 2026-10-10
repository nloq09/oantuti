import { createMatch, joinMatch, getMatch } from "../api/match-api.js";
import { seatKey } from "../config.js";
import { toast } from "../ui/toast.js";
import { openSettings } from "../ui/sound.js";
let mode = "HUMAN_VS_HUMAN";
document.querySelectorAll("[data-mode]").forEach(
  (button) =>
    (button.onclick = () => {
      mode = button.dataset.mode;
      document
        .querySelectorAll("[data-mode]")
        .forEach((b) => b.classList.toggle("chosen", b === button));
    }),
);
async function enter(operation) {
  const buttons = [...document.querySelectorAll("button")];
  buttons.forEach((b) => (b.disabled = true));
  try {
    const seat = await operation();
    sessionStorage.setItem(seatKey, JSON.stringify(seat));
    location.href = "/game.html";
  } catch (error) {
    toast(
      error.message === "Failed to fetch"
        ? "Không kết nối được server."
        : error.message,
      true,
    );
    buttons.forEach((b) => (b.disabled = false));
  }
}
document.querySelector("#create").onclick = () =>
  enter(() => createMatch(mode));
document.querySelector("#join-form").onsubmit = (event) => {
  event.preventDefault();
  const id = document.querySelector("#code").value.trim().toUpperCase();
  if (!id) return;
  enter(async () => {
    await getMatch(id);
    return joinMatch(id);
  });
};
document.querySelector("#settings").onclick = openSettings;
