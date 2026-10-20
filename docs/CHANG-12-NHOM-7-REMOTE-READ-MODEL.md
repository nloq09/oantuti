# Chặng 12 — Nhóm 7: Remote Execution Read Model

## Phạm vi

Bổ sung dữ liệu cho Match Monitor ở nhóm 8; chưa thay giao diện trận hoặc My Match trong nhóm này. API mới chỉ dành cho chủ giải/admin. Không thay GameEngine/RuleSet, kết quả, chính sách ghép cặp hoặc slot admission.

`GET /api/tournaments/{id}/remote-executions?page=0&size=25&status=QUEUED`

Response no-store/Vary Cookie. Guest 401, tài khoản khác 403; bộ lọc không hợp lệ hoặc paging ngoài phạm vi 400. size từ 1–100, page 0–10000. Không filter thì lấy mọi trạng thái remote của giải. Giải chạy local/chưa có job trả danh sách rỗng, không giả remote RUNNING.

DTO Page: tournamentId, revision, serverNow, page, size, total, counts theo execution state, items và ghi chú queuePosition. total áp dụng filter, counts là tổng trạng thái của giải trước filter.

Item: matchId, jobId, executionId, matchStatus, executionState, workerDisplayName, workerStatus, attempt, queuePosition, queuedAt, claimedAt, startingAt, startedAt, lastHeartbeatAt, leaseUntil, leaseState, resultReceivedAt, projectionStatus, failureCategory.

Truy vấn chỉ lấy scalar columns cần thiết, không đọc spec_json/result_json/replay_json hoặc source. Không có credential, registration token, địa chỉ máy, container/sandbox internals, artifact download token hoặc job của giải khác. Worker display name chỉ là tên hiển thị; UI tương lai phải render dạng text. Chỉ trả attempt đang active, hoặc attempt mới nhất nếu không còn active; không gộp nhiều attempt thành nhiều trận.

## Trạng thái

- QUEUED: job đang chờ và chưa có attempt nào.
- ASSIGNED: job LEASED, attempt CLAIMED, lease còn hiệu lực.
- STARTING: Central đã chấp nhận /start, chưa nhận gameplay signal. Worker đang chuẩn bị bot/sandbox hoặc chưa báo mốc gameplay.
- RUNNING_REMOTE: Central đã nhận gameplay signal sau chuẩn bị trận, lease còn hiệu lực. Không chỉ suy ra từ trạng thái trung tâm PENDING/RUNNING.
- RESULT_PENDING: đã có receipt kết quả của attempt hiện tại, giải chưa FINISHED trận tương ứng. Receipt APPLIED mà trạng thái trận chưa phản ánh cũng giữ pending, không dựng kết quả.
- FINISHED: trạng thái trận đã FINISHED; receipt chờ không đè kết quả cuối.
- RETRYING: job QUEUED trở lại sau ít nhất một attempt (decline, recovery hoặc retry theo workflow có sẵn).
- RECOVERING: job LEASED nhưng lease đã hết; chờ reaper/recovery, không tự gán winner hoặc giải phóng slot bằng endpoint đọc.
- ERROR: trận FAILED, recovery terminal hoặc receipt bị bỏ qua/lỗi projection.
- CANCELLED: giải hủy hoặc job hủy khi chưa có kết quả trận cuối.
- UNKNOWN: dữ liệu chưa đủ để xác định, ví dụ job DONE không có receipt. Không ép thành FINISHED.

Tình trạng trận và execution được trả riêng; nhiều trận remote vẫn PENDING ở Central trong lúc thực thi trên worker. FAILURE categories chỉ là các mã đã chuẩn hóa MATCH_ERROR/RESULT_PROJECTION/RECOVERY_EXHAUSTED/LEASE_EXPIRED/INFRASTRUCTURE_RECOVERY/PRE_START_REASSIGNMENT, không trả exception/raw message.

attempt là số lần nhận việc trong lịch sử job, không phải số ván BO hoặc số lần admin retry đã reset. queuePosition là thứ tự tạo các job đang chờ trong chính giải (gồm retry), không phải vị trí hàng đợi global, thứ tự claim có cache/routing hay ETA. lastHeartbeat quá 30 giây làm workerStatus=OFFLINE; execution chưa tự chuyển sang lỗi nếu lease vẫn còn.

## Gameplay signal và nâng cấp

Thêm `POST /internal/workers/{worker}/jobs/{job}/running` với body executionId/epoch, Bearer credential của worker. Session chủ giải/admin không gọi thay được. Tái sử dụng guard, worker ownership, epoch, trust, heartbeat, lease và start fencing. Mốc gameplay_started_at ghi một lần (coalesce); retry cùng execution không đổi thời điểm.

