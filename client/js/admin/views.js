import { message } from "./admin-ui.js";

const routes = [
  [["index.html"], () => import("./dashboard.js"), (module, page, root) => module.dashboard(root)],
  [["workers.html", "worker-setup.html"], () => import("./workers.js"), (module, page, root) => module.mountWorkers(page, root)],
  [["profile.html"], () => import("./profile.js"), (module, page, root) => module.mountProfile(root)],
  [["audit.html", "system.html"], () => import("./operations.js"), (module, page, root) => module.mountOperations(page, root)],
  [["disputes.html", "dispute-detail.html", "replays.html", "replay-detail.html"], () => import("./reviews.js"), (module, page, root) => module.mountReviews(page, root)],
  [["tournaments.html", "tournament-detail.html"], () => import("./tournaments.js"), (module, page, root) => module.mountTournaments(page, root)],
  [["replay.html", "spectator.html"], () => import("./viewer.js"), (module, page, root) => module.mountViewer(page, root)],
  [["matches.html", "match-detail.html"], () => import("./matches.js"), (module, page, root) => module.mountMatches(page, root)],
  [[], () => import("./directory.js"), (module, page, root) => module.mountDirectory(page, root)],
];
const pending = new Map();
export function preload(page) {
  const route = routes.find(([pages]) => pages.includes(page)) || routes.at(-1);
  if (!pending.has(route)) {
    const request = route[1]().catch(error => { pending.delete(route); throw error; });
    pending.set(route, request);
  }
  return pending.get(route);
}
export async function mount(page, root) {
  try {
    const route = routes.find(([pages]) => pages.includes(page)) || routes.at(-1);
    await route[2](await preload(page), page, root);
  } catch (error) { message(error, root); }
}
