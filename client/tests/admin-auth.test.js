import test from "node:test";
import assert from "node:assert/strict";
import { adminAccess } from "../js/admin/admin-auth.js";
test("admin guard denies guests ordinary users and disabled administrators", () => {
  assert.equal(adminAccess(null), "LOGIN");
  assert.equal(adminAccess({ role: "USER", status: "ACTIVE" }), "DENY");
  assert.equal(adminAccess({ role: "ADMIN", status: "DISABLED" }), "DENY");
  assert.equal(adminAccess({ role: "ADMIN", status: "ACTIVE" }), "ALLOW");
});
