import { createMatch, joinMatch, getMatch } from "../api/match-api.js";
import { seatKey } from "../config.js";
import { toast } from "../ui/toast.js";
import { refreshBots, savedBots, botCapabilities } from "../api/bot-api.js";
import { getAccount, loginLink } from "../account/account-session.js";
const controller = document.querySelector("#player-controller");
controller.disabled=true;controller.title="Đang tải danh sách bot…";
let controllerChanged=false;controller.addEventListener('change',()=>{controllerChanged=true;});
// Bind match controls immediately. Loading private bot choices must not freeze human play.
const botChoicesReady=Promise.all([getAccount().catch(error=>{toast(error.message,true);return undefined;}),refreshBots().catch(error=>{toast(error.message,true);return []})]).then(([account])=>{
 if(account===null){const notice=document.querySelector('#guest-notice'),link=document.createElement('a');notice.textContent='Bạn đang chơi với tư cách khách. ';link.href=loginLink();link.textContent='Đăng nhập để dùng bot riêng và lưu lịch sử của bạn.';notice.append(link);}
 for(const bot of savedBots())controller.append(new Option(`${bot.name} · ${bot.language}`,bot.id));
 const preferred=sessionStorage.getItem('rps-preferred-bot');if(!controllerChanged&&savedBots().some(bot=>bot.id===preferred))controller.value=preferred;
}).finally(()=>{controller.disabled=false;controller.removeAttribute('title');});
async function selectedBot() {
  await botChoicesReady;
  const bot = savedBots().find((item) => item.id === controller.value);
  if (bot && !(await botCapabilities()).languages[bot.language])
    throw new Error("BOT_SANDBOX_UNAVAILABLE");
  return bot;
}

let mode = "HUMAN_VS_HUMAN";
let entering = false;
const modes = [...document.querySelectorAll("[data-mode]")];
for (const button of modes) {
  button.onclick = () => {
    mode = button.dataset.mode;
    for (const item of modes) {
      const selected = item === button;
      item.classList.toggle("chosen", selected);
      item.setAttribute("aria-pressed", String(selected));
    }
  };
}
async function enter(operation) {
  if (entering) return;
  entering = true;
  const buttons = [...document.querySelectorAll("button")];
  buttons.forEach((button) => (button.disabled = true));
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
    entering = false;
    buttons.forEach((button) => (button.disabled = false));
  }
}
document.querySelector("#create").onclick = () =>
  enter(async () => createMatch(mode, await selectedBot()));
document.querySelector("#join-form").onsubmit = (event) => {
  event.preventDefault();
  const id = document.querySelector("#code").value.trim().toUpperCase();
  if (!id) return;
  enter(async () => {
    await getMatch(id);
    return joinMatch(id, await selectedBot());
  });
};
