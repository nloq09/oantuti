# Deploy RPS Battle lên VPS Ubuntu

**Đã cài Nginx?** Dùng [Nginx + domain + HTTPS](DEPLOY-VPS-NGINX-DOMAIN.md), hoặc [HTTPS cho IP public](DEPLOY-VPS-NGINX-IP.md) khi chưa có domain. Các hướng dẫn này thay cho bộ Caddy dưới đây; không chạy cả hai proxy trên cùng cổng 80/443.

Hướng dẫn cho mã nguồn hiện tại, cập nhật ngày 23/10/2026. Các lệnh bên dưới chạy trong **Bash trên VPS Ubuntu**, trừ bước SSH từ máy cá nhân. Đây là hướng dẫn triển khai; tài liệu không có nghĩa bản trên VPS đã được triển khai hoặc kiểm thử.

## 1. Mô hình triển khai

Một VPS chạy Docker Compose:

```text
Người chơi → https://game.example.com
                    ↓
               Caddy :443
                    ↓
          rps-server :8080
          ├── Client HTML/CSS/JS
          ├── REST + WebSocket
          ├── PostgreSQL :5432
          └── Docker sandbox cho bot
```

Client đã được đóng trong JAR khi build. Không cần cài npm, deploy Vercel hoặc chạy thêm web server cho client. Một domain phục vụ cả giao diện, tài khoản, API và WebSocket.

Bộ Compose có PostgreSQL 17, server Java 21 và Caddy tự quản lý HTTPS. Bot riêng, build code, public/hidden tests và final validation cần bật Docker sandbox trên VPS. Worker từ xa là tùy chọn ở mục 12.

Giữ **một replica server trung tâm**. Phòng chơi, session và tournament registry còn có trạng thái trong RAM; thêm remote worker không làm server trung tâm tự hỗ trợ nhiều replica.

## 2. Chuẩn bị VPS và domain

Cần:

- VPS Ubuntu 24.04 LTS 64 bit, tài khoản SSH có quyền sudo.
- Domain hoặc subdomain do bạn quản lý, ví dụ `game.example.com`.
- Quyền mở cổng trong firewall/security group của nhà cung cấp VPS.
- Dung lượng cho image Docker, PostgreSQL, source/version bot, replay và backup.

Có thể dùng mốc khởi đầu **4 vCPU, 8 GiB RAM, 40 GiB SSD** để thử số trận bot nhỏ, rồi điều chỉnh theo đo đạc. Đây là dự trù tài nguyên, không phải kết quả benchmark hay cam kết số người chơi. Mặc định bốn trận đồng thời có thể tạo tám tiến trình bot, mỗi sandbox giới hạn RAM 384 MiB; build/test còn dùng tài nguyên riêng. VPS ít RAM nên bắt đầu với `MAX_CONCURRENT_MATCHES=1`.

Tại nơi quản lý DNS, tạo bản ghi **A**:

```text
Tên: game
Giá trị: IPv4 public của VPS
```

Chỉ tạo AAAA khi VPS có IPv6 hoạt động và firewall IPv6 cũng đã mở. Nếu dùng Cloudflare, để DNS only trong lần triển khai đầu; bật proxy/CDN sau khi đã cấu hình lại chuỗi trusted proxy và kiểm tra IP client.

Từ PowerShell trên máy cá nhân:

```powershell
ssh ubuntu@IP_PUBLIC_CUA_VPS
```

Thay `ubuntu` bằng tài khoản SSH thực tế.

## 3. Cài Docker Engine và Compose

Các lệnh cài dưới đây dành cho VPS Ubuntu mới. Nếu máy đã chạy Docker hoặc ứng dụng khác, kiểm tra cài đặt hiện có trước khi thay đổi.

