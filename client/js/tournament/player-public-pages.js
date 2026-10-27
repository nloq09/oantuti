import {node,table,date} from './player-dom.js';
import {statusText,friendlyName} from './player-labels.js';
import {tournamentRequest} from './tournament-api.js';
import {createTournamentRefresh} from './refresh-controller.js';

export function mountPlayerPublicPage(root,t,section){
 let current=t,page=0,total=0,paintedPage=0;
 const notice=node('p','Đang tải…'),groups=node('div'),body=node('div'),pager=node('nav',undefined,'actions'),previous=node('button','← Trước'),next=node('button','Sau →'),label=node('span');
 notice.setAttribute('role','status');pager.setAttribute('aria-label',section==='participants'?'Phân trang người tham gia':'Phân trang bảng xếp hạng');pager.append(previous,label,next);root.append(notice,groups,body,pager);previous.disabled=next.disabled=true;
 if(section==='ranking'){root.prepend(node('p',t.swiss?`Swiss · Vòng ${t.swiss.currentRound}/${t.swiss.totalRounds}`:t.bestOf>1?'Điểm tính theo kết quả cặp đấu, không cộng riêng từng ván.':'Điểm tính theo kết quả mỗi cặp đấu.'));}
 let controller;const load=()=>controller.request(true,'query',{replace:true});
 async function read({signal,isCurrent,reason}){
  previous.disabled=next.disabled=true;notice.textContent='Đang tải…';
  try{
   const [data,groupData]=await Promise.all([tournamentRequest(`/${encodeURIComponent(current.id)}/player-pages/${section}?`+new URLSearchParams({page,size:20}),undefined,undefined,{}, {signal}),section==='ranking'&&current.hasGroups?tournamentRequest(`/${encodeURIComponent(current.id)}/player-extras/groups`,undefined,undefined,{}, {signal}):null]);
   if(!isCurrent())return;if(data.revision<current.revision||groupData&&groupData.revision!==data.revision){notice.textContent='Đang đồng bộ phiên bản giải…';return;}if(page&&page*data.size>=data.total){page=0;load();return;}
   if(reason!=='query'&&body.contains(document.activeElement)){notice.textContent='Đang giữ vị trí bàn phím. Chuyển focus ra ngoài bảng để cập nhật.';return;}
   const columns=section==='participants'?[['Tên',row=>row.displayName],['Chủ',row=>friendlyName(row.ownerName)],['Trạng thái',row=>statusText(row.status)]]:[['Hạng',row=>row.rank],['Tên',row=>row.displayName],['Điểm',row=>row.points],['Thắng',row=>row.wins],['Hòa',row=>row.draws],['Thua',row=>row.losses],...(current.swiss?[['Buchholz',row=>row.buchholz],['Miễn đấu ở vòng',row=>row.byes?.join(', ')||'—']]:[])];
   body.replaceChildren(table(columns,data.items));total=data.total;paintedPage=page;label.textContent=`Trang ${page+1} · ${total} ${section==='participants'?'người/bot':'vị trí'}`;notice.textContent='Cập nhật: '+date(new Date().toISOString());
   if(groupData){const {renderGroups}=await import('./competition-view.js');if(!isCurrent())return;groups.replaceChildren();renderGroups(groups,{groups:groupData.groups});}
  }catch(error){if(isCurrent()){page=paintedPage;notice.textContent='Không tải được dữ liệu. Nhấn Cập nhật để thử lại.';}}
  finally{if(isCurrent()){previous.disabled=paintedPage===0;next.disabled=(paintedPage+1)*20>=total;}}
 }
 previous.onclick=()=>{page=Math.max(0,paintedPage-1);load();};next.onclick=()=>{page=paintedPage+1;load();};const retry=node('button','Cập nhật');retry.onclick=load;pager.append(retry);
 controller=createTournamentRefresh(read,{tournamentId:t.id,connected:()=>root.isConnected});
 return{update(nextTournament){const changed=nextTournament.revision!==current.revision;current=nextTournament;if(changed)load();},stop:()=>controller.dispose()};
}
