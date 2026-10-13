import test from "node:test";
import assert from "node:assert/strict";
import { safeNext, validatePassword } from "../js/account/account-ui.js";
import { historyLoader } from "../js/account/history-loader.js";

test("login return paths reject external origins, credentials, scripts and authentication loops", () => {
  const origin = "http://localhost:8080";
  for (const input of ["https://evil.test/profile.html", "//evil.test/lobby.html", "javascript:alert(1)", "http://user:secret@localhost:8080/profile.html", "/login.html?next=/profile.html", "/register.html", "http://localhost:8081/profile.html", "\\\\evil.test\\profile.html"]) assert.equal(safeNext(input, origin), "/", input);
  assert.equal(safeNext("/tournament.html?id=abc#ranking", origin), "/tournament.html?id=abc#ranking");
  assert.equal(safeNext("http://localhost:8080/history.html", origin), "/history.html");
  assert.equal(safeNext(null, origin), "/");
});
test("password checks match the backend's Unicode character and UTF-8 byte limits without trimming", () => {
  assert.doesNotThrow(() => validatePassword("😀".repeat(18), "😀".repeat(18)));
  assert.throws(() => validatePassword("😀".repeat(19), "😀".repeat(19)), /INVALID_PASSWORD/);
  assert.throws(() => validatePassword("😀".repeat(10), "😀".repeat(10)), /INVALID_PASSWORD/);
  assert.throws(() => validatePassword(" ".repeat(12), " ".repeat(12)), /INVALID_PASSWORD/);
  assert.throws(() => validatePassword("a long password", "different password"), /PASSWORD_MISMATCH/);
  assert.doesNotThrow(() => validatePassword(" password12 ", " password12 "));
});
test("rapid history filter changes cannot display old results or let old errors replace new data", async () => {
  const pending = [], results = [], errors = [], loading = [];
  const load = historyLoader((filter,page,size) => new Promise((resolve,reject) => pending.push({filter,page,size,resolve,reject})), result => results.push(result), error => errors.push(error), busy => loading.push(busy));
  const first = load("win",0), second = load("loss",0);
  pending[0].resolve({total:99}); await first;
  assert.deepEqual(results, []); assert.deepEqual(loading, [true,true]);
  pending[1].resolve({total:1}); await second; assert.deepEqual(results,[{total:1}]);
  const third = load("all",1), fourth = load("draw",0);
  pending[3].resolve({total:0}); await fourth; pending[2].reject(new Error("old failure")); await third;
  assert.deepEqual(errors,[]); assert.deepEqual(results,[{total:1},{total:0}]);
  assert.ok(pending.every(p => p.size === 20));
});
