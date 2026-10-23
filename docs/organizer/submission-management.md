# Theo dõi bài nộp

[Mục lục](../README.md) · [Đăng ký](registration-management.md) · [Kiểm thử chính thức](validation-management.md)

Mở **Bài nộp** trong giải, tại `tournament-submissions.html?id=…`. Với giải timeline, màn hình ưu tiên **người tham gia**, bao gồm cả người chưa mở workspace; không chỉ đếm những bản build đã xuất hiện.

## Trong giờ coding

1. Kiểm tra phase và giờ kết thúc ở header. Đồng hồ server quyết định cửa sổ chỉnh sửa.
2. Xem pipeline: ai chưa có workspace/bản build, ai đang có job, ai có bản READY. Lọc theo nhóm cần xử lý và tìm tài khoản cụ thể.
3. Mở thông tin phiên bản để xem ngôn ngữ, trạng thái và kết quả kiểm thử được phép đọc. Nhiều phiên bản của một người không làm tăng số người tham gia.
4. Khi có lỗi diện rộng, kiểm tra queue/Docker/storage ở [hệ thống](../admin/system-operations.md) trước khi kết luận tất cả bot sai.

**Build thành công chưa phải READY.** Bản phải đáp ứng những bước mà policy yêu cầu. Một người có READY cũng chưa đồng nghĩa bài đó đã được chọn để khóa; deadline policy quyết định dùng bản đã submit hay tự chọn bản READY hợp lệ.

## Tại deadline

Server đóng save/build/test/submit, chọn bài theo policy và ghi freeze. Job đang chạy dở ở thời điểm đóng không được biến thành READY đúng hạn chỉ vì hoàn tất sau đó. Khung bài khóa và lý do không có bài hợp lệ phản ánh quyết định server; tải lại dữ liệu nếu vừa đi qua deadline.

Đối chiếu ba thứ riêng biệt: **tiến độ workspace**, **bản được chọn/khóa**, **kết quả Final Validation**. Người được duyệt đăng ký nhưng không có bản hợp lệ vẫn có thể mất quyền thi đấu theo policy.

## Xử lý bài cần can thiệp

Giải timeline dùng **Kiểm thử chính thức** để retry lỗi hạ tầng hoặc mở nộp khẩn cấp khi đủ điều kiện. Không dùng thao tác retest/allow-resubmit của dashboard legacy để mở lại bài đã freeze; server trả `USE_FINAL_VALIDATION_ACTIONS`.

Các thao tác quản trị hợp lệ cần revision hiện tại, quyền manager và lý do. Gia hạn coding là thay lịch có tác động tới các mốc sau; không phải nút đổi nhãn FAILED thành READY. Sau thay lịch, xem lại freeze/validation hiện tại thay vì chọn nhầm bản lịch sử.

Trang có phân trang phía server. Nếu lọc không thấy ai, bỏ bộ lọc và xem tổng số; đừng cho rằng dữ liệu đã bị xóa. Xem [Build/Test/Submit](../bot/build-test-submit.md) để hướng dẫn người chơi đúng bước.

## Nguồn đối chiếu

- [API bài nộp](../../server/src/main/java/com/rpsbattle/api/controller/TournamentSubmissionController.java), [service](../../server/src/main/java/com/rpsbattle/tournament/coding/TournamentSubmissionService.java).
- [Read model theo người](../../server/src/main/java/com/rpsbattle/tournament/coding/SubmissionDashboardStore.java), [renderer](../../client/js/tournament/submission-view.js).
- [Freeze và phiên bản](../../server/src/main/java/com/rpsbattle/tournament/coding/SubmissionStore.java).
