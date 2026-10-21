import {node} from './player-dom.js';
export function renderDeadlineInfo(root,t){
 if(!t.timeline)return;
 const box=node('aside',undefined,'organizer-deadline-info');box.append(node('h2','Sau khi hết giờ lập trình'));
 const list=node('ul');for(const text of [t.timeline.deadlinePolicy==='REQUIRE_EXPLICIT_SUBMIT'?'✓ Giữ phiên bản người chơi đã bấm nộp đúng hạn.':'✓ Khóa phiên bản READY mới nhất trước hạn.','✓ Không thể sửa bài sau khi khóa.','✓ Bài được đưa sang kiểm thử chính thức.'])list.append(node('li',text));box.append(list);root.append(box);
}
