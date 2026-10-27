import { errorText } from "../ui/toast.js";
export const node = (tag, text, className) => { const element = document.createElement(tag); if (text !== undefined) element.textContent = text; if (className) element.className = className; return element; };
export const link = (text, href) => { const element = node("a", text); element.href = href; return element; };
export const date = value => value ? new Date(value).toLocaleString("vi-VN") : "—";
export function errorMessage(error) {
  return ({401:"Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.",403:"Bạn không có quyền thực hiện thao tác này.",404:"Không tìm thấy dữ liệu.",409:"Dữ liệu đã thay đổi. Hãy tải lại trước khi thao tác.",429:"Bạn thao tác quá nhanh. Hãy chờ rồi thử lại.",500:"Server chưa xử lý được yêu cầu. Hãy thử lại sau.",503:"Dịch vụ tạm thời chưa sẵn sàng."})[error.status] || errorText(error.message || "SERVER_ERROR");
}
export function message(error, target) {
  target.replaceChildren(node("p", errorMessage(error), "error"));
  target.setAttribute("role", "alert");
  if (error.status === 401) target.append(link("Đăng nhập", "/login.html?next=" + encodeURIComponent(location.pathname + location.search)));
}
