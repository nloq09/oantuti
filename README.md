# RPS Battle — Oẳn Tù Tì chiến thuật 9×9

Game chiến thuật theo lượt với Búa, Bao và Kéo. Server Java xử lý luật, tài khoản, giải đấu và bot; client HTML/CSS/JavaScript chạy trên trình duyệt, đồng bộ qua REST/WebSocket.

## Tài liệu

**[Mở bộ tài liệu theo vai trò →](docs/README.md)**

Tài liệu là các file Markdown đọc trực tiếp trên GitHub. Ảnh, ví dụ và liên kết nằm trong repository; cập nhật tài liệu không cần dựng website hoặc deploy lại game.

- **Người chơi:** [bắt đầu](docs/player/getting-started.md), [luật](docs/player/game-rules.md), [cách chơi](docs/player/how-to-play.md), [FAQ](docs/player/faq.md).
- **Viết bot:** [Bot Guide](docs/bot/bot-guide.md), [Protocol](docs/bot/protocol.md), [ví dụ Python/C++/Java](docs/bot/examples.md), [build/test/submit](docs/bot/build-test-submit.md), [giới hạn](docs/bot/limits.md).
- **Tham gia giải:** [toàn bộ luồng](docs/player/tournament-flow.md), [thể thức](docs/tournament/formats.md), [đăng ký](docs/tournament/registration.md), [coding](docs/tournament/coding-phase.md), [validation](docs/tournament/validation.md), [check-in](docs/tournament/check-in.md), [bracket/ranking](docs/tournament/bracket-ranking.md).
- **Chủ giải:** [tạo giải](docs/organizer/create-tournament.md), [đăng ký](docs/organizer/registration-management.md), [bài nộp](docs/organizer/submission-management.md), [kiểm thử chính thức](docs/organizer/validation-management.md), [điều hành](docs/organizer/tournament-operation.md).
- **Admin:** [người dùng](docs/admin/users.md), [bot](docs/admin/bots.md), [worker](docs/admin/workers.md), [bảo mật](docs/admin/security.md), [hệ thống](docs/admin/system-operations.md).
- **Developer:** [kiến trúc](docs/technical/architecture.md), [API](docs/technical/api.md), [WebSocket](docs/technical/websocket.md), [database](docs/technical/database.md), [VPS Ubuntu/Nginx/HTTPS](docs/technical/deployment.md), [backup](docs/technical/backup-recovery.md).

[Thuật ngữ](docs/glossary.md) · [Coverage và giới hạn kiểm chứng](docs/DOCUMENTATION-COVERAGE.md).

## Chức năng

- Mỗi bên 6 Búa, 6 Bao, 6 Kéo; đặt ở một phía đường chéo, chừa cứ điểm. Đi một ô theo 8 hướng. Cùng loại giữ nguyên vị trí; chiếm cứ điểm hoặc ăn hết quân để thắng.
- Chơi tay với bạn bè hoặc bot chiến thuật. Bot riêng có thể điều khiển ghế của bạn trong cả hai mode.
- Tài khoản, bot thuộc tài khoản, lịch sử và replay đã lưu.
- Năm thể thức giải: vòng tròn, loại trực tiếp, vòng bảng rồi loại trực tiếp, Swiss, đồng đội. Hỗ trợ BO1/BO3/BO5, coding workspace, bài khóa, validation và check-in theo cấu hình giải.
- Khu ADMIN riêng; remote worker nhận chạy trận bot được giao. Remote execution hiện dành cho BOT_ONLY có submission LOCKED; build/test/final validation vẫn cần sandbox trên Central.

## Chạy local

Yêu cầu: JDK 21 hoặc JDK 24 phù hợp Gradle Wrapper hiện tại; PostgreSQL đang chạy và đã có database/role ứng dụng. Người chơi dùng server có sẵn chỉ cần trình duyệt. Docker Linux chỉ cần khi vận hành sandbox bot riêng hoặc build/test bot.

Chuẩn bị database theo [hướng dẫn triển khai](docs/technical/deployment.md). Các biến bên dưới là **placeholder** cần thay bằng cấu hình trên máy của bạn; không commit file chứa mật khẩu.

PowerShell, từ thư mục repository:

```powershell
$env:DB_URL = 'jdbc:postgresql://localhost:5432/rpsbattle'
$env:DB_USERNAME = '<role-ung-dung>'
$env:DB_PASSWORD = '<mat-khau-database>'
cd server
.\gradlew.bat bootRun --console=plain
```

Bash:

```sh
export DB_URL='jdbc:postgresql://localhost:5432/rpsbattle'
export DB_USERNAME='<role-ung-dung>'
export DB_PASSWORD='<mat-khau-database>'
cd server
./gradlew bootRun --console=plain
```

Mở [http://localhost:8080](http://localhost:8080). Server phục vụ cả client và API, không cần frontend server riêng. Giữ terminal mở, Ctrl+C để dừng. Sau khi sửa client cần chạy/build lại để server lấy tài nguyên mới.

Đổi cổng bằng `--args="--server.port=8081"`. Kiểm tra sức khỏe: `GET /api/health` hoặc `/actuator/health`. Flyway quản lý schema từ [các migration](server/src/main/resources/db/migration/); không sửa/xóa migration đã áp dụng.

## Build và kiểm tra

Từ `server`:

```sh
./gradlew :test :worker:test :bootJar --console=plain
```

Trên Windows dùng `.\gradlew.bat`. Từ gốc repo, kiểm tra client và tài liệu:

```sh
node --test client/tests/*.test.js
node tools/check-documentation.mjs
python tools/verify-bot-examples.py
```

Lệnh Python biên dịch/chạy mẫu tin cậy của repo, cần Python, g++ và JDK; không chạy source của người khác trên host. Kiểm tra local/contract không thay cho Docker hoặc kiểm thử tải thực tế. Những test bị SKIPPED do thiếu Docker/image không được xem là đã kiểm chứng sandbox.

## Cấu trúc project

- `client/`: giao diện game và quản trị.
- `server/`: Spring Boot, engine/rules, auth, tournament, persistence và sandbox Central.
- `worker/`: ứng dụng remote worker riêng.
- `docs/`: hướng dẫn Markdown theo nhóm người dùng và vận hành.
- `infra/`, `scripts/`, `tools/`: cấu hình triển khai, vận hành và công cụ kiểm chứng.


