# Setup local và deploy lên VPS Ubuntu

[Mục lục](../README.md) · [Database](database.md) · [Monitoring](monitoring.md) · [Backup/Recovery](backup-recovery.md)

Một origin phục vụ client, REST và `/ws/game`; không cần deploy frontend riêng. Production dùng **một Central**, PostgreSQL và volume bot/replay bền vững. Máy chạy bot cần Docker Linux. Chọn **Nginx host** hoặc **Caddy Compose** làm proxy, tránh tranh cổng 80/443.

## Chạy local bằng JDK/Gradle

Cần JDK 21 hoặc 24 phù hợp Gradle wrapper hiện tại, PostgreSQL đang chạy, database và role ứng dụng đã tạo. Không commit password. Từ root repo, ví dụ PowerShell:

```powershell
$env:DB_URL = 'jdbc:postgresql://localhost:5432/rpsbattle'
$env:DB_USERNAME = 'rpsapp'
$env:DB_PASSWORD = Read-Host 'Database password'
Set-Location server
.\gradlew.bat bootRun --console=plain
```

Bash:

```bash
export DB_URL=jdbc:postgresql://localhost:5432/rpsbattle
export DB_USERNAME=rpsapp
read -r -s -p 'Database password: ' DB_PASSWORD
printf '\n'
export DB_PASSWORD
cd server
./gradlew bootRun --console=plain
```

Mở `http://localhost:8080/`. Dev không dùng cookie Secure bắt buộc như prod. Server bind 0.0.0.0; chỉ mở firewall/LAN khi chủ động chia sẻ. Nếu bot upload/build/test cần Docker, kiểm tra cấu hình runner và image theo [giới hạn](../bot/limits.md); không chạy code không tin cậy trực tiếp trên host.

Build/test tại `server`:

```sh
./gradlew :test :worker:test :bootJar --console=plain
./gradlew printBuildDir --console=plain
```

Windows dùng `gradlew.bat`. `printBuildDir` cho đường dẫn thật vì build trên Windows chuyển ra Gradle user home để tránh OneDrive khóa file. Boot JAR ở `libs/rps-battle-1.0-SNAPSHOT.jar` dưới build dir. Client và worker JAR được đóng trong bootJar. Dừng server cũ trước khi ghi đè JAR đang chạy.

## Chuẩn bị VPS

