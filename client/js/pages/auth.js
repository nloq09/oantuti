import { register, login } from "../api/auth-api.js";
import { getAccount, clearAccountState } from "../account/account-session.js";
import { safeNext, validatePassword } from "../account/account-ui.js";
import { errorText } from "../ui/toast.js";
const form = document.querySelector("#auth-form"), message = document.querySelector("#form-message");
const mode = form.dataset.mode;
const next = safeNext(new URLSearchParams(location.search).get("next"), location.origin);
document.querySelector("#alternate-auth").href = (mode === "register" ? "/login.html" : "/register.html") + "?next=" + encodeURIComponent(next);
getAccount().then(account => { if (account) location.replace(next); }).catch(error => { message.textContent = errorText(error.message); });
form.onsubmit = async event => {
  event.preventDefault(); if (form.dataset.busy) return;
  const button = form.querySelector('[type="submit"]'), original = button.textContent;
  try {
    const fields = new FormData(form), password = fields.get("password");
    if (mode === "register") validatePassword(password, fields.get("confirmation"));
    form.dataset.busy = "true"; button.disabled = true; button.textContent = "Đang xử lý…"; message.textContent = "";
    if (mode === "register") {
      await register({ username: fields.get("username"), displayName: fields.get("displayName"), email: fields.get("email"), password });
      // Registration does not create a session; make the next action explicit.
      location.assign("/login.html?registered=1&next=" + encodeURIComponent(next));
    } else {
      await login(fields.get("username"), password); clearAccountState(); location.assign(next);
    }
  } catch (error) { message.textContent = errorText(error.message); }
  finally { delete form.dataset.busy; button.disabled = false; button.textContent = original; form.querySelector('[name="password"]').value = ""; const confirm = form.querySelector('[name="confirmation"]'); if (confirm) confirm.value = ""; }
};
if (mode === "login" && new URLSearchParams(location.search).get("registered")) document.querySelector("#auth-notice").textContent = "Tạo tài khoản thành công. Hãy đăng nhập để tiếp tục.";
if (mode === "login" && new URLSearchParams(location.search).get("passwordChanged")) document.querySelector("#auth-notice").textContent = "Đã đổi mật khẩu và đăng xuất các phiên cũ. Hãy đăng nhập lại.";
