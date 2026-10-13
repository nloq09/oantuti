import { sessionRequest, csrfHeaders } from "./auth-api.js";
const storageKey = "rps-uploaded-bots";
let bots = [];
export const savedBots = () => [...bots];
export function legacyBots() {
  try {
    const old = JSON.parse(localStorage.getItem(storageKey) || "[]");
    return Array.isArray(old) ? old.filter(b => b?.id && b?.token && b?.name) : [];
  } catch { return []; }
}
export async function refreshBots() {
  bots = [];
  try { bots = await sessionRequest("/api/account/bots"); }
  catch(error) { if(error.message !== "UNAUTHENTICATED") throw error; }
  return savedBots();
}
export async function importLegacyBot(bot) {
  const claimed = await sessionRequest("/api/account/bots/import", { botId: bot.id, botToken: bot.token });
  localStorage.setItem(storageKey, JSON.stringify(legacyBots().filter(b => b.id !== bot.id)));
  await refreshBots(); return claimed;
}
export async function renameBot(id, name) {
  const bot = await sessionRequest("/api/account/bots/" + encodeURIComponent(id), { name }, "PATCH");
  await refreshBots(); return bot;
}
export async function deleteBot(id) {
  await sessionRequest("/api/account/bots/" + encodeURIComponent(id), {}, "DELETE");
  await refreshBots();
}
export async function uploadBot(name, file) {
  const form = new FormData(); form.append("name", name); form.append("file", file);
  const response = await fetch("/api/bots", { method: "POST", credentials: "same-origin", cache: "no-store", headers: await csrfHeaders(), body: form });
  const data = await response.json();
  if(!response.ok) throw new Error(data.error || "BOT_UPLOAD_FAILED");
  await refreshBots(); return data;
}
export async function botCapabilities() {
  const response = await fetch("/api/bots/capabilities");
  if(!response.ok) throw new Error("BOT_UPLOAD_FAILED");
  return response.json();
}
