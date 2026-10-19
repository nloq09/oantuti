# Chặng 11 — Nhóm 2: heartbeat, capabilities và quản lý worker

## Hành vi

Worker 0.2.0 chạy liên tục, chủ động gửi capabilities lúc startup rồi heartbeat mỗi 5 giây. Không mở port. Một lần khởi động tạo heartbeat epoch mới trên Central; packet của epoch cũ bị từ chối. Sequence cũ/trùng không thay metrics và không làm mới liveness. Agent thử lại heartbeat khi lỗi mạng/5xx, giãn từ 10 tới tối đa 30 giây và giữ credential; 409 thiết lập lại phiên capabilities. 400/401/403/404 dừng agent để người vận hành kiểm tra. Không tự đăng ký lại khi đã có identity.

Central lấy thời điểm nhận request làm mốc, không dùng đồng hồ worker. Đủ 30 giây không nhận heartbeat hợp lệ, dashboard hiển thị OFFLINE và slot sẵn sàng bằng 0; tác vụ 5 giây ghi trạng thái OFFLINE vào DB. Heartbeat mới đưa máy trở lại ONLINE/BUSY hoặc DEGRADED. Không có thao tác tạo lại trận khi worker offline trong nhóm này.

Các metrics: CPU%, RAM%, CPU cores, tổng RAM MB, OS, Docker Linux, image ngôn ngữ đang có, cấu hình slot/process, active matches/bots, đĩa trống, cache, IP Central quan sát, version/protocol, heartbeat cuối. CPU chưa đo được hiển thị `—`, không bịa số 0. Một hàng heartbeat mới nhất trên mỗi máy, không lưu vô hạn mỗi 5 giây. IP lấy từ servlet request, không tin header IP do worker gửi.

Capabilities dò `docker info` và image Linux có sẵn: `python:3.12-slim`, `gcc:14`, `eclipse-temurin:21-jdk`. Không tự pull hoặc chạy bot để dò. Capabilities là quan sát lúc startup; sau khi thay Docker/image phải restart agent để cập nhật. Không có Docker hoặc image phù hợp thì DEGRADED. Việc sẵn sàng thực thi sandbox thật sẽ được kiểm tra ở nhóm execution; kết quả dò image không phải benchmark/kiểm chứng runtime toàn diện.

## Giao diện admin

- `/admin/workers` → `/admin/workers.html`: mục **Worker** trên sidebar, danh sách phân trang 20 máy, tự làm mới 5 giây; tên, status, approval/trust, IP, metrics, slot, runtime, cache, heartbeat và version. Dữ liệu không có credential hoặc hash.
- **Thêm worker**: chọn số lượt và thời hạn, tạo token với ADMIN/session/CSRF. Token mặc định che, chỉ xem/sao chép từ response vừa tạo; không đưa vào localStorage, URL hoặc log. Đóng form/rời trang sẽ xóa token khỏi UI. Danh sách token chỉ có metadata và nút thu hồi.
- Máy PENDING_APPROVAL có ba lựa chọn: duyệt TRUSTED, duyệt COMMUNITY, từ chối. Bắt buộc reason và revision mới nhất; audit ghi actor, trước/sau, lý do, thời gian. Heartbeat không tăng revision quản trị, tránh làm hỏng form duyệt mỗi 5 giây. Không tự duyệt máy đăng ký bằng token.
- `/admin/workers/setup` → `/admin/worker-setup.html`: tải JAR và hướng dẫn Windows/Linux/macOS, Java 21, Docker, image, token, thư mục riêng, HTTPS/LAN. JAR nằm trong resource riêng của Central; chỉ ADMIN được tải qua `/api/admin/workers/download`, không nằm trong static public.

Máy được duyệt, có heartbeat mới và runtime khả dụng mới có `effectiveFreeSlots > 0`. Giá trị là min(slot worker chào, số match còn, số process bot còn / 2). Máy pending/rejected/offline/degraded có 0 slot sẵn sàng. Đây là metrics tự báo được giới hạn theo cấu hình, **chưa phải reservation/claim job**; nhóm 3 sẽ quản lý slot thực thi bằng quyền sở hữu job/lease. TRUSTED/COMMUNITY ở đây là xét duyệt danh tính, chưa phân phối vào giải theo trust policy.

## API và DB

V30 thêm revision, epoch, heartbeat/IP vào workers; `worker_capabilities`, `worker_heartbeats` (mỗi bảng tối đa một hàng/máy), trường reason/before/after cho identity audit. Không đổi migration cũ. V29 identities và hash credential được giữ nguyên.

- `POST /internal/workers/{id}/capabilities`: worker Bearer credential, ID phải là chính máy đó. Body gồm `cpuCores`, `memoryMb`, `os`, `docker`, `supportedLanguages`, `maxConcurrentMatches`, `maxBotProcesses`, `workerVersion`, `protocolVersion:1`. Trả `epoch`, `heartbeatIntervalSeconds:5`, `offlineAfterSeconds:30`.
- `POST /internal/workers/{id}/heartbeat`: `epoch`, `sequence`, `activeMatches`, `freeSlots`, `activeBotProcesses`, `cpuUsage`/`memoryUsage` nullable (0–100), `diskFreeMb`, `cacheSizeMb`. Các số âm/vượt cấu hình/NaN bị từ chối, không ghi đè sample trước. Body không được chọn IP, trust hoặc timestamp nhận.
- `GET /api/admin/workers?page=0&size=20` (size 1–100), `GET /api/admin/workers/{id}`: ADMIN, no-store.
- `POST /api/admin/workers/{id}/review`: `revision`, `decision:APPROVE|REJECT`, `trustLevel:TRUSTED|COMMUNITY` khi duyệt, `reason`. Chỉ duyệt PENDING; stale hoặc duyệt trùng trả 409. Máy REJECTED không còn xác thực/heartbeat được.
- `GET /api/admin/workers/download`: ADMIN, attachment JAR no-store; không có artifact bot/source trong JAR.

