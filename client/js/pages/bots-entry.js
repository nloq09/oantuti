import { requireAccount } from "../account/account-session.js";
import { errorText } from "../ui/toast.js";
document.querySelector("#account-load-status").textContent = "Đang kiểm tra tài khoản…";
try { if (await requireAccount()) { document.querySelector("#account-load-status").textContent = ""; document.querySelector("[data-protected-content]").hidden = false; await import("./bots.js"); } }
catch (error) { document.querySelector("#account-load-status").textContent = errorText(error.message); }
