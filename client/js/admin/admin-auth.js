export function adminAccess(account) {
  return !account ? "LOGIN" : account.role === "ADMIN" && account.status === "ACTIVE" ? "ALLOW" : "DENY";
}
