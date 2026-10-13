# Chặng 4 — Nhóm 4: Bot ownership + persistence

Đã lưu metadata bot theo tài khoản trong PostgreSQL, chuyển upload và quyền dùng bot riêng sang session owner/ADMIN, thêm My Bots API/rename/soft delete và nhập bot legacy có bằng chứng token. Hai chế độ lobby vẫn là Hai người và Đấu với bot có sẵn; mỗi người có thể chọn bot của mình.

## Thay đổi và ranh giới

- Domain `BotMetadata`/`BotRepository` tách khỏi JPA; entity/adapter dùng bảng bots của migration V2. Không sửa schema/checksum V1–V7.
- Owner từ principal, không nhận ownerUserId từ client. Server không xuất token runner/path/lệnh Docker trong DTO. Các request botToken cũ không cấp quyền dùng bot của account khác.
- Rename/delete có row lock và transaction. Soft delete giữ metadata/owner/artifact cho giải đã đăng ký; bot xóa không dùng để đăng ký mới. Rút participant dựa vào DB ownership, kể cả artifact đã hỏng/mất.
- `LegacyBotImportService` chỉ claim khi id/token đúng và source còn; không auto claim thư mục. INSERT bảo vệ primary key chống hai account cùng ghi đè owner. Import lại cùng account idempotent; không phục hồi bot DELETED bằng import.
- Upload DB rollback dọn đúng artifact mới qua transaction synchronization; crash recovery/orphan reconciliation chưa thuộc nhóm này.
- Client lấy danh sách từ My Bots API, chỉ lưu preferred id trong sessionStorage. Thêm đổi tên/xóa mềm và nút import legacy trong Bot của tôi; không bỏ local proof khi import thất bại. Match/tournament mutation gửi cookie + CSRF, chỉ gửi botId.
- Guest chơi tay/bot sẵn giữ seat token. Creator ownership/persistence của giải ở Nhóm 6; WebSocket/ghế chưa account-bound. UI Login/Register/Profile ở Nhóm 8, hiện đăng nhập API/helper theo [BOT-OWNERSHIP.md](BOT-OWNERSHIP.md).
- Chưa thêm endpoint build/log riêng; compile/test thực thi qua runner khi tạo trận, sau kiểm tra owner. Trạng thái UPLOADED không đồng nghĩa đã build thành công.

## Kết quả kiểm tra cuối

- Backend: **364 pass, 0 fail, 0 error, 0 skipped**.
- Client: **16 pass, 0 fail, 0 skipped**.
- Flyway migration PostgreSQL: **5 pass**, có fresh V1–V7, checksum/constraints và upgrade giữ dữ liệu.
- BotRepositoryPostgresTest: **3 pass** trên PostgreSQL 17 thật trong Docker (commit, concurrent claims, rollback/missing artifact).
- DockerBotIntegrationTest: **4 pass, 0 skipped** cho Python/C++/Java source/JAR.
- `bootJar`: **PASS**. Node syntax-check hai script verify-uploaded-bots/verify-tournaments: PASS. Script stress dài không được chạy lại trong nhóm này; Docker integration đã chạy trong full suite.
- API test kiểm tra stolen token không vượt owner, guest không dùng bot riêng, guest human mode vẫn chạy, owner/ADMIN, DTO không lộ secret, giữ file/roster khi delete, import idempotency/ownership conflict, invalid CSRF trong MockMvc và missing CSRF qua HTTP thật.
- `git diff --check`: PASS.
- GameEngine, game domain và RuleSet/rules: **không sửa**; toàn bộ regression core pass.

Lệnh xác nhận: `server/gradlew.bat test bootJar --no-daemon --console=plain` (JDK 24, Gradle test profile) và `node --test client/tests/*.test.js` (Node 24). Gradle build ở thư mục ngoài OneDrive như cấu hình hiện tại. PostgreSQL/container kiểm thử do Testcontainers quản lý; không chạm dữ liệu production.

## File thêm

- `docs/BOT-OWNERSHIP.md`
- `server/src/main/java/com/rpsbattle/api/controller/AccountBotController.java`
- `server/src/main/java/com/rpsbattle/bot/ownership/BotMetadata.java`
- `server/src/main/java/com/rpsbattle/bot/ownership/BotRepository.java`
- `server/src/main/java/com/rpsbattle/bot/ownership/LegacyBotImportService.java`
- `server/src/main/java/com/rpsbattle/bot/ownership/OwnedBotService.java`
- `server/src/main/java/com/rpsbattle/bot/persistence/BotEntity.java`
- `server/src/main/java/com/rpsbattle/bot/persistence/BotRepositoryAdapter.java`
- `server/src/main/java/com/rpsbattle/bot/persistence/JpaBotRepository.java`
- `server/src/test/java/com/rpsbattle/bot/ownership/BotRepositoryPostgresTest.java`
- `docs/CHANG-4-NHOM-4-BOTS.md`

## File sửa

- `README.md`
- `client/bots.html`
- `client/js/api/auth-api.js`
- `client/js/api/bot-api.js`
- `client/js/api/match-api.js`
- `client/js/pages/bots.js`
- `client/js/pages/lobby.js`
- `client/js/tournament/tournament-api.js`
- `client/js/tournament/tournament-detail.js`
- `client/js/tournament/tournament-list.js`
- `client/js/ui/toast.js`
- `client/tests/bot-api.test.js`
- `client/tests/tournament-api.test.js`
- `docs/AUTH.md`
- `docs/BOT-UPLOAD.md`
- `server/src/main/java/com/rpsbattle/api/controller/BotUploadController.java`
- `server/src/main/java/com/rpsbattle/api/controller/MatchController.java`
- `server/src/main/java/com/rpsbattle/api/controller/TournamentController.java`
- `server/src/main/java/com/rpsbattle/api/error/AuthErrorHandler.java`
- `server/src/main/java/com/rpsbattle/auth/SecurityConfig.java`
- `server/src/main/java/com/rpsbattle/bot/upload/BotUploadService.java`
- `server/src/test/java/com/rpsbattle/bot/upload/BotUploadApiTest.java`
- `server/src/test/java/com/rpsbattle/tournament/StageThreeWebFixture.java`
- `server/src/test/java/com/rpsbattle/tournament/TournamentControllerTest.java`
- `tools/verify-tournaments.mjs`
- `tools/verify-uploaded-bots.mjs`

Hoàn thành Nhóm 4 và dừng theo yêu cầu “Không tự sang nhóm tiếp theo. Chờ tôi review.” Nhóm tiếp theo là Match persistence + History + Stats.
