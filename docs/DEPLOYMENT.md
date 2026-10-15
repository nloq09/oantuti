# Triển khai RPS Battle

## Mô hình được hỗ trợ

Một máy Linux có Docker Engine/Compose chạy ba dịch vụ: **Caddy HTTPS → rps-server → PostgreSQL 17**. Client được đóng trong JAR và phục vụ cùng domain với REST/WebSocket; không cần deploy client riêng. Docker Desktop Linux containers dùng được để thử trên Windows.

Các volume lưu PostgreSQL, source bot, replay JSON và dữ liệu chứng chỉ Caddy. Phòng đang chơi/session và hàng đợi vẫn ở một JVM; không scale server nhiều replica. Metadata đã commit giữ qua restart; giải đang dở cần hủy theo chính sách RECOVERY_REQUIRED. READY chưa có lịch đấu giữ nguyên để chủ giải bắt đầu sau restart. Chưa có resume bàn cờ hoặc distributed worker; xem [vòng đời giải đấu](CHANG-5-NHOM-1-LIFECYCLE.md).

## Chuẩn bị

1. Cài Docker Engine với Compose plugin trên Linux; dùng Docker Desktop ở Windows và chuyển sang Linux containers. Kiểm tra `docker version`, `docker compose version`.
2. Clone repo và đứng ở thư mục gốc. Dockerfile tự build với Java 21/Gradle, không cần cài JDK trên host nếu chạy hoàn toàn bằng Docker.
3. Domain có A/AAAA trỏ về IP máy deploy; mở TCP 80/443. Chỉ proxy publish cổng; server 8080 và PostgreSQL 5432 không publish ra host.
4. Copy `infra/.env.production.example` thành `.env.production`, điền mật khẩu và domain. Không dùng lại `.env` của server dev. Giữ file môi trường ở ngoài Git và giới hạn quyền đọc; trên Linux dùng `chmod 600 .env.production`.

```bash
cp infra/.env.production.example .env.production
```

Hai mật khẩu cần khác nhau: POSTGRES_PASSWORD dành cho quản trị DB; DB_PASSWORD dành cho role ứng dụng DB_USERNAME (mặc định rpsapp). Role ứng dụng không có SUPERUSER/CREATEDB/CREATEROLE, chỉ sở hữu schema public của DB riêng. Init script chạy **chỉ trên volume DB mới**. Thay env của volume cũ không tự đổi password/role PostgreSQL.

Trong `.env.production`:

- `COMPOSE_PROJECT_NAME`: tên deployment duy nhất, ví dụ rpsbattle. Không đổi tùy tiện vì tên volume và label bot dùng tên này.
- `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`: database và tài khoản quản trị. Dùng tên đơn giản chữ/số/gạch dưới cho công cụ ops.
- `DB_USERNAME`, `DB_PASSWORD`: tài khoản ứng dụng, khác POSTGRES_USER. Trong Compose DB_URL được đặt thành `jdbc:postgresql://postgres:5432/<POSTGRES_DB>`; không dùng localhost vì PostgreSQL ở container khác.
- `RPS_DOMAIN`: chỉ hostname, ví dụ game.example.com; `ACME_EMAIL`: email quản trị chứng chỉ.
- `RPS_IMAGE`: mặc định rps-battle:local; khi phát hành nên dùng tag theo commit và giữ tag trước để rollback.
- `RPS_SUBNET`: mặc định 172.30.90.0/24. Nếu subnet đã dùng, đổi sang subnet riêng và đồng thời thêm `TRUSTED_PROXY_REGEX` khớp subnet đó, ví dụ `172[.]30[.]91[.][0-9]+`.
- `HTTP_PORT=80`, `HTTPS_PORT=443`, `BIND_ADDRESS=0.0.0.0`: public proxy. Để thử riêng trên máy, dùng localhost và cổng khác, ví dụ 18080/18443, BIND_ADDRESS=127.0.0.1.
- `MAX_CONCURRENT_MATCHES`, `MAX_PENDING_MATCHES`, `RATE_LIMIT_MULTIPLIER`: giới hạn worker/request, mặc định 4/256/1. Xem [WORKER-OPERATIONS.md](WORKER-OPERATIONS.md).

Không chạy `docker compose config` rồi chia sẻ output vì output có password đã interpolate. Dùng `config --quiet` để kiểm tra cấu hình.

## Khởi động không chạy bot upload

```bash
docker compose --env-file .env.production -f infra/docker-compose.yml config --quiet
docker compose --env-file .env.production -f infra/docker-compose.yml up -d --build --wait --wait-timeout 180
```

Truy cập `https://<RPS_DOMAIN>/`. Chơi tay/hai người và bot chiến thuật của game hoạt động; đăng ký, profile/history và xem dữ liệu đã lưu vẫn có. Bot upload có thể lưu source nhưng chưa thực thi; trận dùng bot riêng hoặc bắt đầu giải sẽ báo BOT_SANDBOX_UNAVAILABLE.

