# Drain, disable, revoke và bảo vệ Worker API

Nhóm 8 bổ sung điều khiển worker trên `/admin/workers.html`. Worker JAR vẫn là 0.7.0; các phản hồi 401/409 đã được agent xử lý để dừng bot. Không đổi GameEngine, luật chơi hoặc protocol bot. Tái sử dụng schema V29–V33, không có migration mới.

## Chính sách vận hành

- **Drain · hoàn tất trận hiện tại:** lưu DRAINING; không cấp thêm execution. Các execution đã giữ slot vẫn được tải artifact, start, renew và gửi kết quả trong lease. Khi không còn reservation, API hiển thị DRAINED, slot nhận mới bằng 0. Heartbeat hoặc capabilities sau restart không xóa trạng thái drain.
- **Vô hiệu hóa · hủy execution hiện tại:** lưu DISABLED, tăng epoch, hết hiệu lực các execution đang giữ, trả slot và đưa job vào luồng retry có giới hạn. Credential chưa bị revoke nhưng bị chặn khi DISABLED. Đây là chính sách cancel-current; muốn finish-current hãy dùng Drain.
- **Bật lại:** chỉ áp dụng cho máy đã được duyệt, đang drain hoặc disabled và credential chưa thu hồi. Lưu STARTING, tăng epoch, xóa thời điểm heartbeat cũ. Khởi động lại agent để gửi capabilities/heartbeat mới. Không khôi phục execution đã bị hủy.
- **Thu hồi quyền vĩnh viễn:** lưu DISABLED và revoked_at, hủy/fence execution như disable. Worker không thể xác thực, claim, download, renew, recovery hoặc upload. Không thể bật lại credential này; muốn dùng máy tiếp phải đăng ký một identity mới và được duyệt lại. Có thể revoke cả máy chưa duyệt hoặc bị từ chối.

Central chặn execution cũ ngay sau commit giao dịch quản trị. Agent đang kết nối sẽ nhận lỗi xác thực ở heartbeat tiếp theo, thường trong 5 giây, rồi đóng hai container bot. Máy mất mạng hoặc không còn hợp tác không thể bị Central buộc dừng CPU tức thì; Central vẫn chặn kết quả cũ. Artifact đã tải xuống máy không thể thu hồi từ xa.

Job bị disable/revoke theo luồng infrastructure recovery đã có: ledger ghi `WORKER_RESTART`, audit worker ghi chính xác `WORKER_DISABLE` hoặc `WORKER_REVOKE` cùng lý do và người thao tác. Lần claim mới có execution ID mới; tối đa số attempt đã cấu hình. Hết retry chuyển lỗi hạ tầng và RECOVERY_REQUIRED, không tự tạo winner/forfeit. Receipt đã commit trước thao tác quản trị được giữ nguyên.

## API và quyền

`POST /api/admin/workers/{id}/control` dùng session ADMIN + CSRF:

```json
{"revision": 1, "action": "DRAIN", "reason": "Bảo trì máy sau trận hiện tại"}
```

Action: DRAIN, RESUME, DISABLE, REVOKE. Lý do bắt buộc, tối đa 500 ký tự. Revision phải đúng snapshot hiện tại; thao tác stale trả 409, không retry tự động. Thao tác sai trạng thái hoặc credential đã revoke trả 409. Giao dịch ghi revision, trạng thái và audit nguyên tử.

`GET /api/admin/workers/{id}/events` trả tối đa 100 sự kiện mới nhất, gồm thời gian, actor, loại thao tác, lý do và trạng thái trước/sau. Cả API này và danh sách worker không trả secret hoặc hash credential. Danh sách có `credentialRevoked`; nút thao tác tương ứng với trạng thái server. Nút lịch sử dùng text nodes để hiển thị lý do an toàn.

