# Chẩn đoán lỗi bot

Đầu tiên xác định lỗi xảy ra ở **build/test trước nộp**, **Final Validation sau khóa bài** hay **trận thi đấu**. Một test thất bại không tự tạo kết quả thua trong lịch thi đấu; lỗi hạ tầng cũng không được xem như bot tự thua.

## Build failed

- **Triệu chứng:** `BUILD_FAILED`, build output có lỗi compiler hoặc không có artifact.
- **Nguyên nhân:** lỗi cú pháp, thiếu Main, package Java, import thư viện không có, artifact quá lớn hoặc quá thời gian/tài nguyên.
- **Kiểm tra:** mở chi tiết build của đúng version, đối chiếu [file mẫu](examples.md) và [giới hạn](limits.md).
- **Sửa:** sửa source và build bản mới trước hạn. Xem còn version; build lỗi đã tạo version vẫn được tính.

## Malformed JSON / protocol error

- **Triệu chứng:** `PROTOCOL_ERROR`, `BOT_FAILED`, phản hồi không parse được hoặc sequence không khớp.
- **Nguyên nhân:** in log/banner ra stdout, JSON nhiều dòng, type sai, thiếu trường, dùng `turn` thay `sequence`, đọc một lượt rồi thoát.
- **Kiểm tra:** dùng [request JSONL](examples/turn.jsonl), lưu stdout riêng; mỗi input phải có đúng một object `ACTION` và sequence tương ứng.
- **Sửa:** dùng JSON parser/serializer đúng, đọc theo vòng lặp, trả nguyên action đã chọn, debug xuống stderr. [Protocol](protocol.md).

## Timeout / process exited

- **Triệu chứng:** `TIMEOUT`, `TEST_TIMEOUT`, EOF hoặc bot thua khi đến lượt.
- **Nguyên nhân:** tính quá lâu, quên flush/newline, chờ đọc hết stdin mới trả lời, exception hoặc chương trình kết thúc sau một request.
- **Kiểm tra:** gửi nhiều request vào cùng process; đo thuật toán và xác nhận response xuất hiện ngay sau từng dòng. Phân biệt boot đầu với ngân sách mỗi nước.
- **Sửa:** giảm độ sâu/tìm kiếm, giới hạn bộ nhớ lịch sử, flush sau mỗi response và giữ process sống cho tới EOF. Đừng lấy đồng hồ chơi tay làm hạn của bot.

## Illegal action

- **Triệu chứng:** `INVALID_ACTION`, nước bị từ chối hoặc `BOT_FAILED`.
- **Nguyên nhân:** pieceId/from sai, di chuyển ngoài bàn, đi vào quân mình, giả định bot luôn P1, tự PASS/SURRENDER khi runner không cấp.
- **Kiểm tra:** response `action` có thuộc `legalActions` của đúng request không? Tọa độ có bị xoay theo UI không?
- **Sửa:** chọn và trả lại object trong danh sách hiện tại; dùng `player`, `ownerId` và tọa độ server, không dùng snapshot cũ.

## Đã build nhưng không READY

- **Triệu chứng:** còn `BUILT` hoặc `TEST_FAILED`, nút nộp không cho chọn version.
- **Nguyên nhân:** chưa đạt cả public và đấu mẫu, còn hidden test bắt buộc hoặc source đã thay đổi từ lần build.
- **Kiểm tra:** đọc từng kết quả của đúng phiên bản; kiểm tra cờ/policy của giải.
- **Sửa:** build lại nếu source đã đổi, chạy **Kiểm tra toàn bộ điều kiện giải** khi được phép. READY không đồng nghĩa đã nộp; cần kiểm tra bài nộp sau đó.

## Hidden test fail

- **Triệu chứng:** `HIDDEN_TESTS_FAILED` hoặc trạng thái hidden chưa đạt.
- **Nguyên nhân:** bot chỉ xử lý được fixture quen thuộc, giả định quân/loại/chủ cố định, gặp trạng thái mới thì sai hoặc vượt hạn.
- **Kiểm tra:** chỉ dùng kết quả tóm tắt được server cung cấp; hệ thống không công khai input/expected action của hidden tests.
- **Sửa:** tổng quát hóa theo board và legal actions. Thử P1/P2, quân đã bị loại, giao chiến thắng/thua/cùng loại và nhiều lượt. Sau khóa bài, làm theo thông báo của ban tổ chức.

## Resource / output limit

- **Triệu chứng:** tiến trình bị dừng, `OUTPUT_LIMIT_EXCEEDED`, `STDERR_LIMIT_EXCEEDED` ở runner tương ứng, hoặc lỗi kiểm thử tài nguyên.
- **Nguyên nhân:** dùng quá RAM/PID/tmp, response quá lớn, log vô hạn hoặc xuất nhiều dòng trước request.
- **Kiểm tra:** đối chiếu [Limits](limits.md), kiểm tra stderr có bị lặp và có tạo process/file không giới hạn không.
- **Sửa:** giảm dữ liệu lưu, thuật toán và log; xóa file tạm trong phạm vi được phép. Không có bước xin thêm mạng hay tự cài dependency trong bot.

## Runner error / lỗi hạ tầng

- **Triệu chứng:** `SANDBOX_UNAVAILABLE`, queue đầy, kiểm thử chính thức `INFRA_ERROR`, hoặc trạng thái trận chờ khôi phục.
- **Nguyên nhân:** Docker/image/storage/worker không sẵn sàng, job gián đoạn hoặc lease hết; chưa đủ bằng chứng kết luận code bot lỗi.
- **Kiểm tra:** người chơi ghi lại giải, version, bước lỗi và thời điểm; người quản lý xem dashboard validation/worker. Không gửi credential, toàn bộ source riêng hay hidden input vào log công khai.
- **Sửa:** liên hệ ban tổ chức; manager có thể retry lỗi hạ tầng trong cửa sổ cho phép. Không nộp nhiều lần hoặc thay artifact đã khóa để cố chạy lại.

## Workspace conflict / hết giờ

- **Triệu chứng:** `WORKSPACE_CONFLICT`, `WORKSPACE_BUSY`, `CODING_NOT_OPEN`, `SUBMISSION_LOCKED`.
- **Nguyên nhân:** revision cũ, một job của user đang chạy, cửa sổ coding đóng hoặc bài đã khóa.
- **Kiểm tra:** đồng bộ trạng thái server, kiểm tra tab khác và deadline.
- **Sửa:** chờ job hoàn tất, dùng **So sánh với server** để xử lý xung đột; nếu hết giờ chỉ tải/đọc bản còn lại. Chỉnh đồng hồ hoặc gọi API trực tiếp không cấp lại quyền.

## Nguồn đối chiếu

- [BotProtocol](../../server/src/main/java/com/rpsbattle/bot/BotProtocol.java), [OnlineMatch](../../server/src/main/java/com/rpsbattle/online/OnlineMatch.java), [WorkspaceSandbox](../../server/src/main/java/com/rpsbattle/tournament/coding/WorkspaceSandbox.java).
- [BotTestService](../../server/src/main/java/com/rpsbattle/codevalidation/BotTestService.java), [FinalValidationWorker](../../server/src/main/java/com/rpsbattle/tournament/coding/FinalValidationWorker.java), [workspace UI](../../client/js/tournament/code-workspace.js).
