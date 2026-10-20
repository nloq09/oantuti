# Chặng 12 — Nhóm 5: Thống kê chủ giải theo giai đoạn

## Chức năng

Khung chủ giải hiển thị nhóm thống kê theo phase server trên Tổng quan. Các mục Đăng ký, Bài nộp, Kiểm thử, Check-in, Trận đấu hiển thị nhóm tương ứng. Các mục Audit/Cài đặt/Kết quả/Replay không bị chèn bảng thống kê không liên quan. Header giữ countdown và deadline theo server.

Số liệu không được lấy từ độ dài trang 25 dòng. API trả scalar projections trong snapshot database chỉ đọc; không tải source/build log/hidden test để đếm. Frontend thay request timeline của shell bằng request metrics, giữ đối chiếu revision và refresh mỗi 5 giây. Snapshot lỗi có cảnh báo dữ liệu cũ; lần đầu lỗi không bịa số 0. Không tự đổi tab hoặc xóa form đang nhập.

## Định nghĩa số liệu

- Đăng ký: tổng đơn gồm cả đã rút, đã duyệt, chờ duyệt, danh sách chờ, từ chối, đã rút. Timeline đếm ứng viên người chơi trong bảng riêng; giải thủ công dùng registrations hiện có.
- Coding: người đã duyệt, số người duy nhất có workspace, số người đã nộp SUBMITTED/LOCKED, số người có current version READY/SUBMITTED/LOCKED, số người current build lỗi và không có bản current READY ở workspace ngôn ngữ khác, chưa tạo workspace. Đây không phải số người online. Bài cũ không còn current version không được tính là READY. Nộp bài khác với READY. Chỉ hiển thị thống kê này cho timeline.
- Validation: tổng, QUEUED, RUNNING, PASSED, FAILED, INFRA_ERROR, nộp khẩn cấp đang mở. Chỉ tính freeze mới nhất của từng người, bỏ history bị TIMELINE_EXTENDED. PASSED thống kê kết quả; đủ check-in còn cần applied.
- Check-in: đủ điều kiện, đã check-in, chưa check-in (gồm lỡ hạn), đã lỡ hạn, bị loại. Với timeline, cần application APPROVED + final PASSED/applied hiện tại + registration hợp lệ; không tính PASS cũ hoặc PASS chưa applied.
- Thi đấu: vòng hiện tại, tổng trận, hoàn thành, đang chạy, chờ, đang/chờ chạy lại, lỗi. Chạy lại là tập con của PENDING/RUNNING có attempt > 1; không cộng lần nữa vào tổng. Đây là trạng thái trận trung tâm, chưa phải chi tiết execution remote.
- Worker: chỉ máy có active assignment tham chiếu job của giải. Heartbeat quá 30 giây hoặc OFFLINE/DISABLED tính offline; ONLINE/BUSY đếm riêng; trạng thái khác còn heartbeat tính degraded. Không đếm máy toàn hệ thống hoặc lịch sử assignment đã kết thúc. Slot khả dụng riêng cho giải=null vì chưa có phân bổ capacity riêng; frontend hiển thị dấu gạch và giải thích, không lấy slot chung làm quota giải. Không trả credential, token, worker identity/name hoặc việc của giải khác.

## Thao tác

Đơn chờ duyệt mở đúng filter PENDING. Đã sửa option HTML bị sai đóng thẻ và giữ filter trong URL khi đổi lựa chọn.

Thẻ validation mở FAILED hoặc INFRA_ERROR bằng filter phía server trước pagination. Chạy lại chỉ thao tác từng bài hạ tầng có lý do/revision theo API cũ, và ẩn khi retryWindowOpen=false. Không thêm retry hàng loạt bot failure. Mặc định Tất cả vẫn giữ dashboard lịch sử cũ.

Gia hạn đăng ký/check-in đưa tới phần điều chỉnh lịch trong Cài đặt, tiếp tục yêu cầu reason/revision và validation server. Không giả lập API đóng cửa đăng ký tách biệt khi backend chưa có. Có giải thích cửa đăng ký đóng theo giờ và chốt roster sau check-in. Gửi nhắc check-in hiển thị disabled vì chưa có API reminder tùy ý.

## API / DTO

