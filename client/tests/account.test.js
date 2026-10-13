import test from "node:test";
import assert from "node:assert/strict";
import {accountProfile,updateProfile,changePassword,adminUsers,setAccountEnabled} from "../js/api/account-api.js";

test("profile/password/admin requests use session cookies and CSRF without accepting ownership or role fields",async()=>{
  const original=globalThis.fetch,calls=[];
  globalThis.fetch=async(url,options)=>{
    calls.push({url,...options});return {ok:true,json:async()=>url.endsWith("csrf")?{headerName:"X-CSRF-TOKEN",token:"csrf"}:{}};
  };
  try {
    await accountProfile();await updateProfile("Player");await changePassword("old password","new password");
    await adminUsers(2,10);await setAccountEnabled("user-id",false);
    assert.equal(calls[0].url,"/api/account/profile");
    assert.equal(calls[2].method,"PATCH");assert.deepEqual(JSON.parse(calls[2].body),{displayName:"Player"});
    assert.equal(calls[2].headers["X-CSRF-TOKEN"],"csrf");
    assert.deepEqual(JSON.parse(calls[4].body),{currentPassword:"old password",newPassword:"new password"});
    assert.equal(calls[5].url,"/api/admin/users?page=2&size=10");
    assert.equal(calls[7].url,"/api/admin/users/user-id/disable");assert.equal(calls[7].method,"POST");
    assert.ok(calls.every(call=>call.credentials==="same-origin" && !call.url.includes("old password") && !call.url.includes("new password")));
  } finally {globalThis.fetch=original;}
});
