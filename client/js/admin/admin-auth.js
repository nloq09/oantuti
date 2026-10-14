export function adminAccess(account) {
  // /auth/me omits account status; the server only returns active authenticated accounts.
  return !account ? "LOGIN" : account.role === "ADMIN" && account.status !== "DISABLED" ? "ALLOW" : "DENY";
}
