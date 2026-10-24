# Viết và Submit bot

Mở trang lập trình từ [luồng tham gia giải](../player/tournament-flow.md). Phần đầu giải giữ tên giải, số người, đồng hồ và tiến trình. Bên dưới là editor và nút **Submit**.

## Nộp bài

1. Chọn ngôn ngữ và viết code. Nếu giải cho dùng bot có sẵn, đổi **Cách nộp** và chọn bot của bạn.
2. Bấm **Submit** ngay dưới editor. Thao tác này xác nhận tuân thủ luật giải; không phải tích lại checkbox hoặc chọn phiên bản.
3. Hệ thống chụp source/ngôn ngữ tại lúc bấm, build, chạy các test cases bắt buộc và ghi nhận bản dự thi nếu đạt. Xem kết quả ngay dưới nút.

Không cần bấm Lưu, Build hay Kiểm thử riêng. Source của lần nộp không thay đổi khi bạn sửa tiếp editor. Nháp tự lưu khi chính sách cho phép; trạng thái nằm cạnh ngôn ngữ. Tự lưu không phải Submit.

## Kết quả chấm

- **AC — Accepted:** mọi kiểm thử bắt buộc đạt và backend đã chốt bản dự thi thành công. Build thành công riêng chưa phải AC.
- **CE — Compilation Error:** không build được; đọc lỗi compiler bên dưới.
- **WA — Wrong Answer:** sai giao thức, sequence, nước đi hoặc test case; xem test bị lỗi và thông báo.
- **TLE / MLE / RE:** quá thời gian, vượt bộ nhớ hoặc lỗi khi chạy. Xem chi tiết để sửa.
- **ERROR:** không thể hoàn tất vì máy chấm, hạn nộp, quyền hoặc giao dịch. Đây không phải kết luận AC/WA.

Test cases có trạng thái và thời gian chạy khi server cung cấp. Kiểm thử ẩn chỉ hiện đạt/không đạt, không lộ input. Lần nộp thất bại không thay bản dự thi thành công trước; giao diện nhắc ngắn gọn bản cũ vẫn được dùng.

**Lịch sử nộp** và **Hướng dẫn viết bot** được thu gọn dưới kết quả. Lịch sử có liên kết xem source bất biến của từng phiên bản và lỗi đã ghi nhận.

## Mất mạng, tải lại và bản nháp

Mỗi lần gửi có mã yêu cầu bền vững. Server và giao diện chặn gửi trùng khi một lần còn đang xử lý. Tải lại đọc tiến trình từ database, không tạo job mới.

Nếu mất phản hồi, **Kiểm tra lần gửi** dùng lại cùng mã và snapshot đã chụp, kể cả khi editor đã sửa tiếp. Nội dung chưa đồng bộ được giữ trong tab khi bộ nhớ trình duyệt còn khả dụng. Khi có hai bản nháp khác nhau, các thao tác giải quyết xung đột chỉ xuất hiện lúc cần; không ghi đè source đang gõ.

Worker dùng lease/token và số lần thử hữu hạn để khôi phục job bị gián đoạn. Mở lại giờ coding do ban tổ chức sẽ kết thúc lần nộp trong cửa cũ và cho biết cần nộp lại.

## Hạn nộp và giới hạn

Lần nộp phải đạt và được chốt trước khi cửa nộp đóng. Bắt đầu trước hạn nhưng xử lý xong sau hạn không được nhận; bản cũ vẫn giữ theo luật giải. Giới hạn tài nguyên và tổng phiên bản vẫn tính cả lần build thất bại.

Quyền nộp lại, khóa ngay khi nộp, hạn riêng và cửa nộp khẩn cấp do backend kiểm tra. Đấu mẫu không thành điều kiện của Submit. Kiểm thử chính thức sau coding vẫn chạy theo [quy trình của giải](../tournament/validation.md).

Xem [giờ coding](../tournament/coding-phase.md), [giới hạn](limits.md), [xử lý lỗi](troubleshooting.md).

## Nguồn đối chiếu

- [Giao diện](../../client/tournament-code.html), [controller](../../client/js/tournament/code-workspace.js), [verdict](../../client/js/tournament/submission-attempt.js).
- [SubmissionAttemptService](../../server/src/main/java/com/rpsbattle/tournament/coding/SubmissionAttemptService.java), [SubmissionAttemptStore](../../server/src/main/java/com/rpsbattle/tournament/coding/SubmissionAttemptStore.java), [CodingWorker](../../server/src/main/java/com/rpsbattle/tournament/coding/CodingWorker.java), [SubmissionStore](../../server/src/main/java/com/rpsbattle/tournament/coding/SubmissionStore.java).
- [API](../technical/api.md).
