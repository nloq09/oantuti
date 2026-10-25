# Vận hành giải từ check-in đến kết quả

[Mục lục](../README.md) · [Kiểm thử chính thức](validation-management.md) · [Theo dõi trận](match-monitor.md)

Trước khi bắt đầu: đăng ký đã đóng, danh sách hợp lệ, bài cần khóa đã LOCKED/qua validation, check-in đáp ứng policy và hạ tầng có sức chứa. Chủ giải theo dõi qua **Tổng quan**, **Check-in**, **Trận đấu**, **Nhánh đấu**, **Kết quả**, **Audit**; tab nào hiện phụ thuộc mode và phase.

## Check-in và bắt đầu

Đọc [hướng dẫn check-in](../tournament/check-in.md). Check-in theo giải khác với READY của workspace và sẵn sàng trước từng trận người chơi. Theo dõi người đủ điều kiện và người chưa check-in trước hạn. Server tự xử lý mốc timeline; không coi đồng hồ client là quyền vào trận.

Khi giải đủ điều kiện, dùng thao tác bắt đầu nếu UI cho phép; lịch tự động vẫn chịu cùng điều kiện server. Nếu bị chặn, xem validation chưa áp dụng, review chưa xử lý, thiếu người, thiếu artifact hoặc thiếu worker đủ điều kiện. Không giảm policy để che lỗi hạ tầng.

Seeding/lịch đã tạo là dữ liệu thi đấu. Điều chỉnh policy thực thi hay roster có thể bị khóa sau thời điểm này. Xem [thể thức](../tournament/formats.md) để hiểu bye, thứ tự vòng, series và đội.

## Tạm dừng, tiếp tục và chuyển vòng

- **Pause:** ngăn tiến trình giải nhận lịch mới theo điều khiển giải; không coi đây là snapshot có thể đóng băng giữa một nước đi. Các cửa sổ ready được xóa để tránh giữ xác nhận cũ.
- **Resume:** chỉ từ PAUSED hoặc RECOVERY_REQUIRED; cần hết review/trận FAILED chưa xử lý và artifact còn hợp lệ. Cửa sổ ready/lineup được chuẩn bị lại.
- **Next round:** chỉ dùng khi đã tắt tự chuyển vòng, vòng trước đã hoàn tất và không còn review. Không ép chạy vòng mới trong lúc cặp đấu trước còn pending/running.
- **Finish:** chỉ khi các trận, bracket và series đã có quyết định, không còn review. Cần lý do. Nút không hợp lệ ở phase hiện tại sẽ bị ẩn/từ chối.
- **Cancel:** kết thúc giải theo hướng hủy, có audit; không xóa lịch sử để tạo lại giải cùng ID.

Giải đội cần lineup hợp lệ; thời gian chọn đội hình và fallback thực hiện theo scheduler. Không ghép tay bằng cách sửa bảng tournament_matches.

## Trận lỗi và quyết định quản trị

Khi cần review, dùng trang trận/chi tiết và nhập lý do. Yêu cầu review khi giải RUNNING có thể chuyển giải sang PAUSED.

Retry trận yêu cầu PAUSED/RECOVERY_REQUIRED và trận FAILED còn có thể thử lại; không chạy lại một trận đã FINISHED chỉ vì không thích kết quả. Loại người tham gia yêu cầu giải đã dừng, không có trận RUNNING chứa người đó; trận chưa đấu được xử theo forfeit, lịch sử đã hoàn tất được giữ.

Override kết quả yêu cầu review và trạng thái phù hợp. Server chặn nếu vòng Swiss phía sau đã tạo, ván sau trong series đã có, qualification vòng bảng đã khóa hoặc nhánh phía sau đã thi đấu. Quyết định quản trị được ghi riêng; replay vẫn giữ sự kiện gốc.

## Sau restart hoặc sự cố

RECOVERY_REQUIRED nghĩa là cần người kiểm tra, không phải trận thua. Xem [khôi phục](../technical/backup-recovery.md): xử lý review/trận lỗi, kiểm tra artifact/remote attempts rồi resume nếu server cho phép; có thể hủy và tạo giải mới. Trạng thái bàn cờ trong RAM không tự phục hồi giữa trận.

## Worker riêng do chủ giải thêm

Worker là máy hỗ trợ chạy trận bot, có thể ở mạng khác với server. Đây là lựa chọn khi chủ giải có máy đáng tin cậy để hỗ trợ thi đấu; người chơi không cần cài worker để tham gia giải.

Mở **Quản lý giải → Worker riêng** (`/tournament-workers.html?id=<id-giai>`), rồi mở mục **Thêm worker cho giải**. Chủ giải và Admin được tải JAR, cấp token và điều khiển máy thuộc giải; người tham gia hoặc chủ giải khác không được truy cập các API này.

