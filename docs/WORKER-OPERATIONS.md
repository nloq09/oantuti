# Worker, hàng đợi và theo dõi server

## Luồng thực thi

`Tournament → ExecutionQueue → WorkerManager → LocalMatchWorker → OnlineMatchService → GameEngine`

Khi chủ giải bắt đầu giải đấu, server tạo lịch, giữ chỗ cho toàn bộ số trận chờ và commit dữ liệu giải trước khi thông báo scheduler. Nếu thiếu chỗ, trả HTTP **503 SERVER_BUSY** và giải vẫn ở DRAFT. Nếu DB commit lỗi, lịch và chỗ đã giữ đều được rollback. Scheduler đưa từng trận vào FIFO; cùng một trận chỉ được đưa vào một lần, kể cả khi scheduler tick liên tục.

Worker khởi tạo phòng trên thread riêng. Mỗi trận giữ một suất chạy suốt thời gian thi đấu, đến khi kết quả/replay được ghi nhận hoặc trận bị lỗi/hủy/timeout. Bot turn tiếp tục dùng executor có giới hạn của OnlineMatchService; không tạo engine hay luật riêng cho worker. Hủy giải trước khi job được khởi chạy sẽ ngăn job tạo phòng. Lỗi một bot/trận không dừng lịch đấu còn lại.

## Cấu hình

- `MAX_CONCURRENT_MATCHES`: mặc định **4**, chấp nhận 1–4 trận giải đấu đang chạy trên toàn server.
- `MAX_PENDING_MATCHES`: mặc định **256**, chấp nhận 1–10000; bao gồm cả chỗ giữ cho lịch giải chưa được scheduler đưa vào FIFO. Không tính trận đã được nhận chạy.
- `rps.tournament.match-timeout-seconds`: mặc định **900** giây cho mỗi trận.
- `RATE_LIMIT_MULTIPLIER`: mặc định **1**; nhân quota request, số thực >0 và ≤100. Giữ 1 khi vận hành; chỉ tăng có chủ đích trong server kiểm thử.
- `rps.rate-limit.max-keys`: mặc định **10000** key rate limit trong RAM. Key hết hạn được dọn khi bảng đầy; bảng đầy sẽ từ chối identity mới thay vì xóa quota đang có.

Giới hạn số trận ở đây áp dụng cho lịch giải đấu. Phòng chơi thường dùng executor bot turn chung bốn thread và rate limit tạo/join phòng; chưa có quota tổng số phòng đang tồn tại hoặc quota lưu trữ theo tài khoản.

## Rate limit

HTTP POST, tính theo cửa sổ 60 giây:

- Login: 30 request; thêm chặn sau **10 lần sai trong 15 phút** theo IP thực của kết nối. Lần sai thứ 10 vẫn trả 401; lần tiếp theo trả 429. Đăng nhập thành công không xóa ngân sách lỗi.
- Register: 10 request.
- Upload bot: 20 request.
- Tạo phòng: 60 request; join phòng: 120 request.
- Tạo giải: 20 request.

Mỗi nhóm kiểm tra quota theo tài khoản đã xác thực (khách dùng IP), và quota IP bằng hai lần quota nhóm. HTTP trả **429 RATE_LIMITED**, `Retry-After` và JSON `retryAfterSeconds`. Request bị chặn không thực hiện mutation. Các kiểm tra session, quyền và CSRF vẫn hoạt động; không nới CSRF để thử lại tự động. Client hiển thị thông báo tiếng Việt và không tự gửi lại thao tác tạo/start/upload.

WebSocket dùng IP thực: tối đa 60 message/giây tổng; PING 2/giây; STATE_REQUEST 5/giây; các loại khác chung 30/giây, bao gồm ACTION, placement/ready và subscribe. Message bị chặn trả ACTION_REJECTED với RATE_LIMITED; không gọi gateway thay đổi bàn cờ. Mở socket mới không đặt lại quota. Text message giới hạn 16 KiB. Upload vẫn giới hạn source 512 KiB, JAR 5 MiB/request 6 MiB.

