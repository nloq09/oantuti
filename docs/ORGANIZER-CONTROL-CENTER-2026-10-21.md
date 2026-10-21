# Organizer Tournament Control Center — 21/10/2026

## Kết quả triển khai

Trang `/tournament.html?id=...` trở thành Control Center dành cho chủ giải. Tái sử dụng state, API thống kê và các trang nghiệp vụ đang có; không viết lại nghiệp vụ giải, không sửa GameEngine và không mở rộng Player UI.

- Sidebar chia bốn nhóm: Tổng quan, Người chơi, Thi đấu, Quản trị. Round Robin/Swiss có mục Hạt giống, không tạo bracket loại trực tiếp giả.
- Header có tên giải, thể thức, số suất/đơn được duyệt, phase, hạn hiện tại, giờ bắt đầu và tình trạng của riêng giải. Không khẳng định toàn bộ hạ tầng khỏe khi chưa có dữ liệu.
- Tổng quan chỉ gồm Current Phase, Next Action, Needs Attention, thống kê theo phase, lịch và tối đa tám hoạt động gần nhất. Tên và lý do được render bằng text node; không dump audit JSON.
- Cảnh báo có nhãn mức độ Thông tin/Cần chú ý/Khẩn cấp và link đúng trang/filter. Không có cảnh báo thì chỉ còn một dòng nhỏ. Giải đã kết thúc nhưng còn review vẫn dẫn sang Kết quả; cảnh báo lỗi lịch sử không biến thành tác vụ mới của giải đã hủy.
- Nút lifecycle chờ snapshot thống kê khớp ID/revision. Dữ liệu lỗi hoặc cũ không mở thêm thao tác. Thay đổi phase theo giờ server được cập nhật dù revision không đổi; callback cũ không được phép thực hiện một next action đã mất hiệu lực.
- Các trang tiếp tục dùng HTTP refresh qua invalidation WebSocket/polling hiện có, có xử lý loading, empty, error, stale và thay đổi quyền. Không retry mutation tự động.

## Chức năng đã di chuyển

- **Cài đặt → Đăng ký:** mở/chốt đăng ký, link mời và roster thủ công. Giải timeline dùng trang duyệt đăng ký riêng, giữ approve/reject/waitlist theo quyền và thời hạn backend.
- **Cài đặt → Bài nộp:** workspace, phiên bản, READY, hàng đợi build/test; thông tin ngắn về việc khóa/nộp bài khi hết giờ.
- **Cài đặt → Kiểm thử:** kiểm thử chính thức, bài lỗi, cấp nộp khẩn cấp và xử lý suất của giải timeline. Chỉ hiện retry cho INFRA_ERROR có `canRetry` và cửa sổ backend còn mở; bot FAILED không có nút retry chung.
- **Danh sách chung → Check-in riêng:** dashboard eligible/checked-in/missing/disqualified, filter thiếu check-in và thao tác chốt check-in thủ công. Không check-in thay người khác. Đơn timeline chưa có roster hợp lệ không xuất hiện như một suất đã đủ điều kiện.
- **Cài đặt → Trận đấu:** pause/resume/mở vòng, cấu hình tự chuyển vòng, review trận và retry lỗi được phép. Match Monitor giữ filter, phân trang, execution/lease/attempt history và replay. Thời gian gameplay chỉ hiển thị khi có tín hiệu bắt đầu, không tính từ lúc worker nhận lease.
- **Cài đặt → Nhánh đấu/Hạt giống:** toàn bộ form seeding; sau khi khóa chỉ còn trạng thái đọc.
- **Cài đặt → Kết quả:** selector trận đang review riêng, giữ/sửa kết quả, xác nhận hoàn tất, bảng điểm, báo cáo và export. Không dùng chung selector với tab Trận đấu. Các rào cản downstream/series/group/Swiss vẫn được giữ.
- **Cài đặt:** chỉ giữ thông tin BO/sức chứa, luật/concurrency/tiebreak, lịch thủ công hoặc timeline, policy và luật đã khóa dạng dễ đọc. BO được hiển thị theo cấu hình tạo giải; không bịa API sửa BO giữa giải.
- **Cuối Cài đặt:** Vùng nguy hiểm. Hủy giải có dialog và lý do bắt buộc. Sửa kết quả, hoàn tất và xử lý suất cũng giữ xác nhận/lý do tương ứng.

## Module/component mới

- `organizer-actions.js`: model next action và khả năng thao tác từ snapshot hiện tại, giữ nguyên quyết định cuối cùng ở backend.
- `organizer-overview.js`: Current Phase, Next Action, metrics, timeline, recent activity.
- `organizer-controls.js`: lifecycle controls và danh sách check-in riêng.
- `organizer-decision-dialog.js`: dialog lý do có giới hạn độ dài, chống gửi lặp, giữ mở khi yêu cầu thất bại.
- `organizer-discipline.js`: xử lý các suất hợp lệ khi giải tạm dừng, không xử lý suất có trận đang chạy.
- `organizer-validation-actions.js`: chỉ dẫn retry/emergency dựa trên capability thật của server.
- `organizer-manual-timing.js`: cấu hình ngày giờ thủ công qua API có sẵn, dùng chung validator với wizard.
- `deadline-info.js`: info card khóa/nộp bài ở Bài nộp và Kiểm thử.
- `organizer-control-center.css`: theme chỉ áp dụng trong `.organizer-page` và dialog riêng; giảm border/shadow, phân cấp nút và sidebar nhóm.

## API và bảo toàn nghiệp vụ

Không thêm endpoint mới, không thêm API giả, không thay DB schema hoặc luật/game engine.