### 1. Chuẩn bị máy và tải worker

Trên chính máy sẽ chạy bot:

1. Cài Java 21 trở lên. Cài và mở Docker Desktop trên Windows/macOS với Linux containers, hoặc Docker Engine trên Linux.
2. Trong mục **Thêm worker cho giải**, bấm **Tải rps-worker.jar**. Tạo thư mục riêng, ví dụ `rps-worker`, và lưu JAR vào đó. Có thể tải bằng máy chủ giải rồi chuyển riêng file JAR sang máy worker; không cần chia sẻ tài khoản game.
3. Windows: mở thư mục chứa JAR, nhập `powershell` vào thanh địa chỉ File Explorer rồi Enter. Linux/macOS: mở Bash và chuyển tới thư mục chứa JAR. Không cần clone repo hay cài PostgreSQL trên máy này.
4. Kiểm tra và tải image cho các ngôn ngữ cần chạy:

```sh
java -version
docker info --format '{{.OSType}}'
docker pull python:3.12-slim
docker pull gcc:14
docker pull eclipse-temurin:21-jdk
```

Kết quả Docker cần có `linux`. Các image lần lượt phục vụ Python, C++ và Java. Chờ tải xong rồi mới tạo token; worker không tự pull image. Sau khi bổ sung image, khởi động lại agent để cập nhật ngôn ngữ hỗ trợ. Máy chỉ kết nối ra Central qua HTTPS, không cần mở port trên router của máy worker.

### 2. Tạo token cho đúng máy

Trong trang **Worker riêng**:

1. Nhập **Tên máy**, chọn **Hệ điều hành** đúng với terminal sẽ dùng.
2. Tích **Tôi tin cậy máy này và cho phép máy đọc bot của người chơi trong giải.** Máy giữ artifact bot để chạy trận, vì vậy chỉ cấp quyền cho máy mà chủ giải tin cậy.
3. Bấm **Tạo token cho một máy**. Token dùng một lần và hết hạn sau 30 phút. Sao chép token để dùng ở bước tiếp theo; server không cho xem lại secret sau khi rời trang.

Mỗi giải tối đa **20 máy chưa thu hồi quyền** và **5 token còn hiệu lực, còn lượt dùng**. Tạo token riêng cho mỗi máy. Nếu đạt giới hạn, thu hồi token hoặc máy không còn dùng trong danh sách bên dưới; không liên tục tạo token mới để thử kết nối.

### 3. Đăng ký và chạy lần đầu

Bấm **Sao chép lệnh** tại **Chạy lần đầu**, chạy toàn bộ khối lệnh trong terminal ở thư mục chứa JAR. Khi hiện `Registration token`, dán token vừa tạo rồi Enter. Token được nhập riêng, không nằm trong dòng lệnh Java.

Ví dụ dưới đây: thay `https://game.example.com` bằng địa chỉ HTTPS của game, thay `ID_GIAI` bằng ID trong URL của giải. Ưu tiên sao chép từ trang vì lệnh đã điền sẵn các giá trị này.

Windows PowerShell:

```powershell
$env:WORKER_REGISTRATION_TOKEN = [System.Net.NetworkCredential]::new('', (Read-Host 'Registration token' -AsSecureString)).Password
try {
    java -jar rps-worker.jar --central=https://game.example.com --name='WORKER-GIAI' --state-dir=./worker-state-tournament-ID_GIAI --max-matches=1 --max-bots=2 --serve
} finally {
    Remove-Item Env:WORKER_REGISTRATION_TOKEN -ErrorAction SilentlyContinue
}
```

Linux/macOS Bash:

```bash
(
  read -r -s -p 'Registration token: ' WORKER_REGISTRATION_TOKEN
  printf '\n'
  export WORKER_REGISTRATION_TOKEN
  java -jar rps-worker.jar --central=https://game.example.com --name='WORKER-GIAI' --state-dir=./worker-state-tournament-ID_GIAI --max-matches=1 --max-bots=2 --serve
)
```

Giữ Java, terminal và Docker hoạt động. Đăng ký thành công tạo danh tính **APPROVED/TRUSTED trong đúng giải**, không cần Admin duyệt lại. Danh sách máy tự cập nhật; kiểm tra tên máy, trạng thái, ngôn ngữ và slot trống. Lệnh do trang chủ giải tạo bắt đầu với một trận/hai bot; slot thực nhận vẫn do server xét theo tài nguyên và giới hạn quản trị.

Phạm vi được server gắn từ token, không phải từ tên thư mục `worker-state-tournament-…`. Sửa tên máy, state-dir hoặc gửi ID giải khác không chuyển quyền sang giải khác. Worker dùng chung vẫn có thể nhận trận của giải nếu phù hợp; thêm máy riêng không làm giải chỉ dùng máy riêng.

