# Câu hỏi thường gặp

## Không có bản READY trước hạn thì sao?

Bạn có thể không có bài hợp lệ để khóa và không đủ điều kiện thi đấu. Bản đang build/test hoặc nội dung editor chưa lưu không được tính là READY. Kiểm tra [chính sách deadline](../tournament/coding-phase.md), không chờ tới giây cuối mới nộp.

## Build PASS có đồng nghĩa READY không?

Không. Build thành công tạo `BUILT`. Cần đạt kiểm thử công khai, đấu mẫu và kiểm thử ẩn nếu được bật mới thành `READY`. Sau đó còn bước **Nộp bot** hoặc chính sách tự nộp của giải. [Các trạng thái bài làm](../bot/build-test-submit.md).

## Hết giờ coding có sửa bot được không?

Workspace chuyển sang chỉ đọc và server chặn lưu/build/test/nộp mới. Code chưa lưu còn trong tab có thể tải về để giữ, nhưng không tự vào bài thi. Chỉ ban tổ chức mới có thể cấp một cửa sổ bổ sung khi chính sách và trạng thái cho phép.

## Check-in để làm gì?

Để xác nhận bạn thực sự tham gia. Được duyệt hoặc có bot READY không thay cho check-in khi giải yêu cầu. Hãy xác nhận trong thời hạn server cấp; xem [check-in](../tournament/check-in.md).

## Vì sao tôi vào waitlist?

Bạn chưa có suất chính thức, thường do sức chứa đã được dùng hết. Danh sách chờ hiện theo thứ tự đăng ký. Khi có chỗ, việc chuyển vào còn phụ thuộc chính sách xét duyệt và hạn khóa danh sách; không bảo đảm mọi đơn chờ đều được vào. [Đăng ký](../tournament/registration.md).

## Tôi đã thắng nhưng chưa có trận tiếp theo?

Bạn có thể mới thắng một ván trong BO3/BO5, còn chờ cặp đấu ở nhánh kia, chờ vòng mở hoặc chờ review kết quả. Kiểm tra tỷ số cặp đấu, **Trận của tôi**, lịch và bracket; không tạo phòng thường để thay trận giải.

## Bot timeout thì sao?

Trong phòng online, bot phản hồi quá hạn có thể bị xử thua ván. Trong workspace, timeout là lỗi kiểm thử. `INFRA_ERROR` của kiểm thử chính thức là lỗi hạ tầng cần ban tổ chức xử lý, khác với bot tự tính quá lâu. [Giới hạn](../bot/limits.md), [chẩn đoán lỗi](../bot/troubleshooting.md).

## Bot lỗi protocol thì sao?

Kiểm tra mỗi request có đúng một response JSON trên một dòng, newline và flush; `type` phải là `ACTION`, sequence phải khớp và action phải nằm trong danh sách server cấp. Không ghi debug ra stdout. [Protocol reference](../bot/protocol.md).

## Có đổi bot sau khi khóa bài không?

Bạn không thể tự thay bản đã khóa bằng cách sửa bot gốc hoặc draft. Chỉ có xử lý theo chính sách giải, ví dụ dùng bản hợp lệ trước đó hoặc lượt nộp khẩn cấp do ban tổ chức cấp; không phải quyền đổi bot tùy ý. [Kiểm thử chính thức](../tournament/validation.md).

## Replay ở đâu?

Trong giải, mở **Replay**, **Trận đấu** hoặc **Trận của tôi** khi bản phát lại đã sẵn sàng. Với trận thường, xem chi tiết kết quả/lịch sử và nút **Replay đầy đủ**. Không có link replay khi dữ liệu chưa sẵn sàng; trận remote đang chạy chưa có live board.

## Tôi mất mạng hoặc đóng tab thì sao?

Mất mạng không dừng đồng hồ. Trình duyệt thử kết nối lại bằng thông tin ghế của session hiện tại; mã phòng không phải khóa khôi phục ghế đang có chủ. Xem [mất kết nối/rời phòng](how-to-play.md). Đang chơi mà bấm **Rời phòng** được tính là đầu hàng.

## READY và kiểm thử chính thức PASSED khác nhau thế nào?

READY là điều kiện phiên bản trước khi nộp. Sau deadline, server kiểm thử lại bài đã khóa. Chỉ khi `PASSED` đã được áp dụng thì server mới cấp bước tiếp theo; dấu tích trên màn hình hoặc kết quả đang chờ áp dụng chưa thay cho quyền check-in.

## Nguồn đối chiếu

- [WorkspaceStore](../../server/src/main/java/com/rpsbattle/tournament/coding/WorkspaceStore.java), [SubmissionStore](../../server/src/main/java/com/rpsbattle/tournament/coding/SubmissionStore.java), [FinalValidationService](../../server/src/main/java/com/rpsbattle/tournament/coding/FinalValidationService.java).
- [Next action](../../client/js/tournament/next-action.js), [check-in](../../client/js/tournament/timeline-check-in.js), [game controller](../../client/js/game/game-controller.js).
