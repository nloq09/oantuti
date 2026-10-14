import { node, link } from "./admin-shell.js";
export async function mountViewer(page,root) {
  const replay=page==="replay.html",query=new URLSearchParams(location.search),id=query.get(replay?"match":"spectate");
  if(!id)throw new Error("Thiếu mã trận cần xem.");
  root.replaceChildren(node("h1",replay?"Replay trận đấu":"Theo dõi trận đấu"),link("← Danh sách trận","/admin/matches.html"));
  // Reuse the existing static board markup and read-only controllers; never construct a player controller.
  for(const path of ["/css/global.css","/css/game.css",...(replay?["/css/replay.css"]:[])]){
    const style=document.createElement("link");style.rel="stylesheet";style.href=path;
    await new Promise((resolve,reject)=>{style.onload=resolve;style.onerror=()=>reject(new Error("Không tải được giao diện bàn cờ."));document.head.insertBefore(style,document.querySelector('link[href="/css/admin.css"]'));});
  }
  const response=await fetch(replay?"/replay.html":"/game.html",{cache:"no-store"});if(!response.ok)throw new Error("Không tải được bố cục bàn cờ.");
  const template=new DOMParser().parseFromString(await response.text(),"text/html").querySelector("main"),section=node("section",undefined,template.className);
  template.querySelectorAll("nav").forEach(element=>element.remove());section.append(...template.childNodes);root.append(section);document.body.classList.add("admin-viewer-page");
  if(replay)await import("../replay/replay-controller.js?v=diagonal");
  else {
    for(const selector of ["#placement-panel","#playing-controls"])section.querySelector(selector).hidden=true;
    const {SpectatorController}=await import("../spectator/spectator-controller.js?v=diagonal");new SpectatorController(id,{admin:true});
  }
}
