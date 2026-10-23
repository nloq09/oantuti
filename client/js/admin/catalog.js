import {node,link,date,message} from "./admin-shell.js";
import {adminRequest} from "./admin-api.js";
import {table} from "./components.js";
export async function listPage(root,{title,path,fields=[],columns,actions}){
  let params=new URLSearchParams(location.search),page=Math.max(0,Number(params.get("page"))||0),version=0;
  root.replaceChildren(node("h1",title));const form=node("form",undefined,"filters"),result=node("section",undefined,"panel");result.setAttribute("aria-live","polite");
  for(const [label,key,choices,type] of fields){const wrap=node("label",label),input=node(choices?"select":"input");input.name=key;
    if(choices){input.append(new Option("Tất cả",""));for(const choice of choices)input.append(new Option(Array.isArray(choice)?choice[1]:choice,Array.isArray(choice)?choice[0]:choice));}else input.type=type||"search";
    input.value=params.get(key)||"";wrap.append(input);form.append(wrap);}
  const reset=node("button","Xóa bộ lọc");reset.type="reset";form.append(node("button","Lọc / Cập nhật"),reset);root.append(form,result);
  async function load(){const generation=++version;result.setAttribute("aria-busy","true");result.replaceChildren(node("p","Đang tải…"));try{
    const query=new URLSearchParams(params);query.set("page",page);query.set("size","20");const data=await adminRequest(path+"?"+query);if(generation!==version)return;
    history.replaceState(null,"",location.pathname+"?"+query);result.replaceChildren(table(columns,data.items,actions));
    const footer=node("div",undefined,"pagination"),previous=node("button","← Trước"),next=node("button","Sau →");previous.disabled=page===0;next.disabled=(page+1)*data.size>=data.total;
    previous.onclick=()=>{page--;load();};next.onclick=()=>{page++;load();};footer.append(node("span",`Trang ${page+1} · ${data.total} kết quả`),previous,next);result.append(footer);
  }catch(error){if(generation===version){message(error,result);const retry=node("button","Thử tải lại");retry.onclick=load;result.append(retry);}}finally{if(generation===version)result.removeAttribute("aria-busy");}}
  form.onsubmit=event=>{event.preventDefault();params=new URLSearchParams(new FormData(form));page=0;load();};form.onreset=()=>{params=new URLSearchParams();page=0;setTimeout(load,0);};await load();
}
export function tabs(root,entries){
  const navigation=node("div",undefined,"admin-tabs");navigation.setAttribute("role","tablist");navigation.setAttribute("aria-label","Nội dung chi tiết");root.append(navigation);
  const buttons=[],panels=[];let initial="";try{initial=decodeURIComponent(location.hash.slice(1));}catch{ /* Ignore a malformed bookmarked tab. */ }
  function select(index){buttons.forEach((button,i)=>{button.setAttribute("aria-selected",String(index===i));button.tabIndex=index===i?0:-1;panels[i].hidden=i!==index;});history.replaceState(null,"",location.pathname+location.search+"#"+entries[index][0]);}
  entries.forEach(([key,label,render],i)=>{const button=node("button",label),panel=node("section",undefined,"panel");button.id="tab-"+key;panel.id="panel-"+key;button.setAttribute("role","tab");button.setAttribute("aria-controls",panel.id);panel.setAttribute("role","tabpanel");panel.setAttribute("aria-labelledby",button.id);button.onclick=()=>select(i);button.onkeydown=event=>{let target;if(event.key==="ArrowRight")target=(i+1)%entries.length;if(event.key==="ArrowLeft")target=(i+entries.length-1)%entries.length;if(event.key==="Home")target=0;if(event.key==="End")target=entries.length-1;if(target!==undefined){event.preventDefault();select(target);buttons[target].focus();}};buttons.push(button);panels.push(panel);navigation.append(button);root.append(panel);render(panel);});
  select(Math.max(0,entries.findIndex(([key])=>key===initial)));
}
export const periodFields=[["Từ ngày (UTC)","from",null,"date"],["Đến ngày (UTC)","to",null,"date"]];