Control, claim, start, renew, recovery, receipt và đọc artifact cùng dùng database queue guard. Nếu claim thắng race thì control thu hồi execution vừa cấp; nếu control thắng thì claim bị chặn. Đọc artifact đã được cho phép trước control có thể hoàn tất trước khi control trả về; sau commit không cấp thêm artifact. Không giữ transaction chỉ đọc với khóa ghi trên PostgreSQL.

Worker API tiếp tục stateless Bearer, không dùng account cookie để cấp quyền worker; credential worker không được dùng thay session quản trị. Worker chỉ truy cập assignment/artifact/result của chính mình, trong lease và trust phù hợp. Artifact TRUSTED không cấp cho COMMUNITY. HTTPS là mặc định; HTTP cần opt-in rõ cho local/LAN. Không có endpoint browse source hoặc sửa bracket cho worker.

## Phạm vi

Scheduler giải thật vẫn chạy local. Admission remote hiện là API nội bộ cho các nhóm tích hợp, chưa tự đưa giải thật vào remote queue. Capacity scheduler, worker groups và các chính sách phân phối rộng hơn thuộc nhóm 9; không tự triển khai trong nhóm này.

## File thêm/sửa

- `WorkerControlService`: state transitions, revision, audit, revoke và fencing.
- `RemoteWorkerController`: control và event APIs có quyền ADMIN.
- `WorkerTelemetryService`: giữ drain qua restart, DRAINED và credentialRevoked.
- `RemoteMatchQueue`: cho assignment đã nhận start trong khi drain, vẫn cần heartbeat mới.
- `RemoteLeaseService`: thu hồi reservation theo retry policy.
- `RemoteArtifactService`: tuần tự hóa download authorization với control.
- `client/js/admin/worker-api.js`, `workers.js`: nút vận hành, lịch sử, hướng dẫn.
- `WorkerControlTest`, `WorkerControlPostgresTest`, `client/tests/admin-workers.test.js`: quyền, CSRF, policy, concurrency và regression.
- README và báo cáo này.

## Kiểm chứng

- **PASS:** 145 backend tests trên H2/PostgreSQL: drain giữ execution/result, capabilities không bật lại drain, disable trả slot/requeue, revoke chặn mọi Worker API, resume không hồi sinh credential, ADMIN/CSRF/reason/revision, race claim/disable và hồi quy lease/artifact/start/local scheduler.
- **PASS:** 27 worker tests, gồm sandbox Docker, cleanup theo execution, outbox, recovery và idempotent upload.
- **PASS:** 127 client/admin tests, gồm chính sách nút điều khiển và request đúng cookie/CSRF/revision, ID được encode, không dùng worker Bearer cho admin.
- **PASS:** build Central bootJar và worker JAR. Tổng 299 tests, không failure hoặc skipped trong các bộ đã chạy.
- **PASS native Docker:** worker đăng ký riêng, ba fixture cô lập. Drain trong lúc hai container đang chạy vẫn hoàn tất một receipt/replay; DRAINED và slot 0. Disable/revoke trong lúc hai container đang chạy khiến agent đóng cả hai, execution EXPIRED, job trở lại queue, không có receipt hoặc winner giả. Bật lại hai lần thành công; revoke không thể xác thực lại. Fixture được hủy sau kiểm tra, credential test giữ trạng thái revoked, không ảnh hưởng worker chính.
- **PASS sau restart Central:** trạng thái revoked và năm control events còn nguyên, không rò reservation; giải cũ giữ standings, frozen artifact và replay. Receipt/replay/idempotency nhóm 6 được kiểm tra lại.
- Backup PostgreSQL và bot/replay trước native integration; không đưa backup, credential, token hoặc state test vào Git. Schema vẫn V33.
- **SKIPPED:** kiểm tra giao diện bằng trình duyệt vì browser tools đang tắt trong Work settings; client API/DOM tests vẫn chạy. Chưa chạy capacity benchmark hoặc tải 4.000 người, thuộc nhóm 9–10.
