import { csrfHeaders } from "../api/auth-api.js";
export class AdminError extends Error {
  constructor(status, code) { super(code); this.status = status; }
}
export async function adminRequest(path, body) {
  const headers = body === undefined ? {} : { "Content-Type": "application/json", ...await csrfHeaders() };
  const response = await fetch("/api/admin/" + path, { method: body === undefined ? "GET" : "POST", credentials: "same-origin", cache: "no-store", headers, body: body === undefined ? undefined : JSON.stringify(body) });
  const data = await response.json().catch(() => ({}));
  if (!response.ok) throw new AdminError(response.status, data.error || "SERVER_ERROR");
  return data;
}
