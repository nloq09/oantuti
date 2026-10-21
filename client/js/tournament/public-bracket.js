import {renderPlayerBracket} from './competition-view.js';
import {node} from './player-dom.js';
import {tournamentRequest} from './tournament-api.js';
import {createTournamentRefresh} from './refresh-controller.js';
import {readSnapshotKey} from './read-snapshot.js';

export function mountPublicBracket(root,t,me){
 const notice=node('p','Đang tải bracket…'),body=node('div');notice.setAttribute('role','status');root.append(notice,body);let view,lastKey;
 async function read({signal,isCurrent}){
  try{const data=await tournamentRequest('/'+encodeURIComponent(t.id)+'/bracket',undefined,undefined,{}, {signal});if(!isCurrent())return;
   if(data.id!==t.id||data.revision<t.revision){notice.textContent='Đang đồng bộ bracket…';return;}
   const key=readSnapshotKey([data.participants,data.bracket,data.series,data.matches]);
   if(key!==lastKey){if(body.contains(document.activeElement)){notice.textContent='Đang giữ vị trí bàn phím; chuyển focus ra ngoài bracket để xem bản cập nhật.';return;}
    if(view)view.update(data);else view=renderPlayerBracket(body,data,me);lastKey=key;
   }notice.textContent='Trạng thái cập nhật tự động.';
  }catch{if(isCurrent())notice.textContent=view?'Không cập nhật được bracket; dữ liệu đang hiển thị có thể đã cũ.':'Không tải được bracket. Hệ thống sẽ thử lại.';}
 }
 const controller=createTournamentRefresh(read,{tournamentId:t.id,connected:()=>root.isConnected});return{refresh:()=>controller.request(),stop:()=>controller.dispose()};
}