Các API worker vẫn không nhận session cookie. Worker không được đọc danh sách/admin API, dữ liệu tài khoản hoặc thao tác trên máy khác. Các route claim/result/artifact chưa có tiếp tục deny mặc định. Drain/disable/revoke credential UI thuộc nhóm 8.

## Chạy

```text
java -jar rps-worker.jar --central=https://central.example --name=LAB-PC-01 --max-matches=1 --max-bots=2
```

Giữ `worker-state` và dùng credential đã đăng ký từ nhóm 1. Lần đầu đặt `WORKER_REGISTRATION_TOKEN` trong môi trường; bỏ token khỏi môi trường sau khi có identity. Có `WORKER_MAX_MATCHES`, `WORKER_MAX_BOTS`; yêu cầu 1–256 match, ít nhất 2 process bot/match, tối đa 512 process. Mặc định 1 match/2 process, không tự suy đoán capacity theo CPU.

HTTP LAN/local phải thêm `--allow-insecure-http`; Internet bắt buộc HTTPS. `--once` gửi capabilities và một heartbeat rồi thoát (sẽ OFFLINE sau 30 giây), `--identity-only` kiểm tra credential rồi thoát. Bình thường chạy liên tục, Ctrl+C dừng. Chưa claim hoặc chạy trận.

Build từ root: `server/gradlew.bat -p server bootJar :worker:workerJar`; kiểm thử `:test` và `:worker:test`. Central processResources phụ thuộc workerJar để đóng gói bản tải đúng mã nguồn; Dockerfile copy worker source cho build này. Không cần Docker socket trên Central để phát heartbeat hoặc quản lý registry; agent runtime probe diễn ra trên máy agent.

## Kiểm thử

Kết quả:

- **PASS 61 backend**, 0 skipped: 11 ca worker/heartbeat trên H2 và cùng 11 ca trên PostgreSQL thật; 25 Flyway migration; 4 AccountAuthorization; 7 AdminConsoleApi; 3 OperationsSecurity. Bao gồm contracts nhóm 1, epoch/sequence, timeout đúng 30 giây, duyệt với revision/reason/CSRF, audit, quyền tải JAR, nâng cấp V29→V30 giữ credential và phân quyền cũ. Testcontainers có log cảnh báo kết nối khi container PostgreSQL đóng trước Spring context; Gradle và toàn bộ assertion đều PASS.
- **PASS 7 worker**, 0 skipped: 4 ca startup/credential và 3 ca heartbeat/runtime/config, thử 503/409/401, giữ credential/sequence khi lỗi mạng, tạo epoch mới, secret không log và giới hạn process.
- **PASS 125 client/admin**, 0 skipped: API session/CSRF/revision không tự retry; quyền hiển thị review; DOM token che/xóa khi đóng hoặc rời trang; cleanup polling và link download/setup.
- **PASS** build Central bootJar và workerJar 0.2.0. Native integration với JAR thật và PostgreSQL: capabilities/metrics, duyệt COMMUNITY có audit, slot sau duyệt, heartbeat liên tục, dừng process rồi OFFLINE sau 30 giây/slot 0, chạy lại khôi phục kết nối. Hash JAR tải qua API admin khớp bản build; khách bị chặn tải. Đường dẫn trang setup/list hoạt động.
- Sao lưu DB/bot/replay trước V30 vào `backups/before-stage11-telemetry-v30`. Các fixture và state/credential native nằm trong đường dẫn ignored, không commit.
- **PASS sau restart Central**: credential, capabilities, approval/revision và JAR download tiếp tục hoạt động; E2E chặng 10 đọc lại lịch, standings/replay, frozen artifact và audit cũ đúng như trước.
- **SKIPPED** browser trực quan (Work settings tắt local browser), Docker image build, máy vật lý thứ hai và load test. DOM unit/integration HTTP không thay thế kiểm tra layout thực tế.

Không sửa GameEngine, RuleSet, combat, tournament scheduler hoặc local WorkerManager. Chưa làm nhóm 3–10, không có load test nhiều máy hoặc claim/execution. Browser local bị tắt trong Work settings nên kiểm tra trực quan browser sẽ được ghi SKIPPED; DOM unit test không thay thế thử layout thực tế.

## File thay đổi

- Backend: V30, `WorkerTelemetryService`, `WorkerSecurityConfig`, `RemoteWorkerController`, `server/build.gradle`, Dockerfile.
- Worker: `RuntimeProbe`, `HeartbeatAgent`, `CentralClient`, `WorkerApplication`, `WorkerConfig`, build.gradle.
- Admin: `workers.html`, `worker-setup.html`, `workers.js`, `worker-api.js`, admin-shell/views và admin.css.
- Tests: `WorkerHeartbeatTest`, `WorkerHeartbeatPostgresTest`, FlywayMigrationTest, HeartbeatAgentTest, WorkerStartupTest, admin-workers/API/UI tests.
- README và tài liệu nhóm 2 này. Báo cáo nhóm 1 giữ nguyên bối cảnh phiên bản 0.1; từ 0.2 agent chạy heartbeat liên tục như mô tả ở đây.

Sao lưu DB/bot/replay trước V30. Có thể rollback JAR Central cũ và giữ các bảng/cột bổ sung; không hạ schema hoặc xóa lịch sử. Epoch/revision tiếp tục bền vững sau restart Central.
