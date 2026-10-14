import { currentUser, logout } from "../api/auth-api.js";
import { safeNext, accountLinks } from "./account-ui.js";
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
    const home = nav.dataset.accountNav === "home";
    const links = accountLinks(account, home, loginLink(), "/register.html?next=" + encodeURIComponent(safeNext(returnDestination(), location.origin)));
    for (const link of document.querySelectorAll("[data-account-destination]")) {
      const destination = safeNext(link.dataset.accountDestination, location.origin);
      link.href = account ? destination : loginLink(destination);
    }
    nav.replaceChildren();
    const actions = document.createElement("div"); actions.className = "account-actions";
    for (const [label, href] of links) {
      const a = document.createElement("a"); a.className = "button"; a.textContent = label; a.href = href;
      if (new URL(href, location.origin).pathname === location.pathname) a.setAttribute("aria-current", "page");
      if (["Đăng nhập", "Đăng ký", "Lịch sử", "Hồ sơ", "Quản trị"].includes(label)) actions.append(a);
      else nav.append(a);
    }
    nav.append(actions);
    if (account) {
      const name = document.createElement("span"), button = document.createElement("button");
      name.className = "account-name"; name.textContent = account.displayName; button.textContent = "Đăng xuất";
      button.onclick = async () => {
        button.disabled = true;
        try { await logout(); clearAccountState(); location.assign("/"); }
        catch (error) { toast(error.message, true); button.disabled = false; }
      };
      actions.append(name, button);
    }
  } catch (error) {
    const message = document.createElement("span"); message.textContent = "Chưa kiểm tra được phiên đăng nhập."; nav.append(message); toast(error.message, true);
  }
}
