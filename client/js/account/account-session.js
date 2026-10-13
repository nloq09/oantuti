import { currentUser, logout } from "../api/auth-api.js";
import { safeNext } from "./account-ui.js";
import { seatKey } from "../config.js";
import { toast } from "../ui/toast.js";
let accountRequest;
export const getAccount = () => accountRequest ||= currentUser().then(result => result.authenticated ? result.user : null);
function returnDestination() {
  return ["/login.html", "/register.html"].includes(location.pathname)
    ? safeNext(new URLSearchParams(location.search).get("next"), location.origin)
    : location.pathname + location.search;
}
export function loginLink(path = returnDestination()) {
  return "/login.html?next=" + encodeURIComponent(safeNext(path, location.origin));
}
export async function requireAccount() {
  const account = await getAccount();
  if (!account) location.replace(loginLink());
  return account;
}
export function clearAccountState() {
  for (const key of [seatKey, "rps-result", "rps-preferred-bot"]) sessionStorage.removeItem(key);
}
export async function initAccountNav() {
  const nav = document.querySelector("[data-account-nav]");
  if (!nav) return;
  try {
    const account = await getAccount();
    const links = [["Trang chủ", "/"], ["Vào trận", "/lobby.html"]];
    if (account) links.push(["Bot của tôi", "/bots.html"]);
    links.push(["Giải đấu", "/tournaments.html"]);
    if (account) links.push(["Lịch sử", "/history.html"], ["Hồ sơ", "/profile.html"]);
    else links.push(["Đăng nhập", loginLink()], ["Đăng ký", "/register.html?next=" + encodeURIComponent(safeNext(returnDestination(), location.origin))]);
    nav.replaceChildren();
    for (const [label, href] of links) {
      const a = document.createElement("a"); a.className = "button"; a.textContent = label; a.href = href;
      if (new URL(href, location.origin).pathname === location.pathname) a.setAttribute("aria-current", "page");
      nav.append(a);
    }
    if (account) {
      const name = document.createElement("span"), button = document.createElement("button");
      name.className = "account-name"; name.textContent = account.displayName; button.textContent = "Đăng xuất";
      button.onclick = async () => {
        button.disabled = true;
        try { await logout(); clearAccountState(); location.assign("/"); }
        catch (error) { toast(error.message, true); button.disabled = false; }
      };
      nav.append(name, button);
    }
  } catch (error) {
    const message = document.createElement("span"); message.textContent = "Chưa kiểm tra được phiên đăng nhập."; nav.append(message); toast(error.message, true);
  }
}
