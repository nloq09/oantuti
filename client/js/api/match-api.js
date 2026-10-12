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
const botCredentials = (bot) =>
  bot ? { botId: bot.id, botToken: bot.token } : {};
export const createMatch = async (mode, bot) => ({
  ...(await request("", { mode, ...botCredentials(bot) })),
  bot,
});
export const joinMatch = (id, bot) =>
  request("/" + encodeURIComponent(id) + "/join", botCredentials(bot)).then(
    (seat) => ({ ...seat, bot }),
  );
export const getMatch = (id) => request("/" + encodeURIComponent(id));
export async function createBotBattle(bot, opponent) {
  if (!bot) throw new Error("BOT_REQUIRED");
  if (!opponent) return createMatch("HUMAN_VS_BOT", bot);
  const seat = await createMatch("HUMAN_VS_HUMAN", bot);
  await joinMatch(seat.matchId, opponent);
  return seat;
}
