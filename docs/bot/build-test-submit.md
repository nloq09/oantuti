# Build, kiểm thử và nộp bot

Đầu tiên xem [Luồng tham gia giải](../player/tournament-flow.md). Hướng dẫn này dành cho workspace `/tournament-code.html?id=...`; không đồng nhất bot đã upload trong **Bot của tôi** với bài đã nộp vào một giải.

## Chọn cách làm bài

Chính sách giải có ba mode: `PRE_UPLOADED_ONLY`, `CODE_IN_PLATFORM_ONLY`, `BOTH`. UI chỉ hiện những lựa chọn được giải cho phép:

- **Viết bot trên hệ thống:** chọn Python/C++/Java, soạn source trong workspace riêng.
- **Dùng bot đã có:** chọn bot thuộc tài khoản rồi **Chụp phiên bản và kiểm thử bot này**. Việc nhập tạo một phiên bản riêng và xếp build; thay bot gốc sau đó không tự đổi snapshot của giải. JAR được dùng qua lựa chọn này khi Java được phép, không sửa JAR trong editor.

Với giải có timeline, chỉ người `APPROVED` mới truy cập được workspace từ giờ coding. Server kiểm tra quyền ở từng thao tác, không dựa vào đồng hồ hoặc trạng thái nút trên máy bạn.

## Các bước trong workspace

1. **Lưu nháp** hoặc Ctrl/Cmd+S nếu giải cho lưu draft. Xác nhận thông báo lưu, không chỉ nhìn nội dung editor.
2. **Build** để tạo phiên bản và artifact. Mỗi lần build tạo một version; build lỗi vẫn tiêu tốn version đã tạo.
3. **Kiểm thử công khai:** kiểm tra giao thức, sequence và nước đi bằng codec/engine thật. Có thể xem dữ liệu public tests khi giải cho phép.
4. **Đấu mẫu:** chạy trận với bot tham chiếu. Cần hoàn tất hợp lệ, không bắt buộc thắng.
5. **Kiểm tra toàn bộ điều kiện giải:** chạy public, đấu mẫu và hidden tests khi chính sách yêu cầu. Đây là kiểm tra trước nộp, chưa phải Final Validation sau khóa bài.
6. Chờ phiên bản thành **READY**. Chọn phiên bản, đồng ý luật và bấm **Nộp bot**.
7. Xem lại trạng thái/phiên bản server đã nhận. Khi cần nộp lại, làm trước hạn và chỉ khi chính sách cho phép.

## Trạng thái thật

| Trạng thái | Ý nghĩa | Việc tiếp theo |
| --- | --- | --- |
| `DRAFT` | Source đang sửa, chưa có bản build tương ứng | Lưu/build |
| `BUILDING` | Job build đang chờ hoặc xử lý | Chờ kết quả; không gửi job trùng |
| `BUILD_FAILED` | Không tạo được artifact hợp lệ | Đọc build output, sửa và build bản mới |
| `BUILT` | Build thành công, điều kiện kiểm thử chưa đầy đủ | Chạy public/đấu mẫu/điều kiện giải |
| `TESTING` | Đang kiểm thử phiên bản | Chờ server |
| `TEST_FAILED` | Ít nhất một kiểm thử chưa đạt | Đọc kết quả; sửa/build lại nếu cần |
| `READY` | Đạt public, đấu mẫu và hidden nếu yêu cầu | Chọn bản và nộp |
| `SUBMITTED` | Đã có bài nộp, có thể còn thay được theo policy | Kiểm tra phiên bản và deadline |
| `LOCKED` | Bài dùng snapshot cố định | Chỉ đọc, theo dõi Final Validation |
| `REJECTED` / `DISQUALIFIED` | Bài không được chấp nhận hoặc bị loại | Xem thông báo/quyền được server cấp |

Đây là trạng thái của workspace/version/bài nộp ở những lớp tương ứng, không phải một enum chung của tất cả dữ liệu. Build PASS không tự chuyển thành SUBMITTED; READY không tự bảo đảm được thi đấu.

## Lưu, xung đột và mất mạng

Sửa source làm bản đang hiển thị quay về draft; cần build và kiểm thử lại nội dung mới. Đổi ngôn ngữ không xóa giới hạn tổng version theo tài khoản trong giải.

Nếu revision xung đột, dùng **So sánh với server**, giữ bản local/server hoặc tải cả hai. Không mở nhiều tab rồi ghi đè mù. Mất mạng có thể giữ bản tạm trong tab; khi có mạng cần đối chiếu revision. Sau deadline, bản chưa lưu có thể tải về nhưng không được gửi thành bài mới.

## Deadline và nộp lại

`lockOnSubmit` có thể khóa ngay khi nộp. Nếu chưa khóa, `allowResubmitBeforeDeadline` quyết định có thay được bài trước hạn không. Giải theo timeline luôn khóa ở hạn coding; xem [coding phase](../tournament/coding-phase.md) để phân biệt tự nộp và bắt buộc tự nộp.

Giới hạn version, CPU/RAM và hạn job: [Limits](limits.md). Lỗi cụ thể: [Troubleshooting](troubleshooting.md).

## Nguồn đối chiếu

- [Workspace UI](../../client/tournament-code.html), [workspace controller](../../client/js/tournament/code-workspace.js), [submission model](../../client/js/tournament/submission-model.js).
- [CodeWorkspaceService](../../server/src/main/java/com/rpsbattle/tournament/coding/CodeWorkspaceService.java), [WorkspaceStore](../../server/src/main/java/com/rpsbattle/tournament/coding/WorkspaceStore.java), [CodingWorker](../../server/src/main/java/com/rpsbattle/tournament/coding/CodingWorker.java), [CodingQueueStore](../../server/src/main/java/com/rpsbattle/tournament/coding/CodingQueueStore.java).
- [PreuploadedSubmissionService](../../server/src/main/java/com/rpsbattle/tournament/coding/PreuploadedSubmissionService.java), [TournamentSubmissionService](../../server/src/main/java/com/rpsbattle/tournament/coding/TournamentSubmissionService.java).
