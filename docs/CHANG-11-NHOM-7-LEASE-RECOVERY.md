# Lease, crash và network recovery

Worker 0.7.0 bổ sung đối chiếu execution khi reconnect/restart. Central V33 lưu started_at và lịch sử worker_execution_recoveries. GameEngine, luật chơi và protocol bot không đổi.

## Chạy

```sh
java -jar rps-worker.jar --central=https://central.example --state-dir=./worker-state
java -jar rps-worker.jar --central=https://central.example --state-dir=./worker-state --recover
java -jar rps-worker.jar --central=https://central.example --state-dir=./worker-state --run-once
```

Agent mặc định heartbeat, recovery lúc khởi động và thử lại mỗi 10 giây; chưa tự claim trận giải thật. --recover đối chiếu một lượt rồi thoát, không chạy bot. --run-once recovery trước khi nhận batch mới. --upload-pending chỉ xử lý kết quả hoàn thành, không abandon trận gián đoạn hoặc claim mới. --once/--identity-only/--prefetch vẫn là mode chẩn đoán và không tự abandon job. Không kết hợp các mode; LAN/local HTTP cần --allow-insecure-http.

Giữ nguyên worker-state và chỉ chạy một agent cho mỗi identity. Không sao chép credential để chạy nhiều agent đồng thời; muốn thêm máy, đăng ký worker riêng.

## Lease do Central quyết định

- Lease ban đầu giữ công thức max(60 giây, hard timeout + 30 giây). Batch gửi heartbeat rồi renew các execution còn giữ mỗi 5 giây.
- `POST /internal/workers/{id}/jobs/renew`: epoch + tối đa 16 execution ID. Cần worker/epoch hiện tại, heartbeat mới, approval/trust phù hợp, đúng ownership và lease chưa hết. Không nhận timestamp gia hạn từ worker.
- Gia hạn tới now + 30 giây hoặc giữ lease còn dài hơn, nhưng không vượt started_at + hard timeout + 120 giây; trước start dùng claimed_at. Không thể giữ job vô hạn bằng heartbeat.
- Server trả ACTIVE/DONE/LEASE_LOST hoặc trạng thái terminal theo từng execution. Worker đóng bot của execution đã mất quyền; DONE không bị coi là lỗi. Khi mất mạng tạm thời, bot vẫn chơi local trong hard deadline và lưu kết quả, chưa claim thêm việc.

## Hết hạn và retry

Central kiểm tra mỗi giây, tối đa 32 execution/lượt. Sau lease + 30 giây grace: transaction dùng cùng admission guard với claim, renew, recovery và complete để ghi EXPIRED, trả slot, lưu recovery ledger và requeue nếu còn được phép. Claim tiếp theo luôn tạo execution ID mới; không hồi sinh execution EXPIRED.

Giới hạn mặc định ba attempt, cấu hình `rps.worker.remote-max-attempts` trong khoảng 1–10. Đếm các attempt đã nhận của job. Hết giới hạn thì job CANCELLED và áp dụng lỗi REMOTE_WORKER_INFRASTRUCTURE_ERROR/MATCH_ERROR vào giải, chuyển sang RECOVERY_REQUIRED để quản trị xem xét. Không chọn winner hoặc xử thua người chơi do lỗi máy. Projection lỗi được thử lại; audit/failure không ghi hai lần.

Giải đã hủy/hoàn tất hoặc match không còn pending không được requeue để ghi đè kết quả admin. Ledger giữ execution, reason, số attempt, terminal và thời điểm projection qua restart.

## Reconnect và restart

`POST /internal/workers/{id}/jobs/recover` nhận executionId và operation:

