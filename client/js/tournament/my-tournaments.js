import {sessionRequest} from "../api/auth-api.js";
import {getAccount} from "../account/account-session.js";
const filter=document.querySelector("#my-tournament-filter"),list=document.querySelector("#my-tournaments");let version=0;
async function load(){const request=++version;try{if(!await getAccount())return;const cups=await sessionRequest("/api/account/tournaments?filter="+encodeURIComponent(filter.value));if(request!==version)return;list.replaceChildren();for(const cup of cups){const row=document.createElement("p"),link=document.createElement("a");link.textContent=`${cup.name} · ${cup.status}`;link.href="/tournament-detail.html?id="+encodeURIComponent(cup.id);row.append(link);list.append(row);}if(!cups.length)list.textContent="Chưa có giải trong mục này.";}catch{if(request===version)list.textContent="Không thể tải lịch sử giải đấu.";}}
if(filter){filter.onchange=load;load();}
