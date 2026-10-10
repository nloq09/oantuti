const storageKey = "rps-uploaded-bots";
export function savedBots() {
  try {
    const bots = JSON.parse(localStorage.getItem(storageKey) || "[]");
    return Array.isArray(bots)
      ? bots.filter((bot) => bot?.id && bot?.token && bot?.name)
      : [];
  } catch {
    return [];
  }
}
export async function uploadBot(name, file) {
  const form = new FormData();
  form.append("name", name);
  form.append("file", file);
  const response = await fetch("/api/bots", { method: "POST", body: form });
  const data = await response.json();
  if (!response.ok) throw new Error(data.error || "BOT_UPLOAD_FAILED");
  localStorage.setItem(storageKey, JSON.stringify([...savedBots(), data]));
  return data;
}
export async function botCapabilities() {
  const response = await fetch("/api/bots/capabilities");
  if (!response.ok) throw new Error("BOT_UPLOAD_FAILED");
  return response.json();
}
