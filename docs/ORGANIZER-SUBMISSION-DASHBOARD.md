# Tab Bài nộp của người tổ chức

Triển khai theo PROMPT-REDESIGN-ORGANIZER-SUBMISSION-TAB, ngày 22/10/2026.

## Phạm vi

Thiết kế lại dashboard bài nộp cho giải có timeline. Giữ tương thích API và thao tác bài nộp của giải cũ không có timeline. Không thay GameEngine, luật thi đấu, scheduler, cơ chế khóa bài hay quyền quản lý. Giao diện admin dùng cùng dashboard tại route admin hiện có. Sidebar tiếp tục dùng 4 nhóm: Tổng quan, Người chơi, Thi đấu, Quản trị; nhãn Kiểm thử không có dấu gạch thừa.

## Giao diện và trạng thái

- Tiêu đề BÀI NỘP; bỏ tên giải lặp lại, liên kết quay lại chi tiết và hàng đợi job lớn trong dashboard timeline.
- Panel navy, viền vàng nhẹ, input màu kem; nền lâu đài ở ngoài. Bốn bước số liệu, khu vực ngoại lệ, bộ lọc và bảng người chơi có khoảng cách riêng.
- Giai đoạn và CTA đi kiểm thử đặt ở đầu nội dung; giai đoạn lấy từ organizer-metrics, không lấy lifecycle REGISTRATION để suy diễn đã mở lập trình.
- Header dùng giai đoạn server làm trạng thái chính. Trong lúc chờ dữ liệu, hiển thị đang đồng bộ thay vì suy diễn từ lifecycle. Đếm người được duyệt riêng với roster. Khi roster khóa, thêm số người trong roster đã khóa.
- Countdown lấy phaseEndsAt, serverNow và thời gian monotonic từ lúc nhận, có nhãn mốc thực tế. Đến giờ vẫn chờ server xác nhận chuyển giai đoạn.
- Trước hạn giải thích chính sách sắp khóa; sau hạn phân biệt đang chốt bài và đã khóa dựa trên các bản freeze thực tế. Giải hủy chỉ hiển thị lịch sử.
- Không có ngoại lệ: thông báo nhỏ; cách tính nằm trong details mở rộng. Job chỉ còn một dòng active / failed, có ghi rõ lỗi là lịch sử.
- Bảng gồm người chơi, ngôn ngữ, workspace, bài nộp, READY, build/test, cập nhật và Xem chi tiết trạng thái. Chi tiết không tải source hoặc hidden tests.
- Mặc định CODING_OPEN lọc chưa READY; SUBMISSION_FROZEN lọc chưa có bài khóa hợp lệ; các giai đoạn khác xem tất cả. Người dùng chọn bộ lọc sẽ giữ lựa chọn đó.
- Tìm kiếm chỉ áp dụng khi bấm Lọc, không ghi đè từ khóa đang gõ. Phân trang 25/50/100, đọc dữ liệu lại mỗi 5 giây qua refresh controller có hủy request cũ. Giữ nguyên details đang mở và thông báo tạm giữ bảng đến khi đóng details.
- Phân biệt chưa có người được duyệt, chưa ai nộp bài và không có kết quả bộ lọc. Người chưa workspace vẫn có dòng NO_WORKSPACE, dịch thành Chưa tạo.

## Read model và API

Mở rộng endpoint có sẵn:

`GET /api/tournaments/{id}/submissions?view=participants&page=0&size=25&filter=ALL&search=...`

Chỉ manager của giải hoặc admin được gọi. View mặc định vẫn là phiên bản cũ (versions), không phá client cũ. View participants chỉ dùng cho giải timeline. Không thêm endpoint hoặc migration.

DTO gồm items, total (sau bộ lọc), page, size, filter, summary (trước bộ lọc), jobs. Items chỉ có thông tin định danh hiển thị và scalar trạng thái. Không có source, logs, email, hidden result hoặc thông tin worker.

Filter hợp lệ: ALL, NO_WORKSPACE, NOT_SUBMITTED, NOT_READY, READY, BUILD_FAILED, LOCKED, NO_VALID_SUBMISSION. Page 0–10000, size 1–100, search tối đa 80 ký tự. Search username/display_name không phân biệt hoa thường, dùng tham số SQL và tìm chuỗi literal (không diễn giải `%`/`_` thành wildcard). Sort ổn định theo tên rồi user UUID. Filter trước LIMIT/OFFSET; tổng số người không phụ thuộc trang.

### Nguồn số liệu

Mỗi dòng bắt đầu từ `tournament_timeline_registrations` có status APPROVED, unique theo người trong giải. LEFT JOIN các bảng trạng thái; không bắt đầu từ danh sách submission hoặc execution participant.