```bash
sudo apt update
sudo apt install -y ca-certificates curl git python3 openssl nano ufw
sudo install -m 0755 -d /etc/apt/keyrings
sudo curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
sudo chmod a+r /etc/apt/keyrings/docker.asc

sudo tee /etc/apt/sources.list.d/docker.sources > /dev/null <<EOF
Types: deb
URIs: https://download.docker.com/linux/ubuntu
Suites: $(. /etc/os-release && printf '%s' "$VERSION_CODENAME")
Components: stable
Architectures: $(dpkg --print-architecture)
Signed-By: /etc/apt/keyrings/docker.asc
EOF

sudo apt update
sudo apt install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
sudo systemctl enable --now docker
sudo docker version
sudo docker compose version
sudo docker run --rm hello-world
```

Dùng `sudo docker` xuyên suốt hướng dẫn, không cần thêm tài khoản SSH vào nhóm Docker. Host không cần cài JDK hoặc Gradle vì Dockerfile tự build bằng Java 21.

Nguồn: [cài Docker Engine trên Ubuntu](https://docs.docker.com/engine/install/ubuntu/).

## 4. Mở cổng

Trong firewall của nhà cung cấp VPS, cho phép:

- Cổng SSH thực tế của bạn, thường TCP 22; nên giới hạn theo IP quản trị.
- TCP 80 và TCP 443 từ Internet.
- Không mở PostgreSQL 5432 hoặc backend 8080 ra Internet.

Nếu bật UFW, phải cho phép đúng cổng SSH **trước khi enable**. Ví dụ khi SSH dùng cổng 22:

```bash
sudo ufw allow 22/tcp
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
sudo ufw enable
sudo ufw status
```

Nếu SSH dùng cổng khác, thay 22 bằng cổng đó. Giữ phiên SSH hiện tại và thử mở phiên mới để xác nhận vẫn truy cập được.

Docker publish port có thể đi vòng qua một số quy tắc UFW. Bộ Compose chỉ publish Caddy 80/443; không thêm `ports:` cho server hoặc database. Xem [lưu ý firewall của Docker](https://docs.docker.com/engine/install/ubuntu/#firewall-limitations).

## 5. Đưa mã nguồn lên VPS

Từ thư mục cá nhân của tài khoản SSH:

```bash
cd ~
git clone https://github.com/nloq09/oantuti.git rps-battle
cd rps-battle
git status --short
git log -1 --oneline
```

VPS chỉ nhận được code đã có trên GitHub. Các thay đổi chưa commit/push trên máy Windows sẽ không xuất hiện khi clone. Nếu repository chuyển sang private, dùng phương thức xác thực Git phù hợp; không chèn token vào URL rồi lưu trong lịch sử lệnh.

Các bước còn lại chạy tại **thư mục gốc repo**, nơi có `Dockerfile`, `client/`, `server/`, `worker/` và `infra/`.

## 6. Tạo cấu hình production

```bash
cp infra/.env.production.example .env.production
chmod 600 .env.production
nano .env.production
```

Nội dung cần điền tương ứng:

```dotenv
COMPOSE_PROJECT_NAME=rpsbattle
POSTGRES_DB=rpsbattle
POSTGRES_USER=rpsuser
POSTGRES_PASSWORD=
DB_USERNAME=rpsapp
DB_PASSWORD=
RPS_DOMAIN=game.example.com
ACME_EMAIL=you@example.com
RPS_IMAGE=rps-battle:local
RPS_SUBNET=172.30.90.0/24
HTTP_PORT=80
HTTPS_PORT=443
BIND_ADDRESS=0.0.0.0
DOCKER_GID=0
MAX_CONCURRENT_MATCHES=1
MAX_PENDING_MATCHES=256
RATE_LIMIT_MULTIPLIER=1
```

Trước khi lưu:

1. Điền `POSTGRES_PASSWORD` và `DB_PASSWORD` bằng **hai mật khẩu khác nhau**, đủ mạnh. Có thể tạo từng mật khẩu với `openssl rand -hex 32`; lưu riêng trong trình quản lý mật khẩu.
2. Đổi `RPS_DOMAIN` thành hostname thật, **không có** `https://` hoặc dấu `/`.
3. Đổi `ACME_EMAIL` thành email của bạn.
4. Lấy group ID của Docker socket:

```bash
sudo stat -c '%g' /var/run/docker.sock
```

Điền số vừa nhận vào `DOCKER_GID`. Không mặc định rằng Ubuntu dùng group 0.

Hai tài khoản database khác nhau: `rpsuser` quản trị PostgreSQL, `rpsapp` là role ứng dụng không có SUPERUSER/CREATEDB/CREATEROLE. Compose tự đặt URL ứng dụng thành `jdbc:postgresql://postgres:5432/rpsbattle`; không dùng `localhost` giữa các container.

Init role/password chỉ chạy khi volume PostgreSQL còn mới. Sửa mật khẩu trong file env sau khi database đã tạo **không tự đổi mật khẩu bên PostgreSQL**.

`.env.production` đã được Git ignore. Không commit file thật, không đưa password vào Dockerfile và không chia sẻ output của `docker compose config`; dùng `config --quiet` để kiểm tra.

Nếu subnet `172.30.90.0/24` đã bị sử dụng, đổi `RPS_SUBNET` và thêm `TRUSTED_PROXY_REGEX` khớp subnet mới. Ví dụ subnet `172.30.91.0/24` dùng `172[.]30[.]91[.][0-9]+`.

## 7. Bật bot và khởi động toàn bộ hệ thống

Tải image trước trên chính VPS:

```bash
sudo docker pull python:3.12-slim
sudo docker pull gcc:14
sudo docker pull eclipse-temurin:21-jdk
```

Kiểm tra cấu hình rồi khởi động:

```bash
sudo docker compose --env-file .env.production -f infra/docker-compose.yml -f infra/docker-compose.bots.yml config --quiet

sudo docker compose --env-file .env.production -f infra/docker-compose.yml -f infra/docker-compose.bots.yml up -d --build --wait --wait-timeout 180
```

Lần đầu build có thể lâu hơn các lần sau. Dockerfile build cả server, client và worker JAR; Flyway tự tạo/nâng schema khi server chạy, Hibernate chỉ validate. Mã nguồn hiện tại có migration tới V41, không cần tạo bảng thủ công.

File `docker-compose.bots.yml` bật `BOTS_ENABLED=true` và mount Docker socket vào server để tạo sandbox. Chỉ dùng Engine/VPS tin cậy dành cho ứng dụng; socket cho server quyền điều khiển Docker host. Bot con không được cấp socket, mạng, database hoặc toàn bộ thư mục source. Không thay cách chạy bằng thực thi code người dùng trực tiếp trên host.

Nếu chỉ cần chơi tay hoặc bot chiến thuật tích hợp, bỏ `-f infra/docker-compose.bots.yml` trong các lệnh. Khi đó bot riêng/build/test/final validation cần sandbox sẽ không hoạt động. Giải người chơi tay không bắt buộc Docker sandbox để thi đấu.

Kiểm tra dịch vụ:

```bash
sudo docker compose --env-file .env.production -f infra/docker-compose.yml -f infra/docker-compose.bots.yml ps
sudo docker compose --env-file .env.production -f infra/docker-compose.yml -f infra/docker-compose.bots.yml logs --tail 100 rps-server
sudo docker compose --env-file .env.production -f infra/docker-compose.yml -f infra/docker-compose.bots.yml logs --tail 100 reverse-proxy
```

Mở:

```text
https://game.example.com/
https://game.example.com/actuator/health
```

Thay hostname bằng domain thật. Health mong đợi `{"status":"UP"}`. `/actuator/health` kiểm tra cả app/database; `/api/health` chỉ là kiểm tra app đơn giản.

Caddy tự cấp và gia hạn HTTPS khi DNS đúng, cổng 80/443 tới được VPS và volume chứng chỉ ghi được. Không cần cài Nginx hoặc Certbot thêm theo mô hình này. Nguồn: [Caddy Automatic HTTPS](https://caddyserver.com/docs/automatic-https).

## 8. Tạo tài khoản admin

1. Mở web, đăng ký một tài khoản bình thường và xác minh đúng username.
2. Trên VPS mở psql bằng role quản trị DB; ví dụ này giữ `POSTGRES_USER=rpsuser`, `POSTGRES_DB=rpsbattle` như mục 6:

```bash
sudo docker compose --env-file .env.production -f infra/docker-compose.yml exec postgres psql -U rpsuser -d rpsbattle
```

3. Trong psql, thay `username_cua_ban` bằng username đã xác minh:

```sql
BEGIN;
SELECT id, username, role FROM users WHERE username='username_cua_ban';
UPDATE users SET role='ADMIN'
WHERE username='username_cua_ban'
RETURNING id, username, role;
```

Chỉ khi kết quả đúng **một tài khoản cần cấp quyền**, nhập:

```sql
COMMIT;
\q
```

Nếu sai tài khoản hoặc số dòng không đúng, nhập `ROLLBACK;` thay cho `COMMIT;`.

4. Đăng xuất rồi đăng nhập lại. Admin đi vào khu quản trị tại `/admin/index.html`.

Không có admin/password mặc định. Tài khoản admin dùng khu quản trị; tạo tài khoản USER riêng nếu muốn chơi hoặc tổ chức giải bằng giao diện người dùng. Chi tiết: [tài khoản và quyền](AUTH.md).

## 9. Kiểm tra sau deploy

Dùng hai tài khoản USER khác nhau hoặc hai trình duyệt độc lập:

- Đăng ký, đăng nhập, đăng xuất; tải lại trang vẫn nhận đúng tài khoản.
- Tạo phòng hai người, máy thứ hai nhập mã; xếp quân, sẵn sàng và chơi một trận.
- Mở DevTools → Network → WS: `wss://<domain>/ws/game` kết nối thành công, thông thường HTTP upgrade 101.
- Upload một bot mẫu hợp lệ; thử build/test và cho bot chơi.
- Tạo giải nhỏ hai người/bot; đăng ký → duyệt → khóa bài/validation/check-in nếu có → thi đấu → kết quả.
- Đăng nhập chủ giải mở được quản lý; tài khoản người chơi chỉ thấy quyền của mình.
- Mở lịch sử và replay; thử tải lại replay sau một lần restart có kế hoạch khi không còn trận đang chạy.
- Admin kiểm tra trang System và Worker; không coi health UP là bằng chứng sandbox đã chạy thành công.

Trong production cookie dùng Secure/HttpOnly/SameSite=Lax, mutation cần CSRF. Giữ HTTPS và cùng origin cho client/API/WebSocket. Không tắt CSRF hoặc mở wildcard WebSocket origin để chữa lỗi cấu hình.

## 10. Dữ liệu, backup và cập nhật

Compose giữ dữ liệu trong named volume: PostgreSQL, bot/source/coding/versions, replay và chứng chỉ Caddy. Không đổi `COMPOSE_PROJECT_NAME` tùy tiện vì tên volume phụ thuộc giá trị này.

Phải backup **cả database lẫn bot/replay** tại cùng một mốc. Công cụ hiện có sẽ dừng server ngắn để lấy bản nhất quán và chạy lại nếu trước đó server đang hoạt động:

```bash
sudo python3 tools/ops.py --env-file .env.production --bots backup --output backups/before-upgrade
sudo python3 tools/ops.py verify --source backups/before-upgrade
```

Thư mục output phải chưa tồn tại; dùng tên riêng cho mỗi lần backup. Sao chép backup sang máy hoặc storage khác và bảo vệ quyền đọc. Script không tự lập lịch backup. Lưu `.env.production` riêng, bảo mật; công cụ không sao lưu file env hoặc chứng chỉ Caddy.

Cập nhật khi đã backup và thông báo downtime:

```bash
sudo docker compose --env-file .env.production -f infra/docker-compose.yml -f infra/docker-compose.bots.yml stop --timeout 60 rps-server
git pull --ff-only
sudo docker compose --env-file .env.production -f infra/docker-compose.yml -f infra/docker-compose.bots.yml up -d --build --wait --wait-timeout 180
```

Nếu pull/build lỗi, kiểm tra và khắc phục trước khi tiếp tục; không xóa volume để thử lại. Sau cập nhật kiểm tra health, login, bot, giải đấu và replay. Server/client/worker nên dùng bản phát hành tương thích; tải lại worker JAR khi nâng giao thức hoặc game core.

Sau restart, session bị mất nên cần đăng nhập lại; phòng chơi trong RAM không được khôi phục nguyên bàn cờ. Giải RUNNING/PAUSED hoặc READY đã có lịch có thể sang RECOVERY_REQUIRED. Chủ giải/admin kiểm tra trận lỗi, review/retry rồi resume khi đủ điều kiện, hoặc hủy giải. READY chưa có lịch đấu được giữ.

Không dùng `docker compose down -v`, volume prune hoặc xóa thư mục dữ liệu để nâng cấp. Migration mới có thể không tương thích binary cũ; rollback cần bộ backup và phiên bản code tương ứng, phục hồi vào deployment trống riêng trước khi chuyển traffic. Chi tiết restore: [vận hành](OPERATIONS.md) và [triển khai tổng quát](DEPLOYMENT.md).

## 11. Lỗi thường gặp

### Domain không mở được / HTTPS chưa cấp

Kiểm tra DNS A/AAAA, firewall nhà cung cấp, cổng 80/443 và log `reverse-proxy`. Một Nginx/Apache khác chiếm 80/443 sẽ khiến Caddy không bind được. Không bỏ qua cảnh báo chứng chỉ để vận hành production.

### Server lỗi kết nối database

Xem log `rps-server` và `postgres`. URL trong Compose dùng hostname `postgres`, không phải localhost. Kiểm tra password thực trong database; thay env không cập nhật password ở volume đã khởi tạo. Không reset volume có dữ liệu.

### BOT_SANDBOX_UNAVAILABLE / SANDBOX_UNAVAILABLE

Kiểm tra đã dùng file override bots, đúng DOCKER_GID và ba image đã pull. Có thể kiểm tra từ server:

```bash
sudo docker compose --env-file .env.production -f infra/docker-compose.yml -f infra/docker-compose.bots.yml exec rps-server docker info --format '{{.OSType}}'
```

Mong đợi `linux`; đây mới là kiểm tra quyền daemon, vẫn cần chạy bot thử. Bot crash/timeout hoặc trả JSON/action sai phải kiểm tra theo [giao thức bot](BOT-PROTOCOL.md), không tăng quyền sandbox để sửa bot.

### WebSocket 403 / đăng nhập không giữ phiên

Dùng đúng domain HTTPS, cùng origin và port. Với Compose mặc định, proxy trust khớp subnet `172.30.90.0/24`. Nếu đổi subnet phải đổi TRUSTED_PROXY_REGEX tương ứng. Nếu thêm CDN/load balancer, cần kiểm tra lại forwarded headers và IP client; không tin mọi IP bằng regex `.*`.

### Server hết RAM hoặc ổ đĩa

Theo dõi tài nguyên:

```bash
sudo docker stats --no-stream
sudo docker system df
df -h
```

Hạ số trận bot đồng thời theo tài nguyên thực; kiểm tra cả coding jobs và container con vì chúng có giới hạn riêng, không nằm toàn bộ trong RAM limit của container server. Theo dõi dung lượng version/source/replay; không xóa artifact đang được lịch sử/giải tham chiếu. Mặc định Compose giới hạn riêng container server ở 1 GiB RAM và 2 CPU.

### Server đạt giới hạn giải / queue đầy

Đây là giới hạn ứng dụng hoặc hàng đợi, không phải lỗi DNS/HTTPS. Kiểm tra giải đang hoạt động, cơ chế cleanup và tài nguyên worker trước khi tăng giới hạn. MAX_PENDING_MATCHES là giới hạn hàng đợi local, không phải số người chơi web đồng thời.

## 12. Tùy chọn: thêm remote bot worker

Bộ Compose ở mục 7 đã chạy được bot trên VPS. Chỉ cần remote worker nếu muốn đưa việc thi đấu bot sang máy Docker khác.

1. Admin mở `/admin/workers.html`, tạo token đăng ký với thời hạn/số lượt phù hợp.
2. Tải `rps-worker.jar` từ trang setup của chính server, cài Java 21+ và Docker Linux trên máy worker.
3. Pull ba image ở mục 7 trên **máy worker**.
4. Nhập token qua biến môi trường, không đưa token vào URL hoặc lệnh có thể lưu vào history.

Ví dụ trên worker Windows/PowerShell:

```powershell
$env:WORKER_REGISTRATION_TOKEN = [System.Net.NetworkCredential]::new('', (Read-Host 'Registration token' -AsSecureString)).Password
try {
    java -jar rps-worker.jar --central=https://game.example.com --name=WORKER-01 --state-dir=./worker-state --max-matches=1 --max-bots=2 --serve
} finally {
    Remove-Item Env:WORKER_REGISTRATION_TOKEN -ErrorAction SilentlyContinue
}
```

Sau khi máy đã đăng ký, giữ thư mục `worker-state`. Những lần chạy tiếp dùng cùng lệnh Java và thư mục state, không cần nhập lại token.

5. Admin duyệt worker TRUSTED, kiểm tra ONLINE/runtime/slot, thêm vào nhóm worker.
6. Trước khi giải tạo lịch/job, đặt routing của giải sang REMOTE_REQUIRED và chọn nhóm/trust/capacity phù hợp.
7. Xác nhận agent chạy với `--serve`; chế độ chỉ heartbeat không nhận và thực thi trận liên tục.

Worker chủ động kết nối HTTPS ra Central, không cần mở cổng inbound trên máy worker. Dừng cửa sổ Java hoặc máy ngủ/tắt thì worker sẽ offline; muốn chạy 24/7 cần quản lý tiến trình bằng service riêng trên hệ điều hành đó.

**Phạm vi hiện tại:** remote execution dành cho giải BOT_ONLY dùng submission LOCKED. Build/compile/test/final validation vẫn chạy qua sandbox của Central; thêm worker từ xa không thay thế Docker cần trên VPS cho các bước này. Giải bot upload legacy và phòng chơi bot riêng vẫn theo đường local tương ứng. Chi tiết: [nhóm worker và routing](CHANG-11-NHOM-9-CAPACITY-GROUPS.md), [quản trị worker](ADMIN-REMOTE-WORKER-OPERATIONS.md).

## 13. Checklist bàn giao

- [ ] Domain thật trỏ đúng VPS; HTTPS hợp lệ.
- [ ] Chỉ cổng SSH thực tế và 80/443 được mở theo nhu cầu; không publish DB/backend.
- [ ] Env dùng mật khẩu riêng, quyền 600, không có trong Git.
- [ ] Một server trung tâm, PostgreSQL và Caddy healthy.
- [ ] Chạy được phòng hai người và WebSocket.
- [ ] Bot build/test/thi đấu thành công nếu bật chức năng bot.
- [ ] Admin và USER dùng đúng giao diện/quyền.
- [ ] Giải nhỏ chạy tới kết quả và mở được replay.
- [ ] Có backup DB + bot/replay, đã thử restore ở môi trường riêng.
- [ ] Có người chịu trách nhiệm cập nhật, theo dõi dung lượng và xử lý recovery.
