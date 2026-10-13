# Chặng 4 — Nhóm 5: Match persistence + History + Stats

Đã lưu metadata trận/ghế trong PostgreSQL, gắn ownership theo session hoặc owner bot của scheduler giải, thêm lịch sử có filter/phân trang và thống kê theo account. Không persist toàn GameState, không thêm UI History trước Nhóm 8.

## Thay đổi chính

- Domain metadata/repository tách JPA. Transaction ghi match và participants; kết quả FINISHED không bị ghi đè bởi thông báo lặp/snapshot cũ. Stats query từ DB, không tăng counter mỗi lần nhận event.
- Archive UUID khác mã phòng ngắn, tránh ghi đè lịch sử khi room code được dùng lại. REST create/join trả thêm historyId; các seat token và WebSocket hiện tại giữ giao thức cũ.
- Chủ ghế và controller được lưu riêng: userId, botId, HUMAN/BUILTIN_BOT/UPLOADED_BOT. Khách/bot sẵn thắng vẫn có winnerSeat để account bên kia nhận LOSS dù winnerUserId null.
- GET /api/account/matches: all/win/loss/draw/human/bot, page 0–100000, size 1–100; chỉ principal hiện tại. GET /api/account/matches/{historyId}: owner ghế, account khác 404. GET /api/account/stats: played/wins/draws/losses/winRate/totalTurns.
- Trận chưa FINISHED, lỗi hạ tầng, UNKNOWN và self-play cùng account không tính stats. Tỷ lệ thắng là phần trăm, làm tròn 2 chữ số, 0 khi chưa có trận.
- Lifecycle capture metadata bất biến trong room lock; DB I/O ngoài lock. Pending retry giữ snapshot mới nếu DB lỗi hoặc update đến khi đang ghi. Final capture diễn ra trước release của giải.
- V8 bổ sung room_id/winner_seat/index, backfill lịch sử cũ; giữ checksum V1–V7. Hướng dẫn chi tiết [MATCH-HISTORY.md](MATCH-HISTORY.md).

## Kết quả kiểm tra

- Backend full suite: **374 pass, 0 fail, 0 error, 0 skipped**.
- Client full suite: **17 pass, 0 fail, 0 skipped**.
- Flyway PostgreSQL: **6 pass**, gồm fresh V1–V8 và upgrade V7→V8 giữ dữ liệu room/winner/turn count.
- MatchHistoryPostgresTest: **4 pass** trên PostgreSQL 17 thật: filter/phân trang/thống kê, idempotency/stale final, bot soft-delete/history và trùng mã phòng nhưng khác archive UUID.
- AccountMatchHistoryTest: **3 pass** HTTP/Cookie/WebSocket thật: account binding, chống spoof userId, lịch sử chỉ của owner, khách thắng, bot sẵn, validation/401/404.
- MatchPersistenceCoordinatorTest: **2 pass**: retry final sau release, capture mới đến trong lúc ghi và xác nhận DB write không giữ room monitor.
- DockerBotIntegrationTest: **4 pass, 0 skipped**, Python/C++/Java source/JAR.
- bootJar: **PASS**; git diff --check: PASS.
- Runtime local: server health UP, PostgreSQL áp dụng V8, smoke WebSocket placement/ready/bot turn/surrender PASS; đã xác nhận 1 trận FINISHED và 2 participant rows trong DB thật.
- GameEngine, GameState/game domain và RuleSet/rules **không sửa**. OnlineMatch/OnlineMatchService là lifecycle ứng dụng, chỉ thêm identity/archive/capture hook; các regression movement/combat/victory/placement vẫn pass.
- Assertion JpaFoundationTest cũ đếm 7 migrations được cập nhật thành 8 rồi chạy lại full suite thành công.

Lệnh: server/gradlew.bat test --no-daemon --console=plain; server/gradlew.bat bootJar --no-daemon --console=plain; node --test client/tests/*.test.js. JDK 24, Node 24, Docker Linux/PostgreSQL 17.

## Ranh giới và vận hành

Lịch sử đã commit tồn tại qua restart. Pending queue còn RAM và không thay durable outbox; crash/kill trước commit có thể mất pending. Bàn cờ/deadline/seat/socket cũ chưa resume sau restart. Tournament metadata/ownership ở Nhóm 6, replay persistence ở Nhóm 7, UI Login/History/Profile ở Nhóm 8, queue/recovery ở Nhóm 9–10. Không tự claim trận khách cho người đăng nhập sau đó. Account metadata binding chưa thay cơ chế capability WebSocket hiện có.

Server local đã được build/cập nhật; Docker DB vẫn giữ volume và dùng cổng 15432 như cấu hình .env/local override hiện tại. Không ghi password/session/token vào report hoặc Git. Các log/PID/config chạy thử ở server/build/local-run và bị ignore.

## File thêm

- `client/tests/history-api.test.js`
- `docs/MATCH-HISTORY.md`
- `server/src/main/java/com/rpsbattle/api/controller/AccountHistoryController.java`
- `server/src/main/java/com/rpsbattle/history/MatchHistoryRepository.java`
- `server/src/main/java/com/rpsbattle/history/MatchMetadataMapper.java`
- `server/src/main/java/com/rpsbattle/history/MatchPersistenceCoordinator.java`
- `server/src/main/java/com/rpsbattle/history/MatchSeatService.java`
- `server/src/main/java/com/rpsbattle/history/UserStatsService.java`
- `server/src/main/java/com/rpsbattle/history/domain/MatchMetadata.java`
- `server/src/main/java/com/rpsbattle/history/persistence/JpaMatchRepository.java`
- `server/src/main/java/com/rpsbattle/history/persistence/JpaParticipantRepository.java`
- `server/src/main/java/com/rpsbattle/history/persistence/MatchEntity.java`
- `server/src/main/java/com/rpsbattle/history/persistence/MatchHistoryRepositoryAdapter.java`
- `server/src/main/java/com/rpsbattle/history/persistence/ParticipantEntity.java`
- `server/src/main/resources/db/migration/V8__add_match_history_identity.sql`
- `server/src/test/java/com/rpsbattle/history/AccountMatchHistoryTest.java`
- `server/src/test/java/com/rpsbattle/history/MatchHistoryPostgresTest.java`
- `server/src/test/java/com/rpsbattle/history/MatchPersistenceCoordinatorTest.java`
- `docs/CHANG-4-NHOM-5-HISTORY.md`

## File sửa

- `README.md`
- `client/js/api/account-api.js`
- `docs/AUTH.md`
- `docs/BOT-OWNERSHIP.md`
- `server/src/main/java/com/rpsbattle/api/controller/MatchController.java`
- `server/src/main/java/com/rpsbattle/api/error/AuthErrorHandler.java`
- `server/src/main/java/com/rpsbattle/online/OnlineMatch.java`
- `server/src/main/java/com/rpsbattle/online/OnlineMatchService.java`
- `server/src/main/java/com/rpsbattle/tournament/execution/TournamentMatchExecutor.java`
- `server/src/test/java/com/rpsbattle/persistence/FlywayMigrationTest.java`
- `server/src/test/java/com/rpsbattle/persistence/JpaFoundationTest.java`

Dừng sau Nhóm 5 theo yêu cầu của prompt “Không tự sang nhóm tiếp theo. Chờ tôi review.” Nhóm kế tiếp là Tournament persistence + ownership.
