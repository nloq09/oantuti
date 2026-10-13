# Chặng 4 — Nhóm 6: Tournament persistence + ownership

Đã chuyển API quản lý giải sang owner account/ADMIN, giữ đăng ký mở cho bot của người dùng khác khi DRAFT, lưu giải/roster/schedule/results trong PostgreSQL và tính lại ranking từ persisted results sau restart.

## Thay đổi

- Thêm TournamentAuthorizationService và OwnedTournamentService. Principal là nguồn owner, body/header/capability client không cấp quyền. Mọi mutation giải cần session + CSRF, public reads/spectator giữ nguyên.
- Create/edit/start/cancel commit DB trước notification; rollback memory khi save thất bại. Scheduler không thấy start chưa commit. Bản đầu chỉ hỗ trợ PUBLIC; không nạp row PRIVATE có sẵn thành public.
- JPA entities/repository/snapshot tách khỏi game engine. Snapshot transaction lưu parent/roster/schedule và match history liên quan, FK composite kiểm tra đúng giải. Không lưu token, source path hoặc command trong bảng giải.
- Pair id là UUID ổn định theo tournament/participant pair, phù hợp PK UUID của schema V4. match_id FK dùng archive UUID Nhóm 5; live room id vẫn dùng cho spectator/replay hiện tại.
- Startup nạp DRAFT/FINISHED/CANCELLED, khôi phục owner/roster/seeds/results/ranking. RUNNING/READY thành RECOVERY_REQUIRED; cặp đang chạy cũ FAILED/SERVER_RESTART, không tự chạy lại.
- Client nhận ownerUserId và current account để bật nút; bỏ đọc/ghi localStorage owner capability. Người dùng vẫn đăng ký/rút bot của họ, owner/ADMIN quản lý draft và start/cancel.
- Các bảng/index/constraints của V4–V8 đủ dùng: không sửa hoặc thêm migration nhóm này. Hướng dẫn: [TOURNAMENT-OWNERSHIP.md](TOURNAMENT-OWNERSHIP.md).

## Kết quả kiểm tra

- Backend full suite: **383 pass, 0 fail, 0 error, 0 skipped**.
- Client full suite: **17 pass, 0 fail, 0 skipped**.
- Flyway migration PostgreSQL V1–V8: **6 pass**.
- TournamentPersistencePostgresTest: **5 pass** (draft restore, finished/ranking/history/idempotency, recovery, FK rollback cả tournament/match, không công bố PRIVATE).
- TournamentOwnershipTest: **2 pass** HTTP/session thật (owner/ADMIN, token/claimed role không vượt quyền, CSRF, người khác join/rút bot).
- OwnedTournamentRollbackTest: **1 pass**, start commit lỗi không đổi DRAFT hoặc notify.
- DockerTournamentPersistenceTest: **1 pass, 0 skipped** trên Docker Python và PostgreSQL 17 thật; forfeit lưu tour result, history, stats và restore ranking.
- DockerBotIntegrationTest: **4 pass, 0 skipped** Python/C++/Java source/JAR.
- bootJar: **PASS**. Syntax checks client tournament detail/stress script và git diff --check: PASS.
- Runtime local: health UP, /tournaments.html và /api/tournaments HTTP 200, smoke WebSocket random placement/ready/bot turn/surrender PASS; DB vẫn ở migration V8.
- GameEngine, GameState/game domain, RuleSet/rules: **không sửa**. Tournament lifecycle/scheduler id và metadata hooks thay đổi; regression core vẫn pass.

Một lỗi ban đầu do scheduler dùng id dạng tournament-id-N trong khi schema yêu cầu UUID đã được sửa bằng stable UUID theo cặp. Sau sửa đã chạy lại full suite thành công. Các snapshot DB lỗi được giữ cho retry, không đẩy token/password vào log.

## Giới hạn

Chạy một server/scheduler cho DB hiện tại. Pending runtime queue còn RAM, chưa durable outbox hoặc distributed leadership. Crash trước commit có thể mất update chưa ghi; recovery đầy đủ ở Nhóm 9–10. Giải RECOVERY_REQUIRED chỉ có thể cancel/tạo giải mới ở bản này. Replay persistence thuộc Nhóm 7, UI Login/Profile/History thuộc Nhóm 8. Dữ liệu legacy giải của phiên bản trước chỉ có RAM/capability, không tự đoán owner để import; artifact bot/database vẫn giữ nguyên.

