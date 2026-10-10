import { createMatch, joinMatch, getMatch } from "../api/match-api.js";
import { seatKey } from "../config.js";
import { toast } from "../ui/toast.js";
import { openSettings } from "../ui/sound.js";
import { savedBots, uploadBot, botCapabilities } from "../api/bot-api.js";
const controller = document.querySelector("#player-controller");
let capabilities = {};
function populateBots(selected = controller.value) {
  controller.replaceChildren(new Option("Tôi tự chơi", ""));
  for (const bot of savedBots())
    controller.append(new Option(`${bot.name} · ${bot.language}`, bot.id));
  controller.value = selected;
}
populateBots();
botCapabilities()
  .then((data) => {
    capabilities = data.languages;
    document.querySelector("#bot-runtime").textContent = Object.values(
      capabilities,
    ).some(Boolean)
      ? "Chọn bot riêng để đấu với người chơi, bot của bạn bè hoặc bot chiến thuật."
      : "Có thể tải bot lên. Chạy bot cần server bật Docker Linux và chuẩn bị các image; xem hướng dẫn.";
  })
  .catch(() => {
    document.querySelector("#bot-runtime").textContent =
      "Chưa kiểm tra được runner bot.";
  });
function selectedBot() {
  const bot = savedBots().find((item) => item.id === controller.value);
  if (bot && !capabilities[bot.language])
    throw new Error("BOT_SANDBOX_UNAVAILABLE");
  return bot;
}
document.querySelector("#bot-upload-form").onsubmit = async (event) => {
  event.preventDefault();
  const button = document.querySelector("#upload-bot");
  button.disabled = true;
  button.textContent = "Đang tải…";
  try {
    const file = document.querySelector("#bot-file").files[0];
    const bot = await uploadBot(
      document.querySelector("#bot-name").value.trim(),
      file,
    );
    populateBots(bot.id);
    toast("Đã tải bot lên: " + bot.name);
  } catch (error) {
    toast(error.message, true);
  } finally {
    button.disabled = false;
    button.textContent = "Tải bot lên";
  }
};
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
  enter(() => createMatch(mode, selectedBot()));
document.querySelector("#join-form").onsubmit = (event) => {
  event.preventDefault();
  const id = document.querySelector("#code").value.trim().toUpperCase();
  if (!id) return;
  enter(async () => {
    await getMatch(id);
    return joinMatch(id, selectedBot());
  });
};
document.querySelector("#settings").onclick = openSettings;
