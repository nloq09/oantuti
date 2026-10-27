import { currentUser, logout } from "../api/auth-api.js";
import { adminAccess } from "./admin-auth.js";
import { node, link, message } from "./admin-ui.js";
import { mount, preload } from "./views.js";
import { watchNotifications } from "./notifications.js";
async function init() {
  const root = document.querySelector("#admin-root");
  const current = location.pathname.split("/").pop() || "index.html";
  // Fetch public view code while checking the session; APIs still require ADMIN.
  preload(current).catch(() => {});
  try {
    const result = await currentUser(), account = result.authenticated ? result.user : null;
    const access = adminAccess(account);
    if (access === "LOGIN") { location.replace("/login.html?next=" + encodeURIComponent(location.pathname + location.search)); return; }
    if (access === "DENY") { root.replaceChildren(node("h1", "403 · Chỉ dành cho quản trị viên"), link("Về trang chủ", "/")); return; }
    root.replaceChildren(); root.className = "admin-shell";
    const top = node("header", undefined, "admin-topbar"), toggle = node("button", "☰"); toggle.setAttribute("aria-label", "Đóng hoặc mở thanh điều hướng"); toggle.setAttribute("aria-controls", "admin-navigation");
    if(matchMedia("(max-width: 700px)").matches)root.classList.add("sidebar-collapsed");
    toggle.setAttribute("aria-expanded",String(!root.classList.contains("sidebar-collapsed")));
    toggle.onclick = () => { root.classList.toggle("sidebar-collapsed"); toggle.setAttribute("aria-expanded", String(!root.classList.contains("sidebar-collapsed"))); };
    const identity = node("div",undefined,"admin-account"), summary = node("span", account.displayName + " · ADMIN"), exit = node("button", "Đăng xuất");
    exit.onclick = async () => { exit.disabled = true; try { await logout(); location.assign("/"); } catch (error) { exit.disabled = false; message(error, content); } };
    identity.append(link("Hồ sơ", "/admin/profile.html"), summary, exit);
    const brand=node("a",undefined,"admin-brand");brand.href="/admin/index.html";brand.append(node("span","♛","admin-crest"),node("strong", "RPS BATTLE · QUẢN TRỊ"));
    const notifications=node("details",undefined,"admin-notifications");top.append(toggle, brand, notifications, identity);
    const sidebar = node("nav", undefined, "admin-sidebar"); sidebar.id="admin-navigation"; sidebar.setAttribute("aria-label", "Quản trị");
    for (const [label, path] of [["Tổng quan", "index.html"], ["Người dùng", "users.html"], ["Bot", "bots.html"], ["Trận đấu", "matches.html"], ["Giải đấu", "tournaments.html"], ["Tranh chấp", "disputes.html"], ["Kho replay", "replays.html"], ["Audit", "audit.html"], ["Worker", "workers.html"], ["Hệ thống", "system.html"]]) {
      const a = link(label, "/admin/" + path); if (current === path || current === ({"workers.html":"worker-setup.html","users.html":"user-detail.html","bots.html":"bot-detail.html","matches.html":"match-detail.html","tournaments.html":"tournament-detail.html","disputes.html":"dispute-detail.html","replays.html":"replay-detail.html"})[path] || (path === "matches.html" && ["replay.html","spectator.html"].includes(current))) a.setAttribute("aria-current", "page");
      const warm = () => preload(path).catch(() => {});
      a.addEventListener("pointerenter", warm, {once:true}); a.addEventListener("focus", warm, {once:true}); sidebar.append(a);
    }

    const content = node("main", undefined, "admin-content"); content.id = "admin-content"; content.tabIndex = -1;
    const skip = link("Đến nội dung", "#admin-content"); skip.className = "skip-link"; root.append(skip, top, sidebar, content);
    const headerSize=new ResizeObserver(()=>root.style.setProperty("--admin-header-height",top.getBoundingClientRect().height+"px"));headerSize.observe(top);addEventListener("pagehide",()=>headerSize.disconnect(),{once:true});
    sidebar.addEventListener("keydown",event=>{if(event.key==="Escape"){root.classList.add("sidebar-collapsed");toggle.setAttribute("aria-expanded","false");toggle.focus();}});
    content.append(node("h1", "Tổng quan"), node("p", "Giao diện quản trị đã sẵn sàng."));
    watchNotifications(notifications, {poll:!["index.html","system.html"].includes(current)});
    await mount(current, content);
  } catch (error) { message(error, root); }
}
init();
