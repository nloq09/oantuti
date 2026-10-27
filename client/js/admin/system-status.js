import { adminRequest } from "./admin-api.js";

const listeners = new Set();
let pending;
export function publishSystem(data, error) {
  for (const listener of listeners) listener(data, error);
  return data;
}
export function subscribeSystem(listener) {
  listeners.add(listener);
  return () => listeners.delete(listener);
}
export function readSystem() {
  if (!pending) pending = adminRequest("system")
    .then(data => publishSystem(data))
    .catch(error => { publishSystem(undefined, error); throw error; })
    .finally(() => { pending = undefined; });
  return pending;
}
