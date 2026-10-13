# Chặng 4 — Nhóm 10: Deployment, Backup và Recovery

## Hoàn thành

- Dockerfile nhiều stage build Java 21/Gradle và client vào JAR; runtime JRE user 10001, read-only filesystem/tmp riêng. Docker CLI có sẵn, không đưa .env/runtime data/backup vào build context.
- Compose PostgreSQL 17 + rps-server + Caddy HTTPS, healthcheck/dependencies/graceful stop; volume DB/bot/replay/cert, không publish DB/server. Mẫu env không chứa secrets. DB app role riêng, NOSUPERUSER/NOCREATEDB/NOCREATEROLE, sở hữu schema ứng dụng; init chỉ trên volume mới.
- Runner bot mặc định tắt trong prod, bật bằng override socket đã opt-in. Giữ sandbox limits; map named volume thành đường dẫn daemon và chỉ mount một file source read-only, không metadata/token/volume bot khác.
- Bot container gắn labels managed/scope/boot/botId/matchId. Match context được bind khi attach BotAgent vào phòng, không sửa GameEngine hay RuleSet.
- Proxy chuyển tiếp IP/protocol/port, loại Forwarded từ client; backend chỉ tin subnet proxy cấu hình. HTTPS same-origin/WSS giữ CSRF/session/origin checks. Body proxy giới hạn 6 MiB, kể cả body stream.
- `tools/ops.py`: backup nhất quán bằng stop/checkpoint → pg_dump custom + tar artifacts/replays + SHA-256 manifest → restart nếu cần. Verify độc lập không cần Docker. Restore chỉ target trống, preflight/hash/archive validation, pg_restore transaction dưới app role. Không có force/clean/xóa target tự động.
- Cleanup mặc định dry run, yêu cầu server Compose đã dừng, chỉ xóa container ID đúng scope/name/labels; running orphan cần --include-running --apply. Không xóa volume/source hoặc container ngoài scope.
- `tools/verify-deployment.mjs`: kịch bản hai account/ownership/match/history/bot/tournament/ranking/replay và kiểm tra lại sau restart/restore, dùng TLS CA hợp lệ.
- Tài liệu DEPLOYMENT.md, OPERATIONS.md, AUTH.md và README cập nhật start/stop/HTTPS/Flyway/env/bot requirements/backup/restore/upgrade/admin/recovery.

## Kiểm chứng

- Toàn bộ backend **409 pass, 0 fail/error, 0 skipped**. Bao gồm Flyway PostgreSQL **7 pass**, Docker Python/C++/Java/JAR **4 pass**, Docker tournament persistence **1 pass**. Không thêm migration, giữ V1–V9.
- Client **21 pass, 0 fail, 0 skipped**.
- Công cụ Python **6 pass**, kiểm tra checksum/corruption/missing/malformed manifest, archive traversal/symlink/device và scope/running cleanup. Lần chạy sandbox đầu không ghi được thư mục Temp Windows; chạy lại với quyền test cần thiết đã PASS, không bỏ test.
- bootJar host **PASS**; Docker image build với Java 21 **PASS**. Compose config --quiet **PASS**; Postgres/server/proxy healthy, Docker CLI trong app thấy Linux Engine, UID app 10001; SQL kiểm tra app role không có superuser/createdb/createrole **PASS**.
- HTTPS local có CA Caddy, Node tin CA qua NODE_EXTRA_CA_CERTS (không tắt TLS verify). Cookie Secure/HttpOnly/SameSite **PASS**. WSS same-origin nhận 101, origin lạ nhận 403 **PASS**, kể cả HTTPS port 18443. Kiểm tra thực phát hiện thiếu forwarding port; đã sửa Caddyfile và kiểm tra lại thành công.
- Kịch bản định nghĩa hoàn thành Chặng 4 **PASS** trên Compose riêng: hai user register/login/logout, upload bot thuộc đúng account; user B không sửa/xóa bot A; hai ghế chơi thật qua WSS; history cả hai, persisted replay; giải có hai bot điều khiển thật, kết thúc không BOT_FAILED và có ranking/champion/replay.
- Fixture bot đầu thử chọn SURRENDER không có trong legalActions thực của bot, bị BOT_FAILED đúng luật validation. Đã sửa fixture chọn MOVE từ legalActions và chạy lại kịch bản thành công; không nới validator hay sửa luật để test qua.
- Backup DB + artifacts/replay rồi restart server **PASS**: login, bot list, history/ranking và exact replay JSON giữ nguyên, bot vẫn thực thi. Restore sang **project/database/volume mới** trên subnet/port riêng **PASS**, kiểm tra lại cùng fixture và bot khôi phục chơi được.
- Restore vào DB không trống bị từ chối **PASS**. Cleanup thật: dry run giữ cả hai fixture container; apply xóa đúng managed scope, container scope khác vẫn còn **PASS**, sau đó chỉ dọn fixture đã tạo.
- HTTP body vượt 6 MiB qua HTTPS trả 413 **PASS**. Native JAR mới localhost:8080: bot có sẵn, WS placement/ready/pass/leave, replay UUID, health/private metrics/PING limit **PASS**.
- Public ACME/DNS/firewall trên domain thật **chưa chạy** vì chưa có môi trường/domain đích. Chứng chỉ local kiểm thử không được mô tả là đã deploy public.
- Không thay layout client, không thêm UI admin tùy chọn. GameEngine/RuleSet/game core **không sửa**; OnlineMatch chỉ bind transport label lúc attach bot.

