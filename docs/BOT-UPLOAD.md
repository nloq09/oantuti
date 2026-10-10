# Bot người chơi: C++, Python, Java

## Sử dụng

Khởi động server như README, mở `/lobby.html`, chọn **Tải bot của tôi lên**.
Đặt tên, chọn `.cpp`, `.py`, `.java` hoặc `.jar`, rồi chọn bot trong **Người chơi của bạn**.

- **Hai người**: tạo phòng hoặc tham gia mã phòng. Mỗi bên có thể dùng bot riêng hoặc chơi tay.
- **Đấu với bot**: bot riêng của bạn đấu với bot chiến thuật có sẵn.
- Bot tự xếp 18 quân, tự sẵn sàng và tự đi. Chủ bot xem trận, kết nối lại, rời phòng hoặc đầu hàng được; không điều khiển nước đi của bot.
- Hai bot vào cùng phòng sẽ tự bắt đầu. Bot lỗi, trả nước đi ngoài danh sách, sai sequence, kết thúc tiến trình hoặc hết thời gian bị xử thua.

Mẫu tải trực tiếp tại `/bot-guide.html`, source ở `client/assets/bot-examples/`.
Các mẫu dùng thư viện chuẩn và chọn nước đi đầu tiên; người chơi thay thuật toán chọn nước đi.

## Runner Docker

Máy chủ cần Docker hoạt động với **Linux containers** và image chuẩn bị sẵn:

```powershell
docker pull python:3.12-slim
docker pull gcc:14
docker pull eclipse-temurin:21-jdk
```

[Cài Docker Desktop trên Windows](https://docs.docker.com/desktop/setup/install/windows-install/).
Kiểm tra `docker info --format '{{.OSType}}'` trả `linux`.
Endpoint `/api/bots/capabilities` báo riêng từng ngôn ngữ đã sẵn sàng.
Server không tự pull image. Khi chưa có runner, upload vẫn lưu được nhưng tạo/tham gia trận bằng bot trả HTTP 503; không giữ chỗ trong phòng. Chơi tay và bot có sẵn hoạt động bình thường.

Mỗi bot có container riêng, duy trì qua các lượt: không mạng, root filesystem chỉ đọc,
user 65534, bỏ capabilities, no-new-privileges, CPU 0.5, RAM/swap tối đa 384 MB,
64 process và 64 file descriptor. Chỉ mount file bot đọc được; không mount repo hay Docker socket.
Compiler ghi vào tmpfs `/tmp` 64 MB. Container bị dừng/xóa khi bot lỗi, trận kết thúc hoặc server shutdown.
Các tùy chọn theo [Docker run reference](https://docs.docker.com/reference/cli/docker/container/run/).
Đây là runner cho demo trên máy chủ có Docker; triển khai công khai với người dùng không tin cậy cần vận hành sandbox trên worker riêng và thêm tài khoản, quota/rate limit, quản trị upload. Docker chia sẻ kernel nên không thay thế một môi trường cách ly máy ảo chuyên dụng.

## File và giao thức

Source UTF-8, tối đa **512 KiB**, JAR tối đa **5 MiB**; kho tối đa 100 bot.

- C++: một `.cpp`, biên dịch C++17 bằng GCC 14; không tải dependencies.
- Python: một `.py`, Python 3.12, isolated mode; thư viện chuẩn.
- Java: một `.java`, `public class Main`, không `package`, Java 21; thư viện chuẩn.
- JAR: Java 21, manifest `Main-Class`, tự chứa dependencies. Ví dụ đóng gói mẫu:

```powershell
javac --release 21 Main.java
jar --create --file bot.jar --main-class Main Main.class
```

Đọc **một dòng JSON** từ stdin mỗi lượt:

```json
{"type":"YOUR_TURN","matchId":"ROOM","turn":1,"sequence":42,"player":"P1","state":{"phase":"PLAYING","currentPlayer":"P1","board":{"rows":9,"cols":9,"pieces":[]},"players":[]},"legalActions":[{"type":"MOVE","pieceId":"P1-ROCK-1","from":{"row":6,"col":0},"to":{"row":5,"col":0}}]}
```

`state.board.pieces` thực tế chứa quân với `id`, `ownerId`, `type`, `status`, `position`.
Trả đúng `sequence` và một object trong `legalActions`:

```json
{"type":"ACTION","sequence":42,"action":{"type":"MOVE","pieceId":"P1-ROCK-1","from":{"row":6,"col":0},"to":{"row":5,"col":0}}}
```

PASS chỉ gửi khi có trong danh sách. Không gửi lệnh đặt quân. Tọa độ server row/col từ 0:
P1 xuất phát hàng 6–8, P2 hàng 0–2; góc nhìn xoay trên UI không đổi tọa độ giao thức.
Không in banner hoặc debug trên stdout; flush sau mỗi dòng. Stderr không trộn vào JSON và không được hiển thị cho người chơi.
Lượt đầu tối đa 25 giây gồm biên dịch/khởi động, các lượt tiếp theo 2 giây, phản hồi tối đa 65.536 ký tự.
Server vẫn kiểm tra nước đi bằng rule/engine. Tối đa 4 bot tính nước đi đồng thời; I/O bot chạy ngoài khóa phòng/scheduler.

## Lưu trữ và quyền sử dụng

Source/metadata lưu mặc định `data/bots/` tương đối với thư mục chạy server (bootRun: `server/data/bots/`).
Thư mục bị Git ignore và không nằm trong static HTTP. Override bằng `rps.bots.directory`;
tắt runner bằng `rps.bots.enabled=false`.
Tên file do người chơi gửi không dùng làm đường dẫn; server tạo UUID và tên cố định.
Upload trả `{id,name,language,token}`. Token cấp quyền dùng bot; client lưu trong localStorage.
Không có endpoint liệt kê bot người khác hoặc tải source của họ. Không chia sẻ token; xóa dữ liệu trình duyệt sẽ mất quyền truy cập nếu chưa giữ token.
Bot giữ lại qua restart server; phòng vẫn chỉ lưu trong bộ nhớ. Bản demo chưa có tài khoản/khôi phục token/xóa bot qua UI.

REST multipart: `POST /api/bots` với `name`, `file`.
`POST /api/matches` nhận thêm `botId`, `botToken`; endpoint join cũng nhận hai trường này.
Bot không có quyền thao tác engine hay gửi action qua WebSocket; worker chuyển JSON response thành action và engine xác thực.

## Kiểm tra

```powershell
cd server
.\gradlew.bat test bootJar --no-daemon --console=plain
cd ..
node --test client/tests/*.test.js
python tools/verify-bot-examples.py
```

Lệnh Python chỉ biên dịch/chạy **mẫu tin cậy trong repository** bằng toolchain local, không chạy upload của người chơi. Cần g++, JDK 21+ (JAVA_HOME) và Python.
JUnit kiểm tra storage/token/quota, multipart API, runner thiếu không chiếm ghế, tự xếp quân,
xử thua, phản hồi đến muộn, cách ly I/O giữa phòng và bot đấu bot qua WebSocket.
`DockerBotIntegrationTest` chạy thật các mẫu C++/Python/Java trong container khi Docker/image sẵn sàng; nếu chưa có sẽ **SKIPPED**, không tính là kiểm chứng sandbox thực tế.
