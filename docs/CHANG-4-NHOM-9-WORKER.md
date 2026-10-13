# Chặng 4 — Nhóm 9: Worker, Queue, Rate Limit và Monitoring

## Hoàn thành

- MatchWorker/LocalMatchWorker dùng phòng và engine hiện có; khởi tạo trận giải trên executor riêng, không chạy trận dài trong HTTP.
- ExecutionQueue FIFO có giữ chỗ cả lịch giải, giới hạn pending và dedup; WorkerManager giữ giới hạn global trong suốt trận, không chỉ lúc launch. Mặc định 4 running/256 pending, trả HTTP 503 SERVER_BUSY khi hết chỗ. Rollback DB trả lại chỗ đã giữ.
- Hủy trước khi launch không tạo phòng; lỗi launch giải phóng slot, các trận khác tiếp tục. Cancel/finish/timeout trả slot sau khi thu nhận kết quả và dọn phòng.
- Rate limit HTTP cho login/register/upload/create/join/tournament create. Chống brute force theo số login 401; trả 429, Retry-After và retryAfterSeconds. Bảng key có giới hạn, không tin client X-Forwarded-For.
- WebSocket giới hạn PING/STATE_REQUEST/action và tổng message theo IP, giữ quota khi reconnect; input tối đa 16 KiB. Message bị chặn không gọi gateway/engine.
- Actuator health công khai tối giản; info/metrics và system API chỉ ADMIN. Metrics account session, room, tournament, bot, timeout/login failure, socket và queue. System API thêm workers/capacity/state.
- Request ID/MDC và event log dùng ID/resource đã kiểm tra; không log body/query/password/session ID/bot capability. Không dùng ID cá nhân làm metric label.
- Graceful lifecycle ngừng nhận phòng/job, DRAINING, checkpoint/flush khi DB còn hoạt động, đóng bot/executor sau flush. Trận chưa kết thúc ghi lỗi hạ tầng; lịch đang dở RECOVERY_REQUIRED, không tự resume; giữ kết quả trận đã xong. Shutdown báo lỗi persistence rõ trong log khi store không hoạt động.
- Client hiển thị lỗi quá tải/thao tác nhanh bằng tiếng Việt, không tự retry mutation. Không thêm panel/status UI vào các trang đã được tinh gọn.

## Kiểm thử

Ngày 13/10/2026:

- Toàn bộ backend: **406 pass, 0 fail/error, 0 skipped**.
- Toàn bộ client: **21 pass, 0 fail, 0 skipped**.
- Bao gồm Flyway PostgreSQL **7 pass**, Docker bot Python/C++/Java/JAR **4 pass**, Docker tournament → history/ranking/replay **1 pass**. Không thêm migration; Flyway V1–V9 được giữ.
- Test mới: atomic queue admission, overload, duplicate job, giữ slot suốt trận, cancellation, launch failure, worker chạy ngoài caller, drain; quota hết hạn/concurrency/bounded storage; chống spoof forwarding/brute force, chặn mutation, CSRF, admin-only metrics; PING/reconnect/socket metrics; timeout counter; thứ tự checkpoint/flush/close và lỗi một store không ngăn đóng executor.
- Test rollback giải hiện có được bổ sung kiểm tra reservation không rò rỉ khi commit lỗi. Test launch failure cập nhật số thông báo vì mỗi worker hiện thông báo thay đổi riêng, vẫn kiểm tra toàn bộ lịch kết thúc đúng.
- bootJar **PASS**. JAR mới khởi động với PostgreSQL local, server UP cổng 8080.
- Smoke HTTP/WS trên JAR: guest vs bot có sẵn, join/random placement/ready/pass/bot turn/leave, persisted replay UUID **PASS**; /api/health và /actuator/health UP không lộ components, X-Request-Id hợp lệ, metrics/admin từ chối khách **PASS**. Socket thật gửi ba PING nhận hai PONG và một RATE_LIMITED **PASS**.
- Không thay đổi layout trang, không cần kiểm tra ảnh giao diện; hành vi lỗi client được kiểm chứng bằng test API/error mapping.
- Diff GameEngine/RuleSet/game core: **không sửa**. Worker gọi luồng engine hiện có.