Endpoint hiện có `POST /api/tournaments/{id}/cancel` được mở rộng tương thích ngược để nhận `{revision, reason}`. UI mới luôn gửi hai trường này. Nếu có metadata, server kiểm tra chủ giải/admin, revision hiện tại và lý do; lý do được ghi vào audit trong transaction đang có. Request legacy rỗng vẫn hoạt động như trước. Các API quản lý khác giữ nguyên route/payload và CSRF/revision/reason đang dùng.

Worker summary chỉ lấy dữ liệu phục vụ giải từ `organizer-metrics`. Không thêm danh sách worker toàn hệ thống, token đăng ký, credential hoặc nút approve/revoke worker vào Organizer. Slot dành riêng chưa xác định được hiển thị `—`.

## Kiểm chứng

- **315/315 client tests PASS**, 0 FAIL, 0 SKIPPED. Có unit test sidebar/overview/next action/attention, từng dashboard, settings, danger zone, phase actions và Match Monitor.
- Integration test dùng HTML và controller thật trong DOM fixture: mount các khối đã chuyển, đổi tab, mở đăng ký, đổi phase timeline mà không đổi revision, chặn start trước giờ và gửi hủy có revision/reason. Đây không phải browser hoặc kiểm thử layout thực.
- **15/15 backend tests PASS**, 0 FAIL/ERROR/SKIPPED: `OrganizerMetricsApiTest`, `TournamentAdministrationApiTest`, `TournamentLifecycleApiTest`, `TournamentOwnershipTest`. Không chạy lại toàn bộ bộ backend/Docker trong thay đổi UI này.
- `:bootJar` PASS. Server đã được chạy lại trên port 8080.
- Native HTTP/API với hai tài khoản thật: bốn trang phục vụ stylesheet mới; outsider bị từ chối metrics/hủy; đăng ký → duyệt → check-in → READY có số liệu khớp; hủy từ revision cũ/lý do trống bị từ chối; hủy đúng được lưu lý do vào audit; metrics timeline dùng phase server. Hai giải tạo cho smoke đã được hủy, không thay đổi giải cũ của người dùng.

### Layout và screenshot

- Đã kiểm tra cấu trúc DOM của `tournament.html` và link stylesheet trên cả bốn trang Organizer; không trùng ID trong HTML gốc, không còn operation nằm trong Settings.
- CSS có desktop sidebar 212px, tablet 180px, menu gấp dưới 800px, card xếp dọc, lưới nhỏ và execution facts một cột dưới 480px; bảng có vùng cuộn giới hạn. Thay đổi CSS được giới hạn ở Organizer.
- **Chưa có screenshot hoặc browser smoke:** đã gọi công cụ browser và nhận `Local browser use is disabled in Work settings`. Không coi kiểm thử DOM/CSS là bằng chứng đã xem layout ở các kích thước thật. Cần kiểm tra trực quan desktop/tablet/mobile khi browser được bật lại.

Log và kết quả native giữ trong `server/build/local-run/organizer-redesign-*.txt` và `organizer-control-center-results.json`, không commit dữ liệu phiên đăng nhập hoặc fixture tài khoản local.

## Danh sách file

Danh sách dưới đây là file sửa/tạo cho lần triển khai này; các script chạy thử và credential local không thuộc commit.

### File đã sửa

- README.md
- client/js/tournament/application-review.js
- client/js/tournament/deadline-policy-editor.js
- client/js/tournament/final-validation-dashboard.js
- client/js/tournament/match-monitor.js
- client/js/tournament/organizer-attention.js
- client/js/tournament/organizer-metrics.js
- client/js/tournament/organizer-shell-model.js
- client/js/tournament/organizer-shell.js
- client/js/tournament/submission-dashboard.js
- client/js/tournament/tournament-api.js
- client/js/tournament/tournament-dashboard.js
- client/js/tournament/tournament-detail.js
- client/tests/organizer-attention.test.js
- client/tests/organizer-metrics.test.js
- client/tests/organizer-shell.test.js
- client/tests/tournament-match-monitor.test.js
- client/tests/tournament-repairs.test.js
- client/tournament-applications.html
- client/tournament-final-validation.html
- client/tournament-submissions.html
- client/tournament.html
- server/src/main/java/com/rpsbattle/api/controller/TournamentController.java
- server/src/main/java/com/rpsbattle/tournament/OwnedTournamentService.java
- server/src/test/java/com/rpsbattle/tournament/TournamentLifecycleApiTest.java

### File mới

- client/css/organizer-control-center.css
- client/js/tournament/deadline-info.js
- client/js/tournament/organizer-actions.js
- client/js/tournament/organizer-controls.js
- client/js/tournament/organizer-decision-dialog.js
- client/js/tournament/organizer-discipline.js
- client/js/tournament/organizer-manual-timing.js
- client/js/tournament/organizer-overview.js
- client/js/tournament/organizer-validation-actions.js
- client/tests/checkin-dashboard.test.js
- client/tests/danger-zone.test.js
- client/tests/helpers/organizer-dom.js
- client/tests/helpers/organizer-fixtures.js
- client/tests/organizer-controller-integration.test.js
- client/tests/organizer-next-action.test.js
- client/tests/organizer-overview.test.js
- client/tests/organizer-sidebar.test.js
- client/tests/phase-actions.test.js
- client/tests/registration-dashboard.test.js
- client/tests/settings-page.test.js
- client/tests/submission-dashboard.test.js
- client/tests/validation-dashboard.test.js
- docs/ORGANIZER-CONTROL-CENTER-2026-10-21.md
