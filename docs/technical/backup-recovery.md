# Backup, restore và khôi phục sự cố

[Mục lục](../README.md) · [Deployment](deployment.md) · [Database](database.md) · [Worker](../admin/workers.md)

Backup đầy đủ cần **PostgreSQL và file bot/source/version/replay ở cùng mốc**. Session/phòng RAM, worker-state trên máy khác và TLS/env trên host không nằm trong bộ backup ứng dụng. Công cụ repo dùng Python 3.11+ stdlib và Docker Compose, không cần pip.

## Backup nhất quán

Chạy tại root repo. Chọn thư mục output **chưa tồn tại**; ví dụ Nginx host, bot bật:

```bash
sudo python3 tools/ops.py --env-file .env.production --bots --compose-file infra/docker-compose.nginx.yml backup --output backups/before-upgrade
sudo python3 tools/ops.py verify --source backups/before-upgrade
```

Với Caddy bỏ `--compose-file infra/docker-compose.nginx.yml`. Backup tạm dừng server để checkpoint, PostgreSQL vẫn chạy, rồi khởi động lại server nếu trước đó chạy; thông báo gián đoạn trước khi thực hiện. Trận RAM đang dở không được backup để resume giữa lượt.

Output gồm `database.dump` custom format, `artifacts.tar`, `manifest.json` ghi version/time/project/size/SHA-256. Nếu lỗi, output có thể thiếu; chỉ dùng bộ verify đạt. Công cụ không tự đặt lịch, mã hóa hoặc gửi backup ra máy khác. Bảo vệ quyền đọc vì dump có password hash/metadata riêng; lưu bản sao ngoài VPS và quản lý retention riêng.

Không dùng pg_dump riêng rồi cho rằng đã giữ source/replay. Không backup nóng từng volume ở các thời điểm khác nhau. Không đưa dump, env hoặc worker credential vào Git.

## Restore vào deployment trống

Chuẩn bị `.env.restore`: COMPOSE_PROJECT_NAME mới; nếu cùng host phải có subnet/proxy trust/ports riêng. Không chạy server đích trước khi restore. Với Nginx overlay, backend đích vẫn cố định 127.0.0.1:8080 trong file mẫu; dùng host riêng hoặc overlay đích do bạn cấu hình cổng riêng, không cho hai deployment tranh cổng.

```bash
sudo python3 tools/ops.py --env-file .env.restore --bots --compose-file infra/docker-compose.nginx.yml restore --source backups/before-upgrade
sudo docker compose --env-file .env.restore -f infra/docker-compose.yml -f infra/docker-compose.nginx.yml -f infra/docker-compose.bots.yml up -d --wait --wait-timeout 180 postgres rps-server
```

Chỉ chạy các lệnh trên sau khi đã giải quyết cổng/host đích như mô tả. Công cụ yêu cầu server dừng, schema public chưa có table, bot/replay volume trống. Archive được kiểm traversal/symlink/device, dump restore trong transaction bằng app role. Không có force ghi đè dữ liệu đích. Nếu lỗi, giữ target để điều tra và thử target trống khác.

SHA-256 phát hiện file thay đổi, không chứng minh dump SQL do người tin cậy tạo. Chỉ restore backup mình quản lý. Env và database password phải tương ứng; init PostgreSQL chỉ chạy khi volume mới.

Sau restore kiểm tra health → login lại → bot/artifact → workspace/bài khóa → history → giải/ranking → replay UUID. Dùng code đúng phiên bản backup trước, rồi nâng migration có kiểm thử. Chỉ chuyển traffic sau khi kiểm tra; không dùng restore drill lên deployment đang phục vụ.

## Central restart và giải đang dở

Graceful stop cho phép persistence coordinator checkpoint trước khi đóng DB pool. Không dùng kill -9 nếu có thể dừng bình thường. Session và phòng online RAM mất; người dùng đăng nhập lại. Metadata/history/replay đã lưu vẫn tồn tại.

Giải đã có thi đấu nhưng chưa xong có thể chuyển RECOVERY_REQUIRED. Không tự gán winner cho trận chưa hoàn tất. Manager kiểm tra trận FAILED/review, artifact và remote receipt/attempt; retry/resume khi service cho phép, hoặc hủy và tạo giải mới. Một số hướng dẫn cũ chỉ nói “hủy” là thiếu: source hiện có review/retry/resume với guard.

## Remote worker mất mạng hoặc restart

Giữ nguyên `worker-state` riêng của máy. Dừng agent trước khi chạy chế độ xử lý một lần, dùng cùng Central và state-dir:

```sh
java -jar rps-worker.jar --central=https://game.example.com --state-dir=./worker-state --upload-pending
java -jar rps-worker.jar --central=https://game.example.com --state-dir=./worker-state --recover
```

Chỉ chạy chế độ cần thiết: `--upload-pending` gửi lại kết quả đã hoàn tất; `--recover` đối chiếu journal, gửi kết quả hợp lệ và abandon execution bị gián đoạn để Central cấp attempt mới. Không chạy lại bot của execution cũ để giả lập kết quả. `--serve` cũng có recovery định kỳ; không chạy hai agent hoặc lệnh recovery đồng thời cùng state.

Central chỉ nhận kết quả đúng assignment/hash/seed/lease, retry completion giống nhau là idempotent. Kết quả khác hoặc attempt cũ bị fence không được ghi đè. Hết retry hạ tầng chuyển lỗi để review, không xử người chơi thua. Credential bị revoke phải đăng ký mới/duyệt lại; mất response đăng ký ban đầu cần Admin cấp token mới, không lặp đăng ký tự động mù.

## Dọn sandbox sau crash

Chỉ dùng khi server project đã dừng và không có replica/native server cùng scope. Mặc định dry run:

```bash
sudo python3 tools/ops.py --env-file .env.production --bots --compose-file infra/docker-compose.nginx.yml cleanup
sudo python3 tools/ops.py --env-file .env.production --bots --compose-file infra/docker-compose.nginx.yml cleanup --apply
```

Công cụ chỉ xét container bot đúng scope/label/name/UUID. Nếu sau crash còn container chạy, xem dry-run với `--include-running` trước, chỉ thêm `--apply` khi đã xác minh. Không dùng container prune/volume prune hay down -v. Cleanup không xóa source/DB/replay và không thay retention.

## Nguồn đối chiếu

- [ops.py](../../tools/ops.py), [ops tests](../../tools/), [persistence coordinator](../../server/src/main/java/com/rpsbattle/tournament/persistence/TournamentPersistenceCoordinator.java).
- [Recovery agent](../../worker/src/main/java/com/rpsbattle/remote/RecoveryAgent.java), [worker entry point](../../worker/src/main/java/com/rpsbattle/remote/WorkerApplication.java).
- [Lease recovery](../../server/src/main/java/com/rpsbattle/worker/remote/RemoteLeaseService.java), [result receipt](../../server/src/main/java/com/rpsbattle/worker/remote/RemoteResultService.java), [điều khiển giải](../../server/src/main/java/com/rpsbattle/tournament/TournamentAdministrationService.java).
