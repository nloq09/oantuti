# VPS Ubuntu đã có Nginx, chỉ có IP public

Dùng hướng dẫn này thay cho phần Caddy trong [hướng dẫn Ubuntu](DEPLOY-VPS-UBUNTU.md). Mô hình: **Nginx trên host → server Docker ở 127.0.0.1:8080 → PostgreSQL Docker**. Bot chạy trong Docker sandbox. Java 21 trên host không cần dùng thêm vì image server đã có Java.

Hướng dẫn dành cho IPv4 public cố định, Nginx Ubuntu có include `sites-enabled/*` trong khối `http`, cổng 80/443 tới được VPS. Không cần mua domain. Let’s Encrypt hỗ trợ chứng chỉ IP thời hạn 160 giờ; phải bật tự gia hạn để chạy quá khoảng sáu ngày. [Nguồn Let’s Encrypt](https://letsencrypt.org/2026/01/15/6day-and-ip-general-availability)

Các lệnh Bash bên dưới chạy **trên VPS**, tại thư mục gốc project. Đây là cấu hình mẫu; chưa thực hiện trên VPS của bạn.

## 1. Đưa code và cấu hình lên VPS

Nếu chưa có repo:

```bash
cd ~
git clone https://github.com/nloq09/oantuti.git rps-battle
cd rps-battle
```

Bản trên VPS phải có các file:

```text
infra/docker-compose.nginx.yml
infra/nginx/rps-battle-ip-http.conf
infra/nginx/rps-battle-ip-https.conf
infra/nginx/reload-on-renew.sh
```

Đây là các file mới chuẩn bị trong workspace. Nếu chưa commit/push, clone GitHub sẽ chưa có chúng: sao chép các file sang VPS hoặc đưa phiên bản đã kiểm tra lên Git trước. Không chép `.env`, database local hoặc worker credential lên Git.

Đặt IP dùng cho các bước tiếp theo:

```bash
read -rp 'IPv4 public của VPS, không kèm port: ' RPS_PUBLIC_IP
```

Nếu mở phiên SSH khác, nhập lại biến này. Không dùng IP LAN/localhost.

Trong firewall nhà cung cấp, mở TCP 80/443 và giữ cổng SSH thực tế. Nếu UFW đang bật:

```bash
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
sudo ufw status
```

Không mở 8080/5432 ra Internet. Kiểm tra 8080 chưa bị ứng dụng khác chiếm bằng `sudo ss -ltnp`; nếu có, xử lý đúng dịch vụ đó trước, không kill tiến trình hàng loạt.

## 2. Cấu hình PostgreSQL và server

Chỉ copy file mẫu khi chưa có cấu hình production:

```bash
cp -n infra/.env.production.example .env.production
chmod 600 .env.production
nano .env.production
```

Điền:

- `POSTGRES_PASSWORD`: mật khẩu quản trị database.
- `DB_PASSWORD`: mật khẩu ứng dụng khác mật khẩu trên.
- `DOCKER_GID`: số trả về từ `sudo stat -c '%g' /var/run/docker.sock`.
- `MAX_CONCURRENT_MATCHES=1`: khởi đầu một trận bot đồng thời cho demo.
- Giữ `COMPOSE_PROJECT_NAME=rpsbattle`, `POSTGRES_DB=rpsbattle`, `POSTGRES_USER=rpsuser`, `DB_USERNAME=rpsapp` nếu theo nguyên hướng dẫn.
- `RPS_DOMAIN`: có thể điền IP public; Caddy không chạy trong chế độ Nginx.
- `ACME_EMAIL`: email của bạn. Hai biến RPS_DOMAIN/ACME_EMAIL vẫn cần có giá trị để base Compose đọc được, nhưng không cấu hình Nginx/Certbot từ các biến này.

Init database chỉ tạo role/password trên volume mới. Nếu có dữ liệu, thay env không tự đổi password bên PostgreSQL. Không dùng xóa volume để sửa lỗi mật khẩu.

Tải image bot:

```bash
sudo docker pull python:3.12-slim
sudo docker pull gcc:14
sudo docker pull eclipse-temurin:21-jdk
```

Kiểm tra cấu hình rồi chạy:

```bash
sudo docker compose --env-file .env.production -f infra/docker-compose.yml -f infra/docker-compose.nginx.yml -f infra/docker-compose.bots.yml config --quiet

sudo docker compose --env-file .env.production -f infra/docker-compose.yml -f infra/docker-compose.nginx.yml -f infra/docker-compose.bots.yml up -d --build --wait --wait-timeout 180 postgres rps-server
```

File nginx overlay chỉ publish backend ở loopback, đưa Caddy vào profile không bật. Không chạy thêm bộ Compose Caddy cũ vì sẽ tranh cổng 80/443 với Nginx. Nếu Caddy từ lần triển khai cũ vẫn chạy, phải dừng đúng container reverse-proxy của deployment đó.

Kiểm tra:

```bash
curl --fail http://127.0.0.1:8080/actuator/health
sudo docker compose --env-file .env.production -f infra/docker-compose.yml -f infra/docker-compose.nginx.yml -f infra/docker-compose.bots.yml ps
```

Kỳ vọng health là `{"status":"UP"}`. Flyway tự tạo/nâng bảng; chưa cần truy cập pgAdmin để tạo bảng.

## 3. Mở đường xác minh chứng chỉ trên Nginx

Trước tiên cài cấu hình HTTP chỉ phục vụ ACME, chưa mở game đăng nhập qua HTTP:

```bash
sudo mkdir -p /var/www/certbot/.well-known/acme-challenge
sudo install -m 0644 infra/nginx/rps-battle-ip-http.conf /etc/nginx/sites-available/rps-battle
sudo sed -i "s/__PUBLIC_IP__/$RPS_PUBLIC_IP/g" /etc/nginx/sites-available/rps-battle
sudo ln -s /etc/nginx/sites-available/rps-battle /etc/nginx/sites-enabled/rps-battle
sudo nginx -t
sudo systemctl reload nginx
```

Lệnh `ln -s` chỉ cần ở lần đầu; nếu link rps-battle đã tồn tại và trỏ đúng file thì bỏ qua. Không xóa cấu hình website khác. Nếu `nginx -t` lỗi, dừng để sửa trước khi reload.

Thử đường dẫn xác minh:

```bash
printf 'rps-acme-ok\n' | sudo tee /var/www/certbot/.well-known/acme-challenge/rps-check > /dev/null
curl --fail "http://$RPS_PUBLIC_IP/.well-known/acme-challenge/rps-check"
```

Cần trả `rps-acme-ok`; kiểm tra thêm từ một máy bên ngoài VPS để xác nhận firewall/NAT không chặn cổng 80. File kiểm tra không chứa secret.

## 4. Lấy HTTPS cho IP bằng Certbot

IP cần Certbot **5.4 trở lên** để dùng webroot. Dùng bản Snap và gọi đường dẫn đầy đủ để tránh gọi nhầm Certbot cũ từ apt:

```bash
sudo apt update
sudo apt install -y snapd
sudo snap install --classic certbot
sudo /snap/bin/certbot --version
```

Nếu Snap đã có Certbot, dùng `sudo snap refresh certbot` thay lệnh install. Không tiếp tục nếu version thấp hơn 5.4.

Xin chứng chỉ thật:

```bash
sudo /snap/bin/certbot certonly --webroot --webroot-path /var/www/certbot --ip-address "$RPS_PUBLIC_IP" --preferred-profile shortlived --cert-name rps-battle-ip
```

Certbot sẽ hỏi email/điều khoản nếu chưa cấu hình. Không thêm `--staging` vào lệnh trên: chứng chỉ staging không được trình duyệt tin cậy. Không dùng `certbot --nginx -d IP` theo các bài cũ; luồng ở đây chủ động lấy chứng chỉ rồi cài vào Nginx.

Tên chứng chỉ cố định là rps-battle-ip; sau khi thành công, file nằm ở:

```text
/etc/letsencrypt/live/rps-battle-ip/fullchain.pem
/etc/letsencrypt/live/rps-battle-ip/privkey.pem
```

Nguồn: [IP certificate với Certbot và webroot](https://letsencrypt.org/2026/03/11/shorter-certs-certbot.html), [cài Certbot bằng Snap](https://certbot.eff.org/instructions?ws=other&os=snap).

## 5. Bật game qua HTTPS và WebSocket

Sau khi có chứng chỉ, thay bootstrap bằng cấu hình HTTPS:

```bash
sudo install -m 0644 infra/nginx/rps-battle-ip-https.conf /etc/nginx/sites-available/rps-battle
sudo sed -i "s/__PUBLIC_IP__/$RPS_PUBLIC_IP/g" /etc/nginx/sites-available/rps-battle
sudo nginx -t
sudo systemctl reload nginx
curl --fail "https://$RPS_PUBLIC_IP/actuator/health"
```

Truy cập `https://IP_PUBLIC_CUA_VPS/` từ máy của bạn, không phải `http://IP:8080`.

Cấu hình proxy toàn bộ client/API/WebSocket cùng origin, chuyển tiếp Upgrade và ghi đè forwarded headers. Với subnet Docker mặc định 172.30.90.0/24, server tin gateway của bridge; nếu đổi subnet phải đổi TRUSTED_PROXY_REGEX tương ứng. Không dùng regex tin mọi IP.

Giữ cookie Secure và CSRF của profile prod. Nginx giới hạn body 6 MiB và tăng timeout cho kết nối WebSocket. Nguồn: [Nginx WebSocket proxying](https://nginx.org/en/docs/http/websocket.html).

## 6. Tự gia hạn để dùng đủ một tuần và lâu hơn

Cài hook để Nginx nạp lại chứng chỉ mới sau mỗi lần gia hạn:

```bash
sudo install -d -m 0755 /etc/letsencrypt/renewal-hooks/deploy
sudo install -m 0755 infra/nginx/reload-on-renew.sh /etc/letsencrypt/renewal-hooks/deploy/rps-nginx-reload
sudo snap services certbot
sudo /snap/bin/certbot renew --cert-name rps-battle-ip --dry-run --run-deploy-hooks
```

Kiểm tra dịch vụ renew của Snap được bật; nếu tắt thì bật lại theo quản lý service của Snap. Giữ cổng 80 và đường dẫn ACME trong cấu hình HTTPS để gia hạn webroot hoạt động. `--dry-run` kiểm tra renewal, không cài chứng chỉ thử vào Nginx.

Nếu dry-run lỗi, xử lý trước khi dựa vào deployment cho buổi demo. Chứng chỉ khoảng sáu ngày sẽ không tự đủ một tuần nếu không gia hạn.

## 7. Kiểm tra chức năng và vận hành

- Dùng hai USER khác nhau tạo/join phòng và chơi thử.
- Kiểm tra login, bot build/test, giải nhỏ, history và replay.
- Trong DevTools → Network → WS, kiểm tra `wss://IP/ws/game` upgrade 101.
- Tạo admin theo mục 8 của [hướng dẫn Ubuntu](DEPLOY-VPS-UBUNTU.md).
- Cập nhật bằng cùng bộ **ba file Compose**, không bỏ nginx overlay.
- Server vẫn một replica; bot Docker cần quyền socket trên Engine dành cho ứng dụng.

Backup/restore với bộ Compose Nginx:

```bash
sudo python3 tools/ops.py --env-file .env.production --bots --compose-file infra/docker-compose.nginx.yml backup --output backups/before-upgrade
sudo python3 tools/ops.py verify --source backups/before-upgrade
```

Tài liệu [vận hành](OPERATIONS.md) mô tả cách restore; khi dùng Nginx, thêm cùng `--compose-file infra/docker-compose.nginx.yml` để công cụ nhận đúng cấu hình. Backup app không bao gồm chứng chỉ/khóa Nginx trên host; quản lý riêng và bảo vệ private key.

Không chạy `down -v` hoặc volume prune. PostgreSQL/bot/replay nằm trong named volume, không phải trong thư mục clone source. Nếu restart khi giải còn chạy, chủ giải/admin cần xử lý recovery; session và bàn cờ RAM không được giữ nguyên.

## 8. Nếu chưa vào được

- **502:** kiểm tra health tại 127.0.0.1:8080, trạng thái rps-server và log Nginx.
- **Cổng 80/443 bị chiếm:** kiểm tra `sudo ss -ltnp`; Caddy và Nginx không cùng bind một port/IP.
- **Chứng chỉ không cấp được:** kiểm tra IP public đúng, HTTP ACME từ Internet, version Certbot và firewall cloud.
- **Login bị mất / WS 403:** dùng HTTPS đúng IP, kiểm tra forwarded proto/host/port và subnet proxy trust.
- **Bot sandbox lỗi:** kiểm tra DOCKER_GID, Docker Linux, image và file override bots.
- **Sau sáu ngày báo hết hạn:** kiểm tra snap renew service, log /var/log/letsencrypt/letsencrypt.log, hook reload và đường ACME. Không bỏ qua cảnh báo trình duyệt để tiếp tục dùng.
