import { node, link, date, message } from "./admin-shell.js";
import { adminRequest } from "./admin-api.js";
import { table, badge, details, confirmAction } from "./components.js";
const userColumns=[["Người dùng","display_name"],["Username","username"],["Email","email"],["Role","role",badge],["Trạng thái","status",badge],["Trận","matches"],["Bot","bots"],["Ngày tạo","created_at",date],["Đăng nhập gần nhất","last_login_at",date]];
function filters(kind,initial){
  const form=node("form",undefined,"filters");
  const fields=kind==="users"?[["Tìm username, email, tên","q"],["Role","role",["USER","ADMIN"]],["Trạng thái","status",["ACTIVE","DISABLED"]]]:[];
  fields.push(["Từ ngày (UTC)","from",null,"date"],["Đến ngày (UTC)","to",null,"date"]);
  for(const [title,key,choices,type] of fields){const label=node("label",title),input=node(choices?"select":"input");input.name=key;if(choices){input.append(new Option("Tất cả",""));for(const choice of choices)input.append(new Option(choice,choice));}else input.type=type??"search";input.value=initial.get(key)??"";label.append(input);form.append(label);}
  const button=node("button","Lọc"),reset=node("button","Xóa bộ lọc");reset.type="reset";form.append(button,reset);return form;
}
async function list(kind,root){
  let params=new URLSearchParams(location.search),page=Number(params.get("page"))||0,revision=0;
  root.replaceChildren(node("h1","Người dùng"));const form=filters(kind,params),result=node("section",undefined,"panel");root.append(form,result);
  async function load(){const request=++revision;result.setAttribute("aria-busy","true");result.replaceChildren(node("p","Đang tải…","muted"));try{
    const query=new URLSearchParams(params);query.set("page",String(page));query.set("size","20");const data=await adminRequest(kind+"?"+query);if(request!==revision)return;
    history.replaceState(null,"",location.pathname+"?"+query);result.replaceChildren(table(userColumns,data.items,(cell,row)=>cell.append(link("Chi tiết","/admin/user-detail.html?id="+encodeURIComponent(row.id)))));
    const footer=node("div",undefined,"pagination"),previous=node("button","← Trước"),next=node("button","Sau →");previous.disabled=page===0;next.disabled=(page+1)*data.size>=data.total;previous.onclick=()=>{page--;load();};next.onclick=()=>{page++;load();};footer.append(node("span",`Trang ${page+1} · ${data.total} kết quả`),previous,next);result.append(footer);
  }catch(error){if(request===revision)message(error,result);}finally{if(request===revision)result.removeAttribute("aria-busy");}}
  form.onsubmit=event=>{event.preventDefault();params=new URLSearchParams(new FormData(form));page=0;load();};form.onreset=()=>{params=new URLSearchParams();page=0;setTimeout(load,0);};await load();
}
function related(root,title,rows,columns,destination){const section=node("section",undefined,"panel");section.append(node("h2",title),table(columns,rows,destination?(cell,row)=>cell.append(link("Mở",destination(row))):null));root.append(section);}
async function userDetail(root){
  const id=new URLSearchParams(location.search).get("id");if(!id)throw new Error("Thiếu mã người dùng.");
  const data=await adminRequest("users/"+encodeURIComponent(id));root.replaceChildren(node("h1","Chi tiết người dùng"),link("← Danh sách","/admin/users.html"));
  root.append(details("Tài khoản",data,userColumns),details("Định danh",data,[["ID","id"],["Cập nhật","updated_at",date]]));
  const actions=node("div",undefined,"actions"),button=node("button",data.status==="ACTIVE"?"Vô hiệu hóa tài khoản":"Mở lại tài khoản");
  button.onclick=()=>confirmAction(button.textContent+" · "+data.username,async reason=>{await adminRequest("users/"+encodeURIComponent(id)+"/"+(data.status==="ACTIVE"?"disable":"enable"),{reason});await userDetail(root);});actions.append(button);root.append(actions,node("p","Đổi role chưa được backend hỗ trợ. Mở khóa không khôi phục phiên đăng nhập cũ.","muted"));
  related(root,"20 trận gần đây",data.recent_matches,[["ID","id"],["Mode","mode"],["Trạng thái","status",badge],["Kết quả","outcome"],["Ngày","created_at",date]],row=>"/admin/match-detail.html?id="+encodeURIComponent(row.id));
  related(root,"20 bot gần đây",data.owned_bots,[["Tên","name"],["Ngôn ngữ","language"],["Trạng thái","status",badge]],row=>"/admin/bot-detail.html?id="+encodeURIComponent(row.id));
  related(root,"20 giải gần đây",data.tournaments,[["Tên","name"],["Trạng thái","status",badge]],row=>"/tournament.html?id="+encodeURIComponent(row.id));
  related(root,"50 thao tác quản trị gần đây",data.audit,[["Thao tác","action"],["Người thực hiện","actor"],["Lý do","reason"],["Trước","before_value"],["Sau","after_value"],["Thời gian","occurred_at",date]]);
}
export async function mountDirectory(page,root){if(page==="users.html")return list("users",root);if(page==="user-detail.html")return userDetail(root);root.replaceChildren(node("h1","Trang chưa được triển khai"));}
