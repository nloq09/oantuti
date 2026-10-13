async function read(response) {
  if (response.status === 204) return undefined;
  const data = await response.json();
  if (!response.ok) throw new Error(data.error || "SERVER_ERROR");
  return data;
}
export async function csrfHeaders() {
  const csrf = await read(await fetch("/api/auth/csrf", { credentials: "same-origin", cache: "no-store" }));
  return { [csrf.headerName]: csrf.token };
}
export async function sessionRequest(path, body, method) {
  const headers = { "Content-Type": "application/json" };
  if (body !== undefined) {
    Object.assign(headers, await csrfHeaders());
  }
  return read(await fetch(path, {
    method: method || (body === undefined ? "GET" : "POST"),
    credentials: "same-origin", cache: "no-store", headers,
    body: body === undefined ? undefined : JSON.stringify(body),
  }));
}
export const authRequest = (path, body) => sessionRequest("/api/auth/" + path, body);
export const register = (account) => authRequest("register", account);
export const login = (username, password) => authRequest("login", { username, password });
export const logout = () => authRequest("logout", {});
export const currentUser = () => authRequest("me");
