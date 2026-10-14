import test from "node:test";
import assert from "node:assert/strict";
import { adminAccess } from "../js/admin/admin-auth.js";
import { accountLinks } from "../js/account/account-ui.js";
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
  assert.ok(!labels({role:"USER"},false).includes("Quản trị"));
  assert.ok(!labels({role:"USER"},false).includes("Đăng nhập"));
});
