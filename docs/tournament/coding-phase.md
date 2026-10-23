# Giờ coding, nộp và khóa bài

Đây là flow của giải bot có timeline. Giải có người tự chơi dùng điều khiển thời gian thủ công; không mặc định có coding workspace. [Đăng ký](registration.md) phải được chấp nhận trước khi có quyền làm bài.

## Các mốc cần theo dõi

- `WAITING_FOR_CODING` / `REGISTRATION_REVIEW`: phần làm bài chưa mở.
- `CODING_OPEN`: người APPROVED được chọn/viết bot và thực hiện thao tác được policy cho phép.
- `SUBMISSION_FROZEN`: hết giờ coding, chỉ đọc; server chốt bài theo từng người.
- `VALIDATING`: xử lý kiểm thử chính thức trên bài khóa đủ điều kiện.

Đồng hồ hiển thị dựa trên giờ server; guard ở server chặn ngay thao tác ngoài hạn. Scheduler xử lý theo tick/batch, nên có thể còn trạng thái **Đang chốt bài** sau khi đồng hồ về 0. Đây không phải gia hạn để gửi thêm source.

## Trong giờ làm bài

Chọn **Viết bot trên hệ thống** hoặc **Dùng bot đã có** theo luật giải. Dùng đúng ngôn ngữ, lưu, build và chạy kiểm thử. [Build/Test/Submit](../bot/build-test-submit.md) giải thích từng nút và version.

READY của version nghĩa đã qua điều kiện trước nộp. Chọn version READY, đồng ý luật và **Nộp bot**; xác nhận server đã ghi nhận phiên bản. Giải có thể khóa ngay khi nộp hoặc cho nộp lại trước deadline.

## Hai chính sách deadline theo timeline

| Policy | Server làm gì lúc hết hạn | Việc người chơi nên làm |
| --- | --- | --- |
| `REQUIRE_EXPLICIT_SUBMIT` | Chỉ chốt bài đã nộp hợp lệ; draft/READY chưa nộp không thay thế bài | Bấm nộp và kiểm tra trạng thái trước hạn |
| `AUTO_SUBMIT_LATEST_READY` | Có thể chọn bản READY mới nhất đủ điều kiện trước hạn | Vẫn kiểm tra bản muốn thi và bài đã nhận; không trông chờ job sát giờ |

Tự nộp chỉ xét dữ kiện READY có trước cutoff, gồm cả thời điểm version được cập nhật. Build trước hạn nhưng test xong sau hạn không thành một READY hợp lệ trước hạn. Bài đã LOCKED không bị tự thay; thay bài SUBMITTED còn phụ thuộc policy nộp lại. Mặc định timeline là AUTO_SUBMIT_LATEST_READY; nhánh giải thủ công dùng `deadlineBehavior` riêng, không đánh đồng hai cấu hình.

## Sau khóa bài

Bản khóa ghi phiên bản, source/artifact hash và quyết định nộp. Nếu không có bài đủ điều kiện, quyết định là `MISSING`; lý do có thể là chưa tự nộp, không có READY trước hạn, tài khoản không còn hoạt động hoặc bài/ artifact không hợp lệ.

Workspace bị chặn sửa/lưu/build/test/nộp lại. Source chưa lưu còn trong tab chỉ dùng để xem/tải. Sửa bot gốc trong **Bot của tôi** không sửa snapshot đang thi. Liên kết xem source phải mở đúng version ở chế độ chỉ đọc, không phải draft mới nhất.

Sau đó theo dõi [kiểm thử chính thức](validation.md). Nếu ban tổ chức cấp lượt nộp khẩn cấp theo policy, bạn sẽ thấy cửa sổ/quyền mới từ server và phải tuân thủ deadline riêng; không tự mở lại bằng cách đổi URL.

> Screenshot: Workspace trong CODING_OPEN và bài bị khóa/đang chốt bài. Chưa bổ sung ảnh trạng thái thật.

## Nguồn đối chiếu

- [TournamentTimeline](../../server/src/main/java/com/rpsbattle/tournament/domain/TournamentTimeline.java), [PhaseResolver](../../server/src/main/java/com/rpsbattle/tournament/TournamentPhaseResolver.java), [TimelineAccessGuard](../../server/src/main/java/com/rpsbattle/tournament/timeline/TimelineAccessGuard.java).
- [CodingDeadlineService](../../server/src/main/java/com/rpsbattle/tournament/coding/CodingDeadlineService.java), [SubmissionStore.freezeAtDeadline](../../server/src/main/java/com/rpsbattle/tournament/coding/SubmissionStore.java), [TournamentSubmissionService](../../server/src/main/java/com/rpsbattle/tournament/coding/TournamentSubmissionService.java).
- [Coding session](../../client/js/tournament/coding-session.js), [frozen submission](../../client/js/tournament/frozen-submission.js), [workspace](../../client/js/tournament/code-workspace.js).
