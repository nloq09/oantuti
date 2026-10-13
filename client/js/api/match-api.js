import { sessionRequest } from "./auth-api.js";
const request = (path, body) => sessionRequest("/api/matches" + path, body);
const botCredentials = (bot) =>
  bot ? { botId: bot.id } : {};
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
