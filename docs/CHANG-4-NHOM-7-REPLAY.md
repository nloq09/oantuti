# Chặng 4 — Nhóm 7: Replay persistence

## Kết quả

Replay của trận thường và giải đấu đã kết thúc được lưu JSON trên filesystem và metadata trong PostgreSQL. REST và snapshot khán giả đọc được sau restart mà không cần phòng/bot đang chạy. Trang kết quả và giải đấu dùng archive UUID cho link replay, giữ tương thích mã phòng cũ.

- Dùng MatchLog và ReplayService hiện có, không tạo engine replay thứ hai.
- Listener chỉ chụp dữ liệu; file/DB được ghi ngoài khóa phòng, atomic rename trước transaction metadata lịch sử + replay. Pending archive giữ lại để retry khi lỗi I/O/DB, kể cả khi giải đấu đã release phòng.
- V9 bổ sung SHA-256 và index tra alias room, giữ migration V1–V8 nguyên checksum. Metadata replay cũ chưa có digest được giữ nhưng invalid.
- Đọc kiểm tra path, digest, schema/version/identity, số event và toàn bộ trạng thái cuối dựng từ event facts. File thiếu/hỏng trả lỗi rõ ràng và đánh dấu invalid, không phục vụ cache RAM cũ.
- Không trả token/capability/path/digest qua REST. Replay và spectator giữ quyền đọc công khai hiện tại; history tài khoản vẫn chỉ cho chính account. PRIVATE chưa hỗ trợ.
- Replay validator được sửa để nhận đầu hàng ngoài lượt đã được game hỗ trợ. Chỉ chấp nhận chuỗi kết thúc có kết quả đúng; vẫn từ chối event di chuyển/đổi lượt ngoài lượt.
- Fixture StageThree dùng thư mục bot tạm riêng theo context để các lần chạy test không vượt quota vì artifact tích lũy.

## Kiểm chứng

Ngày 13/10/2026, bản cuối:

- Toàn bộ backend: **393 pass, 0 fail, 0 error, 0 skipped**.
- Client: **17 pass, 0 fail, 0 skipped**.
- Trong backend: Flyway/PostgreSQL V1–V9 **7 pass**, gồm upgrade V8; replay/PostgreSQL **9 pass**; Docker bot Python/C++/Java/JAR **4 pass**; giải đấu Docker + history/ranking/replay **1 pass**.
- Replay mới kiểm tra: đọc từ store mới khi room đã release, UUID/room alias, notification lặp, seed null trong log, alias bị tái sử dụng, file thiếu/hỏng, path traversal, DB failure/retry, surrender ngoài lượt và từ chối event ngoài lượt giả mạo.
- Build bootJar **PASS**; JAR chứa V9/replay store/client mới, không chứa H2 hoặc application-test.
- Runtime localhost:8080 **PASS**: JOIN, random placement, READY, PASS, bot turn, LEAVE và lấy replay bằng archive UUID. Restart Java thật, đối chiếu toàn bộ payload replay theo UUID và room alias **PASS**; Cache-Control=no-store.
- Sau restart: /api/health, /, /replay.html, /tournaments.html đều HTTP 200. PostgreSQL local đã migrate V9 thành công; replay smoke có valid=true và digest 64 ký tự.
- GameEngine, GameState, Match và RuleSet/luật chơi **không sửa**. ReplayService thay đổi adapter đọc/ghi và validator như giải thích ở trên.

Các lần kiểm thử trung gian đã phát hiện assertion migration còn đếm V8, fixture Java/import/sequence chưa đúng và khóa output do chạy chồng hai Gradle tasks; đã sửa và chạy lại tuần tự toàn bộ suite trước khi chốt kết quả.

## Giới hạn

Đã hoàn thành phạm vi Nhóm 7, chưa tự sang Nhóm 8. Queue retry còn RAM, crash trước commit có thể mất replay chưa ghi; file không có metadata có thể trở thành orphan. Chạy một server, cấu hình REPLAY_DIRECTORY trên ổ bền vững và backup cùng PostgreSQL. Không phục hồi ván đang chơi hoặc replay RAM đã mất của bản cũ. Quota/retention/durable queue/recovery đầy đủ thuộc Nhóm 9–10.

Hướng dẫn: [REPLAY-PERSISTENCE.md](REPLAY-PERSISTENCE.md).

## File thêm/sửa
- `.env.example`
- `client/js/game/game-controller.js`
- `client/js/pages/result.js`
- `client/js/tournament/tournament-detail.js`
- `client/js/ui/toast.js`
- `docs/CHANG-4-NHOM-7-REPLAY.md`
- `docs/MATCH-HISTORY.md`
- `docs/REPLAY-PERSISTENCE.md`
- `docs/TOURNAMENT-OWNERSHIP.md`
- `README.md`
- `server/src/main/java/com/rpsbattle/api/controller/ReplayWebController.java`
- `server/src/main/java/com/rpsbattle/api/error/AuthErrorHandler.java`
- `server/src/main/java/com/rpsbattle/replay/persistence/ReplayEntity.java`
- `server/src/main/java/com/rpsbattle/replay/persistence/ReplayRepositoryAdapter.java`
- `server/src/main/java/com/rpsbattle/replay/ReplayArchiveStore.java`
- `server/src/main/java/com/rpsbattle/replay/ReplayMetadata.java`
- `server/src/main/java/com/rpsbattle/replay/ReplayRegistry.java`
- `server/src/main/java/com/rpsbattle/replay/ReplayRepository.java`
- `server/src/main/java/com/rpsbattle/replay/ReplayService.java`
- `server/src/main/java/com/rpsbattle/spectator/SpectatorService.java`
- `server/src/main/java/com/rpsbattle/tournament/TournamentService.java`
- `server/src/main/resources/application.yml`
- `server/src/main/resources/db/migration/V9__replay_integrity.sql`
- `server/src/test/java/com/rpsbattle/persistence/FlywayMigrationTest.java`
- `server/src/test/java/com/rpsbattle/persistence/JpaFoundationTest.java`
- `server/src/test/java/com/rpsbattle/replay/ReplayPersistencePostgresTest.java`
- `server/src/test/java/com/rpsbattle/tournament/persistence/DockerTournamentPersistenceTest.java`
- `server/src/test/java/com/rpsbattle/tournament/StageThreeWebFixture.java`
- `server/src/test/resources/application-test.yml`
