# Nhật ký quản trị dễ đọc

## Thay đổi giao diện

Trang `/admin/audit.html` dùng sáu cột: Thời gian, Người thực hiện, Hành động, Đối tượng, Kết quả, Chi tiết. Không còn role/source, action code, UUID, HTTP hoặc JSON dài trong bảng chính. Dòng hành động có tiêu đề và một dòng mô tả/tóm tắt; phần dài được cắt bằng ellipsis và có đầy đủ trong modal.

`audit-presentation.js` là lớp diễn giải dùng chung cho trang Audit. Hiện có **156 mã hành động được map rõ ràng**, cộng ba thao tác ENABLE/DISABLE/ARCHIVE diễn giải theo đối tượng tài khoản/bot. Bao gồm các mã động đang phát sinh từ validation, timeline, worker, lifecycle, đăng ký và submission. Test quét literal `TournamentAudit.append` trong production để phát hiện mã mới chưa có mapping.

Các nhóm: Tài khoản, Giải đấu, Đăng ký, Bot, Kiểm thử, Trận đấu, Worker, Bảo mật, Hệ thống.

Không có literal action trong phạm vi quét bị thiếu mapping. Mã cũ hoặc mới chưa biết dùng tiêu đề “Hoạt động hệ thống”; code vẫn hiện trong phần kỹ thuật và có console warning một lần/mã (giới hạn 100 mã mỗi lần tải trang). Không khẳng định mọi mã lịch sử trong database đều đã được biết trước.

Người thực hiện phân biệt username, hệ thống tự động, remote worker, bộ lập lịch, khách chưa đăng nhập và tài khoản không còn tên. Đối tượng dùng tên có thể resolve, gồm tên giải, người đăng ký, bot/phiên bản/người sở hữu; trận không có tên dùng mã rút gọn. Không tạo tên giả cho đối tượng không resolve được.

Kết quả phân biệt thành công, thất bại, bị từ chối và xung đột trạng thái. Event bot trượt validation hiển thị “Không đạt kiểm thử”; lỗi hạ tầng hiển thị riêng, dù thao tác ghi nhận event có result gốc SUCCESS. Result gốc luôn còn trong metadata kỹ thuật.

## Chi tiết và tóm tắt thay đổi

Modal hiển thị thông tin dễ hiểu trước: hành động, thời gian, người thực hiện, đối tượng/giải, kết quả, thay đổi và lý do. Disclosure “Thông tin kỹ thuật” mặc định đóng, chứa action/resource code, ID, HTTP, vai trò/source và kết quả gốc. Before/after có từng disclosure riêng, JSON được format monospace và có nút copy.

Change summary chỉ lấy field có ý nghĩa: trạng thái, thể thức, giờ đăng ký/lập trình/bắt đầu, trust, concurrency và enabled. Hỗ trợ JSON, scalar status và các field timeline trong Java record text cũ. Không dump toàn bộ object hay suy đoán ý nghĩa của giá trị không biết.

## Filter và API

- Tìm tên người dùng, giải, bot/phiên bản, mã đối tượng hoặc lý do bằng server-side search có escape wildcard.
- Filter thường: loại người thực hiện, nhóm hoạt động, đối tượng, kết quả, khoảng ngày UTC. Kích thước trang 25/50/100, mới nhất trước.
- Filter nâng cao: Actor ID, exact action code, Resource ID, Request ID, HTTP status. Giữ URL filters và generation guard để response cũ không ghi đè lần lọc mới; BFCache quay lại sẽ refresh.
- Giữ GET `/api/admin/audit`, Admin-only và no-store. Mặc định size đổi từ 20 sang 25; client cũ vẫn có thể gửi size 20.
- Bổ sung tham số `category`, `actor_kind`, `resource_id`, `request_id`, `http_status`; result nhận thêm FAILED/DENIED/CONFLICT, vẫn tương thích FAILURE/SUCCESS.
- DTO giữ code/metadata cũ và thêm `resource_name`, `tournament_name`, `category`, `outcome`, `request_id`. Trước đây các bảng nguồn không lưu Request ID: giá trị đó là null và UI ghi “Không ghi nhận”, không tự tạo ID lịch sử.
- Giữ ba nguồn audit cũ, bổ sung metadata từ worker_identity_events. Không đọc worker credential/token tables để hiển thị Audit.

