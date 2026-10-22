# Admin Remote Worker Operations Center

## Giao diện và luồng thao tác

- Bốn tab: Workers (mặc định), Registration Tokens, Groups, Tournament Assignment.
- Summary toàn hệ thống và bộ lọc Cần xử lý nằm phía trên; không tính tổng từ trang hiện tại.
- Bảng Workers còn 8 cột: Worker, Status, Trust, CPU/RAM, Slots trống, Active matches, Heartbeat, Thao tác.
- Runtime, ngôn ngữ, Docker, bot processes, cache, dung lượng đĩa, version/protocol, group, giới hạn Admin và công việc gần đây chuyển vào modal Chi tiết. UUID/IP nằm trong disclosure kỹ thuật.
- Mỗi dòng chỉ có Chi tiết và menu thao tác theo trạng thái. Worker offline không có Drain; worker disabled không có Disable lần nữa; worker đã thu hồi không có thao tác cấp quyền lại.
- Bộ lọc tên/status/trust/group, sắp xếp và phân trang 25/50/100 thực hiện trên server. API trả một trang; server dùng snapshot metadata chung để tính summary nhất quán, không truy vấn từng worker.
- Auto refresh dùng refresh controller hiện có (5 giây, xử lý visibility/BFCache). Không thay bảng khi đang mở modal. Lỗi refresh giữ dữ liệu và hiển thị cảnh báo dữ liệu cũ.
- CSS riêng giảm artwork ở vùng vận hành; dưới 900px mỗi dòng worker thành card có nhãn cột. Đây là hành vi CSS đã kiểm tra bằng test, chưa phải xác nhận hình ảnh thực tế.

## Token và nhóm

- Secret chỉ có trong response tạo token và modal một lần; mặc định che, có nút hiện/copy. Đóng modal hoặc pagehide sẽ xóa secret khỏi input và object response; không ghi localStorage/sessionStorage.
- Copy command dùng PowerShell Read-Host -AsSecureString và biến môi trường WORKER_REGISTRATION_TOKEN, không nhúng secret vào command hoặc argument Java. Người dùng tự dán token khi chạy lệnh.
- Danh sách token chỉ có metadata: tạo/hết hạn, người tạo, lượt còn, trạng thái, worker đã dùng và lần dùng gần nhất.
- Ghi nhận lần sử dụng mới vào worker_identity_events. Không suy đoán worker đã sử dụng các token cũ trước thay đổi này.
- Group có capacity card, xem giải đang cấu hình và mức trust, bật/tắt bằng reason. Chọn thành viên bằng tìm kiếm/phân trang/checkbox, không nhập UUID.
- Thay đổi nhiều thành viên dùng các mutation revision hiện có theo thứ tự. Đây không phải transaction batch; nếu thất bại giữa chừng, tải lại thành viên hiện tại để Admin xem và xác nhận lại.

## Phân công giải và capacity

- Tìm/chọn giải từ API Admin phân trang, gồm giải private mà Admin được quản lý; không nhập Tournament UUID.
- Giữ đúng hai mode backend: LOCAL và REMOTE_REQUIRED (nhãn REMOTE GROUP). AUTO chưa được backend hỗ trợ và không được giả lập.
- LOCAL ẩn/vô hiệu các field remote. REMOTE GROUP yêu cầu chọn nhóm, trust và concurrency; preview đọc từ server. Giải đã tạo lịch/bắt đầu bị khóa cấu hình theo policy hiện có.
- Capacity dùng cùng projection WorkerTelemetryService với scheduler: liveness, approval, credential revoked, trạng thái, CPU/RAM budget, admin limit và số reservation trung tâm.
- Preview lọc thêm group enabled, trust, Docker, protocol và ngôn ngữ. Ưu tiên ngôn ngữ bài LOCKED; khi chưa có bài khóa dùng allowed languages của giải.
- Ước lượng bảo thủ: worker phải hỗ trợ tất cả ngôn ngữ cần thiết. Không phải bài toán tối đa capacity theo từng cặp đấu, cũng không đặt trước slot hoặc bảo đảm capacity riêng khi nhóm phục vụ nhiều giải.
- Thiếu capacity có cảnh báo; vẫn cho lưu policy hợp lệ để có thể bổ sung worker sau.
- CPU/RAM/disk warning lấy ngưỡng backend (mặc định 90%, 90%, 1024 MiB); heartbeat cũ dùng ngưỡng hiện có 30 giây. Không tự tạo metric cache-corruption hay nội dung lỗi runtime mà backend chưa cung cấp.

