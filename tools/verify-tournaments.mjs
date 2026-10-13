// Real Docker/end-to-end tournament stress check. Use only a dedicated test server.
import assert from "node:assert/strict";
import { randomUUID } from "node:crypto";
import { execFileSync } from "node:child_process";
import { replayFrames, replayState } from "../client/js/replay/replay-state.js";
const base = process.env.RPS_TEST_URL || "http://127.0.0.1:8081";
const pause = (ms) => new Promise((resolve) => setTimeout(resolve, ms));
const containers = () =>
  execFileSync(
    "docker",
    ["ps", "-a", "--filter", "name=rps-bot-", "--format", "{{.Names}}"],
    { encoding: "utf8" },
  )
    .trim()
    .split(/\r?\n/)
    .filter(Boolean);
const baseline = new Set(containers());
const cookies = new Map();
async function sessionFetch(path, options = {}) {
  const response = await fetch(base + path, {...options, headers: {...options.headers, Cookie: [...cookies].map(([name, value]) => name + "=" + value).join("; ")}});
  for(const cookie of response.headers.getSetCookie()) {
    const pair = cookie.split(";")[0], equal = pair.indexOf("=");
    cookies.set(pair.slice(0, equal), pair.slice(equal + 1));
  }
  return response;
}
async function csrfHeaders() {
  const response = await sessionFetch("/api/auth/csrf");
  assert.equal(response.status, 200);const csrf = await response.json();
  return {[csrf.headerName]: csrf.token};
}
async function request(path, body, expected = 200, extraHeaders = {}) {
  const response = await sessionFetch(path, {
    method: body === undefined ? "GET" : "POST",
    headers: { ...(body === undefined ? {} : { "Content-Type": "application/json" }), ...(body === undefined ? {} : await csrfHeaders()), ...extraHeaders },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  assert.equal(response.status, expected, await response.clone().text());
  return response.json();
}
const aggressive = `import sys,json
for line in sys.stdin:
 q=json.loads(line); actions=q['legalActions']; board=q['state']['board']['pieces']; enemies=[p['position'] for p in board if p['ownerId']!=q['player'] and p['status']=='ALIVE']
 def score(a):
  if a['type']!='MOVE': return -10000
  p=a['to']; return 1000 if p in enemies else -min((abs(p['row']-e['row'])+abs(p['col']-e['col']) for e in enemies),default=0)
 a=max(actions,key=score)
 print(json.dumps({'type':'ACTION','sequence':q['sequence'],'action':a}),flush=True)
`;
async function upload(name, source) {
  const body = new FormData();
  body.append("name", name);
  body.append("file", new File([source], "main.py"));
  const response = await sessionFetch("/api/bots", { method: "POST", headers: await csrfHeaders(), body });
  assert.equal(response.status, 201);
  return response.json();
}
async function run(name, bots) {
  const t = await request(
    "/api/tournaments",
    { name, format: "ROUND_ROBIN" },
    201,
  );
  for (const bot of bots)
    await request(`/api/tournaments/${t.id}/participants`, {
      botId: bot.id,
      displayName: bot.name,
    });
  await request(`/api/tournaments/${t.id}/start`, {}, 200, { "X-Tournament-Token": t.ownerToken });
  let maximum = 0,
    finished,
    observedReplay = false,
    spectatorChecked = false;
  const deadline = Date.now() + 180000;
  while (Date.now() < deadline) {
    const current = await request(`/api/tournaments/${t.id}`);
    maximum = Math.max(maximum, current.progress.running);
    assert.ok(current.progress.running <= 4);
    const live = current.matches.find((m) => m.status === "RUNNING");
    if (live && !spectatorChecked) {
      spectatorChecked = true;
      await observe(live.onlineMatchId);
    }
    const ended = current.matches.find((m) => m.status === "FINISHED");
    if (ended && !observedReplay) {
      observedReplay = true;
      const replay = await request(
        `/api/matches/${ended.onlineMatchId}/replay`,
      );
      assert.equal(replay.initialState.pieces.length, 36);
      assert.ok(replay.events.length > 0);
      assert.equal(replay.finalState.phase, "FINISHED");
      const frames = replayFrames(replay.events);
      const final = replayState(replay.initialState, frames, frames.length);
      assert.deepEqual(
        final.pieces.sort((a, b) => a.id.localeCompare(b.id)),
        replay.finalState.pieces.sort((a, b) => a.id.localeCompare(b.id)),
        "Replay facts must reconstruct final board exactly",
      );
      assert.equal(final.turn, replay.finalState.turn);
      assert.equal(final.winner, replay.finalState.winner);
    }
    if (current.status === "FINISHED") {
      finished = current;
      break;
    }
    await pause(100);
  }
  assert.ok(finished, "Tournament did not finish within test deadline");
  assert.equal(finished.matches.length, (bots.length * (bots.length - 1)) / 2);
  assert.equal(finished.progress.running, 0);
  assert.equal(finished.progress.pending, 0);
  assert.equal(finished.progress.failed, 0);
  assert.ok(finished.champion);
  assert.ok(observedReplay);
  assert.equal(
    finished.ranking.reduce((sum, p) => sum + p.played, 0),
    2 * finished.matches.length,
  );
  for (const m of finished.matches) {
    const response = await fetch(base + `/api/matches/${m.onlineMatchId}`);
    assert.equal(
      response.status,
      404,
      "Finished tournament rooms must be released",
    );
  }
  console.log(
    JSON.stringify({
      name,
      id: t.id,
      matches: finished.matches.length,
      maximumRunning: maximum,
      champion: finished.champion.displayName,
      botFailures: finished.matches.filter((m) => m.failure).length,
    }),
  );
  return finished;
}
async function observe(id) {
  await new Promise((resolve, reject) => {
    const socket = new WebSocket(base.replace(/^http/, "ws") + "/ws/game");
    const timer = setTimeout(() => {
      socket.close();
      reject(new Error("Spectator timeout"));
    }, 10000);
    socket.onopen = () =>
      socket.send(JSON.stringify({ type: "SPECTATE_MATCH", matchId: id }));
    socket.onmessage = (event) => {
      const m = JSON.parse(event.data);
      if (m.type === "GAME_STATE") {
        socket.send(
          JSON.stringify({
            type: "ACTION",
            matchId: id,
            payload: { type: "SURRENDER" },
          }),
        );
      } else if (m.type === "ACTION_REJECTED") {
        try {
          assert.equal(m.payload.error, "SPECTATOR_READ_ONLY");
          clearTimeout(timer);
          socket.close();
          resolve();
        } catch (error) {
          reject(error);
        }
      }
    };
    socket.onerror = reject;
  });
}
const accountName = "stress_" + randomUUID().slice(0, 8), password = randomUUID() + randomUUID();
await request("/api/auth/register", {username: accountName, email: accountName + "@example.com", displayName: "Stress test", password}, 201);
await request("/api/auth/login", {username: accountName, password});
const bots = [];
for (let i = 0; i < 10; i++)
  bots.push(await upload(`Stress Bot ${i + 1}`, aggressive));
await run("4-bot acceptance", bots.slice(0, 4));
await run("10-bot stress", bots);
const crash = await upload("Crash Bot", "import sys\nsys.exit(1)\n");
const invalid = await upload(
  "Invalid Bot",
  "import sys\nfor line in sys.stdin: print('{}',flush=True)\n",
);
const timeout = await upload(
  "Timeout Bot",
  `import sys,json,time\nfirst=True\nfor line in sys.stdin:\n q=json.loads(line)\n if not first: time.sleep(60)\n first=False\n print(json.dumps({'type':'ACTION','sequence':q['sequence'],'action':q['legalActions'][0]}),flush=True)\n`,
);
const failures = await run("Failure isolation", [
  bots[0],
  crash,
  invalid,
  timeout,
]);
assert.ok(failures.matches.some((m) => m.failure === "BOT_FAILED"));
assert.equal((await request("/api/health")).status, "UP");
for (let i = 0; i < 40 && containers().some((name) => !baseline.has(name)); i++)
  await pause(250);
assert.deepEqual(
  containers().filter((name) => !baseline.has(name)),
  [],
  "Test-created Docker containers leaked",
);
console.log(
  "Tournament, ranking, spectator, replay, fault isolation and container cleanup verified.",
);