Tên đối tượng lấy bằng SQL joins trong read-model, không gọi service/DB riêng cho từng dòng. Test 31 event kiểm chứng hai truy vấn Audit: count và page, kể cả ở trang thứ hai. Các joins dùng khóa duy nhất để không nhân số dòng. Tên là dữ liệu hiện tại; chưa bổ sung snapshot tên vào schema. Nếu đối tượng bị rename/delete, tên lịch sử có thể thay đổi/mất và UI dùng fallback. Audit record gốc không bị chỉnh sửa.

## Che dữ liệu nhạy cảm

`AuditRedactor` xử lý tại server trước khi trả before/after/reason về UI. Đệ quy qua JSON object/array, kể cả JSON được nhúng trong chuỗi. Che các key password/passwd, secret, credential, authorization, cookie, session, token, private/API key, sourceCode, database/JDBC URL; chuẩn hóa dấu phân cách trong tên key.

Với dữ liệu record/text cũ, che assignment nhạy cảm, Bearer header, mật khẩu trong URI database và chuỗi token/credential worker theo prefix hiện có. JSON không hợp lệ có sensitive assignment cũng được kiểm thử. Copy chỉ sao chép giá trị đã che từ response. Không ghi đè hoặc xóa dữ liệu audit trong database.

Đây là che dữ liệu theo cấu trúc/key/pattern đã biết; không thể xác định một mật khẩu bất kỳ nếu ai đó nhập nó như văn bản tự do không có dấu hiệu nhận diện. Pipeline audit hiện có vẫn phải tránh ghi payload/secret ngay từ nguồn.

## File

Đã sửa:

- client/css/admin.css
- client/js/admin/operations.js
- server/src/main/java/com/rpsbattle/admin/AdminAuditService.java
- server/src/main/java/com/rpsbattle/api/controller/AdminOperationsController.java

File mới:

- client/css/audit.css
- client/js/admin/audit-presentation.js
- client/js/admin/audit-view.js
- client/tests/audit-presentation.test.js
- client/tests/audit-list-ui.test.js
- server/src/main/java/com/rpsbattle/admin/AuditRedactor.java
- server/src/test/java/com/rpsbattle/admin/AuditRedactorTest.java
- server/src/test/java/com/rpsbattle/admin/AdminAuditPresentationTest.java
- server/src/test/java/com/rpsbattle/admin/AdminAuditPresentationPostgresTest.java
- docs/ADMIN-AUDIT-HUMAN-READABLE.md

Không thêm migration, không sửa GameEngine, không thay đổi action code hoặc chính sách ghi audit gốc.

## Kiểm chứng

- PASS: 430 client tests, gồm 13 test mới về mapping/context, coverage, change summary, modal/JSON, filter/paging/reset, stale response và kết quả validation. Không fail/skip.
- PASS: 20 backend regression tests: AdminConsoleApi 7, AdminDirectoryPostgres 3, AdminAuditPresentation H2 4, AdminAuditPresentationPostgres 4, AuditRedactor 2. Không fail/skip.
- Các thay đổi SQL cuối tiếp tục được kiểm tra lại trên cả H2/PostgreSQL với bộ read-model/directory (11 tests).
- PASS: bootJar và diff whitespace check.
- PASS: sau restart, health/trang Audit/JS/CSS mới trả HTTP 200; API Audit chưa đăng nhập trả 401. Tunnel vẫn tắt.
- SKIPPED: browser smoke test và screenshot desktop/mobile. Công cụ trình duyệt đã từ chối truy cập URL trong phiên; không dùng trình duyệt khác để vượt hạn chế. DOM tests không thay thế xác nhận trực quan.

Lệnh: `node --test client/tests/*.test.js`; Gradle `:test` với các suite trên; `:bootJar` để đóng gói client/server.