Ubuntu 64 bit, tài khoản SSH/sudo, Git, Python 3.11+, Docker Engine và Compose plugin. Cài theo [Docker Ubuntu chính thức](https://docs.docker.com/engine/install/ubuntu/), rồi kiểm tra `sudo docker version`, `sudo docker compose version`. Dockerfile build/runtime Java 21; không cần cài Gradle/JDK trên host nếu chạy hoàn toàn bằng Compose.

Trỏ bản ghi A của domain về IP public VPS; chỉ tạo AAAA khi IPv6 hoạt động. Mở TCP 80/443 tại cloud firewall, giữ cổng SSH thực tế. Không mở backend 8080 hoặc DB 5432 ra Internet. Docker publish ports có thể đi vòng UFW, nên giữ private port ngay trong Compose, không chỉ dựa vào rule UFW.

Từ máy cá nhân: `ssh ubuntu@IP_PUBLIC`. Trong Bash trên VPS:

```bash
cd ~
git clone https://github.com/nloq09/oantuti.git rps-battle
cd rps-battle
git status --short
cp -n infra/.env.production.example .env.production
chmod 600 .env.production
nano .env.production
```

Nếu repo đã có, vào đúng thư mục và kiểm tra phiên bản; clone không nhận code chưa push. Không dán các dấu hàng rào Markdown vào terminal.

## Điền cấu hình production

| Biến | Giá trị/cách chọn |
| --- | --- |
| COMPOSE_PROJECT_NAME | Ví dụ rpsbattle; giữ ổn định vì quyết định tên volume/scope. |
| POSTGRES_DB / POSTGRES_USER | Ví dụ rpsbattle / rpsuser, quản trị DB. |
| POSTGRES_PASSWORD | Mật khẩu quản trị riêng, không để trống. |
| DB_USERNAME / DB_PASSWORD | Ví dụ rpsapp và mật khẩu **khác** tài khoản quản trị. |
| RPS_DOMAIN / ACME_EMAIL | Hostname không có scheme/path và email của người vận hành. |
| RPS_IMAGE | Mặc định rps-battle:local; có thể dùng tag theo release. |
| RPS_SUBNET | Mặc định 172.30.90.0/24; đổi cả TRUSTED_PROXY_REGEX nếu đổi subnet. |
| DOCKER_GID | Kết quả `sudo stat -c '%g' /var/run/docker.sock`. |
| MAX_CONCURRENT_MATCHES | Local worker pool, mặc định 4; demo ít RAM nên bắt đầu 1 rồi đo. |
| MAX_PENDING_MATCHES / RATE_LIMIT_MULTIPLIER | Mặc định 256 / 1; không tăng để che lỗi worker/proxy. |

Có thể sinh mỗi mật khẩu bằng `openssl rand -hex 32` và lưu riêng. Không dùng password ví dụ chung; file env thật nằm ngoài Git. `config --quiet` kiểm cấu hình không in env đã interpolate.

Compose kết nối `jdbc:postgresql://postgres:5432/<db>`; `localhost` trong container không phải DB container. Init script tạo app role trên **volume mới**; sửa env không tự đổi password PostgreSQL đã tồn tại. Không dùng `down -v` để sửa lỗi auth.

Pull runtime trên Central:

```bash
sudo docker pull python:3.12-slim
sudo docker pull gcc:14
sudo docker pull eclipse-temurin:21-jdk
```

Overlay bots bật runner và Docker socket cho server tin cậy. Bot sandbox không được mount socket. Quyền Docker socket có thể kiểm soát host; dùng Engine dành riêng cho ứng dụng và giữ các giới hạn sandbox.

## Phương án A: Nginx host và domain

Áp dụng khi đã cài Nginx. Tại root repo:

```bash
sudo docker compose --env-file .env.production -f infra/docker-compose.yml -f infra/docker-compose.nginx.yml -f infra/docker-compose.bots.yml config --quiet
sudo docker compose --env-file .env.production -f infra/docker-compose.yml -f infra/docker-compose.nginx.yml -f infra/docker-compose.bots.yml up -d --build --wait --wait-timeout 180 postgres rps-server
curl --fail http://127.0.0.1:8080/actuator/health
```

Overlay publish backend chỉ ở loopback và không khởi động Caddy. Nếu Caddy deployment cũ đang chạy, dừng đúng service reverse-proxy trước khi dùng Nginx; không dừng site khác.

Thay hostname ví dụ; nếu đã có site cùng tên thì backup file trước. Chỉ cài template ở lần đầu:

```bash
RPS_DOMAIN=game.example.com
getent ahostsv4 "$RPS_DOMAIN"
sudo install -m 0644 infra/nginx/rps-battle-domain.conf /etc/nginx/sites-available/rps-battle
sudo sed -i "s/__DOMAIN__/$RPS_DOMAIN/g" /etc/nginx/sites-available/rps-battle
sudo ln -s /etc/nginx/sites-available/rps-battle /etc/nginx/sites-enabled/rps-battle
sudo nginx -t
sudo systemctl reload nginx
curl --fail "http://$RPS_DOMAIN/actuator/health"
```

`ln -s` chỉ lần đầu; nếu link đúng đã có thì bỏ qua. `nginx -t` lỗi thì sửa trước reload. Kiểm tra HTTP/DNS từ máy ngoài VPS, nhưng chưa gửi password qua HTTP.

Bật HTTPS theo [Certbot Nginx/Snap](https://certbot.eff.org/instructions?ws=nginx&os=snap). Nếu Snap/Certbot chưa có, cài theo hướng dẫn đó; với bản Snap đã cài:

```bash
sudo /snap/bin/certbot --nginx -d "$RPS_DOMAIN" --redirect
sudo nginx -t
curl --fail "https://$RPS_DOMAIN/actuator/health"
sudo /snap/bin/certbot renew --dry-run
sudo snap services certbot
```

Điền email, đọc/chấp nhận điều khoản nếu đồng ý, chọn chia sẻ email tùy ý. Kiểm tra renewal timer. **Không cài lại template HTTP lên file Certbot đã cấu hình TLS khi cập nhật game.** Không tắt Secure cookie/CSRF/origin để chữa lỗi login/WSS.

## Phương án B: Caddy Compose

Chỉ dùng khi không có proxy khác chiếm 80/443:

```bash
sudo docker compose --env-file .env.production -f infra/docker-compose.yml -f infra/docker-compose.bots.yml config --quiet
sudo docker compose --env-file .env.production -f infra/docker-compose.yml -f infra/docker-compose.bots.yml up -d --build --wait --wait-timeout 180
```

[Caddy tự quản lý HTTPS](https://caddyserver.com/docs/automatic-https) cho domain public khi DNS, cổng và storage đúng. Giữ caddy-data/config bền vững. Không dùng overlay Nginx trong phương án này. Khi thêm CDN/proxy phía trước, cấu hình lại trust theo hạ tầng thật; không tin mọi proxy.

## Kiểm tra sau triển khai

1. Health HTTPS UP và đăng ký/login bằng tài khoản USER. Bootstrap ADMIN theo [Users](../admin/users.md); không có password admin mặc định.
2. Hai trình duyệt tạo/join phòng và chơi đến kết quả; WSS `/ws/game` upgrade được, thường HTTP 101.
3. Bot nhỏ build/test/READY, giải thử với **chủ giải và người thi đấu khác nhau**, check-in/match/result/replay.
4. Kiểm tra cả Central Docker và worker remote nếu dùng: Admin duyệt máy dùng chung hoặc chủ giải đăng ký máy riêng đúng giải. Server tự phân trận, không cần nhóm. Kiểm tra bài LOCKED, policy và scope; UI ONLINE không chứng minh job đã được giao. Xem [worker dùng chung](../admin/workers.md) và [worker riêng](../organizer/tournament-operation.md#worker-riêng-do-chủ-giải-thêm).
5. Verify backup và thử restore ở deployment trống riêng.

Proxy mẫu Nginx/Caddy hiện giới hạn body **6 MiB**, trong khi backend remote completion cho phép request đến **22 MiB** và replay 15 MiB. Replay nhỏ hoạt động trong giới hạn proxy; muốn hỗ trợ trọn giới hạn backend phải cấu hình body limit riêng cho route complete trên proxy và thử upload lớn. Không tuyên bố mẫu hiện tại đã hỗ trợ replay 15 MiB qua Internet.

## Cập nhật bản mới

Thông báo downtime, backup theo [Backup/Recovery](backup-recovery.md). Ví dụ Nginx:

```bash
sudo docker compose --env-file .env.production -f infra/docker-compose.yml -f infra/docker-compose.nginx.yml -f infra/docker-compose.bots.yml stop --timeout 60 rps-server
git pull --ff-only
sudo docker compose --env-file .env.production -f infra/docker-compose.yml -f infra/docker-compose.nginx.yml -f infra/docker-compose.bots.yml up -d --build --wait --wait-timeout 180 postgres rps-server
```

Nếu pull/build lỗi, giữ volume và xử lý; không xóa dữ liệu. Với Caddy bỏ overlay nginx, dùng cùng bộ Compose như lúc tạo deployment. Login lại sau restart; kiểm tra giải RECOVERY_REQUIRED. Rollback schema không chỉ là chạy JAR cũ: restore backup tương thích vào deployment riêng rồi kiểm tra trước khi chuyển traffic.

Build tạo URL JS/CSS dạng `/ui/<mã-nội-dung>/…` và cập nhật tham chiếu trong HTML/module/style. Các file này cache public một năm; mã thay đổi khi frontend thay đổi nên trình duyệt nhận URL mới sau deploy. URL `/js/` và `/css/` cũ vẫn revalidate; HTML và API vẫn không lưu cache. Backend nén JS/CSS khi client gửi `Accept-Encoding` phù hợp, kể cả qua Nginx. Không áp dụng cache public cho `/api/admin/` hoặc cookie/session.

Trang Admin chỉ tải module của mục đang mở, tải trước module khi rê chuột hoặc focus sidebar. Cảnh báo dùng chung kết quả với Tổng quan/Hệ thống thay vì gọi hệ thống hai lần mỗi chu kỳ. Docker được kiểm tra bằng một luồng nền riêng mỗi 15 giây; trạng thái ban đầu là UNKNOWN/đang kiểm tra, không suy ra Docker hỏng khi mẫu đầu tiên chưa hoàn tất.

Bản tối ưu người chơi tự chạy migration V45 khi khởi động. Frontend và backend cần cập nhật cùng bản vì trang chi tiết dùng `summary?light=true` cùng API phân trang người tham gia/bảng điểm. Header, thanh giai đoạn và quyền đăng ký/nộp bot/check-in giữ nguyên. Không cache public API cá nhân; cache thông báo được giới hạn theo từng tài khoản và trạng thái giải. Tab Tạo giải và các mục mở rộng chỉ tải code/dữ liệu khi mở; chuyển mục vẫn giữ form và dữ liệu chưa gửi.

### Nâng cấp bản điều phối worker tự động và worker riêng

Flyway chạy V43/V44 khi Central mới khởi động, không cần chạy SQL thủ công hoặc xóa database:

- V43 thêm policy/route cho pool chung, sao chép policy và hàng rào retry cũ. Giải đã đặt LOCAL/REMOTE_REQUIRED giữ nguyên; giải chưa có policy riêng mặc định AUTO. Dữ liệu nhóm cũ còn để tra lịch sử và không dùng để điều phối.
- V44 thêm scope token/máy theo giải. Worker/credential dùng chung đã có vẫn dùng được, không tự biến thành máy riêng. Máy riêng phải đăng ký từ token của đúng giải và dùng state-dir riêng.
- Kiểm tra **Admin → Worker** có ba tab **Máy worker / Token đăng ký / Điều phối**; chủ giải mở **Quản lý giải → Worker riêng → Thêm worker cho giải**. Thêm worker không đổi policy hoặc các giới hạn do Admin đặt.
- Worker dùng cùng protocol 1. Khi cập nhật binary agent, dừng agent cũ, thay JAR bằng bản tải từ Central mới và chạy lại với cùng state-dir; không tạo token mới cho danh tính còn hợp lệ. Dùng ngừng nhận trận và chờ hết việc trước khi dừng nếu muốn tránh gián đoạn.

Central vẫn cần Docker/image/quyền để build và kiểm thử bot, kể cả khi mọi trận được chạy từ xa. Xem [schema worker](database.md#điều-phối-worker-và-phạm-vi-theo-giải) và [runbook khôi phục](backup-recovery.md#remote-worker-mất-mạng-hoặc-restart) trước khi thao tác với job đang dở.

## Database ngoài VPS và chạy JAR native

PostgreSQL ngoài VPS được nếu có kết nối an toàn và quyền schema/migration phù hợp. Base Compose hiện hard-code hostname postgres; cần overlay riêng thay environment DB_URL/credentials và dependency phù hợp, không chỉ thêm DB_URL vào env rồi mong base file đổi theo. Công cụ `ops.py` hiện backup PostgreSQL Compose, chưa tự backup database managed bên ngoài.

Chạy native: đặt SPRING_PROFILES_ACTIVE=prod, DB_URL/DB_USERNAME/DB_PASSWORD, BOT_DIRECTORY/REPLAY_DIRECTORY bền vững; build bootJar rồi chạy `java -jar <bootJar>`. Runner mặc định tắt, bật BOTS_ENABLED=true chỉ khi Docker Linux/image/quyền đã sẵn sàng. BOT_DOCKER_VOLUME để rỗng khi dùng file trên host. Giữ HTTPS proxy và trusted proxy đúng môi trường.

## Nguồn đối chiếu

- [Dockerfile](../../Dockerfile), [Gradle server](../../server/build.gradle), [prod profile](../../server/src/main/resources/application-prod.yml).
- [Compose](../../infra/docker-compose.yml), [Nginx overlay](../../infra/docker-compose.nginx.yml), [bot overlay](../../infra/docker-compose.bots.yml), [env mẫu](../../infra/.env.production.example).
- [Nginx template](../../infra/nginx/rps-battle-domain.conf), [Caddyfile](../../infra/Caddyfile), [ops tool](../../tools/ops.py).
