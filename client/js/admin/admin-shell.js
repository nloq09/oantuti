import { currentUser, logout } from "../api/auth-api.js";
import { adminAccess } from "./admin-auth.js";
export const node = (tag, text, className) => { const element = document.createElement(tag); if (text !== undefined) element.textContent = text; if (className) element.className = className; return element; };
export const link = (text, href) => { const element = node("a", text); element.href = href; return element; };
export const date = value => value ? new Date(value).toLocaleString("vi-VN") : "—";
export function message(error, target) {
  target.replaceChildren(node("p", ({ 401: "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.", 403: "Bạn không có quyền thực hiện thao tác này.", 404: "Không tìm thấy dữ liệu.", 409: "Dữ liệu đã thay đổi. Hãy tải lại trước khi thao tác.", 503: "Dịch vụ tạm thời chưa sẵn sàng." })[error.status] || error.message || "Không thể tải dữ liệu.", "error"));
  if (error.status === 401) target.append(link("Đăng nhập", "/login.html?next=" + encodeURIComponent(location.pathname + location.search)));
}
async function init() {
  const root = document.querySelector("#admin-root");
  try {
    const result = await currentUser(), account = result.authenticated ? result.user : null;
    const access = adminAccess(account);
    if (access === "LOGIN") { location.replace("/login.html?next=" + encodeURIComponent(location.pathname + location.search)); return; }
    if (access === "DENY") { root.replaceChildren(node("h1", "403 · Chỉ dành cho quản trị viên"), link("Về trang chủ", "/")); return; }
    root.replaceChildren(); root.className = "admin-shell";
    const top = node("header", undefined, "admin-topbar"), toggle = node("button", "☰"); toggle.setAttribute("aria-label", "Đóng hoặc mở thanh điều hướng"); toggle.setAttribute("aria-expanded", "true");
    toggle.onclick = () => { root.classList.toggle("sidebar-collapsed"); toggle.setAttribute("aria-expanded", String(!root.classList.contains("sidebar-collapsed"))); };
    const identity = node("div",undefined,"admin-account"), summary = node("span", account.displayName + " · ADMIN"), exit = node("button", "Đăng xuất");
    exit.onclick = async () => { exit.disabled = true; try { await logout(); location.assign("/"); } catch (error) { exit.disabled = false; message(error, content); } };
    identity.append(summary, exit);
    const brand=node("a",undefined,"admin-brand");brand.href="/admin/index.html";brand.append(node("span","♛","admin-crest"),node("strong", "RPS BATTLE · QUẢN TRỊ"));
    top.append(toggle, brand, identity);
    const sidebar = node("nav", undefined, "admin-sidebar"); sidebar.setAttribute("aria-label", "Quản trị");
    const current = location.pathname.split("/").pop() || "index.html";
    for (const [label, path] of [["Tổng quan", "index.html"], ["Người dùng", "users.html"], ["Bot", "bots.html"], ["Trận đấu", "matches.html"], ["Giải đấu", "tournaments.html"], ["Tranh chấp", "disputes.html"], ["Kho replay", "replays.html"]]) {
      const a = link(label, "/admin/" + path); if (current === path || current === ({"users.html":"user-detail.html","bots.html":"bot-detail.html","matches.html":"match-detail.html"})[path] || (path === "matches.html" && ["replay.html","spectator.html"].includes(current))) a.setAttribute("aria-current", "page"); sidebar.append(a);
    }
    for (const label of ["Audit", "Hệ thống"]) { const item = node("span", label + " · Sắp có", "muted"); sidebar.append(item); }
    const content = node("main", undefined, "admin-content"); content.id = "admin-content"; content.tabIndex = -1;
    const skip = link("Đến nội dung", "#admin-content"); skip.className = "skip-link"; root.append(skip, top, sidebar, content);
    content.append(node("h1", "Tổng quan"), node("p", "Giao diện quản trị đã sẵn sàng."));
    const module = await import("./views.js"); await module.mount(current, content);
  } catch (error) { message(error, root); }
}
init();
