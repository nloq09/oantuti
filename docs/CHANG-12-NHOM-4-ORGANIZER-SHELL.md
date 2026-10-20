# Chặng 12 — Nhóm 4: Khung quản lý giải

## Phạm vi hoàn thành

Trang `/tournament.html?id=...` dùng khung quản lý khi tài khoản là chủ giải hoặc có quyền quản lý hiện có. Khách/người khác không nhận sidebar chủ giải; với giải timeline tiếp tục chuyển sang trang người chơi. Backend vẫn kiểm tra quyền cho từng API, không dựa vào trạng thái sidebar.

Header chung hiển thị tên, thể thức, giai đoạn, roster/capacity, tình trạng vận hành được quan sát và hạn giờ hiện tại. Phase/deadline/serverNow lấy từ GET timeline, không tính giai đoạn dựa vào đồng hồ trình duyệt. Countdown dùng thời gian server và thời gian trôi qua monotonic. Chỉ nhận schedule cùng revision với summary; lỗi cập nhật có thông báo dữ liệu có thể cũ.

Không gọi 0 suất thi đấu là 0 đơn được duyệt: header dùng số participant trong roster hiện có, chưa thay bằng chỉ số approved toàn bộ ứng viên. Có review/trận lỗi hoặc RECOVERY_REQUIRED thì thông báo cần kiểm tra. Nếu chưa có read-model hạ tầng, hiển thị “Hạ tầng: chưa có dữ liệu tổng hợp”; không giả định HEALTHY hoặc cảnh báo worker không liên quan giải.

Desktop dùng sidebar; breakpoint 800px đổi sang menu details thu gọn. Mỗi mục là link có thể mở tab mới, có aria-current và focus theo theme. Menu có:

- Tổng quan.
- Đăng ký: trang duyệt ứng viên đối với timeline, tab danh sách hiện có đối với giải thủ công.
- Bài nộp, Kiểm thử chính thức: tới các dashboard đã có; ẩn mục không phù hợp với policy/luồng thủ công.
- Check-in: roster và đơn hiện có. Chưa thêm metrics/check-in dashboard mới.
- Trận đấu, Nhánh đấu nếu thể thức hỗ trợ, Kết quả (bảng xếp hạng hiện có), Replay.
- Audit riêng, tách khỏi form cài đặt.
- Cài đặt và điều khiển: giữ luật, hạt giống, chính sách deadline, thao tác có reason/revision/audit.

Các mục nội bộ dùng `view=overview|check-in|matches|bracket|results|replays|audit|settings`; tải lại link sẽ mở đúng mục. Giá trị không hỗ trợ trở về tổng quan. Gợi ý mục theo phase không tự chuyển trang và không giành tab đang làm việc. Cập nhật header không thay form hoặc xóa nội dung đang nhập. Menu thu gọn không bị đóng bởi mỗi chu kỳ refresh.

Các trang duyệt đăng ký, bài nộp và final validation của chủ giải cũng dùng chung header/sidebar. Các trang `/admin/` tiếp tục dùng console quản trị riêng. Nội dung dashboard hiện có vẫn chạy trong vùng content, không bị xóa khi shell làm mới.

Giải timeline ẩn các nút chốt đăng ký/check-in và bắt đầu thủ công của luồng cũ, do scheduler và cửa sổ giờ server điều khiển. Mở đăng ký cho DRAFT và hủy giải tiếp tục dùng quyền/API sẵn có.

## API / DTO

Không thêm hoặc thay API/DTO/migration. Tái sử dụng summary và GET `/api/tournaments/{id}/timeline` cho shell. Header làm mới mỗi 5 giây, timer mỗi giây; dừng khi pagehide và phục hồi khi BFCache pageshow. Dashboard chính tiếp tục nhận stream hiện có. Không gửi thêm mutation chỉ để hiển thị layout.

## File thay đổi

Mới:

- client/js/tournament/organizer-shell-model.js
- client/js/tournament/organizer-shell.js
- client/tests/organizer-shell.test.js
- Tài liệu này.

Sửa:

- client/tournament.html
- client/tournament-final-validation.html
- client/js/tournament/tournament-detail.js
- client/js/tournament/application-review.js
- client/js/tournament/submission-dashboard.js
- client/js/tournament/final-validation-dashboard.js
- client/css/tournament.css
- README.md

Không sửa GameEngine, RuleSet, dữ liệu migration hoặc phân quyền server.

## Kiểm chứng ngày 20/10/2026

- PASS: 159 client tests. Sáu test mới kiểm tra owner/admin/stranger, route và URL encoding, điều hướng thủ công/human-only, phase guidance, health không bịa dữ liệu, header theo giờ server và roster thật.
- PASS: 11 backend tests: TournamentOwnershipTest (2), TournamentAdministrationApiTest (2), TournamentTimelineApiTest (3), TimelineRegistrationApiTest (4). Quyền session/revision và các chặn cửa sổ timeline giữ nguyên.
- PASS: bootJar cuối cùng.
- PASS: native server/PostgreSQL: đọc phase/deadline/revision của giải mẫu; chủ giải vào được participant-applications, submissions, coding-jobs, final-validations; tài khoản khác nhận 403 ở cả bốn endpoint. Các trang/module mới trả HTTP 200.
- PASS: browser desktop: tổng quan/sidebar, chuyển cài đặt, mở đăng ký giải mẫu từ DRAFT sang UPCOMING đúng giờ server, không có nút chốt/bắt đầu cũ. Nội dung reason đang gõ giữ qua refresh. Duyệt đơn, bài nộp, kiểm thử và link audit đều giữ shell. Đã xem screenshot bố cục và thu gọn header.
- SKIPPED: visual mobile vì browser capability không hỗ trợ resize/emulation. Có CSS một cột/menu details và xử lý media-query; chưa xác minh thiết bị thật.
- SKIPPED: thao tác review/override trên trận thật và Docker execution vì nhóm này không đổi nghiệp vụ trận, worker hoặc sandbox; không tạo kết quả giả để thử sidebar.

Lệnh backend: `:test --tests '*TournamentOwnershipTest' --tests '*TournamentAdministrationApiTest' --tests '*TournamentTimelineApiTest' --tests '*TimelineRegistrationApiTest' :bootJar`. Client: `node --test client/tests/*.test.js`. Fixture và smoke script chỉ nằm trong thư mục build bỏ qua Git.

## Giới hạn và phần tiếp theo

Nhóm 5 mới bổ sung metrics đầy đủ theo từng phase; nhóm 6 làm Needs Attention và deep links/filter; nhóm 7 làm remote execution read-model. Các số approved/workspace/worker health chưa có aggregate authoritative không được thay bằng số suy đoán. Kết quả hiện dùng ranking/champion/replay sẵn có, chưa thêm quy trình publish. Refresh/Hub/notification cleanup toàn diện thuộc nhóm sau. Không đánh dấu hoàn thành toàn Chặng 12.
