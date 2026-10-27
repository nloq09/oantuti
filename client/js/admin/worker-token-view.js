import {node,date} from './admin-ui.js';
import {workerTokens,revokeWorkerToken} from './worker-api.js';
import {table,confirmAction} from './components.js';

export async function renderWorkerTokens(root,{detail=()=>{}}={}){
 const data=await workerTokens();root.replaceChildren();
 root.append(node('h2','Token đăng ký'),node('p','Theo dõi lượt sử dụng, thời hạn và thu hồi token đã cấp. Để đăng ký máy mới, chọn “+ Thêm worker” ở đầu trang; các bước tải file, tạo token và chạy nằm trong cùng một hướng dẫn.'));
 if(!data.items.some(t=>t.status==='ACTIVE'))root.append(node('p','Không có token đăng ký đang hoạt động.'));
 const labels={ACTIVE:'Còn hiệu lực',USED:'Đã dùng hết lượt',EXPIRED:'Đã hết hạn',REVOKED:'Đã thu hồi'};
 root.append(table([['Tạo lúc','createdAt',date],['Hết hạn','expiresAt',date],['Lượt còn','remainingUses'],['Trạng thái','status',status=>labels[status]||status],['Người tạo','createdBy'],['Phạm vi','tournamentId',id=>id?'Chỉ một giải':'Pool chung'],['Đã dùng bởi','usedBy',items=>items?.map(w=>w.name).join(', ')||'Chưa ghi nhận'],['Dùng gần nhất','lastUsed',date]],data.items,(cell,row)=>{
  if(row.status==='ACTIVE'){const revoke=node('button','Thu hồi');revoke.onclick=()=>confirmAction('Thu hồi token đăng ký',async()=>{await revokeWorkerToken(row.id);await renderWorkerTokens(root,{detail});});cell.append(revoke);}
  for(const worker of row.usedBy||[]){const open=node('button','Xem '+worker.name);open.onclick=()=>detail(worker.id);cell.append(open);}
 }));
}
