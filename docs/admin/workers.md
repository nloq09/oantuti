# Thêm và vận hành Remote Worker

[Mục lục](../README.md) · [Giao thức worker](../technical/worker-protocol.md) · [Khôi phục](../technical/backup-recovery.md)

Remote worker là máy khác chạy trận bot trong Docker và kết nối ra Central qua HTTPS. Không cần mở cổng inbound trên máy worker. Hiện chỉ nhận trận giải **BOT_ONLY** dùng bài **LOCKED**; build/public tests/Final Validation vẫn chạy trên Central.

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

Admin mở **Worker → + Thêm worker**, tại `/admin/worker-setup.html`. Trang có năm bước: chuẩn bị, tải JAR, tải runtime, đăng ký/chạy lần đầu, duyệt và phân công.

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

Thay hostname bằng Central thật; dán token vào lời nhắc, không thay token vào dòng Java hoặc URL. Giữ terminal chạy để agent nhận việc. `Ctrl+C` dừng agent. HTTP chỉ được opt-in bằng `--allow-insecure-http` ở localhost/IP LAN riêng; Internet dùng HTTPS.

## 4. Duyệt máy và giao giải

1. Trở lại **Worker → Workers**, mở máy vừa đăng ký. Ban đầu PENDING_APPROVAL/UNTRUSTED; kết nối được chưa có nghĩa được nhận bài riêng tư.
2. Duyệt và chọn TRUSTED hoặc COMMUNITY theo mức tin cậy thực tế. TRUSTED nghĩa là chấp nhận máy nhận artifact riêng của người thi đấu; máy chủ vẫn có thể đọc byte artifact dù bot chạy trong container.
3. Kiểm tra Docker, ngôn ngữ, protocol, heartbeat, CPU/RAM và slot thực nhận.
4. Trong **Groups**, tạo/chọn nhóm, thêm máy và bật nhóm. Cập nhật nhiều thành viên là nhiều mutation; nếu lỗi giữa chừng, tải lại để biết những máy nào đã được cập nhật.
5. Trong **Tournament Assignment**, tìm giải, chọn **REMOTE GROUP** (`REMOTE_REQUIRED`), nhóm, trust và concurrency. Xem preview capacity rồi lưu **trước khi tạo lịch**. LOCAL là chạy tại Central; chưa có AUTO/fallback local.

Preview không giữ chỗ riêng. Nhiều giải dùng chung nhóm có thể tranh slot; máy phải đáp ứng runtime/trust/ngôn ngữ và trạng thái đủ điều kiện. Capacity bảo thủ yêu cầu máy hỗ trợ các ngôn ngữ cần cho giải.

## 5. Chạy lại và kiểm tra tình trạng

Chạy lại từ **cùng thư mục**, dùng **cùng state-dir**, không cần token mới:

```sh
java -jar rps-worker.jar --central=https://game.example.com --name='LAB-PC-01' --state-dir=./worker-state --max-matches=1 --max-bots=2 --serve
```

| Tình trạng | Ý nghĩa và kiểm tra |
| --- | --- |
| STARTING | Chờ capabilities/heartbeat của lần chạy hiện tại. |
| ONLINE | Có heartbeat và khả năng nhận việc; vẫn cần duyệt/nhóm/trust phù hợp. |
| BUSY | Không báo slot trống. |
| DEGRADED | Docker hoặc runtime chưa sẵn sàng; pull image rồi khởi động lại. |
| OFFLINE | Không nhận heartbeat trong khoảng 30 giây; kiểm tra mạng, terminal, sleep máy. |
| DRAINING / DRAINED | Ngừng nhận việc mới; DRAINED là nhãn khi việc đã nhận không còn. |
| DISABLED | Admin đã vô hiệu; không tiếp tục gửi việc cho máy. |

Heartbeat mặc định 5 giây. Slot thực tế lấy từ khai báo, ngân sách CPU/RAM, giới hạn Admin, tài nguyên đang dùng và reservation Central. `--max-matches=8` không tự làm máy có tám slot. Bắt đầu một slot, đo bằng `java -jar rps-worker.jar benchmark --state-dir=./benchmark-state` khi không chạy agent, rồi thử giải nhỏ trước khi tăng.

## Dừng, thu hồi và phục hồi

**Drain** cho việc đã nhận hoàn tất. **Disable** fence execution hiện tại để recovery/thử lại có giới hạn. **Resume** từ draining/disabled yêu cầu khởi động lại agent. **Revoke** thu hồi credential vĩnh viễn; muốn dùng lại phải đăng ký danh tính mới và duyệt lại. Các thao tác cần lý do/revision hiện tại.

Cache mặc định 20 GiB tại worker-state/cache, kiểm SHA-256 khi tải và tái sử dụng; đổi bằng `--cache-dir`, `--cache-mb`. Không chia sẻ cache chứa artifact riêng tư. Khi mất mạng sau trận, giữ state và dùng recovery/upload-pending theo [hướng dẫn khôi phục](../technical/backup-recovery.md); không xóa state để tránh gửi trùng hoặc bỏ kết quả chưa nhận.

## Nguồn đối chiếu

- [Luồng setup](../../client/js/admin/worker-setup-view.js), [lệnh theo OS](../../client/js/admin/worker-setup-commands.js), [token](../../client/js/admin/worker-registration.js).
- [Identity/API](../../server/src/main/java/com/rpsbattle/api/controller/RemoteWorkerController.java), [telemetry](../../server/src/main/java/com/rpsbattle/worker/remote/WorkerTelemetryService.java), [control](../../server/src/main/java/com/rpsbattle/worker/remote/WorkerControlService.java).
- [Nhóm và phân công](../../server/src/main/java/com/rpsbattle/api/controller/WorkerGroupController.java), [agent](../../worker/src/main/java/com/rpsbattle/remote/WorkerApplication.java).
