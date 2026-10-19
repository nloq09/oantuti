# Chặng 10 — Nhóm 4: Workspace theo timeline

Giải có timeline dùng codingOpenAt/codingCloseAt làm cửa sổ lập trình duy nhất. Các mốc submissionOpenAt/submissionDeadline legacy không đóng/mở editor, import hoặc submit của giải theo lịch mới. Giải không có timeline vẫn giữ policy và hành vi cũ.

## Quyền và API

`participant-flow` bổ sung codingCloseAt, canAccessWorkspace và canModifyWorkspace. Chỉ application APPROVED mới có quyền. Trước giờ coding hoặc khi DRAFT không đọc source, lịch sử version hay mở workspace; đến đúng codingOpenAt mới tạo/lưu/build/test/import/submit. Từ codingCloseAt trở đi, các mutation trả 409 nhưng vẫn có thể đọc source, kết quả và phiên bản của chính mình. Đọc không tự tạo workspace mới.

TimelineAccessGuard kiểm tra Clock server ở mọi thao tác; UI không thể cấp quyền bằng đổi giờ máy hoặc nhập URL. Sau coding, workspace chưa tồn tại trả WORKSPACE_NOT_FOUND thay vì tạo template mới. Giải pause/cancel/finish không cho mutation; quyền đọc bài của người đã được duyệt được giữ sau giờ mở để tải bài của mình.

## Ba cách nộp

- CODE_IN_PLATFORM_ONLY: vào editor, không có bộ chọn bot có sẵn; server từ chối import.
- PRE_UPLOADED_ONLY: chỉ chọn/chụp bot thuộc tài khoản, build/validation, xem phiên bản và submit. Không hiện source editor hoặc nút build source; server vẫn từ chối tạo/save/build source mới.
- BOTH: hỏi dùng bot đã có hay code trên hệ thống. Chọn cách nào mới mở phần tương ứng. Chuyển cách nộp không ghi đè draft chưa lưu; import tạo version riêng, build code tạo version có provenance PLATFORM_CODED. Version cũ giữ nguyên.

Ngôn ngữ ưu tiên từ query hợp lệ, phiên bản gần nhất hoặc ngôn ngữ dự kiến khi đăng ký. Metadata và artifact vẫn do pipeline chặng 9 quản lý. Việc liên kết application với roster thi đấu cuối cùng, tự freeze/auto-submit và final validation sẽ hoàn thiện ở nhóm 5–6; nhóm này chưa tự khởi động giải.

## Đồng hồ, draft và read-only

Countdown nhận serverNow/codingCloseAt rồi trừ thời gian trôi đơn điệu (performance.now); không dựa vào thay đổi Date.now sau khi mở trang. Server capability được lấy lại mỗi 5 giây. Client có thể tự khóa sớm khi countdown về 0, không tự mở phase tương lai bằng timer. Cảnh báo 30/10/5/1 phút chỉ hiện một lần mỗi mốc; không phát notification nền hoặc ghi số đo giả.

Giữ debounce autosave 3 giây, lưu nháp phía server và bản tạm sessionStorage. Khi autosave đang bay, editor vẫn cho gõ; response cũ không xóa phần gõ tiếp. Offline giữ nội dung tạm. Reconnect sau deadline không tự upload; các nút lưu/build/test/submit/import bị khóa, source đọc/tải vẫn dùng được. Đồng hồ không render lại log test mỗi giây để tránh đóng các mục chi tiết đang xem.

Worker đã nhận job trước giờ đóng có thể chạy xong và cập nhật kết quả; không nhận job mới sau giờ đóng. Job freeze/auto-submit theo codingCloseAt chưa triển khai ở nhóm này. Scheduler deadline legacy bỏ qua giải có timeline để không dùng nhầm hạn cũ khóa bài giữa giờ code; phần xử lý deadline mới thuộc nhóm 5.

## Kiểm chứng

Backend: 53 PASS, 0 FAIL, 0 SKIPPED trong lượt kiểm tra timeline/coding/review/registration và hồi quy workspace/submission. Có case exact open/close, legacy deadline xung đột, đọc bài sau giờ đóng, late save/job/import/submit bị chặn, PRE-only chỉ import và người chưa được duyệt bị từ chối. Client: 94 PASS, gồm countdown/capability/entry choice/warnings. Không có thay đổi schema nên không thêm migration; schema vẫn V25.

Build bootJar thành công. Trình duyệt local đã kiểm tra BOTH không mở editor trước khi chọn; chọn code, gõ và autosave thành công, build và validation Docker thật trả READY; PRE-only không có editor, hộp xác nhận nhập bot của game tạo version/build riêng. Bài đã hết cửa sổ hiển thị source read-only, giữ nút tải source và khóa các mutation. Native E2E CODE/PRE/BOTH đều build, validation và submit thật thành công; sau restart kiểm tra version/artifact hash của bài nộp và từ chối late save trên bài read-only. Những fixture bài nộp/replay legacy vẫn phục hồi bình thường.

Không sửa GameEngine, RuleSet, Match Engine, BotProtocol hoặc DockerBotProcess; không thay giới hạn roster 32 hay một replica. Các kiểm thử Flyway không chạy lại riêng vì không sửa schema hoặc migration ở nhóm này; integration tests vẫn khởi tạo/validate schema V25 qua Spring Boot.

File mới: coding-session.js, coding-session.test.js, TimelineCodingApiTest và tài liệu này. File sửa: TimelineAccessGuard, TimelineRegistrationService (flow), CodeWorkspaceService, PreuploadedSubmissionService, TournamentSubmissionService, code-workspace.js, tournament-code.html, code-workspace.css và README.
