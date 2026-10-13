import { sessionRequest } from "./auth-api.js";

export const accountProfile = () => sessionRequest("/api/account/profile");
export const updateProfile = (displayName) => sessionRequest("/api/account/profile", { displayName }, "PATCH");
export const changePassword = (currentPassword,newPassword) => sessionRequest("/api/account/change-password", { currentPassword,newPassword });
export const adminUsers = (page=0,size=20) => sessionRequest(`/api/admin/users?page=${encodeURIComponent(page)}&size=${encodeURIComponent(size)}`);
export const setAccountEnabled = (id,enabled) => sessionRequest(`/api/admin/users/${encodeURIComponent(id)}/${enabled?"enable":"disable"}`,{});
export const adminSystem = () => sessionRequest("/api/admin/system");
