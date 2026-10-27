import { node, link, message } from "./admin-ui.js";
import { readSystem, subscribeSystem } from "./system-status.js";

export function watchNotifications(target, {poll = true} = {}) {
  const summary = node("summary", "Cảnh báo · Đang tải…"), content = node("div", undefined, "admin-notification-list");
  content.setAttribute("role", "status"); target.append(summary, content);
  const unsubscribe = subscribeSystem((data, error) => {
    if (error) { summary.textContent = "Cảnh báo · Chưa cập nhật"; message(error, content); return; }
    content.setAttribute("role", "status");
    summary.textContent = "Cảnh báo · " + data.alerts.length; content.replaceChildren();
    for (const alert of data.alerts) content.append(node("p", alert.severity + " · " + alert.message));
    if (!data.alerts.length) content.append(node("p", "Không có cảnh báo."));
    content.append(link("Xem hệ thống", "/admin/system.html"));
  });
  const refresh = () => { if (!document.hidden) readSystem().catch(() => {}); };
  const restore = () => { if (!document.hidden && poll) refresh(); };
  if (poll) refresh();
  const timer = poll ? setInterval(refresh, 10000) : undefined;
  document.addEventListener("visibilitychange", restore);
  const dispose = () => { clearInterval(timer); unsubscribe(); document.removeEventListener("visibilitychange", restore); };
  addEventListener("pagehide", dispose, {once:true});
  return {dispose};
}
