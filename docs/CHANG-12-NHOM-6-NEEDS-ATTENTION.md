# Chặng 12 — Nhóm 6: Các vấn đề cần xử lý

## Phạm vi

Thêm TournamentAttentionPanel vào khung quản lý chủ giải, dùng dữ liệu organizer-metrics và summary đã có. Không thêm request polling hoặc mutation mới. Khách/người không có quyền quản lý không được mount shell; endpoint metrics vẫn giữ kiểm tra owner/admin.

Chỉ hiển thị cảnh báo từ snapshot có tournamentId và revision khớp summary. Không ghép số của giải khác hoặc revision cũ/mới không khớp. FINISHED/CANCELLED không giữ cảnh báo thao tác đang mở; hướng người dùng xem Audit. Số âm, không phải số nguyên an toàn hoặc chuỗi không được dùng làm số cảnh báo.

## Quy tắc và liên kết

- Thông tin (INFO): đơn chờ xét duyệt trong REGISTRATION_OPEN/REGISTRATION_REVIEW/WAITING_FOR_CODING. Giải timeline tới trang duyệt đơn với status=PENDING; giải thủ công tới danh sách PENDING.
- Cần chú ý (WARNING): final validation FAILED trong giai đoạn khóa bài/validation/check-in/chờ bắt đầu. Mở filter FAILED phía server.
- Khẩn cấp (CRITICAL): validation INFRA_ERROR trong các phase trên. Mở filter INFRA_ERROR để xử lý từng bài có lý do, không tự retry.
- Cần chú ý: suất đủ điều kiện chưa check-in trong CHECK_IN_OPEN/READY_TO_START. Mở view=check-in&filter=MISSING; không báo thiếu check-in trước giờ mở hoặc sau khi thi đấu đã bắt đầu.
- Khẩn cấp: trận FAILED khi RUNNING/PAUSED/RECOVERY_REQUIRED. Mở view=matches&filter=FAILED.
- Cần chú ý: trận đang review khi RUNNING/PAUSED/RECOVERY_REQUIRED. Mở filter REVIEW.
- Khẩn cấp: giải RECOVERY_REQUIRED. Mở Cài đặt để kiểm tra và thực hiện nghiệp vụ theo quyền hiện có.
- Khẩn cấp: worker offline đang giữ assignment của đúng giải, trong giai đoạn thi đấu/tạm dừng/khôi phục. Mở phần máy phục vụ giải trong tab Trận đấu. Không đưa link tới quản trị worker toàn hệ thống.

Sắp xếp CRITICAL trước WARNING trước INFO. Nhãn có chữ, không chỉ dựa vào màu. Mỗi item là link có thể mở tab mới, mã giải được URL encode. Không bịa cảnh báo stuck match hoặc capacity thấp vì chưa có read-model xác định các điều kiện đó. Trận FAILED được gọi đúng là trận lỗi, không gọi là trận treo.

## Bộ lọc

Trang chủ giải thêm lựa chọn trận ALL/FAILED/REVIEW và đăng ký ALL/PENDING/MISSING; link trực tiếp giữ trong URL. Dữ liệu trận/roster dùng summary hiện có, không lọc sai trên trang 25 dòng. Filter lạ trở về ALL.

MISSING với timeline chỉ nhận registration APPROVED có participant trong roster hoặc entry NOT_CHECKED_IN đã được ghi nhận; loại CHECKED_IN, người chưa vào roster và các suất bị DISQUALIFIED/WITHDRAWN. Giải thủ công dùng APPROVED/NOT_CHECKED_IN. Đây là danh sách roster hiện tại; không phải danh sách tất cả ứng viên đăng ký.

Validation FAILED/INFRA_ERROR và application PENDING tái sử dụng filter phía server của nhóm 5. Nhóm này không thêm match execution remote filter; chi tiết đó thuộc nhóm 7–8.

## Refresh và trạng thái

Tái sử dụng chu kỳ 5 giây của organizer shell. Khi có snapshot khớp mới, cảnh báo đã hết được thay bằng trạng thái rỗng; không giữ bản sao lịch sử cảnh báo. Khi lỗi tải, hiển thị thông báo dữ liệu có thể cũ, hoặc chưa có dữ liệu nếu lỗi ngay lần đầu. Không tuyên bố cả hệ thống khỏe chỉ vì bảng rỗng. Giữ focus đang ở link cảnh báo và không sửa form đang nhập. Vẫn dùng pagehide/pageshow lifecycle của shell, không thêm vòng timer mới.

## File thay đổi / API

Mới:

- client/js/tournament/organizer-attention.js
- client/js/tournament/organizer-filters.js
- client/tests/organizer-attention.test.js
- Tài liệu này.

Sửa:

- client/js/tournament/organizer-shell.js
- client/js/tournament/organizer-metrics.js
- client/js/tournament/tournament-detail.js
- client/tournament.html
- client/css/tournament.css
- README.md

Không đổi API/DTO/schema, GameEngine/RuleSet hoặc quyền server.

## Kiểm chứng ngày 20/10/2026

- PASS: 178 client tests. 11 test mới kiểm tra scope/revision, phase, severity/order, URL/filter, không cảnh báo suy đoán, terminal/zero/invalid counts, loading/stale/empty/resolved, lọc lỗi/review và roster thiếu check-in kể cả người bị loại/rút.
- PASS: 9 backend regression tests: OrganizerMetricsApiTest (7), TournamentOwnershipTest (2), xác nhận dữ liệu tổng hợp/quyền dùng cho cảnh báo.
- PASS: bootJar bản cuối.
- PASS: HTTP/PostgreSQL native: summary và organizer-metrics cùng giải/revision; model trả rỗng cho giải mẫu không có vấn đề liên quan; guest 401 và tài khoản khác 403. Module mới, trang chứa filter và URL điểm đến trả HTTP 200. Không tạo trận lỗi hoặc worker offline giả trong database đang chạy.
- SKIPPED: kiểm tra trực quan desktop/mobile vì browser báo Local browser use is disabled in Work settings. Không dùng công cụ khác vượt cài đặt. DOM renderer và URL đã kiểm thử tự động; chưa xác minh bằng screenshot.
- SKIPPED: worker Docker offline thực tế/trận lỗi thật vì nhóm này không đổi execution; chỉ test cảnh báo trên snapshot và endpoint quyền dữ liệu hiện có. Không tuyên bố E2E mọi loại sự cố.

Lệnh client: `node --test client/tests/*.test.js`. Backend: `:test --tests '*OrganizerMetricsApiTest' --tests '*TournamentOwnershipTest' :bootJar`. Smoke script ở build bỏ qua Git, không commit tài khoản thử.

Nhóm 7 — Remote Execution Read Model chưa triển khai trong commit này. Chặng 12 còn các nhóm tiếp theo.