Lần kiểm thử đầu có một assertion cũ kỳ vọng chỉ một lần thông báo thay đổi giải; đã cập nhật theo lifecycle worker và chạy lại targeted/full suite thành công. Không bỏ qua test hoặc tắt limiter trong test bảo mật: suite bảo mật dùng quota production, các suite chung dùng multiplier riêng để nhiều kịch bản localhost không chặn nhau.

## File thêm/sửa

- `server/src/main/java/com/rpsbattle/worker/`: ExecutionQueue, WorkerManager, MatchWorker, LocalMatchWorker, MatchExecutionRequest, MatchExecutionResult.
- `server/src/main/java/com/rpsbattle/operations/`: RequestLimits, RequestLimitFilter, RequestLogFilter, RuntimeMetrics, OperationsConfig, GracefulMatchShutdown.
- `server/src/main/java/com/rpsbattle/tournament/OwnedTournamentService.java`
- `server/src/main/java/com/rpsbattle/tournament/execution/TournamentExecutionService.java`
- `server/src/main/java/com/rpsbattle/online/OnlineMatchService.java`
- `server/src/main/java/com/rpsbattle/websocket/handler/GameWebSocketHandler.java`
- `server/src/main/java/com/rpsbattle/auth/SecurityConfig.java`
- `server/src/main/java/com/rpsbattle/account/AdminService.java`
- `server/src/main/java/com/rpsbattle/api/error/ApiErrorHandler.java`
- `server/src/main/java/com/rpsbattle/bot/ownership/BotRepository.java`
- `server/src/main/java/com/rpsbattle/bot/persistence/BotRepositoryAdapter.java`
- `server/src/main/java/com/rpsbattle/bot/persistence/JpaBotRepository.java`
- `server/src/main/resources/application.yml`, `server/build.gradle`
- `server/src/test/java/com/rpsbattle/worker/WorkerManagerTest.java`
- `server/src/test/java/com/rpsbattle/operations/`: RequestLimitsTest, OperationsSecurityTest, GracefulMatchShutdownTest, WebSocketLimitTest.
- `server/src/test/java/com/rpsbattle/tournament/TournamentExecutionServiceTest.java`
- `server/src/test/java/com/rpsbattle/tournament/persistence/OwnedTournamentRollbackTest.java`
- `server/src/test/resources/application-test.yml`
- `client/js/ui/toast.js`, `client/tests/operations-errors.test.js`
- `docs/WORKER-OPERATIONS.md`, báo cáo này và `README.md`.

Smoke script/fixture/log và dữ liệu runtime nằm trong server/build/local-run hoặc data đã ignore; không đưa credential/dữ liệu người chơi lên GitHub.

## Giới hạn và điểm dừng

Hoàn thành Nhóm 9; dừng trước Nhóm 10 (Docker Compose, Backup, Recovery và Production Docs). Hướng dẫn vận hành: [WORKER-OPERATIONS.md](WORKER-OPERATIONS.md).

Queue/quota/counter ở RAM một JVM; chưa có distributed worker/durable queue, quota tổng phòng/tài nguyên theo account, hoặc resume bàn cờ đang chơi. Qua proxy phải cấu hình remote address thực và không tin header từ client. Graceful checkpoint cần process được dừng bình thường; force kill/DB lỗi vẫn có thể mất snapshot chưa commit. Docker sandbox vẫn cần Engine Linux/image đã cài; cleanup container orphan sau crash và triển khai production thuộc nhóm tiếp theo. Không tạo thêm tài khoản quản trị hay đổi role tài khoản của người dùng trong quá trình test.
