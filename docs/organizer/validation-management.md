# Quản lý kiểm thử chính thức

[Mục lục](../README.md) · [Bài nộp](submission-management.md) · [Vận hành giải](tournament-operation.md)

Mở **Kiểm thử** → **Kiểm thử chính thức**, tại `tournament-final-validation.html?id=…`. Quy trình kiểm tra bản đã freeze, không tự build lại source sau hạn. Chi tiết các bước ở [Final Validation](../tournament/validation.md).

## Đọc tiến độ và kết quả

**Tiến độ** gồm tổng số, đang chờ, đang chạy và đã hoàn tất. **Kết quả** gồm đạt, thất bại và lỗi hạ tầng. Hoàn tất bao gồm các kết quả terminal lỗi hạ tầng; không có nghĩa mọi người đều đủ điều kiện. Cũng cần chờ server **áp dụng kết quả**, không chỉ thấy job PASSED.

1. Xem hướng dẫn hành động tiếp theo ở đầu dashboard.
2. Lọc FAILED hoặc INFRA_ERROR khi có người cần xử lý. Tìm theo username/display name; API này không hứa tìm theo tên bot.
3. Mở **Xem chi tiết** để đọc bước thất bại và lời giải thích. Mục **Thông tin kỹ thuật** chứa ID/hash/timestamp phục vụ đối chiếu, không phải thông tin người chơi cần tự sửa.
4. Mặc định xem đợt freeze hiện tại. Bật lịch sử chỉ để tra cứu; dữ liệu cũ/superseded không được coi là bài hiện tại.

Summary luôn phản ánh tập hiện tại của giải, kể cả khi danh sách đang lọc hay xem lịch sử. Không cộng số dòng của từng trang để tính tổng.

## Chọn đúng cách xử lý

| Trường hợp | Hành động |
| --- | --- |
| QUEUED/RUNNING | Chờ, xem queue và cập nhật; không tạo job trùng. |
| FAILED do bot | Đọc lỗi cụ thể; retry hạ tầng không phải cách cho bot sai được thi đấu. |
| INFRA_ERROR | Sửa nguyên nhân Central rồi retry **nếu server cho phép**, trước validation deadline và trên bản hiện tại. |
| EMERGENCY_OPEN | Theo dõi hạn riêng, bản mới và lần validation tiếp theo. |
| PASSED chưa áp dụng | Chờ projection/áp dụng, kiểm tra hạ tầng nếu treo; chưa tuyên bố đủ roster. |

**Nộp khẩn cấp** chỉ xuất hiện khi policy và trạng thái cho phép, có lý do, hạn mới nằm trong cửa sổ validation và giới hạn cấp lại. Không phải quyền sửa bot tùy ý sau khóa. Người chơi vẫn phải build/test đạt yêu cầu; bản mới phải được kiểm thử và áp dụng trước khi đi tiếp.

`retry` và `emergency` là hai hành động riêng, cùng kiểm tra manager/revision/deadline. Không có bulk retry trên dashboard hiện tại. Nếu cửa sổ đã đóng, nút không xuất hiện hoặc API từ chối; không sửa trực tiếp DB để bỏ kiểm tra.

Quyết định loại bài của chủ giải được giữ khi kết quả kiểm thử đến sau. Gia hạn lập trình toàn giải không mở lại bài đã bị loại hoặc từ chối chưa được cấp quyền nộp lại. Kiểm thử đạt không tự hủy quyết định này.

Nếu loại bài trước khi giải bắt đầu, server đồng thời chuyển đăng ký bot sang REJECTED và bỏ người đó khỏi danh sách thi đấu, kể cả đã check-in. Đến giờ bắt đầu, giải tiếp tục với người hợp lệ còn lại; nếu không đủ số lượng tối thiểu theo thể thức thì hủy theo quy định hiện có. Bước bắt đầu cũng kiểm tra lại các bài bị loại để xử lý danh sách đã check-in được lưu từ phiên bản server cũ.

## Chuyển sang check-in

Đối chiếu số người đủ điều kiện, lỗi còn lại và lịch check-in. Dashboard không tự bấm check-in hoặc start thay chủ giải. Đừng đưa người chỉ có Public Test PASS vào roster thi đấu khi Final Validation chưa cho phép.

## Nguồn đối chiếu

- [Controller](../../server/src/main/java/com/rpsbattle/api/controller/FinalValidationController.java), [service/hành động](../../server/src/main/java/com/rpsbattle/tournament/coding/FinalValidationService.java), [read model](../../server/src/main/java/com/rpsbattle/tournament/coding/FinalValidationStore.java).
- [Dashboard](../../client/js/tournament/final-validation-dashboard.js), [hiển thị và quyền thao tác](../../client/js/tournament/organizer-validation-view.js).