## Bật bot upload và giải đấu

Tải trước image trên **cùng Docker Engine** với server:

```bash
docker pull python:3.12-slim
docker pull gcc:14
docker pull eclipse-temurin:21-jdk
```

Trên Linux, lấy group socket:

```bash
stat -c '%g' /var/run/docker.sock
```

Điền DOCKER_GID tương ứng trong env. Docker Desktop thử nghiệm dùng 0. Sau đó:

```bash
docker compose --env-file .env.production -f infra/docker-compose.yml -f infra/docker-compose.bots.yml up -d --build --wait --wait-timeout 180
```

Override `docker-compose.bots.yml` bật runner và cấp socket **cho server tin cậy**. Socket Docker có quyền điều khiển host; user không root của server không biến socket thành sandbox. Chỉ dùng trên host/Engine dành cho ứng dụng này, không chia sẻ với workload nhạy cảm. Đây là quyền cần thiết để server tạo sandbox con; bot không được mount socket, DB, root host hay toàn bộ volume bot. [Docker: bảo vệ daemon](https://docs.docker.com/engine/security/protect-access/).

Source ở named volume `/app/data/bots` trong server được ánh xạ qua Mountpoint mà daemon trả về, rồi mount đúng **một file** vào `/bot/<filename>`, read-only. Metadata/token và source bot khác không vào sandbox. Bot vẫn non-root, no network, cap-drop, no-new-privileges, CPU 0.5/RAM 384 MiB/PID 64/tmp 64 MiB và timeout/output limit. Volume dữ liệu tồn tại độc lập với container. [Docker: volume](https://docs.docker.com/engine/storage/volumes/).

Chỉ một server cho một COMPOSE_PROJECT_NAME; mỗi deployment có rps.scope riêng. Bot có label rps.managed/rps.scope/rps.boot/matchId/botId để inspect/cleanup sau khi server dừng. Không chạy Docker-in-Docker trong bot và không giảm các giới hạn để deploy.

## HTTPS, cookie và WebSocket

Caddyfile tự cấu hình HTTPS cho domain public và chuyển tiếp WebSocket. DNS/ports 80/443 phải đúng để cấp chứng chỉ; caddy-data giữ dữ liệu chứng chỉ qua restart. Với localhost, Caddy cấp CA local, không phải chứng chỉ public; cần tin cậy CA local chỉ trong máy thử. [Caddy: Automatic HTTPS](https://caddyserver.com/docs/automatic-https).

Proxy ghi đè X-Forwarded-For/X-Real-IP từ địa chỉ kết nối và chuyển X-Forwarded-Port; Tomcat chỉ tin header từ subnet Compose được cấu hình. Giữ cùng origin cho client, HTTP API và WSS; không bật wildcard CORS/origin. Header Forwarded do client gửi được xóa tại proxy. Prod dùng cookie Secure/HttpOnly/SameSite=Lax và CSRF cho mutation tài khoản. Không tắt Secure/CSRF để chữa lỗi deploy. Nếu WSS 403 trên cổng HTTPS khác 443, kiểm tra forwarded port và Origin/Host.

Không đặt thêm CDN/proxy ở phía trước mà chưa cấu hình trust: hiện Caddy xem địa chỉ kết nối trực tiếp là IP client. [Spring Boot: proxy headers](https://docs.spring.io/spring-boot/3.5/how-to/webserver.html), [Caddy reverse_proxy](https://caddyserver.com/docs/caddyfile/directives/reverse_proxy).

Proxy giới hạn body HTTP 6 MiB, bao gồm request chunked; backend vẫn kiểm tra source/JAR và giới hạn multipart. [Caddy request_body](https://caddyserver.com/docs/caddyfile/directives/request_body).

## Build và Flyway

```bash
docker compose --env-file .env.production -f infra/docker-compose.yml build rps-server
```

Image runtime dùng Java 21 JRE, user 10001, read-only root, thư mục tmp riêng và hai volume writable. Build context không chứa .env, runtime data, backup hoặc .git. Docker CLI nằm trong image nhưng runner tắt khi chưa opt-in socket.

Nếu chạy JAR ngoài Docker: build `server/gradlew bootJar` (Windows: `server/gradlew.bat -p server bootJar`), đặt SPRING_PROFILES_ACTIVE=prod, DB_URL/DB_USERNAME/DB_PASSWORD và BOT_DIRECTORY/REPLAY_DIRECTORY ở thư mục bền vững, rồi `java -jar <bootJar>`. Khi runner đọc source trên chính host, để BOT_DOCKER_VOLUME rỗng. Cần HTTPS proxy và trust IP của proxy riêng; không publish backend trực tiếp.

Prod mặc định tắt runner; nếu host JAR có Docker Linux/image và muốn dùng bot upload, đặt BOTS_ENABLED=true. Compose dùng override riêng để bật quyền này.

Flyway chạy V1–V16 khi app khởi động; V10 là Java migration cho lifecycle giải đấu, được đóng trong JAR cùng SQL V1–V9, V11–V12; V13 là Java migration cho vòng đấu/bracket/standings; V14 lưu audit giải đấu và lock_version. V15 thêm audit quản trị user/bot và trạng thái khóa bot; V16 thêm journal HTTP quản trị cho Audit/System; sao lưu DB trước khi nâng version. V11 lưu đăng ký/duyệt/check-in; V12 lưu hash/version lock và seeding. V13 cho phép loại trực tiếp, tối đa 32 bot và lưu cấu trúc giải/số đo thời gian. Vòng tròn 32 bot cần 496 suất chờ; tăng MAX_PENDING_MATCHES nếu chạy lịch này (mặc định 256). Backup toàn thư mục bots gồm versions/ cùng DB/replay. Giải đang dở chuyển RECOVERY_REQUIRED sau restart; owner review/retry rồi resume, không tự chạy lại. Backup giữ audit trong DB và frozen source trong bots/versions. Hibernate chỉ validate. Không sửa migration đã áp dụng, không clean/baseline tự động trong prod. DB quyền/credential hoặc schema sai sẽ làm startup thất bại. Chờ server healthy trước khi mở traffic.

## Backup, restore và upgrade

Cài Python 3.11+ trên host, không cần thư viện pip. Công cụ [tools/ops.py](../tools/ops.py) gọi Docker trực tiếp và ghi binary qua Python, tránh làm hỏng pg_dump/tar do redirect PowerShell.

```bash
python tools/ops.py --env-file .env.production --bots backup --output backups/before-upgrade
python tools/ops.py verify --source backups/before-upgrade
```

Backup dừng server trong thời gian ngắn để checkpoint và lấy một bộ DB/source/replay nhất quán, rồi khởi động lại nếu trước đó đang chạy. Không backup nóng từng volume riêng. File dump/custom format + tar + manifest SHA-256 chứa dữ liệu nhạy cảm; bảo vệ quyền đọc và copy sang nơi khác/mã hóa theo chính sách của bạn. Công cụ không backup session hoặc chứng chỉ Caddy; chứng chỉ có thể cấp lại nhưng cần giữ volume nếu muốn tránh cấp lại khi nâng cấp.

Restore vào **deployment mới/trống**, cấu hình env riêng, COMPOSE_PROJECT_NAME/subnet/ports khác nếu cùng host. Không khởi động server đích trước restore:

```bash
python tools/ops.py --env-file .env.restore --bots restore --source backups/before-upgrade
docker compose --env-file .env.restore -f infra/docker-compose.yml -f infra/docker-compose.bots.yml up -d --wait --wait-timeout 180
```

Restore chỉ nhận bộ backup tin cậy do bạn quản lý; checksum phát hiện thay đổi file, không xác thực người tạo dump SQL. Database public phải chưa có table, bot/replay volume trống và server đích đã dừng. Công cụ kiểm tra archive không có traversal/symlink/device, khôi phục file rồi pg_restore trong một transaction dưới role ứng dụng. Nếu restore lỗi, giữ target để kiểm tra và thử lại bằng target trống khác; không tự xóa dữ liệu. Không dùng `--clean` vào database đang hoạt động.

Nâng cấp: backup → dừng server → pull code/build image mới → up → kiểm tra health/login/history/bot/replay. Migration mới có thể không tương thích JAR cũ: rollback an toàn là khôi phục bộ backup vào deployment riêng với phiên bản code tương ứng, kiểm tra rồi mới chuyển traffic. Không dùng `down -v`, volume prune hay xóa source/replay khi nâng cấp.

## Kiểm chứng deployment

Trên **server thử riêng**, có Node.js 22+:

```bash
node tools/verify-deployment.mjs --url https://game-test.example.com --fixture server/build/local-run/deployment-fixture.json --seed
```

Script tạo hai account/bot và một trận/giải, kiểm tra HTTPS cookie, WSS same-origin và chặn origin lạ, ownership, history/ranking/replay. Sau restart hoặc restore, dùng cùng fixture:

```bash
node tools/verify-deployment.mjs --url https://game-test.example.com --fixture server/build/local-run/deployment-fixture.json --verify
```

Fixture chứa mật khẩu thử; chỉ lưu ở thư mục ignored/quyền hạn chế. Với localhost TLS, xuất CA của bộ thử và đặt NODE_EXTRA_CA_CERTS; không dùng NODE_TLS_REJECT_UNAUTHORIZED=0. Public TLS phải kiểm tra bằng domain thật sau triển khai. Agent không tự chọn domain/IP hay tạo hosting account.

Hướng dẫn thường ngày: [OPERATIONS.md](OPERATIONS.md). Tài khoản/quyền: [AUTH.md](AUTH.md).