## API/DTO

Các endpoint mới đều requireAdmin và Cache-Control: no-store:

- GET /api/admin/workers/operations: trang workers, summary, attention, serverNow.
- GET /api/admin/workers/operations/{id}: metadata chi tiết và 20 công việc gần đây.
- GET /api/admin/worker-groups/capacity: nhóm, capacity và giải đang cấu hình.
- GET /api/admin/worker-assignments/tournaments: tìm giải phân trang.
- GET /api/admin/tournaments/{id}/worker-capacity: preview theo group/trust/requested.

TokenView bổ sung metadata người tạo/trạng thái/usedBy/lastUsed. Không trả credential hash, token hash, raw token, job spec hoặc source code. Các mutation, CSRF, reason, optimistic revision và trust policy hiện có được giữ lại. Không sửa GameEngine, không thêm migration/schema.

## File đã sửa

- client/css/admin.css
- client/js/admin/workers.js
- client/js/admin/worker-api.js
- client/js/admin/worker-groups.js
- client/tests/admin-workers-ui.test.js
- client/tests/admin-workers.test.js
- server/src/main/java/com/rpsbattle/api/controller/WorkerGroupController.java
- server/src/main/java/com/rpsbattle/worker/remote/WorkerIdentityService.java
- server/src/main/java/com/rpsbattle/worker/remote/WorkerTelemetryService.java

## File mới

- client/css/worker-operations.css
- client/js/admin/worker-operations-view.js
- client/js/admin/worker-ops-controls.js
- client/js/admin/worker-token-view.js
- client/js/admin/worker-setup-view.js (tách hướng dẫn cài worker hiện có)
- client/js/admin/worker-assignment-view.js
- client/tests/admin-worker-operations.test.js
- server/src/main/java/com/rpsbattle/worker/remote/WorkerOperationsService.java
- server/src/test/java/com/rpsbattle/worker/remote/WorkerOperationsTest.java
- server/src/test/java/com/rpsbattle/worker/remote/WorkerOperationsPostgresTest.java
- docs/ADMIN-REMOTE-WORKER-OPERATIONS.md

## Kiểm chứng

- PASS: toàn bộ 417 client tests, 0 fail/skip. Gồm DOM mount thực tế, bốn tab, server filters, modal token/clear-on-close/pagehide, CSRF, BFCache refresh, mở thẳng tab assignment và không lặp empty state.
- PASS: 83 backend regression tests (WorkerOperations 21, WorkerControl 20, WorkerHeartbeat 11, WorkerMetrics 17, RemoteRouting 14), 0 fail/skip.
- PASS: 4 test operations mới trên PostgreSQL 16 qua Testcontainers: snapshot/filter, capacity eligibility, Admin-only/không lộ secret, token usage metadata. 0 fail/skip.
- PASS: :bootJar và git diff --check.
- PASS: server sau restart trả HTTP 200 cho health, trang Workers, JS/CSS mới ở localhost và qua tunnel; kết nối WebSocket công khai mở thành công. Tunnel cũ đã mất phía Cloudflare nên được khởi động lại với URL mới.
- SKIPPED: browser smoke test, screenshot desktop/mobile và xác nhận trực quan. Công cụ trình duyệt đã từ chối truy cập URL trong phiên; không dùng browser khác để vượt hạn chế. Các DOM/CSS tests không thay thế visual QA.

Lệnh kiểm thử: node --test client/tests/*.test.js; Gradle :test với các suite nêu trên; :bootJar để đóng gói client mới vào server.