Mới: `GET /api/tournaments/{id}/organizer-metrics`, yêu cầu chủ giải/admin; guest 401, tài khoản khác 403. Response no-store, Vary Cookie.

DTO gồm tournamentId, revision, serverNow, phase, phaseEndsAt, registration, coding, validation, checkIn, running, workers. Chỉ số liệu tổng hợp, không có source hoặc bí mật sandbox/worker.

Mở rộng tương thích: `GET /api/tournaments/{id}/final-validations?status=...&page=...`. Các trạng thái hợp lệ QUEUED/RUNNING/PASSED/FAILED/INFRA_ERROR/EMERGENCY_OPEN; filter sai 400. Khi có filter chỉ lấy lượt freeze hiện tại; không filter giữ lịch sử. Dashboard thêm retryWindowOpen do server quyết định. Không đổi mutation API.

Không thêm migration hoặc sửa GameEngine/RuleSet.

## File thay đổi

Mới:

- server/src/main/java/com/rpsbattle/tournament/OrganizerMetricsStore.java
- server/src/main/java/com/rpsbattle/tournament/OrganizerMetricsService.java
- server/src/main/java/com/rpsbattle/api/controller/OrganizerMetricsController.java
- server/src/test/java/com/rpsbattle/tournament/OrganizerMetricsApiTest.java
- client/js/tournament/organizer-metrics.js
- client/tests/organizer-metrics.test.js
- Tài liệu này.

Sửa:

- server/src/main/java/com/rpsbattle/api/controller/FinalValidationController.java
- server/src/main/java/com/rpsbattle/tournament/coding/FinalValidationService.java, FinalValidationStore.java
- client/js/tournament/organizer-shell.js, application-review.js, final-validation-dashboard.js
- client/tournament-applications.html, client/css/tournament.css
- README.md

## Kiểm chứng ngày 20/10/2026

- PASS: 167 client tests, gồm tám test mới cho chọn nhóm theo phase/tab, loading/lỗi đầu tiên, số liệu server, link filter, reminder disabled, slot chưa biết và dữ liệu cũ.
- PASS: 44 backend tests: OrganizerMetricsApiTest (7), FinalValidationApiTest (35), TournamentOwnershipTest (2). Test mới dùng database thật của test context để kiểm tra >25 đơn, nhiều ngôn ngữ/workspace, chỉ current version, validation thay freeze/gia hạn, emergency, applied PASS/check-in, lọc trước pagination, privacy/no-store, guest/stranger, không đếm máy global. Projection test kiểm tra submitted khác READY, lỡ hạn, retry là tập con.
- PASS: bootJar cuối cùng. Một lượt compile test lỗi do compound var declaration; đã sửa cú pháp test rồi chạy lại đầy đủ thành công, không bỏ qua test.
- PASS: PostgreSQL native localhost: tạo giải bot mẫu, duyệt người chơi, mở coding sớm có audit, tạo workspace rồi đọc metrics. Phase CODING_OPEN, approved=1, workspaceUsers=1, notStarted=0, submitted=0, servingWorkers=0, availableSlots=null. Guest 401 và tài khoản khác 403; no-store và filter INFRA_ERROR hoạt động.
- SKIPPED: kiểm tra trực quan desktop/mobile ở lượt hoàn tất vì công cụ browser báo Local browser use is disabled in Work settings. Không dùng phương thức khác để vượt cài đặt này. CSS dùng grid responsive; chưa khẳng định đã kiểm chứng hình ảnh thực tế nhóm 5.
- SKIPPED: benchmark 4.000 người và worker Docker vật lý/assignment nhiều máy. Không tuyên bố đã đo capacity hoặc tốc độ render ở quy mô đó. Không đổi execution/sandbox.

Lệnh: `node --test client/tests/*.test.js`; Gradle `:test --tests '*OrganizerMetricsApiTest' --tests '*FinalValidationApiTest' --tests '*TournamentOwnershipTest' :bootJar`. Fixture/smoke script và tài khoản thử chỉ lưu trong build bỏ qua Git.

## Phần tiếp theo

Nhóm 6 — Needs Attention (quy tắc, severity, deep links/filter và refresh) chưa triển khai. Chi tiết remote queue/assigned/starting/running/worker lease thuộc nhóm 7; không thay số liệu hiện tại bằng suy đoán. Chưa hoàn thành toàn Chặng 12.
