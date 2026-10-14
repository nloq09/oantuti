# Chặng 5 — Nhóm 3: khóa bot và seeding

Giải chạy từ bản sao bot đã khóa theo SHA-256, không lấy source đang thay đổi hoặc bot upload mới. Thứ tự hạt giống hỗ trợ RANDOM, MANUAL, RANKING và được lưu khi giải chuyển READY.

## Khóa phiên bản

Đơn mới snapshot sourceHash khi đăng ký. Khi duyệt, server kiểm tra source còn đúng hash rồi sao chép vào `data/bots/{botId}/versions/{artifactHash}/{filename}`; lưu sourceHash, artifactHash, lockedAt và botVersionId. DRAFT thêm trực tiếp là approval theo luồng cũ nên cũng khóa ngay. Hash của source và artifact bằng nhau vì artifact ở chặng này là bản sao source/JAR upload, không phải binary compiler sinh ra.

`botVersionId` vẫn là UUID upload bất biến hiện tại; mỗi upload mới có ID riêng. Giải giữ ID/hash đã nhận, không tự chuyển sang upload mới. Chưa bổ sung chức năng cập nhật nhiều version trong cùng một bot ID.

Trước start và trước launch từng trận, server kiểm tra bản sao đúng hash. File thiếu/hỏng trả BOT_ARTIFACT_CHANGED hoặc khiến launch thất bại; không tự lấy source mới để bù. Source gốc đổi sau khi khóa không đổi bot trong giải. Token, đường dẫn và nội dung source không trả qua API; hash/version ID là metadata công khai của giải.

V12 không thể suy ra nội dung đã duyệt trước đây từ SQL: registration/participant cũ giữ hash null. Khi chuẩn bị start/READY, server khóa artifact hiện có của các participant cũ. Giải đã kết thúc giữ kết quả cũ; giải đang dở vẫn theo RECOVERY_REQUIRED, không tự resume. Hash chỉ kiểm tra integrity, không phải chữ ký xác nhận tác giả.

Backup phải giữ cả DB và toàn thư mục bots, gồm `versions/`; công cụ backup hiện có đã bao gồm thư mục con. Không sửa V1–V11 hoặc migration đã áp dụng.

## Seeding

Chủ giải/ADMIN chọn cách xếp trong Tổng quan, sửa trước READY (DRAFT/REGISTRATION/CHECK_IN):

- **RANDOM:** cùng participant IDs và seed cho cùng thứ tự, không phụ thuộc thứ tự list đầu vào. Seed hỗ trợ signed 64-bit; client/API trả và gửi dạng chuỗi để không mất chính xác trong JavaScript.
- **MANUAL:** mỗi participant đang được duyệt cần số riêng 1..N; không trùng hoặc thiếu. Nếu một số bot không check-in, giữ thứ tự tương đối của bot còn lại và đánh số seedNumber 1..N lại cho roster thực tế.
- **RANKING:** tổng điểm từ các giải PUBLIC FINISHED theo bot ID, rồi số thắng, quân còn và participant ID để phá hòa ổn định. Bot chưa có thành tích dùng điểm 0. Sử dụng scoring/ranking service hiện có.

Thứ tự cuối khóa khi chuyển READY; start từ READY giữ nguyên, không xếp lại theo kết quả mới. Quick-start DRAFT chuẩn bị/khóa trước khi tạo lịch. participant.seed là RNG seed cũ của bot và không bị thay đổi; seedNumber chỉ là thứ tự hạt giống. Round Robin vẫn tạo đủ các cặp, chưa bổ sung vòng/BYE/bracket trong nhóm này.

## API và database

`POST /api/tournaments/{id}/seeding` cần session + CSRF và owner/ADMIN:

```json
{"strategy":"MANUAL","seed":"9223372036854775807","manualSeeds":{"participant-uuid-a":2,"participant-uuid-b":1}}
```

RANDOM/RANKING không cần manualSeeds. List/detail/export/WebSocket có thêm seedingStrategy, seedingSeed, participant.artifactHash, seedNumber/manualSeed, registration.sourceHash/artifactHash/lockedAt. Cấu hình sau READY trả TOURNAMENT_LOCKED; mapping thiếu/trùng/sai trả INVALID_MANUAL_SEEDS.

Migration V12 thêm cấu hình/seed/hash/lockedAt và constraints: unique seedNumber theo giải, số seed dương, hash 64 ký tự, lock có source/artifactHash bằng nhau và timestamp. Legacy data được giữ. Adapter lưu seed/hash, xử lý hoán đổi seed trong cùng transaction, restore theo seedNumber. DB lỗi rollback cấu hình, seed, hash, roster, revision trong RAM và không phát notification. Frozen file đã tạo có thể giữ lại khi transaction thất bại; bản sao idempotent, chưa có participant được commit sử dụng nó.

## Kiểm thử và build

Ngày 14/10/2026: **433 backend passed, 0 failed, 0 skipped; 24 client passed; 10 migration tests passed**. `bootJar` thành công. PostgreSQL cục bộ áp dụng V12 sau backup.

Kiểm thử bao gồm deterministic random với list đảo thứ tự, manual thiếu/trùng, ranking/tie, quyền API, số 64-bit, READY khóa cấu hình, artifact bị sửa không được start, bản sao giữ đúng khi source gốc đổi/upload mới, restore hash và seed qua PostgreSQL, swap seed và rollback DB. Docker Python chạy được bản sao cũ khi source gốc bị thay bằng source không hợp lệ.

Giao diện: chọn MANUAL, đảo hai bot, lưu seed Long.MAX_VALUE, khóa READY và xác nhận #1/#2 cùng các trường cấu hình bị khóa; không có JavaScript error. Kiểm tra native restart giữ nguyên version/hash/order/seed.

GameEngine/RuleSet và các package game/rule/rules không thay đổi.

## File thêm/sửa

Thêm `SeedingStrategy`, `seeding/SeedingService`, migration V12; tests `SeedingServiceTest`, `TournamentSeedingApiTest`, `BotVersionLockTest`, `client/tests/tournament-seeding.test.js`.

Sửa Tournament/Participant/Registration, OwnedTournamentService/TournamentService/TournamentController, TournamentMatchExecutor, BotUploadService/OwnedBotService; persistence TournamentEntity/Metadata/ParticipantEntity/RegistrationEntity/RepositoryAdapter/PersistenceCoordinator; tests DockerBotIntegrationTest, FlywayMigrationTest, JpaFoundationTest, OwnedTournamentRollbackTest, TournamentPersistencePostgresTest.

Client: tournament.html, tournament-detail.js, tournament-api.js, toast.js. Tài liệu: README, Deployment và báo cáo này.

Nhóm tiếp theo: **Round Robin nâng cao**. Dừng sau nhóm 3 theo kế hoạch chặng 5.
