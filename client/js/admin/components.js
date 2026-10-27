import { node, link, errorMessage } from "./admin-ui.js";
export function badge(value) {const element=node("span",value??"—","badge");element.dataset.status=value??"UNKNOWN";return element;}
export function table(columns,rows,actions) {
  const wrap=node("div",undefined,"table-scroll"), element=node("table"), head=node("thead"), heading=node("tr"), body=node("tbody");
  for(const [label] of columns){const th=node("th",label);th.scope="col";heading.append(th);}if(actions){const th=node("th","Thao tác");th.scope="col";heading.append(th);}head.append(heading);
  wrap.tabIndex=0;wrap.setAttribute("role","region");wrap.setAttribute("aria-label","Bảng dữ liệu · cuộn ngang để xem thêm cột");
  for(const row of rows){const tr=node("tr");for(const [,field,render] of columns){const cell=node("td");if(render){const value=render(row[field],row);cell.append(value instanceof Node?value:node("span",value??"—"));}else cell.textContent=row[field]??"—";tr.append(cell);}if(actions){const cell=node("td");actions(cell,row);tr.append(cell);}body.append(tr);}
  element.append(head,body);wrap.append(element);if(!rows.length)wrap.append(node("p","Không có dữ liệu phù hợp.","empty"));return wrap;
}
export function details(title,row,fields){const panel=node("section",undefined,"panel"),list=node("dl");panel.append(node("h2",title));for(const [label,key,render] of fields){const value=render?render(row[key]):row[key]??"—",description=node("dd");description.append(value instanceof Node?value:node("span",value??"—"));list.append(node("dt",label),description);}panel.append(list);return panel;}
export function confirmAction(title,run){
  const previousFocus=document.activeElement;
  const dialog=node("dialog"),form=node("form"),reason=node("textarea"),label=node("label","Lý do (bắt buộc)"),submit=node("button","Xác nhận"),cancel=node("button","Hủy"),error=node("p",undefined,"error");
  reason.required=true;reason.maxLength=500;label.append(reason);cancel.type="button";submit.type="submit";cancel.onclick=()=>dialog.close();
  const heading=node("h2",title),help=node("p","Thao tác được lưu audit; lịch sử và replay được giữ nguyên.","muted");heading.id="admin-confirm-title";help.id="admin-confirm-help";dialog.setAttribute("aria-labelledby",heading.id);dialog.setAttribute("aria-describedby",help.id);error.setAttribute("role","alert");
  form.append(heading,label,error,help,cancel,submit);dialog.append(form);document.body.append(dialog);
  form.onsubmit=async event=>{event.preventDefault();if(submit.disabled)return;if(!reason.value.trim()){reason.focus();return;}submit.disabled=true;cancel.disabled=true;try{await run(reason.value.trim());dialog.close();const notice=node("p","Đã thực hiện: "+title,"admin-toast");notice.setAttribute("role","status");document.body.append(notice);setTimeout(()=>notice.remove(),6000);}catch(failure){error.textContent=errorMessage(failure);}finally{submit.disabled=false;cancel.disabled=false;}};
  dialog.addEventListener("cancel",event=>{if(submit.disabled)event.preventDefault();});dialog.addEventListener("close",()=>{dialog.remove();if(previousFocus?.isConnected)previousFocus.focus();else document.querySelector("#admin-content")?.focus();},{once:true});dialog.showModal();reason.focus();
}
