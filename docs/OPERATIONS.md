# Vận hành server

Đứng ở thư mục gốc repo, dùng `.env.production` đã chuẩn bị theo [DEPLOYMENT.md](DEPLOYMENT.md). Các ví dụ dưới bật bot upload; bỏ `-f infra/docker-compose.bots.yml`/`--bots` nếu không dùng Docker runner.

## Start, stop, health và log

```bash
docker compose --env-file .env.production -f infra/docker-compose.yml -f infra/docker-compose.bots.yml up -d --wait --wait-timeout 180
docker compose --env-file .env.production -f infra/docker-compose.yml -f infra/docker-compose.bots.yml ps
docker compose --env-file .env.production -f infra/docker-compose.yml -f infra/docker-compose.bots.yml logs --tail 100 rps-server
docker compose --env-file .env.production -f infra/docker-compose.yml -f infra/docker-compose.bots.yml logs --tail 100 postgres reverse-proxy
```

Mở `https://<domain>/actuator/health`: UP là app/DB sẵn sàng. `/api/health` cũ chỉ kiểm tra app. Metrics/system cần session ADMIN; không publish env/heapdump hay port DB/server. Theo dõi worker_pending_matches, worker_active_matches, login_failure_total, bot_timeout_total và log shutdown_persistence_failed.

Dừng bình thường:

```bash
docker compose --env-file .env.production -f infra/docker-compose.yml -f infra/docker-compose.bots.yml stop --timeout 60 rps-server
```

Docker gửi SIGTERM, Java PID 1 thực hiện graceful checkpoint trước khi đóng DB pool. Trận đang dở không được resume; thông báo downtime trước khi stop/backup/upgrade. Không kill -9 khi có thể stop bình thường. `docker compose down` không xóa named volume mặc định, nhưng `down -v` sẽ xóa dữ liệu; không dùng để nâng cấp hoặc sửa lỗi kết nối.

## Backup đầy đủ

```bash
python tools/ops.py --env-file .env.production --bots backup --output backups/2026-10-14
python tools/ops.py verify --source backups/2026-10-14
```

Output mới gồm database.dump (pg_dump custom format), artifacts.tar (bot source/metadata + replay files), manifest.json (version/time/project/bytes/SHA-256). Thư mục output phải chưa tồn tại. Script tạm dừng server và khởi động lại nếu trước đó đang chạy; PostgreSQL tiếp tục hoạt động, proxy có thể trả lỗi tạm thời. Nếu backup lỗi, thư mục có thể chưa đầy đủ; không restore bộ thiếu manifest hoặc verify lỗi.

Backup chứa password hash tài khoản và capability/metadata nội bộ, không đưa lên Git/GitHub public. Giữ .env/backup ở quyền hạn chế, copy backup sang thiết bị/máy khác và kiểm tra restore định kỳ. Cần snapshot DB và artifacts cùng một mốc; pg_dump riêng không đủ để chạy lại bot hoặc mở replay. Script không tự đặt lịch hay tự gửi backup ra dịch vụ ngoài.

## Restore và thử khôi phục

Tạo `.env.restore` cho **project mới**, đổi COMPOSE_PROJECT_NAME và, nếu cùng host, RPS_SUBNET/TRUSTED_PROXY_REGEX cùng HTTP_PORT/HTTPS_PORT. Domain/tài khoản DB có thể giữ theo bộ backup nhưng không được trùng tên volume đang dùng. Chưa start server đích:

```bash
python tools/ops.py --env-file .env.restore --bots restore --source backups/2026-10-14
docker compose --env-file .env.restore -f infra/docker-compose.yml -f infra/docker-compose.bots.yml up -d --wait --wait-timeout 180
```

Script validate hash/archive trước; yêu cầu server đích dừng, DB public không có table và bot/replay volume trống. pg_restore dùng transaction, --no-owner/--no-acl và --role DB_USERNAME để app tiếp tục ghi dữ liệu bằng role không superuser. Không restore backup SQL không tin cậy. SHA-256 là kiểm tra tính toàn vẹn, không phải chữ ký người tạo.

Sau restore: health UP → login lại → bot list/source thực thi → history/stats → giải/ranking → replay UUID. Dùng đúng phiên bản code/migration tương ứng backup, rồi nâng cấp có kiểm thử. Session/phòng RAM cũ không có trong backup. Chỉ chuyển DNS/traffic khi kiểm tra xong; không ghi đè deployment đang phục vụ.

