# Health, metrics và chẩn đoán

[Mục lục](../README.md) · [System Operations](../admin/system-operations.md) · [Worker](../admin/workers.md)

## Đọc đúng health

```bash
curl --fail https://game.example.com/actuator/health
curl --fail https://game.example.com/actuator/health/readiness
curl --fail https://game.example.com/actuator/health/liveness
```

Thay hostname bằng deployment. Health probes public, không lộ chi tiết; `/api/health` chỉ là kiểm tra app nhẹ và được Compose healthcheck dùng. `actuator/health` đánh giá health contributors, trong đó có DB; readiness/liveness có ý nghĩa probe riêng, không tự chứng minh Docker/runtime/capacity của giải.

Các endpoint Actuator ngoài health và `/api/admin/**` cần session ADMIN. Mặc định chỉ expose health, info, metrics; chưa có endpoint Prometheus được cấu hình. Không bật env/heapdump hoặc tắt auth để scrape cho tiện.

## Metrics và queue

Metrics của ứng dụng gồm login_failure_total, bot_timeout_total, websocket_sessions, active_users; local worker metrics được đăng ký qua OperationsConfig. Dùng `/actuator/metrics` với phiên ADMIN để lấy danh sách tên đang có trước khi truy vấn; tên hiển thị theo registry không phải mọi counter đều là một URL `/…_total` cố định ở exporter khác.

| Nguồn | Điều cần quan sát |
| --- | --- |
| `/api/admin/system` | DB/Docker, local worker, queueCount/queueCapacity, storage, JVM, alerts, pendingAudit. |
| Local execution queue | MAX_PENDING_MATCHES mặc định 256; MAX_CONCURRENT_MATCHES mặc định 4. Tăng queue không tăng CPU thực. |
| Coding queue | Mặc định 200 chờ, 2 chạy; build/public/sample/validation dùng tài nguyên Central, khác remote slots. |
| `/api/admin/workers/operations` | Trang worker cùng summary toàn hệ thống và attention; không cộng một trang để tính toàn bộ máy. |
| `/api/admin/workers/metrics` | Queue/attempt/result/recovery và thống kê theo giải nếu có tournamentId. |
| Group capacity/preview | Eligibility thực tế theo trust/runtime/ngôn ngữ/reservation; không giữ chỗ. |

CPU/RAM/disk worker là mẫu tự báo có kiểm tra kiểu/range; không phải giám sát host chống giả mạo. Heartbeat 5 giây, hết khoảng 30 giây không có mẫu mới thì OFFLINE. Packet sequence cũ không làm mới liveness.

## Log cho Compose và Nginx

Tại root repo, dùng đúng overlay của deployment. Ví dụ Nginx host:

```bash
sudo docker compose --env-file .env.production -f infra/docker-compose.yml -f infra/docker-compose.nginx.yml -f infra/docker-compose.bots.yml ps
sudo docker compose --env-file .env.production -f infra/docker-compose.yml -f infra/docker-compose.nginx.yml -f infra/docker-compose.bots.yml logs --tail 100 rps-server postgres
sudo tail -n 80 /var/log/nginx/error.log
```

Với Caddy bỏ overlay nginx và xem thêm service reverse-proxy. Dùng request ID và mã lỗi để nối HTTP với audit/trận; không đăng full env, cookie, source hoặc credential ra công khai. Giữ log shutdown_persistence_failed/AUDIT_WRITE_PENDING để xem lỗi ghi dữ liệu trước khi restart.

## Chẩn đoán theo triệu chứng

- **ERR_TIMED_OUT, không có log app:** kiểm tra DNS A/AAAA, IP public hiện tại, cloud firewall và 80/443. Nếu VPS truy cập được loopback nhưng ngoài mạng timeout, xem hạ tầng trước DB.
- **502/504:** kiểm tra backend/port loopback/container, health, OOM và log proxy. Không mở port DB ra Internet để chữa lỗi.
- **Trang tải chậm:** phân biệt tải asset, request API và JS dựng UI bằng Network/Timing. HTML/API riêng tư dùng no-store; asset có caching theo StaticResourceConfig. Không cache chung response mang cookie/permission hoặc đổ lỗi mọi lần chuyển tab cho ảnh nền.
- **Nhiều job chờ:** kiểm tra concurrency, busy/lease/reservation, routing, Docker và disk. Remote ONLINE chưa chắc eligible cho giải.
- **Nhiều bot timeout:** xem tỷ lệ/nhóm ngôn ngữ và resource của host; phân biệt lỗi bot với INFRA_ERROR. Không tăng timeout toàn hệ thống trước khi đo.
- **Stale revision hoặc summary lệch:** cập nhật snapshot; không retry mutation cũ liên tục.

## Cảnh báo và năng lực

Backend có cảnh báo queue từ 80%, disk/storage, worker pressure, replay invalid, giải recovery, audit pending và trận chạy lâu. Xem giải thích [trong khu Admin](../admin/system-operations.md). Chưa tự gửi email/Slack hay tự scale máy từ các cảnh báo UI.

`remoteLoadTest` là benchmark synthetic control-plane, không chạy hàng nghìn bot Docker thật. Worker `benchmark` chỉ là baseline nhỏ; cần đo nhiều trận thực cùng ngôn ngữ/giới hạn dự định dùng trước khi tăng slot. Không dùng số khai báo max-matches làm bằng chứng throughput.

## Nguồn đối chiếu

- [Exposure/probes](../../server/src/main/resources/application.yml), [security](../../server/src/main/java/com/rpsbattle/auth/SecurityConfig.java), [runtime metrics](../../server/src/main/java/com/rpsbattle/operations/RuntimeMetrics.java), [operations metrics](../../server/src/main/java/com/rpsbattle/operations/OperationsConfig.java).
- [System alerts](../../server/src/main/java/com/rpsbattle/admin/AdminOperationsService.java), [worker metrics](../../server/src/main/java/com/rpsbattle/worker/remote/WorkerMetricsService.java), [static cache](../../server/src/main/java/com/rpsbattle/web/StaticResourceConfig.java).
- [Test/benchmark tasks](../../server/build.gradle).
