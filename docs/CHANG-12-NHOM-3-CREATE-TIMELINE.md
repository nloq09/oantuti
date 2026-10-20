# Chặng 12 — Nhóm 3: Tạo giải với timeline đầy đủ

## Cách sử dụng

Vào Giải đấu, đăng nhập, điền form Tạo giải đấu theo thứ tự:

1. Tên, thể thức, số ván; cấu hình các giai đoạn nếu là vòng bảng.
2. Người tham gia.
3. Chính sách nộp bot, ngôn ngữ và giới hạn/kiểm thử.
4. Timeline.
5. Sức chứa, số trận đồng thời và check-in.

Mặc định cuộc thi Chỉ bot sử dụng timeline đầy đủ. Có thể bỏ chọn để giữ lịch thủ công. Chế độ có người tự chơi giữ quy trình thủ công vì luồng coding/final validation hiện dành cho cuộc thi bot; form giải thích giới hạn này. Giải theo timeline không thêm sẵn bot của chủ giải vào roster: người chơi đăng ký rồi chọn/viết bot trong giờ coding.

Timeline có mở/đóng đăng ký, mở/đóng coding, bắt đầu/hạn kiểm thử, mở/đóng check-in, bắt đầu giải, kết thúc dự kiến (bắt buộc theo DTO server), công bố dự kiến (không bắt buộc). Có lựa chọn chính sách tự nộp và xử lý validation thất bại. Giờ công bố là metadata, chưa triển khai chức năng tự công bố kết quả.

Ngày giờ nhập theo múi giờ thiết bị, nhãn hiển thị rõ timezone. Gửi lên server dưới dạng ISO UTC. Nút điền lịch mẫu chỉ điền khi được bấm; người dùng kiểm tra trước khi tạo. Preview hiển thị ngày và giờ đầy đủ, không chỉ giờ trong ngày.

## Validation và đồng bộ policy

- Các mốc bắt buộc phải hợp lệ, không chấp nhận ngày bị Date tự chuẩn hóa như 30/2.
- Đăng ký, coding, check-in phải có thời lượng dương.
- Các giai đoạn liên tiếp được bằng nhau ở ranh giới; không được chồng ngược. Công bố không trước kết thúc dự kiến.
- Lỗi được đặt dưới đúng field, có aria-invalid/aria-describedby; gửi form sẽ focus field đầu lỗi.
- Backend vẫn kiểm tra toàn bộ và có quyền từ chối cấu hình.
- Khi dùng timeline, ẩn các giờ nộp trùng trong bot policy; submissionOpenAt=codingOpenAt, submissionDeadline=codingCloseAt, lockAtDeadline=true, deadlineBehavior khớp deadlinePolicy. Check-in được bật bắt buộc trong luồng này.
- Chuyển sang thủ công không bắt nhập timeline. Các nhóm cấu hình ẩn không gây lỗi native validity cho thể thức/chế độ khác.

## Giao dịch và API

Không thay API/DTO/schema. Dùng POST /api/tournaments, POST stage-plan khi cần, rồi PUT /api/tournaments/{id}/timeline với revision mới nhất. Lịch thủ công tiếp tục dùng POST timing; chỉ luồng thủ công mới thêm bot đã chọn.

Các bước cấu hình là những command riêng đã có, không phải một transaction tạo giải nguyên tử. Không tự mở đăng ký hoặc bắt đầu giải. Nếu tạo xong bản nháp nhưng bước cấu hình sau lỗi, giữ liên kết tới bản nháp, thông báo chưa hoàn tất và chặn gửi lại để tránh tạo giải trùng. Không âm thầm chuyển trang như khi thành công. Nếu tạo giải thất bại trước khi có ID thì cho phép sửa/thử lại.

## File thay đổi

Mới:

- client/js/tournament/create-timeline-model.js
- client/js/tournament/create-timeline-editor.js
- client/js/tournament/create-configured-tournament.js
- client/tests/create-timeline.test.js
- Tài liệu này.

Sửa:

- client/tournaments.html
- client/js/tournament/tournament-list.js
- client/js/tournament/bot-policy.js
- client/css/tournament.css
- server/src/test/java/com/rpsbattle/tournament/TournamentTimelineApiTest.java
- README.md

Không sửa GameEngine/RuleSet, không tạo migration.

## Kiểm chứng ngày 20/10/2026

- PASS: 153 client tests. Tám test mới kiểm tra parse ngày/UTC, mốc bắt buộc, mọi ranh giới ngược, ranh giới bằng nhau hợp lệ, đồng bộ policy, revision/PUT, không thêm bot/tự mở giải, dừng khi lỗi cấu hình và giữ luồng thủ công.
- PASS: 23 backend tests: TournamentTimelineTest (14), TournamentTimelineApiTest (3), TournamentBotPolicyTest (6). Test tích hợp mới xác nhận lịch sai bị từ chối không đổi revision, cấu hình đúng vẫn DRAFT, policy khớp coding, roster rỗng.
- PASS: bootJar bản cuối.
- PASS: browser desktop localhost, guest bị khóa form; đăng nhập test, gửi thiếu timeline thấy lỗi dưới field, lịch mẫu cập nhật preview, chọn BOTH và tạo bản nháp thực tế thành công. Chuyển HUMAN_ONLY ẩn timeline, hiện lịch thủ công và trả quyền chọn check-in. Đã xem screenshot bố cục trường ngày giờ.
- PASS: native PostgreSQL/API đọc lại bản nháp vừa tạo qua browser: đủ 11 ngày giờ, submission window/khóa/tự nộp khớp timeline, không có registration/participant hoặc hiệu ứng mở giải. Các module mới phục vụ HTTP 200.
- SKIPPED: visual mobile vì capability trình duyệt không có resize/emulation; đã có breakpoint một cột, chưa kiểm chứng thiết bị thật.
- Không chạy lại toàn bộ Docker trận đấu vì thay đổi này chỉ tạo/cấu hình giải; không đổi sandbox hoặc execution.

Lệnh: `node --test client/tests/*.test.js`; Gradle `:test --tests '*TournamentTimelineApiTest' --tests '*TournamentTimelineTest' --tests '*TournamentBotPolicyTest' :bootJar`. Tài khoản, smoke script và fixture cục bộ không đưa lên Git.

## Bước tiếp theo

Nhóm 4 — Organizer Shell chưa triển khai trong commit này. Không đánh dấu hoàn thành toàn Chặng 12.
