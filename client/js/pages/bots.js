import { savedBots, uploadBot, botCapabilities } from "../api/bot-api.js";
import { createMatch } from "../api/match-api.js";
import { seatKey } from "../config.js";
import { openSettings } from "../ui/sound.js";
import { toast } from "../ui/toast.js";

let selected =
  savedBots().find(
    (bot) => bot.id === sessionStorage.getItem("rps-preferred-bot"),
  ) || savedBots()[0];
let capabilities = {},
  busy = false;
function log(message, error = false) {
  const item = document.createElement("li");
  item.textContent = `[${new Date().toLocaleTimeString("vi-VN")}] ${message}`;
  item.classList.toggle("error", error);
  document.querySelector("#upload-log").prepend(item);
  const list = document.querySelector("#upload-log");
  while (list.children.length > 30) list.lastElementChild.remove();
}
function render() {
  const list = document.querySelector("#bot-list");
  list.replaceChildren();
  for (const bot of savedBots()) {
    const button = document.createElement("button");
    button.className =
      "bot-entry" + (selected?.id === bot.id ? " selected" : "");
    button.setAttribute("aria-pressed", String(selected?.id === bot.id));
    const avatar = document.createElement("span");
    avatar.className = "bot-avatar";
    avatar.textContent = "⚙";
    const body = document.createElement("span"),
      name = document.createElement("strong"),
      state = document.createElement("small");
    name.textContent = bot.name;
    state.textContent = `${bot.language} · ${capabilities[bot.language] === undefined ? "Đang kiểm tra Docker…" : capabilities[bot.language] ? "Docker sẵn sàng" : "Đã tải lên · cần Docker"}`;
    state.className = capabilities[bot.language] ? "bot-state" : "";
    body.append(name, state);
    button.append(avatar, body);
    button.disabled = busy;
    button.onclick = () => {
      selected = bot;
      sessionStorage.setItem("rps-preferred-bot", bot.id);
      render();
    };
    list.append(button);
  }
  if (!list.children.length) {
    const empty = document.createElement("p");
    empty.className = "empty-bots";
    empty.textContent = "Chưa có bot. Tải chiến thuật đầu tiên của bạn lên.";
    list.append(empty);
  }
  document.querySelector("#test-bot").disabled = !selected || busy;
  document.querySelector("#use-bot").disabled = !selected || busy;
  document.querySelector("#upload-bot").disabled = busy;
}
async function refresh() {
  try {
    capabilities = (await botCapabilities()).languages;
    const names = Object.entries(capabilities)
      .filter(([, ready]) => ready)
      .map(([language]) => language);
    document.querySelector("#bot-runtime").textContent = names.length
      ? `Docker sẵn sàng: ${names.join(" · ")}`
      : "Server chưa nhận Docker hoặc image. Bạn vẫn có thể tải file lên và chạy khi Docker sẵn sàng.";
    render();
  } catch {
    document.querySelector("#bot-runtime").textContent =
      "Không kết nối được server để kiểm tra Docker.";
  }
}
document.querySelector("#settings").onclick = openSettings;
document.querySelector("#new-bot").onclick = () => {
  document.querySelector("#bot-upload-form").reset();
  setLanguage();
  document.querySelector("#bot-name").focus();
};
function setLanguage() {
  const language = document.querySelector('[name="language"]:checked').value;
  document.querySelector("#bot-file").value = "";
  document.querySelector("#bot-file").accept = {
    PYTHON: ".py",
    CPP: ".cpp",
    JAVA: ".java,.jar",
  }[language];
  document.querySelector("#file-help").textContent = {
    PYTHON: "Python: file .py, tối đa 512 KB.",
    CPP: "C++: file .cpp, tối đa 512 KB; compiler GCC 14, C++17.",
    JAVA: "Java: Main.java không package, tối đa 512 KB; hoặc JAR có Main-Class, tối đa 5 MB.",
  }[language];
}
document
  .querySelectorAll('[name="language"]')
  .forEach((input) => (input.onchange = setLanguage));
document.querySelector("#clear-log").onclick = () =>
  document.querySelector("#upload-log").replaceChildren();
document.querySelector("#bot-upload-form").onsubmit = async (event) => {
  event.preventDefault();
  if (busy) return;
  busy = true;
  render();
  const name = document.querySelector("#bot-name").value.trim(),
    file = document.querySelector("#bot-file").files[0];
  log(`Đang tải ${file.name} lên server…`);
  try {
    selected = await uploadBot(name, file);
    sessionStorage.setItem("rps-preferred-bot", selected.id);
    log(
      `Đã lưu ${selected.name} (${selected.language}). Dùng Đấu thử để kiểm tra bot trong trận thật.`,
    );
    toast("Đã tải bot lên: " + selected.name);
    await refresh();
  } catch (error) {
    log("Tải lên thất bại: " + error.message, true);
    toast(error.message, true);
  } finally {
    busy = false;
    render();
  }
};
document.querySelector("#use-bot").onclick = () => {
  if (!selected) return;
  sessionStorage.setItem("rps-preferred-bot", selected.id);
  location.href = "/lobby.html";
};
document.querySelector("#test-bot").onclick = async () => {
  if (!selected || busy) return;
  busy = true;
  render();
  try {
    await refresh();
    if (!capabilities[selected.language])
      throw new Error("BOT_SANDBOX_UNAVAILABLE");
    log(`Tạo trận thử cho ${selected.name}…`);
    sessionStorage.setItem(
      seatKey,
      JSON.stringify(await createMatch("HUMAN_VS_BOT", selected)),
    );
    location.href = "/game.html";
  } catch (error) {
    log("Không tạo được trận: " + error.message, true);
    toast(error.message, true);
    busy = false;
    render();
  }
};
render();
refresh();