Server local được build/cập nhật bằng .env hiện có, PostgreSQL giữ volume và cổng 15432. Metadata public của phiên server cũ đã sao lưu vào server/build/local-run/legacy-tournaments-before-group6.json trước upgrade; số giải cũ là 0. Log/PID/snapshot và credentials nằm trong file ignore, không commit.

## File thêm

- `docs/TOURNAMENT-OWNERSHIP.md`
- `server/src/main/java/com/rpsbattle/tournament/OwnedTournamentService.java`
- `server/src/main/java/com/rpsbattle/tournament/TournamentAuthorizationService.java`
- `server/src/main/java/com/rpsbattle/tournament/persistence/JpaTournamentMatchRepository.java`
- `server/src/main/java/com/rpsbattle/tournament/persistence/JpaTournamentParticipantRepository.java`
- `server/src/main/java/com/rpsbattle/tournament/persistence/JpaTournamentRepository.java`
- `server/src/main/java/com/rpsbattle/tournament/persistence/TournamentEntity.java`
- `server/src/main/java/com/rpsbattle/tournament/persistence/TournamentMatchEntity.java`
- `server/src/main/java/com/rpsbattle/tournament/persistence/TournamentMetadata.java`
- `server/src/main/java/com/rpsbattle/tournament/persistence/TournamentParticipantEntity.java`
- `server/src/main/java/com/rpsbattle/tournament/persistence/TournamentPersistenceCoordinator.java`
- `server/src/main/java/com/rpsbattle/tournament/persistence/TournamentRepository.java`
- `server/src/main/java/com/rpsbattle/tournament/persistence/TournamentRepositoryAdapter.java`
- `server/src/test/java/com/rpsbattle/tournament/TournamentOwnershipTest.java`
- `server/src/test/java/com/rpsbattle/tournament/persistence/DockerTournamentPersistenceTest.java`
- `server/src/test/java/com/rpsbattle/tournament/persistence/OwnedTournamentRollbackTest.java`
- `server/src/test/java/com/rpsbattle/tournament/persistence/TournamentPersistencePostgresTest.java`
- `docs/CHANG-4-NHOM-6-TOURNAMENT.md`

## File sửa

- `README.md`
- `client/js/tournament/ranking-renderer.js`
- `client/js/tournament/tournament-api.js`
- `client/js/tournament/tournament-detail.js`
- `client/js/ui/toast.js`
- `client/tests/tournament-api.test.js`
- `docs/AUTH.md`
- `docs/BOT-OWNERSHIP.md`
- `server/src/main/java/com/rpsbattle/api/controller/TournamentController.java`
- `server/src/main/java/com/rpsbattle/auth/SecurityConfig.java`
- `server/src/main/java/com/rpsbattle/history/MatchPersistenceCoordinator.java`
- `server/src/main/java/com/rpsbattle/tournament/TournamentService.java`
- `server/src/main/java/com/rpsbattle/tournament/domain/Tournament.java`
- `server/src/main/java/com/rpsbattle/tournament/domain/TournamentMatch.java`
- `server/src/main/java/com/rpsbattle/tournament/domain/TournamentParticipant.java`
- `server/src/main/java/com/rpsbattle/tournament/domain/TournamentStatus.java`
- `server/src/main/java/com/rpsbattle/tournament/execution/TournamentMatchExecutor.java`
- `server/src/main/java/com/rpsbattle/tournament/registry/TournamentRegistry.java`
- `server/src/main/java/com/rpsbattle/tournament/scheduler/RoundRobinScheduler.java`
- `server/src/test/java/com/rpsbattle/tournament/StageThreeWebFixture.java`
- `server/src/test/java/com/rpsbattle/tournament/TournamentControllerTest.java`
- `tools/verify-tournaments.mjs`

Hoàn thành Nhóm 6, dừng theo prompt “Không tự sang nhóm tiếp theo. Chờ tôi review.” Nhóm kế tiếp là Replay persistence.
