# Kiểm tra và vận hành hệ thống

[Mục lục](../README.md) · [Monitoring](../technical/monitoring.md) · [Deployment](../technical/deployment.md) · [Backup/Recovery](../technical/backup-recovery.md)

Admin mở **Tổng quan**, **Hệ thống** (`/admin/system.html`) và **Worker**. Trang hệ thống đọc `/api/admin/system`: ứng dụng, PostgreSQL, Docker, local workers/queue, users/socket, JVM memory, storage và alerts. Một health UP không khẳng định runtime bot, worker group và mọi giải đều khỏe.

## Kiểm tra trước một buổi thi

1. Health DB/app UP; kiểm tra cả readiness, dung lượng và volume bot/replay.
2. Docker Linux và image đúng ngôn ngữ trên Central. Thực hiện một build/public test bằng tài khoản thử trên môi trường thử; không chỉ nhìn Docker daemon UP.
3. Local execution queue và coding queue còn chỗ. Hai queue có giới hạn khác nhau; thêm worker remote không giải quyết queue build/test Central.
4. Với REMOTE GROUP: worker đã duyệt, nhóm bật, trust/runtime phù hợp, capacity và heartbeat còn mới. Giao giải trước khi tạo lịch.
5. Bản backup gần nhất đã verify; biết chỗ lưu và cách restore. Không kiểm tra restore bằng cách ghi vào DB đang phục vụ.

## Đọc cảnh báo

Các cảnh báo có thể gồm DATABASE_UNAVAILABLE, DOCKER_UNAVAILABLE, WORKER_DRAINING, QUEUE_PRESSURE, STORAGE_DEGRADED, TOURNAMENT_RECOVERY, INVALID_REPLAY, BOT_FAILURE_SPIKE, LONG_RUNNING_MATCH và AUDIT_WRITE_PENDING. Tên/mức độ lấy từ backend.

Queue local từ 80% sức chứa được cảnh báo. Trận chạy hơn 15 phút hoặc ít nhất 50% trong tối thiểu năm trận gần đây lỗi bot là tín hiệu cần xem; không phải bằng chứng mọi người chơi gian lận. Audit pending có thể nghĩa DB chưa nhận journal, nên giữ dữ liệu điều tra trước khi restart.

## Dọn giải rác

Backend có cơ chế đánh giá giải không hoạt động, thời gian chờ và đưa vào trash; không xóa trực tiếp lịch sử thi đấu chỉ vì giải đầy sức chứa. Admin đọc `/api/admin/tournament-cleanup`, phục hồi giải được phép bằng action restore có lý do. Chủ giải có action keep với revision để xác nhận giữ giải khi hợp lệ.

Xem policy thực tế trong source trước khi đổi thời hạn. Trash không phải purge vật lý toàn bộ bot/replay; không chạy SQL DELETE theo tên giải. Cơ chế có thể tắt bằng `rps.tournament.cleanup.enabled=false` nếu người vận hành chủ động cấu hình và chấp nhận phần dọn thủ công.

## Dừng và nâng cấp

Thông báo downtime, dùng graceful stop, backup nhất quán rồi cập nhật theo [Deployment](../technical/deployment.md). Không `down -v`, volume prune hoặc kill -9 để “làm mới cache”. Sau restart phải login lại; trận RAM không resume giữa nước. Xử lý RECOVERY_REQUIRED ở từng giải theo [quy trình organizer](../organizer/tournament-operation.md).

Khi storage lỗi, giữ file/hash/metadata và đối chiếu backup. Archive bot hoặc trash giải không cấp quyền xóa tùy ý artifact đang được bài khóa/lịch sử tham chiếu. Log cần request ID và mã lỗi an toàn; không chia sẻ full env hoặc docker inspect.

Chưa có nút backup/restore, nâng cấp binary, điều chỉnh role, prune Docker hay tự sửa DB trong UI. Những việc này do người vận hành host thực hiện với công cụ repo và quyền riêng.

## Nguồn đối chiếu

- [Hệ thống và alerts](../../server/src/main/java/com/rpsbattle/admin/AdminOperationsService.java), [API](../../server/src/main/java/com/rpsbattle/api/controller/AdminController.java), [metrics](../../server/src/main/java/com/rpsbattle/operations/RuntimeMetrics.java).
- [Cleanup API](../../server/src/main/java/com/rpsbattle/api/controller/TournamentCleanupController.java), [service/policy](../../server/src/main/java/com/rpsbattle/tournament/cleanup/).
- [Công cụ vận hành](../../tools/ops.py), [Compose](../../infra/docker-compose.yml).