Nếu target đã có dữ liệu, script từ chối. Không có nút force hay xóa tự động. Tạo target trống khác để thử lại khi cần; giữ target lỗi để điều tra. Dữ liệu bot cũ của server native không tự chuyển sang named volume: import/copy **cả** metadata/source và dữ liệu DB từ một backup nhất quán, không tải riêng các file .py lên rồi giả định giữ cùng botId.

## Inspect và cleanup bot Docker

```bash
docker ps -a --filter label=rps.managed=true --filter label=rps.scope=rpsbattle
```

Bot sandbox có name rps-bot-UUID, labels scope/bootId/botId/matchId; không log token/source/path ra public DTO. Khi inspect, đừng chia sẻ toàn bộ docker inspect/env hoặc metadata.json vì có dữ liệu nội bộ. Không `docker container prune` để dọn bot, vì có thể tác động container khác.

Chỉ cleanup khi server của project đã dừng:

```bash
python tools/ops.py --env-file .env.production --bots cleanup
python tools/ops.py --env-file .env.production --bots cleanup --apply
```

Mặc định dry run, chỉ chọn bot đã dừng thuộc đúng scope và đủ label UUID/name hợp lệ. Sau crash có bot vẫn chạy, cần chỉ rõ:

```bash
python tools/ops.py --env-file .env.production --bots cleanup --include-running
python tools/ops.py --env-file .env.production --bots cleanup --include-running --apply
```

Script kiểm tra server Compose không chạy, inspect lại labels ngay trước xóa và xóa bằng container ID. Không xóa DB/server/proxy, bot scope khác, container thiếu label hay volume/file. Không chạy server native/một replica khác với cùng scope trong lúc cleanup. Bot cũ chưa có labels phải kiểm tra/dọn thủ công theo ID; không tự nhận chúng là bot của deployment mới.

## Giải đang dở và account bị khóa

Crash/restart làm giải RUNNING/READY chuyển RECOVERY_REQUIRED; trận chưa hoàn thành không nhận winner giả. Chủ giải hoặc ADMIN đăng nhập, mở trang giải và **hủy**, hoặc POST `/api/tournaments/{id}/cancel` với session + CSRF. Không tự restart giải cũ; tạo giải mới nếu muốn thi đấu lại. Trận đã hoàn tất/ranking/replay giữ trong DB/source archive.

ADMIN dùng POST `/api/admin/users/{id}/disable` hoặc `/enable` với session/CSRF. Disable/đổi password thu hồi phiên cũ khi server kiểm tra lại; không hard-delete lịch sử. Server từ chối disable chính admin đang thao tác. Muốn bootstrap admin, xem [AUTH.md](AUTH.md); không tạo admin mặc định hoặc cập nhật role từ browser.

## Lỗi thường gặp

- DB startup/auth lỗi: kiểm tra role/password thực trong DB; sửa .env không đổi password của volume cũ. Không dùng down -v để reset DB đang có dữ liệu.
- BOT_SANDBOX_UNAVAILABLE: kiểm tra override bots, quyền DOCKER_GID, Docker Engine Linux, ba image đã pull và khả năng docker info từ server.
- BOT_FAILED: bot timeout/crash/JSON/action sai cũng có cùng lỗi; kiểm tra bot ở server thử theo BOT-PROTOCOL.md. Chỉ trả action từ legalActions, không dựa vào action có trên UI của người chơi.
- Restore bot không chạy: xác minh bot-data đúng volume và source file tồn tại, UID 10001 sở hữu thư mục; chỉ một source file read-only được mount vào sandbox.
- WSS 403: cùng origin HTTPS/Host/port và forwarding port. Không mở wildcard origins để bỏ kiểm tra.
- RATE_LIMITED cho nhiều người: NAT dùng chung IP hoặc proxy trust regex chưa đúng; không tăng quota tùy tiện để che lỗi proxy.
- REPLAY_FILE_MISSING/INVALID: restore đúng artifacts cùng dump, không sửa DB valid=true để bỏ digest/validator.
- Hết dung lượng: kiểm tra Docker disk và backup retention theo chính sách; không tự xóa artifact đã được soft-delete nhưng còn dùng trong lịch sử.

## Giới hạn

Một instance/host, queue và counters ở RAM; chưa có autoscaling, distributed rate limiter, resume engine state, job retry qua crash, quota dung lượng theo account hoặc sandbox worker máy khác. Socket runner là quyền host nhạy cảm đã opt-in; cần môi trường host phù hợp. HTTPS public/DNS/firewall và backup ngoài máy cần người triển khai cấu hình theo môi trường thực.