Worker 0.11.0 gọi /start như trước, sau đó chuẩn bị hai process và trận, rồi gọi /running trước vòng chơi. Nếu không nhận được ACK, không tiếp tục chơi hoặc tạo winner giả; giữ workflow lease/recovery hiện có. Đây là tín hiệu worker đã qua bước chuẩn bị, không phải bằng chứng kiểm chứng từng nước đi hoặc bảo đảm tiến trình không chết sau đó.

Nâng Central và schema trước, sau đó cập nhật worker JAR. Worker cũ không gọi endpoint mới vẫn tiếp tục cơ chế cũ nhưng read model giữ STARTING khi chưa có tín hiệu gameplay; không backfill mốc dự đoán. Worker mới cần Central mới, không triển khai ngược thứ tự.

V36 chỉ thêm nullable gameplay_started_at vào match_execution_attempts và index (job_id,claimed_at,id) hỗ trợ chọn attempt/tổng đếm. Không sửa checksum migration cũ. Tests nâng cấp cập nhật số migrations kỳ vọng từ 35 lên 36, giữ số migrations của các target cũ không đổi.

## Files

Mới:

- server/src/main/java/com/rpsbattle/tournament/RemoteExecutionReadService.java
- server/src/main/java/com/rpsbattle/api/controller/RemoteExecutionReadController.java
- server/src/main/resources/db/migration/V36__remote_gameplay_start.sql
- server/src/test/java/com/rpsbattle/worker/remote/RemoteExecutionReadTest.java
- server/src/test/java/com/rpsbattle/worker/remote/RemoteExecutionReadPostgresTest.java
- Tài liệu này.

Sửa:

- server/src/main/java/com/rpsbattle/worker/remote/RemoteMatchQueue.java, WorkerSecurityConfig.java
- server/src/main/java/com/rpsbattle/api/controller/RemoteWorkerController.java
- server/src/test/java/com/rpsbattle/persistence/FlywayMigrationTest.java
- worker/build.gradle
- worker/src/main/java/com/rpsbattle/remote/CentralClient.java, WorkerApplication.java, ExecutionBatch.java, HeadlessMatch.java
- worker/src/test/java/com/rpsbattle/remote/HeadlessMatchTest.java
- README.md

## Kiểm chứng

- PASS: 20 kiểm thử RemoteExecutionReadTest trên H2 và 20 kiểm thử RemoteExecutionReadPostgresTest trên PostgreSQL Testcontainers, gồm lifecycle, phân trang/lọc, isolation và quyền truy cập.
- PASS: 31 kiểm thử FlywayMigrationTest, gồm nâng schema V35 lên V36. Server PostgreSQL thực tế cũng khởi động và áp dụng V36 thành công.
- PASS: toàn bộ 30 kiểm thử worker, gồm 4 HeadlessMatchTest và 4 SandboxBotDockerTest; không có test bị bỏ qua.
- PASS: toàn bộ 178 kiểm thử client, không có test bị bỏ qua.
- PASS: bootJar; worker JAR chạy --help xác nhận phiên bản 0.11.0.
- PASS: HTTP smoke trên server thật ở localhost:8080: chủ giải đọc được summary rỗng của giải chưa có remote job, tài khoản khác 403, guest 401, filter/size sai 400; các trang tournament, tournament-lobby và tournament-detail trả HTTP 200. Dữ liệu remote có job/attempt được kiểm chứng ở integration tests, không tạo worker giả trên server đang dùng.
- PASS: git diff --check.
- SKIPPED: kiểm tra screenshot/layout; nhóm này không thay giao diện, browser automation đang bị tắt trong Work settings. Không coi HTTP 200 là kiểm chứng bố cục hoặc một trận remote end-to-end trên server thật.

## Giới hạn

Không có My Match/Match Monitor UI mới trong nhóm này, không có endpoint remote public cho người chơi. Nhóm 8 sẽ xử lý quyền đọc theo người tham gia và bố cục màn hình khi cần. API hiện là summary theo giải, chưa cung cấp lịch sử đầy đủ mọi attempt. Truy vấn scalar snapshot toàn bộ remote job của một giải để lọc/tổng đếm rồi phân trang; đã kiểm tra nhiều hơn một trang, chưa benchmark 4.000 người hoặc toàn bộ Swiss nhiều vòng. Không tuyên bố độ trễ/ETA hay capacity.
