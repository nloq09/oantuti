async function read(response) {
  const data = await response.json();
  if (!response.ok) throw new Error(data.error || "SERVER_ERROR");
  return data;
}
export async function authRequest(path, body) {
  const headers = { "Content-Type": "application/json" };
  if (body !== undefined) {
    const csrf = await read(await fetch("/api/auth/csrf", { credentials: "same-origin", cache: "no-store" }));
    headers[csrf.headerName] = csrf.token;
  }
  return read(await fetch("/api/auth/" + path, {
    method: body === undefined ? "GET" : "POST",
    credentials: "same-origin", cache: "no-store", headers,
    body: body === undefined ? undefined : JSON.stringify(body),
  }));
}
export const register = (account) => authRequest("register", account);
export const login = (username, password) => authRequest("login", { username, password });
export const logout = () => authRequest("logout", {});
export const currentUser = () => authRequest("me");
