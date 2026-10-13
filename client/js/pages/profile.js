import { requireAccount, clearAccountState, loginLink } from "../account/account-session.js";
import { accountProfile, updateProfile, changePassword, accountStats } from "../api/account-api.js";
import { validatePassword, dateLabel, showStats } from "../account/account-ui.js";
import { errorText } from "../ui/toast.js";
const status = document.querySelector("#account-load-status");
function fail(error, target) {
  if (error.message === "UNAUTHENTICATED") location.replace(loginLink());
  else target.textContent = errorText(error.message);
}
try {
  if (await requireAccount()) {
    const profile = await accountProfile();
    for (const field of ["username", "email", "role"]) document.querySelector("#profile-" + field).textContent = field === "role" ? (profile.role === "ADMIN" ? "Quản trị viên" : "Người chơi") : profile[field];
    for (const field of ["createdAt", "lastLoginAt"]) document.querySelector("#profile-" + field).textContent = dateLabel(profile[field]);
    document.querySelector('[name="displayName"]').value = profile.displayName;
    document.querySelector("[data-protected-content]").hidden = false; status.textContent = "";
    accountStats().then(stats => showStats(document.querySelector("#account-stats"), stats)).catch(error => fail(error, document.querySelector("#stats-status")));
  }
} catch (error) { fail(error, status); }
for (const id of ["profile-form", "password-form"]) {
  const form = document.querySelector("#" + id), message = form.querySelector("[role=status]");
  form.onsubmit = async event => {
    event.preventDefault(); if (form.dataset.busy) return;
    const button = form.querySelector("button");
    try {
      const fields = new FormData(form);
      if (id === "password-form") validatePassword(fields.get("newPassword"), fields.get("confirmation"));
      form.dataset.busy = "true"; button.disabled = true; message.textContent = ""; message.classList.remove("form-notice");
      if (id === "profile-form") {
        const profile = await updateProfile(fields.get("displayName")); form.elements.displayName.value = profile.displayName;
        const name = document.querySelector(".account-name"); if (name) name.textContent = profile.displayName;
        message.classList.add("form-notice"); message.textContent = "Đã lưu tên hiển thị.";
      } else {
        await changePassword(fields.get("currentPassword"), fields.get("newPassword"));
        clearAccountState(); location.assign("/login.html?next=%2Fprofile.html&passwordChanged=1");
      }
    } catch (error) { fail(error, message); }
    finally { delete form.dataset.busy; button.disabled = false; if (id === "password-form") form.reset(); }
  };
}
