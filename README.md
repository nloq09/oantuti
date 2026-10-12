# RPS Battle — Oẳn Tù Tì Chiến Thuật

Game chiến thuật trên bàn cờ **9 × 9**, kết hợp luật Búa–Bao–Kéo với di chuyển theo lượt. Server Java xử lý luật và trạng thái trận; client chạy trên trình duyệt, đồng bộ thời gian thực qua WebSocket.

Repository: [nloq09/oantuti](https://github.com/nloq09/oantuti).

## Chức năng hiện tại

### Chế độ và phòng chơi

- **Hai người**: tạo phòng, sao chép/chia sẻ mã và tham gia phòng từ trình duyệt khác.
- **Đấu với bot**: chơi với bot chiến thuật có sẵn của game.
- Trong **cả hai chế độ**, mỗi người được chọn **Tôi tự chơi** hoặc bot riêng đã tải lên để điều khiển quân. Lựa chọn áp dụng khi tạo hoặc tham gia phòng.
- Giao diện chỉ có hai chế độ trên, không có nút chế độ Bot đấu bot. Hai người vẫn có thể cùng chọn bot riêng trong một phòng Hai người.
- Phòng quản lý vị trí Xanh/P1 và Đỏ/P2, kết nối, xếp quân và trạng thái sẵn sàng.
- Server xác thực token vị trí, kiểm tra lượt/sequence và từ chối thao tác không hợp lệ.

### Bàn cờ và trận đấu

- Đặt quân thủ công, di chuyển quân đang xếp, xếp ngẫu nhiên, đặt lại, sẵn sàng và hủy sẵn sàng.
- Bàn cờ xoay theo người chơi: **quân của bạn luôn ở dưới**, kể cả P2; tọa độ gửi tới server giữ nguyên.
- Đánh dấu quân đã chọn, ô di chuyển hợp lệ và ô tấn công; hiệu ứng di chuyển/giao chiến.
- Đồng hồ lượt, số quân từng loại của hai bên và diễn biến trận; nút Xóa chỉ xóa nhật ký đang hiển thị.
- Bỏ lượt, đầu hàng và rời phòng. Rời khi trận đang chơi được tính là đầu hàng.
- Tự kết nối lại và nhận trạng thái đầy đủ khi WebSocket bị mất kết nối.
- Màn hình kết quả hiển thị thắng/thua/hòa, thời gian, số lượt và quân còn lại; tạo ván mới hoặc về trang chủ.
- Nhật ký ở màn hình kết quả chỉ chứa sự kiện trong trạng thái cuối trận, **không phải replay toàn trận**.

### Bot của người chơi

- Trang **Bot của tôi** quản lý danh sách bot lưu trên trình duyệt, chọn bot và tải file lên server.
- Hỗ trợ **Python `.py`, C++ `.cpp`, Java `.java` và `.jar`**.
- Chọn ngôn ngữ, xem trạng thái Docker, nhật ký tải lên và dùng bot ở trang Vào trận.
- **Đấu thử với bot** tạo một trận thật với bot chiến thuật của game.
- Bot được tự xếp 18 quân, tự sẵn sàng và tự chọn nước đi; chủ bot theo dõi trận.
- Bot sai giao thức, trả nước đi không hợp lệ, lỗi tiến trình hoặc quá thời gian sẽ bị xử thua.
- Bot riêng chạy trong container Docker; chơi tay và bot chiến thuật có sẵn **không cần Docker**.
- ZIP, tải manifest riêng, sửa mã nguồn, build độc lập và replay toàn trận trên web chưa được hỗ trợ.

### Giao diện

- Trang chủ có Bắt đầu chơi, Bot của tôi, Cách chơi và Cài đặt.
- Trang Vào trận có chọn chế độ, chọn người/bot điều khiển, tạo phòng; phần nhập mã phòng nằm dưới khung Kết nối với đối thủ ở cột phải.
- Phong cách đấu trường với nền lâu đài, khung vàng, màu Xanh/Đỏ; bố cục thích ứng điện thoại.
- Cài đặt âm thanh, nhạc nền và hiệu ứng lưu trên trình duyệt.
- Hướng dẫn bot và giao thức có cả bản Markdown và trang web.

## Luật chơi

1. Mỗi bên có **6 Búa, 6 Bao, 6 Kéo**; đặt trong ba hàng của mình. Trận bắt đầu khi cả hai bên đủ quân và sẵn sàng.
2. Mỗi lượt di chuyển một quân **một ô lên/xuống/trái/phải**; không đi chéo, ra ngoài bàn hoặc vào ô có quân cùng bên.
3. Đi vào quân đối thủ để giao chiến: **Búa thắng Kéo, Kéo thắng Bao, Bao thắng Búa**. Cùng loại thì cả hai bị loại.
4. Loại hết quân đối thủ để thắng; cả hai hết quân thì hòa. Đầu hàng thì đối thủ thắng.
5. Giới hạn mặc định **200 lượt**: bên còn nhiều quân hơn thắng, bằng nhau thì hòa.
6. Thời gian lượt online mặc định **30 giây**; hết giờ server tự bỏ lượt. Đây là cấu hình server, không phải lựa chọn trên client.

## Công nghệ và yêu cầu

- **Server**: Java 21, Spring Boot 3.5.16, Gradle Wrapper 8.14.3, REST và Spring WebSocket; Jackson xử lý JSON.
- **Client**: HTML/CSS và JavaScript ES modules, không cần framework hay npm để chơi.
- **Kiểm thử**: JUnit/Spring Boot Test và Node.js test runner.
- **Bot riêng**: Docker Engine Linux hoặc Docker Desktop ở chế độ Linux containers.
- Dùng **JDK 21 hoặc JDK 24** để chạy Gradle hiện tại; không dùng JDK 25 với wrapper 8.x. Mã được biên dịch cho Java 21.
- Node.js dùng để chạy test client; script kiểm thử upload cần bản có `fetch`, `File` và `WebSocket` toàn cục, ví dụ Node.js 24.

## Chạy trong VS Code / Windows

Mở thư mục repository trong VS Code, mở terminal PowerShell tại thư mục gốc:

```powershell
cd server
# Nếu Java mặc định chưa phù hợp, đặt đường dẫn JDK thực tế trên máy:
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21'
.\gradlew.bat bootRun --console=plain
```

Nếu đang dùng JDK 24 đã cài trên máy phát triển hiện tại, có thể thay dòng JAVA_HOME bằng:

```powershell
$env:JAVA_HOME = 'C:\Users\hi\.jdks\openjdk-24.0.2+12-54'
```

Mở [http://localhost:8080](http://localhost:8080). Server phục vụ cả client và API, không cần Live Server hoặc chạy frontend riêng. Giữ terminal server mở; **Ctrl+C** để dừng.

Sau khi sửa code: dừng server, chạy lại `bootRun`, rồi **Ctrl+F5** trên trình duyệt. Tài nguyên client được copy vào server khi build/chạy.

Đổi cổng nếu 8080 đang được sử dụng:

```powershell
.\gradlew.bat bootRun --console=plain --args="--server.port=8081"
```

Kiểm tra sức khỏe server từ terminal khác:

```powershell
Invoke-RestMethod http://localhost:8080/api/health
```

Kết quả mong đợi: `status` là `UP`.

### Linux / macOS

Từ thư mục gốc repository, dùng JDK 21:

```bash
cd server
chmod +x gradlew
./gradlew bootRun --console=plain
```

### Build file JAR

Trong thư mục `server`:

```powershell
.\gradlew.bat bootJar --no-daemon --console=plain
.\gradlew.bat -q printBuildDir
```

Lệnh cuối in thư mục output. File chạy server nằm tại `<build-dir>/libs/rps-battle-1.0-SNAPSHOT.jar`:

```powershell
java -jar "<build-dir>/libs/rps-battle-1.0-SNAPSHOT.jar"
```

Trên Windows, build output nằm dưới `%USERPROFILE%\.gradle\rps-battle-builds\<checkout-key>` để tránh OneDrive khóa file class. Linux/macOS mặc định dùng `server/build`. Dùng `bootJar` cho server online; task `run` là chương trình console riêng.

## Bật Docker để dùng bot riêng

1. Mở Docker Desktop, chờ Engine sẵn sàng và giữ Docker chạy.
2. Kiểm tra `docker info --format '{{.OSType}}'` trả về `linux`.
3. Chuẩn bị image một lần:

```powershell
docker pull python:3.12-slim
docker pull gcc:14
docker pull eclipse-temurin:21-jdk
```

Server cần tìm được lệnh `docker` trong PATH. Nếu vừa cài Docker, mở lại VS Code/terminal rồi khởi động lại server. Không cần bấm Run từng image: server tự tạo và dọn container cho bot.

Vào **Bot của tôi**, tải file, chọn bot rồi bấm **Dùng trong trận**. Ở Vào trận, chọn chế độ và kiểm tra mục Người chơi của bạn trước khi tạo/vào phòng. Chọn Tôi tự chơi nếu muốn điều khiển bằng tay.

- Source tối đa **512 KiB**, JAR tối đa **5 MiB**; kho server tối đa 100 bot.
- Python: Python 3.12, thư viện chuẩn.
- C++: GCC 14, C++17; một file `.cpp`.
- Java source: `public class Main`, không `package`; Java 21.
- JAR: có `Main-Class`, tương thích Java 21.
- Container không mạng, root filesystem chỉ đọc, user không phải root, CPU 0.5, RAM 384 MB, giới hạn tiến trình và thư mục tạm. C++/Java được biên dịch khi trận bắt đầu.
- Danh sách/quyền sử dụng bot lưu trong `localStorage`; file và metadata lưu tại `server/data/bots` khi chạy từ thư mục server. Đổi trình duyệt hoặc xóa storage không tự phục hồi danh sách bot.

Bot nhận một dòng JSON `YOUR_TURN` qua stdin, tính toán từ trạng thái và `legalActions`, rồi trả một dòng JSON `ACTION` qua stdout với đúng `sequence`; phải flush và chỉ trả nước đi hợp lệ. Đọc chi tiết trong [BOT-PROTOCOL.md](docs/BOT-PROTOCOL.md), [BOT-UPLOAD.md](docs/BOT-UPLOAD.md) hoặc trang `/bot-protocol.html` và `/bot-guide.html`.

Mẫu tải trực tiếp từ trang hướng dẫn, source ở `client/assets/bot-examples/`: `main.py`, `tactical.py`, `main.cpp`, `Main.java`.

## Cấu hình server

Cấu hình mặc định nằm trong `server/src/main/resources/application.yml`. Có thể ghi đè bằng tham số Spring Boot:

- `server.port`: mặc định 8080.
- `rps.turn-seconds`: mặc định 30 giây.
- `rps.bots.enabled`: mặc định `true`; đặt `false` để tắt runner bot riêng.
- `rps.bots.directory`: mặc định `data/bots`, tính từ thư mục làm việc server.

Ví dụ chạy JAR với thời gian 60 giây:

```powershell
java -jar "<build-dir>/libs/rps-battle-1.0-SNAPSHOT.jar" --server.port=8080 --rps.turn-seconds=60
```

## API và WebSocket

REST hiện có:

- `GET /api/health` (alias `/api/v1/health`): sức khỏe server.
- `POST /api/matches`: tạo phòng với `mode` là `HUMAN_VS_HUMAN` hoặc `HUMAN_VS_BOT`; tùy chọn `seed`, `botId`, `botToken`.
- `GET /api/matches/{id}`: thông tin phòng.
- `POST /api/matches/{id}/join`: tham gia, tùy chọn `botId`, `botToken`.
- `POST /api/bots`: tải multipart với `name` và `file`, trả metadata/quyền sử dụng bot.
- `GET /api/bots/capabilities`: trạng thái Docker/image từng ngôn ngữ và giới hạn kích thước file.

WebSocket `/ws/game` nhận JSON với thông tin vị trí/token. Các thông điệp chính: `JOIN_MATCH`, `RESUME_MATCH`, `PLACE`, `RANDOM_PLACEMENT`, `RESET_PLACEMENT`, `READY`, `UNREADY`, `ACTION`, `STATE_REQUEST`, `LEAVE`, `PING`. Server trả `GAME_STATE`, `ACTION_REJECTED`, `LEFT_ROOM`, `PONG`. Snapshot server là nguồn trạng thái chính; client không tự quyết định kết quả nước đi.

Client tự dùng `ws://` với HTTP và `wss://` với HTTPS, cùng host với trang web.

## Cấu trúc project

```text
oantuti/
├── README.md
├── client/                         # Client web, được server phục vụ cùng host
│   ├── index.html                  # Trang chủ
│   ├── lobby.html                  # Chế độ, chọn bot, tạo/vào phòng
│   ├── bots.html                   # Quản lý, tải và đấu thử bot
│   ├── game.html                   # Xếp quân và chơi online
│   ├── result.html                 # Kết quả và sự kiện cuối trận
│   ├── bot-guide.html              # Hướng dẫn upload, bot mẫu
│   ├── bot-protocol.html           # Giao thức stdin/stdout
│   ├── css/                        # Theme, layout từng trang, hiệu ứng
│   ├── assets/
│   │   ├── pieces/                 # SVG Búa/Bao/Kéo cho hai bên
│   │   ├── bot-examples/           # Bot mẫu Python/C++/Java
│   │   └── castle-courtyard.png    # Nền đấu trường
│   ├── js/
│   │   ├── pages/                  # Khởi tạo và thao tác từng màn hình
│   │   ├── api/                    # REST phòng và upload bot
│   │   ├── socket/                 # Kết nối, reconnect WebSocket
│   │   ├── game/                   # Client state, board, input, animation
│   │   └── ui/                     # Dialog, thông báo, timer, âm thanh
│   ├── tests/                      # Node test runner
│   ├── demo/                       # Demo cố định của chặng 2A
│   └── package.json                # Script test; không cần npm để chạy game
├── server/
│   ├── build.gradle                # Java, Spring Boot, test, copy client
│   ├── settings.gradle
│   ├── gradlew / gradlew.bat        # Gradle Wrapper
│   ├── gradle/wrapper/
│   └── src/
│       ├── main/
│       │   ├── resources/application.yml
│       │   └── java/com/rpsbattle/
│       │       ├── RpsBattleApplication.java  # Entry point server online
│       │       ├── game/            # Action, board, piece, player, state, engine
│       │       ├── rule/            # Các interface luật độc lập
│       │       ├── rules/           # Luật classic và biến thể online
│       │       ├── match/           # Điều phối trận ở tầng game
│       │       ├── agent/           # Human, random bot, tactical bot
│       │       ├── bot/             # Giao thức bot và process runner
│       │       │   └── upload/      # Lưu file, quyền bot, Docker sandbox
│       │       ├── online/          # Registry phòng, placement, ready, timeout
│       │       ├── api/             # Controller, DTO, mapper, lỗi HTTP
│       │       ├── websocket/       # Gateway, handler, message, cấu hình
│       │       ├── config/          # Cấu hình online
│       │       ├── replay/          # Log và replay cho CLI
│       │       └── app/             # Entry point console và CLI bot
│       └── test/                    # Unit, architecture, API/WS, Docker tests
├── tools/                          # Kiểm tra bot mẫu/upload, sinh hướng dẫn
└── docs/                           # Đặc tả, hướng dẫn, báo cáo các chặng
```

Các file client cũ `client/game.js`, `client/style.css` được giữ từ demo trước. Client chính hiện tải module trong `client/js/`; demo cố định được giữ riêng trong `client/demo/`.

Luồng chính: **trình duyệt → REST/WebSocket → dịch vụ phòng → GameEngine/RuleSet → snapshot → trình duyệt**. Với bot riêng, server gửi trạng thái qua BotProtocol cho Docker runner, kiểm tra action rồi áp dụng qua engine như nước đi của người chơi.

## Kiểm thử

Từ thư mục `server`:

```powershell
.\gradlew.bat test --no-daemon --console=plain
```

Từ thư mục gốc repository:

```powershell
node --test client/tests/*.test.js
```

Kiểm tra mẫu tin cậy bằng compiler/interpreter local, nếu Python/C++/Java đã cài:

```powershell
python tools/verify-bot-examples.py
```

Kiểm tra upload và chạy bot thật qua REST/WebSocket: bật Docker, tải image, khởi động server kiểm thử cổng 8081 rồi chạy từ thư mục gốc:

```powershell
node tools/verify-uploaded-bots.mjs
```

Script tạo bot và phòng thử trên server đích; mặc định `http://127.0.0.1:8081`, đổi bằng biến `RPS_TEST_URL`. Chỉ dùng với server kiểm thử. Test Docker trong Gradle có thể **SKIPPED** nếu thiếu Engine/image; skipped không có nghĩa đã kiểm chứng sandbox.

## Chương trình console và replay

Chặng 1 được giữ độc lập với server web. Trong thư mục `server`:

```powershell
.\gradlew.bat run --args="human-vs-human"
.\gradlew.bat run --args="human-vs-human --export match-local.json"
.\gradlew.bat run --args="bot-vs-bot demo:1 demo:2 --export match-bots.json"
.\gradlew.bat run --args="replay match-bots.json"
```

CLI có thể đấu hai bot và export/replay log JSON. Đây là chức năng console, không phải một chế độ bổ sung trên trang Vào trận. Bot CLI có thể chỉ định executable hoặc file JSON mảng lệnh; chỉ chạy local code bạn tin cậy.

## Giới hạn hiện tại

- Phòng và trận lưu trong RAM: restart server làm mất phòng/trận. File bot có thể còn trên ổ đĩa, nhưng danh sách và token phía client phụ thuộc storage trình duyệt.
- Chưa có tài khoản, database, bảng xếp hạng, matchmaking tự động hoặc replay đầy đủ trên web.
- Chạy nhiều instance chưa có cơ chế chia sẻ trạng thái phòng.
- Hosting chỉ chạy một container ứng dụng không tự cung cấp Docker Engine cho runner bot riêng. Muốn giữ tính năng upload bot, cần môi trường phù hợp hoặc worker Docker riêng.
- Bản hiện tại phù hợp demo/kiểm thử; public với người dùng không tin cậy cần bổ sung kiểm soát truy cập, quota/rate limit và cách ly worker.

## Tài liệu liên quan

- [Giao diện và các màn hình](docs/CLIENT-BATTLE-UI.md).
- [Upload bot và Docker](docs/BOT-UPLOAD.md).
- [Giao thức bot chi tiết](docs/BOT-PROTOCOL.md).
- [Hướng dẫn chặng 2B](docs/CHANG-2B-CLIENT.md), [đặc tả client](docs/CHANG-2B-CLIENT-SPEC.md).
- [Demo chặng 2A](docs/CHANG-2A-DEMO.md), [lộ trình Demo First](docs/CHANG-2-DEMO-FIRST.md).
- [Báo cáo kiến trúc chặng 1](docs/CHANG-1-REVIEW.md).

Tài liệu các chặng mô tả mốc phát triển và có thể chứa giao diện cũ; README và hướng dẫn giao diện hiện tại phản ánh cách sử dụng mới nhất.
