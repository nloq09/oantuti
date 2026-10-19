# Chặng 11 — Nhóm 1: ứng dụng Worker, đăng ký và credential

## Phạm vi đã triển khai

Ứng dụng Java 21 riêng ở `worker/`, cùng Gradle build qua `server/settings.gradle`. Worker chủ động gọi HTTP(S) ra Central, không lắng nghe port. Nhóm này chỉ đăng ký, lưu danh tính và kiểm tra kết nối; chương trình kết thúc sau kiểm tra, chưa có heartbeat, duyệt trên UI, claim, download artifact hoặc chạy trận. Worker mới có `STARTING / PENDING_APPROVAL / UNTRUSTED`, không được cấp quyền thực thi.

Central có migration V29 với `workers`, `worker_credentials`, `worker_registration_tokens`, `worker_identity_events`. Token đăng ký và credential độc lập, ngẫu nhiên 256 bit, chỉ lưu SHA-256 trong DB. Raw secret chỉ trả về khi tạo. Token mặc định dùng một lần trong 15 phút; admin có thể đặt 1–100 lượt và thời hạn 1–1.440 phút, thu hồi token. Số lượt bị trừ trong transaction bằng conditional UPDATE; rollback không mất lượt và nhiều Central dùng chung DB không thể tiêu thụ vượt hạn.

API `/internal/**` có Spring Security chain stateless riêng, không nhận cookie hoặc quyền tài khoản. Chỉ credential worker hợp lệ được đọc danh tính của chính worker đó. Các internal route chưa triển khai mặc định bị chặn; credential không đăng nhập tài khoản và không truy cập API admin. Việc revoke/disable credential bằng UI thuộc nhóm 8; ở nhóm 1, bộ xác thực đã kiểm tra các cột revoked/disabled/rejected. Admin API vẫn cần tài khoản ADMIN và CSRF.

## API

- `POST /api/admin/workers/registration-tokens`: body `{"uses":1,"lifetimeMinutes":15}`, trả 201 với `id`, `registrationToken`, `expiresAt`, `remainingUses`.
- `GET /api/admin/workers/registration-tokens`: tối đa 100 token mới nhất, chỉ metadata, không trả hash hoặc secret.
- `POST /api/admin/workers/registration-tokens/{id}/revoke`: revoke idempotent, không thu hồi credential đã cấp.
- `POST /internal/workers/register`: body gồm `registrationToken`, `name`, `workerVersion`, `protocolVersion:1`; trả 201 với `worker` và `workerCredential`.
- `GET /internal/workers/{id}/identity`: header `Authorization: Bearer <workerCredential>`, không dùng session cookie.

Các response danh tính/token đều `Cache-Control: no-store`. Đăng ký có rate limit theo IP; từ chối protocol không tương thích trước khi tiêu thụ token. Audit riêng lưu actor, loại sự kiện, resource UUID, thời gian; không lưu raw token, credential hoặc payload.

## Build và chạy

Từ thư mục repository, trên Windows:

```powershell
.\server\gradlew.bat -p server :worker:test :worker:workerJar
.\server\gradlew.bat -p server bootJar
```

Linux/macOS: `sh server/gradlew -p server :worker:test :worker:workerJar`. Task `workerJar` in đường dẫn JAR. Windows giữ output trong `%USERPROFILE%/.gradle/rps-battle-builds/worker-<key>/libs/rps-worker.jar` để tránh OneDrive khóa file; Linux/macOS mặc định `worker/build/libs/rps-worker.jar`. Chép JAR sang máy worker. Không cần chép source hoặc cài PostgreSQL trên worker.

Admin tạo token qua API có session và CSRF; trang tạo token sẽ được bổ sung ở nhóm 2. Đưa token cho người vận hành máy qua kênh riêng. Đặt `WORKER_REGISTRATION_TOKEN` trong môi trường tiến trình lần đầu, rồi chạy:

```text
java -jar rps-worker.jar --central=https://central.example --name=LAB-PC-01 --state-dir=./worker-state
```

Worker cũng nhận `WORKER_CENTRAL_URL`, `WORKER_NAME`, `WORKER_STATE_DIR`. Có `--register-token=...` để tương thích CLI, nhưng nên dùng biến môi trường để tránh secret xuất hiện trong lịch sử lệnh/danh sách tiến trình. Chỉ truyền token lần đầu; lần chạy sau dùng cùng `--central` và `--state-dir`, không cần token. Không chép state directory sang nhiều máy.

Test LAN/local phải bật rõ HTTP:

```text
java -jar rps-worker.jar --central=http://192.168.0.105:8080 --allow-insecure-http --name=LAB-PC-01
```

HTTP chỉ chấp nhận localhost, loopback hoặc IPv4 RFC1918 dạng địa chỉ literal. Production dùng HTTPS với chứng chỉ hợp lệ. Không bỏ kiểm tra TLS, không theo redirect để tránh lộ credential. Central URL phải là origin, không chứa userinfo/query/path/fragment. Reverse proxy phải bảo vệ public endpoint bằng HTTPS. HTTP LAN vẫn có nguy cơ bị nghe lén; chỉ dùng trên mạng tin cậy.

