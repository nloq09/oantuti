import test from "node:test";
import assert from "node:assert/strict";
import { adminAccess } from "../js/admin/admin-auth.js";
import { accountLinks, adminDestination, accountLanding } from "../js/account/account-ui.js";
test("admin guard denies guests ordinary users and disabled administrators", () => {
  assert.equal(adminAccess(null), "LOGIN");
  assert.equal(adminAccess({ role: "USER", status: "ACTIVE" }), "DENY");
  assert.equal(adminAccess({ role: "ADMIN", status: "DISABLED" }), "DENY");
  assert.equal(adminAccess({ role: "ADMIN", status: "ACTIVE" }), "ALLOW");
  assert.equal(adminAccess({ id: "admin-id", username: "admin", role: "ADMIN" }), "ALLOW");
});
test("normal account navigation never displays guest authentication links and only admins get console access",()=>{
  const labels=(account,home)=>accountLinks(account,home,"/login.html","/register.html").map(([label])=>label);
  assert.deepEqual(labels(null,true),["Đăng nhập","Đăng ký"]);
  assert.deepEqual(labels({role:"USER"},true),["Lịch sử","Hồ sơ"]);
  assert.ok(labels({role:"ADMIN"},true).includes("Quản trị"));
  assert.deepEqual(labels({role:"ADMIN"},false),["Quản trị"]);
  assert.ok(!labels({role:"USER"},false).includes("Quản trị"));
  assert.ok(!labels({role:"USER"},false).includes("Đăng nhập"));
});
test("admin login and direct player URLs stay inside management while retaining read-only monitoring",()=>{
  const admin={id:"admin-id",role:"ADMIN"};
  assert.equal(accountLanding(admin,"/lobby.html"),"/admin/index.html");
  assert.equal(accountLanding(admin,"/admin/users.html?page=2"),"/admin/users.html?page=2");
  assert.equal(accountLanding(admin,"https://attacker.invalid/admin/users.html"),"/admin/index.html");
  assert.equal(accountLanding({role:"USER"},"/lobby.html"),"/lobby.html");
  assert.equal(adminDestination(admin,"/"),"/admin/index.html");
  assert.equal(adminDestination(admin,"/game.html","?spectate=room-id"),"/admin/spectator.html?spectate=room-id");
  assert.equal(adminDestination(admin,"/game.html"),"/admin/index.html");
  assert.equal(adminDestination(admin,"/replay.html","?match=match-id"),"/admin/replay.html?match=match-id");
  assert.equal(adminDestination(admin,"/tournament.html","?id=cup-id"),"/admin/matches.html?tournament=cup-id");
  assert.equal(adminDestination(admin,"/admin/replay.html","?match=match-id"),null);
});
