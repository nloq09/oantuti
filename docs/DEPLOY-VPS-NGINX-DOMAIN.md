# Deploy trên Ubuntu đã có Nginx và domain

Dành cho trường hợp đã cài Docker/Nginx, có domain riêng. **Dùng hướng dẫn này cho domain; không cần làm luồng chứng chỉ IP.** Các lệnh chạy trong Bash trên VPS. Mô hình: Nginx host → Docker server tại 127.0.0.1:8080 → PostgreSQL Docker; bot dùng Docker sandbox.

## 1. Trỏ domain và mở cổng

Domain dùng trong các lệnh là `rpsbattle.duckdns.org`. Bản ghi A của hostname phải trỏ tới IPv4 public của VPS. Với DuckDNS, cập nhật IP trong dashboard DuckDNS khi IP VPS thay đổi; không nhập https:// hoặc port vào giá trị IP.

Chỉ tạo AAAA nếu IPv6 VPS hoạt động. Nếu dùng Cloudflare, để DNS only khi thiết lập ban đầu; triển khai CDN cần cấu hình thêm proxy trust.

Mở TCP 80/443 trong firewall nhà cung cấp; giữ cổng SSH thực tế. Nếu UFW đang bật:

```bash
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
sudo ufw status
```

Không mở 8080/5432 ra Internet.

## 2. Đưa code lên VPS và đặt domain

Nếu chưa clone:

```bash
cd ~
git clone https://github.com/nloq09/oantuti.git rps-battle
cd rps-battle
```

VPS phải nhận được bản code đầy đủ có `infra/docker-compose.nginx.yml` và `infra/nginx/rps-battle-domain.conf`. Các file mới chưa commit/push sẽ không có trong bản clone GitHub; cần chuyển file sang VPS hoặc phát hành bản code trước. Không đưa file env thật lên Git.

Đặt domain dùng trong các lệnh:

```bash
RPS_DOMAIN=rpsbattle.duckdns.org
getent ahostsv4 "$RPS_DOMAIN"
```

Kết quả DNS phải trỏ đúng IP public VPS. Nếu mở phiên SSH khác, nhập lại RPS_DOMAIN.

## 3. Cấu hình database và bot

Tại thư mục gốc repo:

```bash
cp -n infra/.env.production.example .env.production
chmod 600 .env.production
nano .env.production
```

Điền POSTGRES_PASSWORD và DB_PASSWORD bằng hai mật khẩu khác nhau; RPS_DOMAIN là domain thật, ACME_EMAIL là email của bạn. Giữ POSTGRES_DB=rpsbattle, POSTGRES_USER=rpsuser và DB_USERNAME=rpsapp nếu theo ví dụ. Tạo mật khẩu bằng `openssl rand -hex 32` nếu cần.

Lấy group ID Docker:

```bash
sudo stat -c '%g' /var/run/docker.sock
```

Điền số này vào DOCKER_GID; đặt MAX_CONCURRENT_MATCHES=1 để bắt đầu demo. Giữ subnet mặc định 172.30.90.0/24 hoặc đổi cả RPS_SUBNET và TRUSTED_PROXY_REGEX cho khớp.

Không đặt DB_URL=localhost: Compose tự kết nối server tới hostname `postgres` trong mạng Docker. PostgreSQL tự tạo role ứng dụng khi volume mới; Flyway tạo/nâng bảng lúc khởi động. Thay env không tự thay password của database đã có dữ liệu.

```bash
sudo docker pull python:3.12-slim
sudo docker pull gcc:14
sudo docker pull eclipse-temurin:21-jdk

sudo docker compose --env-file .env.production -f infra/docker-compose.yml -f infra/docker-compose.nginx.yml -f infra/docker-compose.bots.yml config --quiet

sudo docker compose --env-file .env.production -f infra/docker-compose.yml -f infra/docker-compose.nginx.yml -f infra/docker-compose.bots.yml up -d --build --wait --wait-timeout 180 postgres rps-server

curl --fail http://127.0.0.1:8080/actuator/health
```

Kỳ vọng `{"status":"UP"}`. Overlay Nginx publish backend chỉ trên loopback, không chạy Caddy. Không chạy lệnh Compose Caddy cũ cùng lúc vì tranh cổng 80/443. Nếu Caddy cũ còn chạy, dừng đúng reverse-proxy của deployment đó trước khi dùng Nginx.

Java 21 đã cài trên VPS có thể giữ lại, nhưng không cần chạy thêm JAR: image Docker đã có Java, server và client.

## 4. Cài cấu hình Nginx

Bộ mẫu proxy cả giao diện, API và WebSocket. Nginx Ubuntu cần include `sites-enabled/*` trong khối http. Nếu đã có site tên rps-battle, sao lưu file đó trước khi thay thế; không xóa site khác.