- RESULT_READY: kết quả đã chơi xong. Nếu RUNNING vẫn thuộc worker, chưa quá lease + grace, chưa quá giới hạn thời gian và chưa bị thu hồi thì Central cho UPLOAD_ALLOWED với lease mới. Worker gửi lại cùng result/replay; receipt DONE vẫn idempotent sau lease.
- ABANDON: trận bị gián đoạn hoặc claim không có state local do mất phản hồi. Central ghi WORKER_RESTART, thu hồi execution, trả slot và retry theo policy. Không resume bot hoặc tiếp tục từ một board chưa có checkpoint.
- SUPERSEDED: execution đã hết quyền/đã bị thay thế. Worker giữ assignment/replay và dữ liệu kết quả để đối chiếu, đánh dấu terminal local và không cố gửi kết quả cũ vào execution mới.

Worker thử upload READY_FOR_UPLOAD trước; nếu lease cần recovery thì đối chiếu Central. STARTING/FAILED và state ghi dở được xác minh với Central rồi đánh dấu RECOVERED. Claim server còn giữ nhưng không có journal cũng được abandon, tránh rò slot khi response claim bị mất. ACKNOWLEDGED/RECOVERED/SUPERSEDED không chặn batch sau; state chưa giải quyết vẫn được giữ và chặn claim mới.

Kết quả hết hạn hoặc được thay thế không thể commit sau khi execution mới đã nhận job. Claim/complete/recovery/expiry chung guard và DB unique constraints bảo đảm một kết quả chính thức. Máy offline có thể vẫn đang tính toán đến hard deadline khi Central đã thu hồi quyền; không thể buộc máy mất mạng dừng tức thì. Kết quả của máy đó bị fence trên Central, không được ghi lần hai.

## Dọn orphan và bảo vệ dữ liệu

Chỉ dọn container có đồng thời label rps.scope=remote-worker và rps.execution đúng UUID đã được Central xác nhận. Kiểm tra container ID trước khi docker rm; không prune Docker, không kill mọi java/process khác. Xóa các file staging đã biết của execution, giữ replay/assignment/outcome. Cache/credential tiếp tục owner-only, state lock ngăn hai tiến trình dùng cùng thư mục.

File outcome.tmp ghi dở được thay thế an toàn trong state riêng. Replay giới hạn 15 MiB đồng bộ với API; file quá lớn không được đánh dấu uploadable. Nếu Docker hoặc Central chưa sẵn sàng, state giữ lại để retry; một lệnh --run-once có thể thoát lỗi khi mất mạng kéo dài, agent heartbeat có thể tiếp tục gửi state đó khi reconnect.

## Kiểm chứng

- 133 kiểm thử backend PASS: H2/PostgreSQL cho renew ownership/epoch/trust, grace recovery, expired result fencing, race expiry/recovery, retry tối đa không tạo winner, migration từ V32 và hồi quy artifact/start/local scheduler.
- 27 kiểm thử worker PASS: journal ghi dở/untracked claim, gửi lại kết quả, SUPERSEDED không mất replay, ACK và kiểm thử Docker dọn đúng orphan, giữ nguyên container execution khác.
- 125 kiểm thử client PASS; README và setup admin cập nhật lệnh/retry policy.
- Backup PostgreSQL + bot/replay trước khi native DB lên V33.
- Native: ngắt mạng qua proxy localhost trong khi Docker đang chơi; trận đủ 12 lượt ENGINE_RESULT, giữ READY_FOR_UPLOAD rồi ACK sau reconnect với cùng execution. Kill tiến trình worker thật khi hai bot đang chạy; --recover thu hồi execution, dọn orphan, retry bằng execution mới; mỗi job chỉ một receipt/kết quả và không có active slot bị rò. Hai giải fixture được hủy riêng sau test.
- Sau restart Central: recovery ledger, một kết quả mỗi job, slot về 0 và replay đều PASS. Kiểm tra ACK/idempotency nhóm 6 và E2E giải cũ cũng PASS, giữ nguyên standings/frozen artifact/audit.
- UI trực quan SKIPPED vì browser tools bị tắt trong Work settings.

Đây là nhóm 7. Drain/disable/revoke và các quyền vận hành mở rộng thuộc nhóm 8; chưa chuyển scheduler giải thật sang tự phân phối remote.