## Lưu trữ và lỗi kết nối

Worker lưu `identity.json` gồm Central origin, UUID và credential; không lưu token đăng ký. Thư mục/file chỉ cấp quyền owner trên POSIX hoặc Windows ACL. Ghi tạm, fsync rồi atomic move; lock file ngăn hai agent dùng cùng state đồng thời. Không hỗ trợ filesystem thiếu private permissions hoặc atomic move. State mặc định bị `.gitignore` bỏ qua. Credential thuộc Central đã đăng ký, không được gửi tới origin khác.

Không tự retry đăng ký: nếu Central đã tiêu thụ token nhưng mất response hoặc không ghi được identity, yêu cầu admin tạo token mới; worker cũ vẫn chờ duyệt, không được chạy trận. Giữ state khi kiểm tra kết nối thất bại; sửa mạng/quyền/credential rồi chạy lại. Nhóm này chưa tự reconnect hoặc phát heartbeat. Không log secret, response lỗi hoặc nội dung exception có thể phản chiếu dữ liệu riêng tư.

## Kiểm thử và giới hạn

Kết quả:

- **PASS 10** ca backend đăng ký/xác thực: 5 trên H2 và cùng 5 ca trên PostgreSQL thật bằng Testcontainers; đăng ký đồng thời, hạn lượt/expiry/revoke, ADMIN/CSRF, cookie không cấp quyền worker, credential không cấp quyền admin, danh tính chéo worker, hash DB, protocol, restart credential. Phát hiện lỗi bind `Instant` của PostgreSQL trong lần đầu; đã chuyển sang JDBC Timestamp và chạy lại toàn bộ 10 ca, không còn lỗi.
- **PASS 38** ca hồi quy: 24 Flyway migration PostgreSQL, 4 AccountAuthorization, 7 AdminConsoleApi, 3 OperationsSecurity. Các ca này chạy trong lượt hồi quy đầu; lần chốt sau sửa timestamp chạy riêng 10 ca worker trên hai DB.
- **PASS 4** ca worker: file ACL, lock, lưu/reload, token không lưu, HTTPS/LAN, chặn redirect và lỗi không lộ secret.
- **PASS 122** ca client/admin hiện có, 0 skipped.
- **PASS** build Central `bootJar`, fat JAR `workerJar`, chạy JAR thật trên Windows với Central và PostgreSQL native: token một lần không tái dùng, credential riêng tư, origin binding, danh tính chờ duyệt, quyền chéo bị chặn; khởi động lại worker và Central vẫn dùng cùng credential.
- **PASS** đọc lại E2E giải đấu chặng 10 sau V29/restart: lịch, standings, replay, frozen artifact và audit cũ giữ nguyên. Đã backup DB + bot/replay vào `backups/before-stage11-identity-v29` trước migration.
- **SKIPPED** E2E qua máy vật lý thứ hai, Docker image build và UI trực quan; nhóm này chưa có UI mới. Kiểm thử tích hợp thực tế ở trên là hai tiến trình trên cùng máy, không phải thử qua LAN/VPS.

Không sửa GameEngine, RuleSet, combat, scheduler hoặc local WorkerManager. Không có tuyên bố chạy trận phân tán/đa máy hay load test 4.000 người trong nhóm 1. Các nhóm 2–10 chưa triển khai. Chưa có thay đổi UI để kiểm thử trực quan.

## File thêm/sửa

- `worker/build.gradle`, `worker/src/main/java/com/rpsbattle/remote/{WorkerApplication,WorkerConfig,CentralClient,IdentityStore}.java`, `worker/src/test/java/com/rpsbattle/remote/WorkerStartupTest.java`.
- `server/settings.gradle`, `Dockerfile` để Central Docker build vẫn thấy project worker.
- `server/src/main/resources/db/migration/V29__remote_worker_identity.sql`.
- `server/src/main/java/com/rpsbattle/worker/remote/{WorkerIdentityService,WorkerSecurityConfig}.java`.
- `server/src/main/java/com/rpsbattle/api/controller/RemoteWorkerController.java`, `server/src/main/java/com/rpsbattle/auth/SecurityConfig.java`.
- `server/src/test/java/com/rpsbattle/worker/remote/{WorkerRegistrationTest,WorkerRegistrationPostgresTest}.java`, `server/src/test/java/com/rpsbattle/persistence/FlywayMigrationTest.java`.
- `.gitignore`, `.dockerignore`, `README.md`, tài liệu này. State mặc định không được đưa vào Git hoặc Docker build context.

Sao lưu DB và bot/replay trước V29. Migration chỉ thêm bảng, không thay dữ liệu trận/giải/tài khoản. Muốn rollback ứng dụng, dùng JAR cũ và giữ các bảng mới; không chỉnh migration cũ hoặc xóa schema. Token một lần hết hạn/revoke không thể khôi phục từ hash; credential mất phải cấp danh tính mới.