### 4. Chạy lại hoặc dừng máy

Để chạy lại bình thường, mở đúng thư mục đã dùng, chọn đúng tên máy/hệ điều hành trên trang rồi sao chép khối **Chạy lại**. Không tạo token mới:

```sh
java -jar rps-worker.jar --central=https://game.example.com --name='WORKER-GIAI' --state-dir=./worker-state-tournament-ID_GIAI --max-matches=1 --max-bots=2 --serve
```

Giữ nguyên thư mục state theo giải. Mỗi máy dùng state riêng, kể cả hai máy cùng hỗ trợ một giải; không sao chép state/cache sang máy khác, pool chung hoặc giải khác. Không chạy hai agent cùng state-dir.

- **Ngừng nhận trận:** máy không nhận việc mới và hoàn tất trận đang giữ. Muốn tắt có trật tự, đợi hết trận rồi dùng `Ctrl+C`.
- **Bật lại (cần khởi động agent):** cho phép nhận việc trở lại; sau khi xác nhận, khởi động lại agent bằng lệnh **Chạy lại**.
- **Thu hồi quyền:** chặn credential vĩnh viễn, execution đang giữ được xử lý theo cơ chế thử lại. Muốn dùng máy lại, đăng ký danh tính mới với token mới và state-dir riêng. Thu hồi không chuyển máy thành worker dùng chung.
- **Thu hồi token:** ngăn token chưa dùng đăng ký máy mới. Không ảnh hưởng credential của máy đã đăng ký. **Ẩn và xóa token khỏi trang** chỉ xóa secret khỏi giao diện, không thu hồi token trên server.

Các thao tác điều khiển máy yêu cầu lý do và trạng thái mới nhất. Nếu báo trạng thái đã đổi, chờ danh sách cập nhật rồi kiểm tra trước khi thử lại. Không xóa state để xử lý lỗi kết nối; xem [khôi phục worker](../technical/backup-recovery.md#remote-worker-mất-mạng-hoặc-restart).

### Khi chưa nhận được trận

Worker riêng hiện chỉ hỗ trợ giải **BOT_ONLY có coding/submission**, và chỉ chạy trận bằng bài đã LOCKED. Build, kiểm thử và Final Validation vẫn chạy trên Central. Khi giải chưa thi đấu, chưa có trận phù hợp hoặc các máy còn bận, máy có thể chưa được giao việc.

Nếu trạng thái DEGRADED, kiểm tra Docker và image rồi chạy lại agent. OFFLINE thường cần kiểm tra mạng tới Central, terminal hoặc chế độ ngủ. Nếu trang báo giải dùng **LOCAL**, Admin đã cấu hình chạy trên Central; thêm worker không thay đổi policy đó. Chủ giải không tự chỉnh policy hoặc giới hạn tài nguyên cấp hệ thống. Xem [các chế độ điều phối](../admin/workers.md#chế-độ-điều-phối-của-giải).

Giải đã kết thúc, bị hủy hoặc lưu trữ không cho cấp token mới. Token riêng không đăng ký được máy nếu giải đã kết thúc/bị hủy/đưa vào trash hoặc tài khoản chủ giải không còn ACTIVE. Danh sách và thao tác thu hồi vẫn dùng được theo quyền để dọn quyền truy cập; token còn thời hạn không vượt qua các kiểm tra này.

## Nguồn đối chiếu

- [Điều khiển giải](../../server/src/main/java/com/rpsbattle/tournament/TournamentAdministrationService.java), [state machine](../../server/src/main/java/com/rpsbattle/tournament/domain/TournamentStateMachine.java).
- [Quyền và bắt đầu](../../server/src/main/java/com/rpsbattle/tournament/OwnedTournamentService.java), [UI controls](../../client/js/tournament/organizer-controls.js).
- [Điều hướng](../../client/js/tournament/organizer-shell-model.js), [controller](../../server/src/main/java/com/rpsbattle/api/controller/TournamentAdministrationController.java).
- [Trang worker riêng](../../client/js/tournament/tournament-worker-view.js), [lệnh theo hệ điều hành](../../client/js/admin/worker-setup-commands.js), [API worker theo giải](../../server/src/main/java/com/rpsbattle/api/controller/TournamentWorkerController.java).
- [Quyền/hạn mức worker](../../server/src/main/java/com/rpsbattle/worker/remote/TournamentWorkerService.java), [đăng ký danh tính](../../server/src/main/java/com/rpsbattle/worker/remote/WorkerIdentityService.java), [điều phối](../../server/src/main/java/com/rpsbattle/worker/remote/RemoteRoutingService.java).
