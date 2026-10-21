import {node,table,date} from './player-dom.js';
import {tournamentRequest} from './tournament-api.js';
import {matchOpen,matchStatusText} from './match-execution.js';
import {createTournamentRefresh} from './refresh-controller.js';
import {readSnapshotKey} from './read-snapshot.js';
const preferences=new Map();
export function renderMatchPages(root,t,me,kind='schedule'){
 const saved=preferences.get(t.id+'/'+kind)||{},filters=node('div',undefined,'tournament-filters'),mine=node('input'),round=node('select'),stage=node('select'),order=node('select'),body=node('div'),pager=node('nav',undefined,'actions'),notice=node('p');
 let page=saved.page||0,lastKey=null,controller;mine.type='checkbox';mine.checked=!!saved.mine;mine.disabled=!me?.states?.length;if(mine.disabled)mine.checked=false;
 const ml=node('label','Chỉ trận của tôi');ml.prepend(mine);round.append(new Option('Tất cả vòng',''));for(const r of t.rounds)round.append(new Option('Vòng '+r.number,r.number));stage.append(new Option('Tất cả giai đoạn',''));for(const s of t.stages)stage.append(new Option('Giai đoạn '+s.number,s.number));order.append(new Option('Theo vòng','round'),new Option('Theo thời gian','time'));round.value=saved.round||'';stage.value=saved.stage||'';order.value=saved.order||'round';
 for(const [text,control]of [['Vòng đấu',round],['Giai đoạn',stage],['Sắp xếp',order]]){const label=node('label',text);label.append(control);filters.append(label);}filters.prepend(ml);notice.setAttribute('role','status');pager.setAttribute('aria-label','Phân trang lịch đấu');const prev=node('button','← Trước'),next=node('button','Sau →'),pageLabel=node('span');prev.disabled=next.disabled=true;pager.append(prev,pageLabel,next);root.append(filters,notice,body,pager);
 const load=(replace=false)=>controller.request(false,replace?'query':'manual',{replace});
 async function read({isCurrent,signal,reason}){
  preferences.set(t.id+'/'+kind,{page,mine:mine.checked,round:round.value,stage:stage.value,order:order.value});const query=new URLSearchParams({page,size:20,mine:mine.checked,order:order.value,replay:kind==='replays'});if(round.value)query.set('round',round.value);if(stage.value)query.set('stage',stage.value);body.setAttribute('aria-busy','true');if(!lastKey)notice.textContent='Đang tải lịch đấu…';
  try{const result=await tournamentRequest('/'+encodeURIComponent(t.id)+'/matches?'+query,undefined,undefined,{}, {signal});if(!isCurrent())return;if(result.tournamentId&&result.tournamentId!==t.id||result.revision<t.revision){notice.textContent='Đang đồng bộ trạng thái giải…';return;}if(page&&page*20>=result.total){page=0;load();return;}
   const key=readSnapshotKey([result.items,result.total]);if(key!==lastKey){if(reason!=='query'&&body.contains(document.activeElement)){notice.textContent='Đang giữ vị trí bàn phím; chuyển focus ra ngoài bảng để xem bản cập nhật.';return;}
    body.replaceChildren(table([['Vòng',m=>m.round],['Giai đoạn',m=>m.stageNumber],['Cặp đấu',m=>m.participantA.displayName+' vs '+m.participantB.displayName],['Trạng thái',matchStatusText],['Dự kiến',m=>date(m.scheduledAt)],['Mở',m=>matchOpen(m,t.id)||'Chưa có replay']],result.items));lastKey=key;
   }
   prev.disabled=page===0;next.disabled=(page+1)*20>=result.total;pageLabel.textContent=`Trang ${page+1} · ${result.total} trận`;notice.textContent=result.items.length?'Trạng thái cập nhật tự động.':'Chưa có trận phù hợp với bộ lọc.';
  }catch(error){if(!isCurrent())return;if(['UNAUTHENTICATED','TOURNAMENT_FORBIDDEN','ACCOUNT_DISABLED'].includes(error.message)){body.replaceChildren();pager.replaceChildren();lastKey=null;notice.textContent='Phiên đăng nhập hoặc quyền truy cập đã thay đổi. Hãy tải lại trang.';controller.dispose();return;}notice.textContent=lastKey?'Không cập nhật được lịch đấu; dữ liệu đang hiển thị có thể đã cũ.':'Không tải được lịch đấu. Hệ thống sẽ thử lại.';}
  finally{if(isCurrent())body.setAttribute('aria-busy','false');}
 }
 prev.onclick=()=>{page--;load(true);};next.onclick=()=>{page++;load(true);};for(const control of [mine,round,stage,order])control.onchange=()=>{page=0;lastKey=null;body.replaceChildren();load(true);};
 controller=createTournamentRefresh(read,{tournamentId:t.id,connected:()=>root.isConnected});return{refresh:load,stop:()=>controller.dispose(),update(next,nextMe){
  const changed=next.revision!==t.revision;
  for(const [select,before,after,label]of [[round,t.rounds,next.rounds,'Vòng '],[stage,t.stages,next.stages,'Giai đoạn ']])if(readSnapshotKey(before.map(r=>r.number))!==readSnapshotKey(after.map(r=>r.number))){const value=select.value;select.replaceChildren(new Option(select===round?'Tất cả vòng':'Tất cả giai đoạn',''),...after.map(r=>new Option(label+r.number,r.number)));select.value=after.some(r=>String(r.number)===value)?value:'';if(select.value!==value)page=0;}
  t=next;me=nextMe;mine.disabled=!me?.states?.length;if(mine.disabled&&mine.checked){mine.checked=false;page=0;lastKey=null;body.replaceChildren();load(true);}else if(changed)load();
 }};
}
