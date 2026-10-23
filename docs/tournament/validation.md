# Kiểm thử chính thức sau khóa bài

Final Validation chạy trên **phiên bản đã khóa**, khác với public test/đấu mẫu khi đang coding. Mở tab **Kiểm thử** và xem trạng thái server, phiên bản thực sự được chọn cùng các bước kết quả. Không nộp lại source chỉ để làm mới màn hình.

## Pipeline hiện có

| Bước | Nội dung được source xác minh |
| --- | --- |
| `BUILD` | Kiểm tra source hash và artifact đã build; **không compile lại** source khóa |
| `PROTOCOL` | Chạy public fixtures bằng protocol/engine thật |
| `TIMEOUT` | Kết quả hạn phản hồi của quá trình kiểm thử |
| `RESOURCE` | Kết quả chạy trong sandbox; được tổng hợp cùng public tests |
| `LEGAL_ACTION` | Action được engine chấp nhận trong các tình huống kiểm thử |
| `HIDDEN_TESTS` | Bộ tình huống ẩn, không xuất input/expected action cho người chơi |
| `TOURNAMENT_TESTS` | Chạy trận kiểm thử với bot tham chiếu theo bộ kiểm thử hiện có |
| `ARTIFACT_HASH` | Kiểm tra toàn vẹn artifact lần cuối |

Không phải tám bộ benchmark độc lập: TIMEOUT/RESOURCE/LEGAL_ACTION lấy dữ kiện từ các lượt kiểm thử tương ứng. Kiểm thử tài nguyên không phải phép đo để khẳng định máy chạy được bao nhiêu trận đồng thời. Nếu một bước lỗi thì những bước sau có thể chưa chạy; đừng đọc một vài dấu tích thành kết luận PASSED.

## Trạng thái kết quả

- `QUEUED`: đã xếp hàng, chưa có kết quả.
- `RUNNING`: worker kiểm thử đang xử lý.
- `PASSED`: kiểm thử đạt; còn phải được server áp dụng (`applied=true`) để cấp quyền tiếp theo.
- `FAILED`: bài bot chưa đạt điều kiện.
- `INFRA_ERROR`: hạ tầng/worker kiểm thử gián đoạn; cần manager xử lý, không coi như người chơi thua.
- `EMERGENCY_OPEN`: một cửa sổ nộp bổ sung đã được cấp riêng theo policy.

Các ký hiệu step nội bộ PASS/FAIL được UI trình bày thành đạt/không đạt/chờ. Nếu không tải đủ chi tiết, dùng **Thử lại** để lấy lại dữ liệu; lỗi tải trang không có nghĩa kết quả bị đổi.

## Khi bài bot không đạt

Chính sách giải có thể là:

- `DISQUALIFY`: bài thất bại được xử lý theo loại khỏi điều kiện thi đấu.
- `USE_LAST_VALID_VERSION`: trước validation deadline, server có thể chọn version cũ đủ điều kiện và xếp kiểm thử lại. Không phải lấy bản bất kỳ hoặc bỏ qua kiểm thử chính thức. UI có thể cho biết bản dùng khác bản khóa ban đầu; kiểm tra version thực tế.
- `ALLOW_EMERGENCY_RESUBMISSION`: manager có thể cấp một lượt riêng khi bài FAILED đã được áp dụng và còn trong điều kiện cho phép. Deadline mới phải trước validation deadline; được cấp thêm số version theo guard. Không phải mọi người đều tự có quyền nộp lại.

Người chơi xem lý do công khai, bước lỗi và hướng dẫn được cấp. Hidden input, source của người khác hoặc chi tiết credential không được dùng để giải thích lỗi trên trang công khai.

## Khi lỗi hạ tầng

Manager có thể retry một kết quả INFRA_ERROR còn hiệu lực trước `validationDeadline`, có reason/revision/audit. Hết thời hạn hoặc bài đã bị thay thế có thể không còn được retry. Thêm remote worker chạy trận không tự làm build/test/final validation chuyển khỏi Central; sandbox Central vẫn phải sẵn sàng.

Chỉ sau khi kết quả PASSED đã được áp dụng mới xem tiếp điều kiện [check-in](check-in.md). Đến giờ bắt đầu mà chưa đủ validation/danh sách hợp lệ, giải có thể bị chặn bắt đầu thay vì tạo kết quả giả.

> Screenshot: bảng kiểm thử chính thức PASSED đã áp dụng, FAILED và INFRA_ERROR của đúng người chơi. Chưa có bộ ảnh thật theo các trạng thái này.

## Nguồn đối chiếu

- [FinalValidationWorker](../../server/src/main/java/com/rpsbattle/tournament/coding/FinalValidationWorker.java), [FinalValidationService](../../server/src/main/java/com/rpsbattle/tournament/coding/FinalValidationService.java), [FinalValidationStore](../../server/src/main/java/com/rpsbattle/tournament/coding/FinalValidationStore.java).
- [BotTestService](../../server/src/main/java/com/rpsbattle/codevalidation/BotTestService.java), [failure policies](../../server/src/main/java/com/rpsbattle/tournament/domain/TournamentTimeline.java), [player validation UI](../../client/js/tournament/final-validation-view.js).
