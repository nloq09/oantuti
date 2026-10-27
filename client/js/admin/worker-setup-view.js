import {node,link} from './admin-ui.js';
import {field,input,select,copy} from './worker-ops-controls.js';
import {mountWorkerRegistration} from './worker-registration.js';
import {workerRunCommand,workerFirstRunCommand} from './worker-setup-commands.js';

export function renderWorkerSetup(root){
 root.replaceChildren();root.classList.add('worker-onboarding');
 const heading=node('div',undefined,'worker-setup-heading');
 heading.append(link('← Quản lý worker','/admin/workers.html'),node('h1','Thêm máy worker'),node('p','Làm lần lượt các bước dưới đây trên máy sẽ chạy bot. Máy có thể ở mạng khác; chỉ cần kết nối Internet tới server này.'));
 const steps=node('ol',undefined,'worker-setup-progress');
 for(const [index,title]of ['Chuẩn bị','Tải worker','Tải runtime','Đăng ký và chạy','Duyệt máy'].entries()){const item=node('li');item.append(link(title,'#worker-setup-step-'+(index+1)));steps.append(item);}
 root.append(heading,steps);
 function step(number,title,intro){const card=node('section',undefined,'panel worker-setup-step');card.id='worker-setup-step-'+number;card.append(node('p','Bước '+number,'worker-step-number'),node('h2',title),node('p',intro));root.append(card);return card;}
 function command(parent,label){const box=node('div',undefined,'worker-command'),head=node('div',undefined,'worker-command-heading'),title=node('strong',label),button=node('button','Sao chép lệnh'),pre=node('pre'),notice=node('p');button.type='button';button.setAttribute('aria-label','Sao chép · '+label);notice.setAttribute('role','status');head.append(title,button);box.append(head,pre,notice);parent.append(box);button.onclick=()=>copy(pre.textContent,notice);return{set(value,valid=true){pre.textContent=value;button.disabled=!valid;notice.textContent='';}};}
 const settings=node('div',undefined,'worker-setup-settings'),platform=select([['powershell','Windows · PowerShell'],['bash','Linux / macOS · Bash']]),name=input('text','LAB-PC-01'),matches=input('number',1),settingError=node('p'),advanced=node('details');
 platform.id='worker-setup-platform';name.id='worker-setup-name';name.maxLength=80;name.required=true;matches.min=1;matches.max=256;matches.required=true;
 advanced.append(node('summary','Số trận chạy đồng thời'),field('Số trận tối đa trên máy này',matches),node('small','Bắt đầu với 1 trận = 2 bot. Đây là giới hạn khai báo; server còn xét tài nguyên, mức tin cậy và quota.'));
 settingError.setAttribute('role','status');settings.append(field('Hệ điều hành của máy chạy bot',platform),field('Tên máy hiển thị trên trang quản trị',name),advanced,settingError);root.append(settings);
 const prepare=step(1,'Kiểm tra Java và Docker','Cài Java 21 trở lên. Windows/macOS: mở Docker Desktop, chờ Engine chạy và dùng Linux containers. Linux: Docker Engine phải chạy và tài khoản mở Terminal có quyền dùng Docker.');
 const installers=node('div',undefined,'worker-row-actions');installers.append(link('Tải Java 21','https://adoptium.net/temurin/releases/?version=21'),link('Docker Desktop','https://www.docker.com/products/docker-desktop/'),link('Docker Engine trên Ubuntu','https://docs.docker.com/engine/install/ubuntu/'));prepare.append(installers);
 command(prepare,'Kiểm tra máy').set("java -version\ndocker info --format '{{.OSType}}'");
 prepare.append(node('p','Kết quả cần có Java 21 hoặc mới hơn và dòng linux. Nếu Docker chưa chạy hoặc bị từ chối quyền, sửa ở bước này rồi mới tạo token. Worker không cần mở cổng inbound.'));
 const download=step(2,'Tải file và mở đúng thư mục','Tạo một thư mục riêng trên máy worker, ví dụ rps-worker trong thư mục cá nhân. Lưu rps-worker.jar vào đó.');
 const jar=link('Tải rps-worker.jar','/api/admin/workers/download');jar.className='button primary';jar.download='rps-worker.jar';download.append(jar,node('p','Chỉ admin tải được file. Nếu worker là máy của người khác, tải ở máy quản trị rồi chuyển riêng file JAR sang máy đó; không chia sẻ tài khoản admin hoặc thư mục worker-state.'),node('p','Windows: mở thư mục chứa JAR trong File Explorer, nhập powershell vào thanh địa chỉ rồi Enter. Linux/macOS: mở Bash và chuyển tới thư mục chứa JAR trước khi chạy các lệnh bên dưới.'));
 const list=command(download,'Xác nhận JAR trong thư mục');
 const images=step(3,'Tải runtime cho bot','Chạy trên chính máy worker, không phải trên VPS. Lần tải đầu có thể mất vài phút; đợi cả ba lệnh hoàn thành trước khi tạo token.');
 command(images,'Tải image Docker').set('docker pull python:3.12-slim\ndocker pull gcc:14\ndocker pull eclipse-temurin:21-jdk');
 images.append(node('p','Ba image tương ứng Python, C++ và Java. Worker không tự tải image; sau khi thêm image mới cần khởi động lại worker để cập nhật ngôn ngữ hỗ trợ.'));
 const register=step(4,'Tạo token và chạy lần đầu','Token dùng để đăng ký máy với server, không phải mật khẩu admin. Mặc định dành cho một máy và hết hạn sau 30 phút.');
 const token=mountWorkerRegistration(register);
 register.append(node('h3','Chạy lệnh sau trên máy worker'),node('p','Sao chép lệnh vào cửa sổ đã mở ở bước 2. Khi hiện Registration token, dán token vừa tạo rồi Enter. Lời nhắc sẽ che nội dung; lệnh sao chép không chứa token.'));
 const first=command(register,'Đăng ký và chạy lần đầu');
 const firstHint=node('p');register.append(firstHint,node('p','Giữ cửa sổ Java và Docker hoạt động. Sau khi đăng ký thành công, máy xuất hiện ở tab Máy worker; việc xuất hiện chưa đồng nghĩa đã được duyệt hoặc đã có trận phù hợp để chạy.'));
 const finish=step(5,'Duyệt máy và nhận trận tự động','Quay lại quản lý worker, tìm đúng tên máy đã đặt ở trên và thực hiện các bước sau.');
 const checklist=node('ol');for(const text of ['Mở thao tác của máy đang chờ duyệt → duyệt TRUSTED nếu đây là máy bạn tin cậy.','Kiểm tra ONLINE và có slot trống. DEGRADED thường cần kiểm tra lại Docker hoặc image ở bước 1–3.','Giữ agent chạy với --serve. Server tự giao trận phù hợp từ các giải; không cần thêm nhóm hoặc phân công giải.'])checklist.append(node('li',text));
 const manage=link('Mở danh sách máy worker','/admin/workers.html#workers'),links=node('div',undefined,'worker-row-actions');links.append(manage);finish.append(checklist,links,node('p','Remote hiện dành cho giải BOT_ONLY dùng submission LOCKED. Build, public/hidden tests và final validation vẫn cần Docker trên server trung tâm. ONLINE nhưng không có trận có thể do chưa có job phù hợp hoặc chưa được duyệt TRUSTED.'));
 const restart=node('section',undefined,'panel worker-setup-step');restart.id='restart';restart.append(node('h2','Chạy lại máy đã đăng ký'),node('p','Mở lại đúng thư mục chứa JAR và worker-state của máy đó. Giữ nguyên tên máy và cài đặt ở đầu trang; không tạo token mới, không chép worker-state sang máy khác.'));
 const again=command(restart,'Chạy lại · không cần token');restart.append(node('p','Ctrl+C để dừng. Khi chạy tiếp, dùng lại thư mục này. Máy ngủ/tắt hoặc Docker dừng sẽ làm worker mất khả năng nhận trận. Không chạy hai agent cùng thư mục state.'));root.append(restart);
 const help=node('details',undefined,'panel worker-setup-help');help.append(node('summary','Gặp lỗi hoặc cần cấu hình nâng cao?'));
 for(const [title,body]of [
 ['Không tìm thấy java / Docker','Kiểm tra cài đặt và mở lại Terminal; Docker Desktop cần Engine hoạt động. Trên Linux, kiểm tra quyền của chính tài khoản chạy worker.'],
 ['Unable to access jarfile','Bạn đang đứng sai thư mục hoặc tên file không phải rps-worker.jar. Kiểm tra bằng lệnh ở bước 2; đổi tên file có hậu tố (1) nếu trình duyệt tải trùng.'],
 ['Token hết hạn, đã dùng hết lượt hoặc đăng ký bị từ chối','Tạo token mới ở bước 4 khi thư mục worker-state chưa có danh tính hợp lệ. Token cũ quản lý/thu hồi tại tab Token đăng ký; không thể xem lại secret đã cấp.'],
 ['ONLINE nhưng chưa chạy bot','Kiểm tra đã duyệt, mức tin cậy, bài LOCKED, slot và job chờ. Lệnh phải có --serve; chế độ chỉ heartbeat không chạy trận liên tục.'],
 ['OFFLINE / DEGRADED','Giữ Terminal và Docker chạy, tắt chế độ ngủ nếu muốn máy tiếp tục làm việc. Máy cần Internet tới server; không thêm --allow-insecure-http cho domain public.'],
 ['Cache và khôi phục','Artifact cache mặc định 20 GiB trong worker-state/cache; --cache-mb đổi giới hạn. LRU chỉ dọn file không đang dùng, SHA-256 kiểm tra dữ liệu. --recover và --upload-pending chỉ dùng khi cần đối chiếu/gửi lại kết quả, không xóa state để ép chạy lại.'],
 ['Drain, vô hiệu hóa và thu hồi','Drain ngừng nhận trận mới và chờ trận đang chạy hoàn tất. Vô hiệu hóa hủy execution hiện tại; thu hồi credential là vĩnh viễn. Dùng các thao tác quản trị có lý do trên trang danh sách.']
 ])help.append(node('h3',title),node('p',body));
 root.append(help);
 function refreshCommands(){try{const options={origin:location.origin,platform:platform.value,name:name.value,maxMatches:matches.value};first.set(workerFirstRunCommand(options));again.set(workerRunCommand(options));settingError.textContent='';}catch{first.set('Nhập tên máy hợp lệ và số trận nguyên từ 1–256.',false);again.set('Kiểm tra cài đặt máy ở đầu trang.',false);settingError.textContent='Tên máy dài 1–80 ký tự, chỉ gồm chữ, số, khoảng trắng, dấu chấm, gạch dưới hoặc gạch ngang.';}const windows=platform.value==='powershell';list.set(windows?'Get-Location\nGet-Item .\\rps-worker.jar':'pwd\nls -l ./rps-worker.jar');firstHint.textContent=windows?'Windows PowerShell: giữ nguyên toàn bộ khối try/finally khi sao chép.':'Linux/macOS: chạy bằng Bash; khối (...) giữ token trong tiến trình con và xóa khỏi môi trường khi thoát.';}
 platform.onchange=name.oninput=matches.oninput=refreshCommands;refreshCommands();
 return{dispose:()=>token.dispose()};
}