Các quota lưu trong RAM của **một instance**, đặt lại khi restart. Người dùng cùng NAT dùng chung quota IP. Server không tin `X-Forwarded-For` do client gửi. Khi deploy qua reverse proxy, cần cấu hình proxy đáng tin để cung cấp remote address thực; nếu chưa làm, toàn bộ client qua proxy có thể bị tính chung một IP. Đây không phải cơ chế rate limit phân tán.

## Monitoring và log

Spring Boot Actuator chỉ expose `health`, `info`, `metrics`:

- `/actuator/health`: công khai, không trả chi tiết DB/ứng dụng; `/api/health` cũ vẫn dùng được.
- `/actuator/metrics`, `/actuator/info`, `/api/admin/system`: yêu cầu session của **ADMIN**. Khách trả 401, tài khoản thường trả 403. `/api/admin/system` có `workers` gồm state, active, pending và hai giới hạn cấu hình.
- `env`, `configprops`, heapdump và các endpoint nhạy cảm khác không expose. Không dùng URL query chứa password/token để truy cập metrics.

Metrics: `active_users` (số account có session đăng nhập, không phải số người đang nhìn trang), `active_matches`, `running_tournaments`, `uploaded_bots` (bot chưa soft-delete), `bot_timeout_total`, `login_failure_total`, `websocket_sessions`, `worker_active_matches`, `worker_pending_matches`. Có thể xem riêng bằng `/actuator/metrics/worker_pending_matches`. Gauge bot trả NaN khi DB không đọc được, không báo giả bằng 0. Counter tính từ lúc server khởi động, chưa lưu bền vững và chưa expose Prometheus.

HTTP API có `X-Request-Id` server sinh; log chứa event/requestId/userId/route/resourceId/status. Job start có tournamentId/matchId/botId/opponentBotId. MDC gắn ID resource hợp lệ theo route. Không log request body, query, username đăng nhập, mật khẩu, cookie/session ID hay capability bot. Không dùng ID từng người/trận làm label metric để tránh tăng cardinality.

## Dừng server và khôi phục

Dừng bình thường bằng **Ctrl+C** trên terminal chạy Java/bootRun. Spring chờ HTTP đang xử lý kết thúc, rồi worker chuyển DRAINING, ngừng nhận job/phòng mới. Scheduler không khởi chạy thêm; bot result đang chờ không cập nhật phòng sau khi bắt đầu checkpoint.

Server ghi nhận những trận đã kết thúc, đánh dấu metadata phòng chưa kết thúc là lỗi hạ tầng, ghi giải đang dở là **RECOVERY_REQUIRED**, giữ PENDING và đánh dấu RUNNING chưa hoàn thành là FAILED/SERVER_SHUTDOWN. Sau đó flush history, giải đấu, replay trước khi đóng bot/executor và DB pool. Nếu mọi trận của một giải đã hoàn thành/lỗi thì giải có thể kết thúc FINISHED. Checkpoint không giả lập người thắng cho trận bị ngắt.

Sau crash hoặc buộc kill process, chính sách cũ vẫn áp dụng: dữ liệu đã commit được khôi phục; giải RUNNING/READY trở thành RECOVERY_REQUIRED, không tự chạy lại và không tự cộng điểm cho trận chưa hoàn thành. Chủ giải/admin có thể **hủy** giải qua API hiện có. Chưa có resume bàn cờ đang chơi. Graceful checkpoint là best effort: DB không hoạt động sẽ có log `shutdown_persistence_failed`; các snapshot chưa commit có thể mất khi process thoát. Cần backup DB **và** bot source/replay files.

Worker hiện chạy local trong cùng JVM, runner bot riêng vẫn cần Docker Linux và các image ngôn ngữ. Chưa hỗ trợ worker ở máy khác, durable queue hay nhiều instance. Docker Compose/backup/production deploy và cleanup orphan thủ công theo label đã có ở Nhóm 10: xem [DEPLOYMENT.md](DEPLOYMENT.md), [OPERATIONS.md](OPERATIONS.md). Cleanup cần dừng server trước, mặc định dry run; không tự dọn container khi khởi động.
