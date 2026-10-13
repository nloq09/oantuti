import { refreshBots, legacyBots, importLegacyBot, renameBot, deleteBot, savedBots, uploadBot, botCapabilities } from "../api/bot-api.js";
import { createMatch } from "../api/match-api.js";
import { seatKey } from "../config.js";
import { toast } from "../ui/toast.js";
await refreshBots().catch(error => toast(error.message, true));

let selected =
  savedBots().find(
    (bot) => bot.id === sessionStorage.getItem("rps-preferred-bot"),
  ) || savedBots()[0];
let capabilities = {},
  busy = false;
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
    empty.textContent = "Đăng nhập để xem và quản lý bot của tài khoản.";
    list.append(empty);
  }
  document.querySelector("#rename-bot").disabled = !selected || busy;
  document.querySelector("#delete-bot").disabled = !selected || busy;
  document.querySelector("#import-legacy").hidden = !legacyBots().length;
  document.querySelector("#import-legacy").disabled = busy;
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
document.querySelector("#bot-upload-form").onsubmit = async (event) => {
  event.preventDefault();
  if (busy) return;
  busy = true;
  render();
  const name = document.querySelector("#bot-name").value.trim(),
    file = document.querySelector("#bot-file").files[0];
  try {
    selected = await uploadBot(name, file);
    sessionStorage.setItem("rps-preferred-bot", selected.id);
    toast("Đã tải bot lên: " + selected.name);
    await refresh();
  } catch (error) {
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
    sessionStorage.setItem(
      seatKey,
      JSON.stringify(await createMatch("HUMAN_VS_BOT", selected)),
    );
    location.href = "/game.html";
  } catch (error) {
    toast(error.message, true);
    busy = false;
    render();
  }
};
render();
refresh();

async function manageBot(task) {
  if(busy) return;
  busy = true; render();
  try { await task(); selected = savedBots().find(bot => bot.id === selected?.id) || savedBots()[0]; }
  catch(error) { toast(error.message, true); }
  finally { busy = false; render(); }
}
document.querySelector("#import-legacy").onclick = () => manageBot(async () => {
  for(const bot of legacyBots()) await importLegacyBot(bot);
  toast("Đã nhập bot cũ vào tài khoản.");
});
document.querySelector("#rename-bot").onclick = () => {
  if(!selected) return;
  const name = prompt("Tên mới của bot", selected.name);
  if(name !== null) manageBot(() => renameBot(selected.id, name));
};
document.querySelector("#delete-bot").onclick = () => {
  if(selected && confirm("Xóa bot khỏi danh sách? File của giải đã đăng ký vẫn được giữ.")) manageBot(() => deleteBot(selected.id));
};
