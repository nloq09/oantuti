# Cài đặt và vận hành worker

[Mục lục](../README.md) · [Giao thức worker](../technical/worker-protocol.md) · [Khôi phục](../technical/backup-recovery.md)

Remote worker là máy chạy trận bot trong Docker và kết nối ra server trung tâm (Central) qua HTTPS. Server tự phân trận cho máy phù hợp, không cần tạo nhóm worker hoặc gán từng máy cho từng giải. Máy worker không cần mở cổng inbound.

Remote hiện phục vụ trận giải **BOT_ONLY** dùng bài coding đã **LOCKED**. Build, kiểm thử công khai, kiểm thử ẩn và Final Validation vẫn chạy trên Central; thêm worker không tăng sức chứa của các hàng đợi này.

## Chọn đúng nơi đăng ký máy

- **Worker dùng chung:** Admin mở **Worker → + Thêm worker** (`/admin/worker-setup.html`). Sau khi được Admin duyệt, máy có thể nhận trận phù hợp từ nhiều giải. Làm theo các bước bên dưới.
- **Worker riêng của một giải:** chủ giải mở **Quản lý giải → Worker riêng → Thêm worker cho giải**. Máy được cấp quyền trong đúng giải khi dùng token, không cần Admin duyệt lại. Làm theo [hướng dẫn dành cho chủ giải](../organizer/tournament-operation.md#worker-riêng-do-chủ-giải-thêm).

Phạm vi do server gắn với token và danh tính máy, không do tên máy hay tham số dòng lệnh quyết định. Một máy vật lý có thể chạy các danh tính riêng, nhưng mỗi danh tính phải dùng thư mục state riêng; tổng tài nguyên vẫn dùng chung phần cứng.

## Các phần trong trang quản trị

Trang **Worker** (`/admin/workers.html`) có ba tab:

- **Máy worker:** trạng thái, tài nguyên, slot, chi tiết, duyệt và điều khiển từng máy. Máy riêng được đánh dấu **Chỉ giải**.
- **Token đăng ký:** thời hạn, lượt dùng, phạm vi, máy đã đăng ký và thu hồi token. Tab này không tạo thêm một form cấp token; muốn cấp token dùng chung, mở **+ Thêm worker**.
- **Điều phối:** cấu hình nâng cao theo giải và xem sức chứa hiện tại. Luồng thông thường không cần thao tác ở đây.

## 1. Chuẩn bị máy sẽ chạy bot

Cài Java 21 trở lên và Docker Desktop (Windows/macOS, chọn Linux containers) hoặc Docker Engine (Linux). Mở PowerShell/Bash và kiểm tra:

```sh
java -version
docker version
docker info --format '{{.OSType}}'
```

Phải thấy cả Docker client/server và OS `linux`. Nếu thiếu quyền Docker trên Linux, cấu hình quyền cho tài khoản chạy agent theo chính sách của máy; không chạy bot ngoài sandbox để chữa lỗi.

Tải image trước, chỉ lấy ngôn ngữ cần phục vụ:

```sh
docker pull python:3.12-slim
docker pull gcc:14
docker pull eclipse-temurin:21-jdk
```

Không cần clone toàn bộ project hay cài PostgreSQL trên worker. JAR dùng chung game core với Central; dùng bản được phát hành cùng server.

## 2. Tải JAR và tạo thư mục riêng

Trang **Thêm máy worker** có năm bước: **Chuẩn bị → Tải worker → Tải runtime → Đăng ký và chạy → Duyệt máy**. Không có bước phân nhóm hoặc phân công giải.

Bấm tải `rps-worker.jar`, chuyển **chỉ file JAR** sang máy chạy bot, ví dụ thư mục `rps-worker`. Mở terminal tại thư mục đó:

```powershell
# Windows PowerShell: thay đường dẫn bằng thư mục bạn vừa tạo
Set-Location C:\rps-worker
Get-Item .\rps-worker.jar
```

```bash
# Linux/macOS Bash
mkdir -p ~/rps-worker
cd ~/rps-worker
ls -l rps-worker.jar
```

Chuyển JAR vào thư mục trước khi chạy `Get-Item`/`ls`. Mỗi máy dùng một `worker-state` riêng; không gửi thư mục này cho người khác hoặc đưa lên Git.

## 3. Tạo token và chạy lần đầu

Tạo token **ngay trong trang Thêm worker**, sau khi máy đã sẵn sàng. UI mặc định một máy/30 phút; tùy chọn nâng cao cho 1–100 lượt, 1–1440 phút. Nếu gọi API trực tiếp mà bỏ lifetimeMinutes, mặc định backend là 15 phút. Token chỉ hiện một lần; copy có chủ ý và giữ kín.

Tab **Token đăng ký** để xem thời hạn/lượt dùng/máy đã dùng và thu hồi, không có form tạo token thứ hai. Xóa secret khỏi trang không đồng nghĩa thu hồi token ở server.

Chọn đúng hệ điều hành, tên máy và số trận trên trang rồi dùng lệnh được tạo. Ví dụ với Central `https://game.example.com`, một trận/hai bot:

```powershell
$env:WORKER_REGISTRATION_TOKEN = [System.Net.NetworkCredential]::new('', (Read-Host 'Registration token' -AsSecureString)).Password
try {
    java -jar rps-worker.jar --central=https://game.example.com --name='LAB-PC-01' --state-dir=./worker-state --max-matches=1 --max-bots=2 --serve
} finally {
    Remove-Item Env:WORKER_REGISTRATION_TOKEN -ErrorAction SilentlyContinue
}
```

```bash
(
  read -r -s -p 'Registration token: ' WORKER_REGISTRATION_TOKEN
  printf '\n'
  export WORKER_REGISTRATION_TOKEN
  java -jar rps-worker.jar --central=https://game.example.com --name='LAB-PC-01' --state-dir=./worker-state --max-matches=1 --max-bots=2 --serve
)
```

Thay hostname bằng Central thật, hoặc dùng nguyên khối lệnh do trang tạo theo domain hiện tại. Dán token vào lời nhắc, không thay token vào dòng Java hoặc URL. Giữ terminal chạy để agent nhận việc. `Ctrl+C` dừng agent. HTTP chỉ được opt-in bằng `--allow-insecure-http` ở localhost/IP LAN riêng; Internet dùng HTTPS.

Registration token chỉ dùng khi đăng ký lần đầu. Sau đó agent dùng credential lưu trong state-dir. Token hết hạn hoặc bị thu hồi không tự thu hồi máy đã đăng ký; muốn chặn máy đó phải dùng **Thu hồi quyền** trên máy.

## 4. Duyệt máy và tự động nhận trận

1. Trở lại **Worker → Máy worker**, mở máy vừa đăng ký. Máy dùng token chung ban đầu PENDING_APPROVAL/UNTRUSTED; kết nối được chưa có nghĩa được nhận bài riêng tư.
2. Duyệt và chọn TRUSTED hoặc COMMUNITY theo mức tin cậy thực tế. TRUSTED nghĩa là chấp nhận máy nhận artifact riêng của người thi đấu; máy chủ vẫn có thể đọc byte artifact dù bot chạy trong container.
3. Kiểm tra Docker, ngôn ngữ, protocol, heartbeat, CPU/RAM và slot thực nhận.
4. Giữ agent chạy với `--serve`. Giải mới mặc định **AUTO**: Central tự đưa trận bot có bài coding đã khóa vào hàng đợi chung khi có worker TRUSTED online hỗ trợ cả hai ngôn ngữ. Không cần tạo nhóm hoặc gán máy cho từng giải.
5. Máy dùng chung còn slot nhận trận phù hợp từ các giải. Máy riêng chỉ nhận trận của giải đã gắn. Khi các máy tương thích đều bận, trận remote chờ slot; giới hạn máy, CPU/RAM, số bot processes, lease, kiểm hash và kiểm replay vẫn được áp dụng. FIFO có ưu tiên cache giới hạn; bài chờ lâu được ưu tiên để tránh đói việc.

AUTO chỉ quyết định nơi chạy trước khi giao việc: chưa có worker tương thích thì trận chạy local. Khi đã vào hàng đợi remote, trận giữ quyền sở hữu remote kể cả lúc máy mất kết nối; recovery/thử lại xử lý theo lease, không chạy thêm bản local. Build, public/hidden tests và final validation vẫn chạy trên Central.

Central chạy đủ giới hạn trận local vẫn tiếp tục xét gửi trận cho worker remote bằng số tác vụ điều phối có giới hạn. Nếu trận cần chạy local mà chưa có slot, nó tiếp tục chờ trong queue; không tăng trần local hoặc chạy thêm một bản trận để né giới hạn.

### Chế độ điều phối của giải

- **AUTO** là mặc định khi giải chưa lưu policy riêng. Với trận bot coding hợp lệ, Central ưu tiên hàng đợi remote khi có máy được duyệt, còn kết nối, đúng phạm vi, đạt mức tin cậy và hỗ trợ cả hai ngôn ngữ. Không có máy tương thích tại lúc quyết định thì chạy local. Máy tương thích đang bận vẫn được tính là có máy; trận sẽ chờ slot.
- **LOCAL:** chạy trận trên Central. Thêm worker riêng không tự thay đổi chế độ này.
- **REMOTE_REQUIRED:** chỉ chạy qua worker phù hợp; thiếu máy hoặc slot thì chờ. Chế độ này chỉ áp dụng cho giải bot coding được hỗ trợ.

Chỉ Admin đổi policy, mức tin cậy tối thiểu và trần trận remote trong tab **Điều phối**. Server chỉ cho đổi khi giải ở DRAFT/REGISTRATION, chưa có lịch trận và chưa có execution job; thao tác cần lý do và revision mới nhất. Sức chứa hiển thị là ảnh chụp hiện tại, không giữ chỗ cho giải.

Policy LOCAL/REMOTE_REQUIRED đã lưu từ bản cũ được giữ nguyên khi nâng cấp. Chỉ chuyển sang AUTO nếu giải còn được phép cấu hình. Dữ liệu nhóm cũ được giữ để tra lịch sử; nhóm không còn quyết định máy nhận trận.

## Worker riêng của chủ giải

Chủ giải có trang **Worker riêng** trong Quản lý giải. Token gắn với đúng giải trên Central; đăng ký bằng token đó tạo danh tính APPROVED/TRUSTED với phạm vi cố định. Claim, bắt đầu trận, tải artifact, gia hạn lease, recovery và gửi kết quả đều kiểm phạm vi. Máy không thể đổi sang giải khác hoặc pool chung bằng tham số.

Admin vẫn có thể giới hạn tài nguyên, ngừng nhận trận, vô hiệu hóa hoặc thu hồi máy riêng. Chủ giải chỉ quản lý máy và token thuộc giải mình; không được tạo token dùng chung, duyệt máy khác hoặc đổi capacity cấp hệ thống. Mỗi giải tối đa 20 máy chưa thu hồi và 5 token còn hiệu lực; mỗi token dùng một lần, hạn 30 phút.

Worker riêng không làm giải độc quyền sử dụng máy riêng: worker dùng chung phù hợp vẫn có thể nhận trận của giải đó. Không có nút đổi phạm vi danh tính đã đăng ký; muốn đăng ký danh tính khác phải dùng token mới và state-dir riêng.

## 5. Chạy lại và kiểm tra tình trạng

Chạy lại từ **cùng thư mục**, dùng **cùng state-dir**, không cần token mới. Ví dụ này dành cho máy dùng chung:

```sh
java -jar rps-worker.jar --central=https://game.example.com --name='LAB-PC-01' --state-dir=./worker-state --max-matches=1 --max-bots=2 --serve
```

Máy riêng dùng đường dẫn do trang của giải sinh ra, dạng `./worker-state-tournament-<id-giai>`. Mở đúng thư mục chứa state đó rồi sao chép mục **Chạy lại**; không thay bằng `./worker-state`. Không chạy hai agent cùng state-dir.

| Tình trạng | Ý nghĩa và kiểm tra |
| --- | --- |
| STARTING | Chờ capabilities/heartbeat của lần chạy hiện tại. |
| ONLINE | Có heartbeat và khả năng nhận việc; vẫn cần duyệt/trust/ngôn ngữ phù hợp. |
| BUSY | Không báo slot trống. |
| DEGRADED | Docker hoặc runtime chưa sẵn sàng; pull image rồi khởi động lại. |
| OFFLINE | Không nhận heartbeat trong khoảng 30 giây; kiểm tra mạng, terminal, sleep máy. |
| DRAINING / DRAINED | Ngừng nhận việc mới; DRAINED là nhãn khi việc đã nhận không còn. |
| DISABLED | Admin đã vô hiệu; không tiếp tục gửi việc cho máy. |

Heartbeat mặc định 5 giây. Slot thực tế lấy từ khai báo, ngân sách CPU/RAM, giới hạn Admin, tài nguyên đang dùng và reservation Central. `--max-matches=8` không tự làm máy có tám slot. Bắt đầu một slot, đo bằng `java -jar rps-worker.jar benchmark --state-dir=./benchmark-state` khi không chạy agent, rồi thử giải nhỏ trước khi tăng.

## Dừng, thu hồi và phục hồi

**Drain** cho việc đã nhận hoàn tất. **Disable** fence execution hiện tại để recovery/thử lại có giới hạn. **Resume** từ draining/disabled yêu cầu khởi động lại agent. **Revoke** thu hồi credential vĩnh viễn; muốn dùng lại phải đăng ký danh tính mới và duyệt lại. Các thao tác cần lý do/revision hiện tại.

Với danh tính mới từ token chủ giải, quyền được cấp trong phạm vi giải ngay khi đăng ký; danh tính dùng chung vẫn cần Admin duyệt. Thu hồi không chuyển máy riêng thành máy dùng chung. Muốn dừng có trật tự, dùng **Ngừng nhận trận**, chờ hết trận đang giữ rồi mới đóng terminal.

Cache mặc định 20 GiB tại worker-state/cache, kiểm SHA-256 khi tải và tái sử dụng; đổi bằng `--cache-dir`, `--cache-mb`. Không chia sẻ cache chứa artifact riêng tư. Khi mất mạng sau trận, giữ state và dùng recovery/upload-pending theo [hướng dẫn khôi phục](../technical/backup-recovery.md); không xóa state để tránh gửi trùng hoặc bỏ kết quả chưa nhận.

## Máy đã online nhưng chưa chạy trận

Kiểm tra theo thứ tự:

1. Agent đang chạy với `--serve`, Docker hoạt động và máy không ngủ. Thêm image mới cần khởi động lại agent để cập nhật ngôn ngữ hỗ trợ.
2. Máy dùng chung đã được duyệt và đạt mức tin cậy của giải; máy riêng thuộc đúng giải. Máy riêng của giải A không bổ sung sức chứa cho giải B.
3. Giải có trận bot coding với bài LOCKED, đang thi đấu và có trận đủ điều kiện nhận việc. Build hoặc kiểm thử đang chờ vẫn thuộc hàng đợi Central.
4. Admin chưa đặt LOCAL, chưa drain/disable/revoke máy; kiểm tra slot thực nhận, tài nguyên và trần trận của giải. Tăng khai báo trên dòng lệnh không bỏ qua các giới hạn này.

`Unable to access jarfile` thường do sai thư mục hoặc tên file có hậu tố tải trùng. Token bị từ chối có thể đã hết hạn, hết lượt hoặc bị thu hồi; token riêng còn bị chặn khi giải đã kết thúc, bị hủy, đưa vào trash hoặc tài khoản chủ giải không còn ACTIVE. Nếu đã có danh tính hợp lệ, dùng lại state-dir thay vì đăng ký lại.

## Nguồn đối chiếu

- [Luồng setup](../../client/js/admin/worker-setup-view.js), [lệnh theo OS](../../client/js/admin/worker-setup-commands.js), [token](../../client/js/admin/worker-registration.js).
- [Identity/API](../../server/src/main/java/com/rpsbattle/api/controller/RemoteWorkerController.java), [telemetry](../../server/src/main/java/com/rpsbattle/worker/remote/WorkerTelemetryService.java), [control](../../server/src/main/java/com/rpsbattle/worker/remote/WorkerControlService.java).
- [Điều phối và quản lý máy](../../server/src/main/java/com/rpsbattle/api/controller/WorkerOperationsController.java), [agent](../../worker/src/main/java/com/rpsbattle/remote/WorkerApplication.java).
- [Trang worker của chủ giải](../../client/js/tournament/tournament-worker-view.js), [API theo giải](../../server/src/main/java/com/rpsbattle/api/controller/TournamentWorkerController.java), [quyền và hạn mức](../../server/src/main/java/com/rpsbattle/worker/remote/TournamentWorkerService.java), [phạm vi thực thi](../../server/src/main/java/com/rpsbattle/worker/remote/WorkerScope.java).