- **Được duyệt:** số dòng APPROVED.
- **Đã tạo workspace:** người có ít nhất một `tournament_code_workspaces`.
- **Đã nộp:** `tournament_bot_submissions` có trạng thái SUBMITTED hoặc LOCKED.
- **Có READY:** người có ít nhất một `tournament_submission_versions` trạng thái READY / SUBMITTED / LOCKED với public_passed và sample_passed. Đây là bước chuẩn bị, không phải kết luận đạt kiểm thử chính thức.
- **Build/test lỗi:** workspace hiện có BUILD_FAILED / TEST_FAILED và người chưa có bản READY. Các nhóm ngoại lệ có thể giao nhau.
- **Chưa workspace:** APPROVED không có workspace; **đã nộp chưa READY:** có bài nộp hợp lệ về trạng thái nhưng chưa có phiên bản đáp ứng điều kiện READY.
- **Bài bị từ chối/loại:** submission REJECTED / DISQUALIFIED.
- **Bản khóa:** `tournament_coding_freezes` đúng codingCloseAt hiện tại; decision khác MISSING là hợp lệ. Chưa có dòng là PENDING, không tự nhận đã khóa. Không trộn freeze của lần gia hạn trước.
- **Đạt kiểm thử chính thức:** `tournament_final_validations` PASSED liên kết đúng freeze hiện tại.
- **Job:** đếm job QUEUED / RUNNING và FAILED từ bot_coding_jobs của workspace thuộc giải; đây là job, không phải người.
- **Header:** organizer-metrics.registration.approved và maxParticipants; roster dùng registrationControl.lockedCount.

Read model chạy transaction REPEATABLE_READ: summary, tổng kết quả, trang và số liệu job nhất quán trong một lần đọc. CTE gom workspace/READY trước khi join, tránh nhân số người do ngôn ngữ hoặc phiên bản. Số query cố định, không N+1; trang trả về tối đa 100 dòng. Dashboard không gọi API lấy toàn bộ job nữa.

## File thay đổi

Mới:
- client/js/tournament/submission-view.js — model, renderer và controller dashboard timeline.
- server/src/main/java/com/rpsbattle/tournament/coding/SubmissionDashboardStore.java — scalar read model có search/filter/paging và summary.
- client/tests/submission-view.test.js — DOM, phase, empty states, controller và query.
- server/src/test/java/com/rpsbattle/tournament/registration/SubmissionDashboardPostgresTest.java — kiểm thử read model PostgreSQL thật.
- docs/ORGANIZER-SUBMISSION-DASHBOARD.md — báo cáo này.

Sửa:
- client/tournament-submissions.html, client/admin/submissions.html — vùng dashboard và stylesheet.
- client/js/tournament/submission-dashboard.js — chuyển giải timeline sang renderer mới, giữ legacy.
- client/js/tournament/tournament-header.js — count nguồn rõ ràng, phase chính và countdown có nhãn.
- client/js/tournament/organizer-shell.js — roster count, tránh metrics/attention trùng và không render lại panel đang ẩn.
- client/css/organizer-control-center.css — panel, pipeline, bộ lọc và bảng responsive.
- client/tests/tournament-header.test.js, client/tests/organizer-shell.test.js — semantics header mới.
- server/src/main/java/com/rpsbattle/api/controller/TournamentSubmissionController.java — tham số view/filter/search/size.
- server/src/main/java/com/rpsbattle/tournament/coding/TournamentSubmissionService.java — kiểm tra manager và gọi read model.
- server/src/test/java/com/rpsbattle/tournament/TimelineCodingApiTest.java, TournamentSubmissionApiTest.java — API người chưa workspace, filter không hợp lệ và chống truy cập trái quyền.

## Xác minh

- Client: **PASS, 362 tests**; có test controller thực, không chỉ đối chiếu source code.
- Backend: **PASS, 27 tests**, gồm 7 tests PostgreSQL thật; không có test bị skip. Bao phủ count unique, workspace vắng mặt, search literal, lọc/phân trang, freeze đúng cutoff, bản khóa hợp lệ, auth và legacy regression.
- Schema/migration: không thay đổi.
- Browser smoke và kiểm tra hình ảnh desktop/mobile: **SKIPPED**. Công cụ trình duyệt chặn quyền truy cập URL của tab hiện tại; không đi vòng chính sách bằng browser khác hoặc Playwright ngoài công cụ.
- Responsive mới đã kiểm tra cấu trúc/CSS: pipeline 4 cột desktop, 2 cột màn hình <=800px; filter xuống hàng; bảng cuộn ngang trong container min-width:0. Chưa khẳng định hình ảnh hiển thị desktop/mobile đã đạt khi chưa chụp được trình duyệt.

Log kiểm thử nằm trong server/build/local-run/submissions-redesign-*.txt (ignored). Server chạy bản bootJar mới trên localhost:8080 sau khi hoàn tất.