```bash
sudo install -m 0644 infra/nginx/rps-battle-domain.conf /etc/nginx/sites-available/rps-battle
sudo sed -i "s/__DOMAIN__/$RPS_DOMAIN/g" /etc/nginx/sites-available/rps-battle
sudo ln -s /etc/nginx/sites-available/rps-battle /etc/nginx/sites-enabled/rps-battle
sudo nginx -t
sudo systemctl reload nginx

curl --fail "http://$RPS_DOMAIN/actuator/health"
```

Lệnh ln chỉ cần ở lần đầu; nếu link đã tồn tại và trỏ đúng file, bỏ qua. Nếu nginx -t lỗi, sửa trước khi reload. Kiểm tra từ máy bên ngoài VPS để xác nhận DNS/firewall.

Chưa đăng nhập hoặc gửi mật khẩu qua HTTP ở bước này. Profile prod giữ cookie Secure; hoàn thành HTTPS ở bước tiếp theo rồi dùng game.

Nếu Nginx đang quản lý website khác, dùng hostname/subdomain riêng và kiểm tra không có server_name trùng.

## 5. Bật HTTPS

Theo [hướng dẫn Certbot cho Nginx](https://certbot.eff.org/instructions?ws=nginx&os=snap), có thể dùng bản Snap:

```bash
sudo apt update
sudo apt install -y snapd
sudo snap install --classic certbot
sudo /snap/bin/certbot --version

sudo /snap/bin/certbot --nginx -d "$RPS_DOMAIN" --redirect
```

Nếu đã cài Certbot bằng Snap, dùng `sudo snap refresh certbot` thay lệnh install. Gọi đường dẫn /snap/bin để tránh nhầm Certbot bản apt cũ. Certbot sẽ hỏi email và điều khoản nếu chưa cấu hình.

Certbot đọc site Nginx, cấp chứng chỉ cho domain và thêm HTTPS/redirect. Chỉ thêm hostname www bằng -d khi bạn thực sự tạo DNS và server_name cho hostname đó.

```bash
sudo nginx -t
curl --fail "https://$RPS_DOMAIN/actuator/health"
sudo /snap/bin/certbot renew --dry-run
sudo snap services certbot
```

Kiểm tra dịch vụ renew được bật. Mở `https://<domain>/` để đăng nhập/chơi. Không tắt Secure cookie, CSRF hoặc kiểm tra origin để chữa lỗi deployment.

## 6. Kiểm tra và dùng thử

- Hai tài khoản USER ở hai trình duyệt tạo/join phòng và chơi.
- DevTools → Network → WS: wss://<domain>/ws/game upgrade thành công, thường 101.
- Bot upload/build/test và giải nhỏ chạy đến kết quả; mở history/replay.
- Tạo admin theo [mục 8 hướng dẫn Ubuntu](DEPLOY-VPS-UBUNTU.md), dùng tài khoản USER riêng để chơi/tổ chức giải.
- Nếu thêm CDN sau này, kiểm tra lại IP client và forwarded headers; không dùng regex tin mọi proxy.

Server trung tâm chỉ một replica; source/coding/version bot và replay lưu trong volume bên cạnh PostgreSQL. Remote worker là tùy chọn, không thay Docker local cho build/final validation.

## 7. Log, backup và cập nhật

```bash
sudo docker compose --env-file .env.production -f infra/docker-compose.yml -f infra/docker-compose.nginx.yml -f infra/docker-compose.bots.yml logs --tail 100 rps-server postgres
sudo tail -n 80 /var/log/nginx/error.log
```

Backup nhất quán bằng công cụ hiện có, có thể dừng server ngắn:

```bash
sudo python3 tools/ops.py --env-file .env.production --bots --compose-file infra/docker-compose.nginx.yml backup --output backups/before-upgrade
sudo python3 tools/ops.py verify --source backups/before-upgrade
```

Output phải là thư mục mới; bảo vệ và sao chép backup sang máy khác. Không dùng down -v hoặc volume prune.

Sau backup, thông báo downtime, dừng server rồi cập nhật code:

```bash
sudo docker compose --env-file .env.production -f infra/docker-compose.yml -f infra/docker-compose.nginx.yml -f infra/docker-compose.bots.yml stop --timeout 60 rps-server
git pull --ff-only
sudo docker compose --env-file .env.production -f infra/docker-compose.yml -f infra/docker-compose.nginx.yml -f infra/docker-compose.bots.yml up -d --build --wait --wait-timeout 180 postgres rps-server
```

Nếu pull/build lỗi, dừng để xử lý; không xóa dữ liệu. Dùng cùng ba file Compose khi vận hành. Không cài lại template HTTP lên site sau khi Certbot đã sửa HTTPS, trừ khi chủ động cấu hình lại TLS.

Đăng nhập lại sau restart; bàn cờ RAM không giữ nguyên. Giải đang chạy có thể cần chủ giải/admin xử lý RECOVERY_REQUIRED trước khi tiếp tục. Backup app không bao gồm Nginx, private key/chứng chỉ hoặc env trên host; quản lý chúng riêng.

Hướng dẫn này chuẩn bị cấu hình; chỉ sau khi kiểm tra trên VPS mới xác nhận deployment thực tế.
