import {node,date,message,errorMessage} from "./admin-ui.js";
import {details} from "./components.js";
import {accountProfile,updateProfile,changePassword} from "../api/account-api.js";
import {validatePassword} from "../account/account-ui.js";
export async function mountProfile(root){
  const profile=await accountProfile();root.replaceChildren(node("h1","Hồ sơ quản trị"),details("Tài khoản",profile,[["Tên đăng nhập","username"],["Email","email"],["Vai trò","role"],["Ngày tạo","createdAt",date],["Đăng nhập gần nhất","lastLoginAt",date]]));
  function form(title,fields,save){
    const panel=node("section",undefined,"panel"),form=node("form",undefined,"admin-form"),status=node("p"),submit=node("button","Lưu thay đổi");status.setAttribute("role","status");panel.append(node("h2",title),form);root.append(panel);
    for(const [name,label,type,autocomplete]of fields){const wrap=node("label",label),input=node("input");input.name=name;input.type=type;input.autocomplete=autocomplete;input.required=true;if(name==="displayName"){input.maxLength=60;input.value=profile.displayName;}wrap.append(input);form.append(wrap);}
    form.append(status,submit);form.onsubmit=async event=>{event.preventDefault();if(submit.disabled)return;submit.disabled=true;status.textContent="Đang lưu…";try{await save(new FormData(form));status.textContent="Đã lưu thay đổi.";}catch(error){status.textContent=errorMessage(error);}finally{submit.disabled=false;if(title==="Đổi mật khẩu")form.reset();}};return panel;
  }
  form("Tên hiển thị",[["displayName","Tên hiển thị","text","nickname"]],async data=>{const result=await updateProfile(data.get("displayName"));document.querySelector(".admin-account span").textContent=result.displayName+" · ADMIN";});
  const password=form("Đổi mật khẩu",[["currentPassword","Mật khẩu hiện tại","password","current-password"],["newPassword","Mật khẩu mới","password","new-password"],["confirmation","Xác nhận mật khẩu mới","password","new-password"]],async data=>{validatePassword(data.get("newPassword"),data.get("confirmation"));await changePassword(data.get("currentPassword"),data.get("newPassword"));location.assign("/login.html?next=%2Fadmin%2Fprofile.html&passwordChanged=1");});
  password.insertBefore(node("p","Mật khẩu mới cần ít nhất 12 ký tự, tối đa 72 byte UTF-8. Thay đổi sẽ đăng xuất tất cả phiên hiện tại.","muted"),password.querySelector("form"));
}
