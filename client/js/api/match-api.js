async function request(path, body) {
  const response = await fetch("/api/matches" + path, {
    method: body === undefined ? "GET" : "POST",
    headers: { "Content-Type": "application/json" },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const data = await response.json();
  if (!response.ok) throw new Error(data.error || "SERVER_ERROR");
  return data;
}
export const createMatch = (mode) => request("", { mode });
export const joinMatch = (id) =>
  request("/" + encodeURIComponent(id) + "/join", {});
export const getMatch = (id) => request("/" + encodeURIComponent(id));