Test dùng hai project rps-deploy-test/rps-restore-test, .env/fixture/password ngẫu nhiên và backup trong thư mục ignored. DB/server gốc của người dùng không được đổi role hoặc restore đè. Các stack kiểm thử được dừng sau kiểm tra, giữ volume/backup; server native được build/khởi động lại ở 8080.

## File thêm/sửa

- `Dockerfile`, `.dockerignore`, `.gitattributes`, `.gitignore`, `README.md`.
- `infra/docker-compose.yml`, `infra/docker-compose.bots.yml`, `infra/Caddyfile`, `infra/postgres-init.sh`, `infra/.env.production.example`. Compose Postgres dev cũ được giữ.
- `server/src/main/resources/application-prod.yml`.
- `server/src/main/java/com/rpsbattle/bot/upload/DockerArtifactPaths.java`, `BotUploadService.java`, `DockerBotProcess.java`.
- `server/src/main/java/com/rpsbattle/bot/BotProcess.java`, `BotAgent.java`.
- `server/src/main/java/com/rpsbattle/online/OnlineMatch.java`.
- `server/src/test/java/com/rpsbattle/bot/upload/DockerArtifactPathsTest.java`.
- `tools/ops.py`, `tools/tests/test_ops.py`, `tools/verify-deployment.mjs`.
- `docs/DEPLOYMENT.md`, `docs/OPERATIONS.md`, `docs/AUTH.md`, `docs/WORKER-OPERATIONS.md`, báo cáo này.

## Điểm dừng và giới hạn

Hoàn thành implementation Nhóm 10 và kịch bản Chặng 4 trên deployment thử. Đưa public cần host/Docker/domain/DNS phù hợp; không tự tạo account hosting hoặc chọn dịch vụ trả phí. Socket runner là quyền host nhạy cảm đã opt-in; bot không được socket. Chưa có worker máy khác, nhiều replica/durable queue/distributed rate limit, autoscaling, quota storage theo account, resume engine state hoặc backup ngoài máy tự động.

Graceful/backup cần thông báo downtime; force kill hoặc DB lỗi vẫn có thể mất state chưa commit. Restore nhận bộ backup tin cậy và chỉ ghi vào target trống. Không hard-delete bot/source đã có lịch sử, không sửa valid/digest để ép replay đọc. Hướng dẫn chi tiết: [DEPLOYMENT.md](DEPLOYMENT.md), [OPERATIONS.md](OPERATIONS.md), [AUTH.md](AUTH.md).
