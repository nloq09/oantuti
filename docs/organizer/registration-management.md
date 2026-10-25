# Quản lý đăng ký

[Mục lục](../README.md) · [Tạo giải](create-tournament.md) · [Theo dõi bài nộp](submission-management.md)

Mở giải của mình → **Đăng ký**. Giải timeline dùng trang `tournament-applications.html?id=…`; giải không có timeline giữ luồng đăng ký cũ. Không dùng trạng thái của luồng cũ để suy diễn một đơn timeline đã được duyệt hay chưa.

## Chọn cách duyệt

| Policy | Cách xử lý |
| --- | --- |
| AUTO_APPROVE | Server tự đánh giá điều kiện và sức chứa; không phải mọi đơn đều được nhận. |
| MANUAL_REVIEW | Chủ giải xét duyệt những đơn cần xử lý. |
| HYBRID | Đơn đáp ứng điều kiện được xử lý tự động; các trường hợp cần người xem được giữ lại. |

Trang hiển thị **Tổng đơn, Đã duyệt, Cần xử lý, Danh sách chờ, Từ chối**. Đó là số liệu server của giải, không phải số dòng trên trang đang mở. Các trạng thái thực tế gồm NEEDS_REVIEW, APPROVED, WAITLISTED, REJECTED, CANCELLED và PENDING của dữ liệu cũ.

1. Mở bộ lọc **Cần xử lý**; tìm theo người chơi hoặc dùng các bộ lọc có trên trang.
2. Mở chi tiết đơn, xem điều kiện và lý do đánh giá. Source code không phải hồ sơ đăng ký và không được công khai ở đây.
3. Chọn quyết định hợp lệ, nhập lý do khi form yêu cầu, rồi xác nhận. Duyệt hàng loạt chỉ dùng cho những đơn đã chọn và vẫn chịu kiểm tra quyền/revision/điều kiện trên server.
4. Cập nhật danh sách và xem kết quả từng đơn. Không coi thao tác nhiều đơn là được phép bỏ qua sức chứa hoặc tự động duyệt mọi lỗi.

Danh sách chờ chỉ áp dụng với selection **FIRST_COME_FIRST_SERVED**. Thứ tự chờ do server quản lý; không đổi thành thứ tự tên hay dòng đang thấy. Giải đầy không có nghĩa phải xóa người đã được duyệt để nhận đơn tiếp theo.

## Đóng đăng ký và chuyển sang coding

Server dùng đồng hồ của mình để mở/đóng nhận đơn. Hết `registrationCloseAt`, người chơi không đăng ký mới được; đến `codingOpenAt`, người đã được chấp nhận được mở workspace theo policy. **Không cần bấm chốt danh sách** làm điều kiện chuyển giờ. Hai mốc có thể bằng nhau.

**Đóng nhận đơn** là thao tác đóng sớm. **Khóa danh sách** là quyết định quản trị riêng nếu UI còn cho phép, có kiểm tra số đơn cần xử lý và điều kiện roster. Khóa sớm có thể ngăn các thay đổi tiếp theo; đọc phần xác nhận. Khi server đã hết thời gian xét duyệt, không tiếp tục duyệt đơn bằng cách gọi API cũ.

Muốn thêm thời gian, dùng gia hạn đăng ký khi cửa sổ còn cho phép. Gia hạn mở lại nhận đơn nếu đã đóng và server tự đóng lại đúng hạn mới; các đơn và quyết định duyệt trước đó được giữ. Danh sách đã khóa rõ ràng không được mở khóa bằng gia hạn. Không kéo dài đăng ký sang một giai đoạn coding đã bắt đầu. Xem [cách thay lịch](create-tournament.md#thay-lịch-sau-khi-công-bố).

## Khi số liệu hoặc thao tác không cập nhật

Nếu snapshot đổi giữa lúc đọc tổng số và trang đơn, UI sẽ đọc lại. `REGISTRATION_SNAPSHOT_CHANGED` hoặc `STALE_TOURNAMENT_REVISION` cần cập nhật trang và quyết định lại trên dữ liệu mới. Lỗi mạng không chứng minh thao tác thất bại: tải lại đơn trước khi gửi lần hai.

Đọc tiếp [luồng đăng ký của người chơi](../tournament/registration.md) và [Audit](audit.md) để đối chiếu quyết định.

## Nguồn đối chiếu

- [API đăng ký timeline](../../server/src/main/java/com/rpsbattle/api/controller/TimelineRegistrationController.java), [service và policy](../../server/src/main/java/com/rpsbattle/tournament/timeline/TimelineRegistrationService.java).
- [Dashboard và snapshot](../../client/js/tournament/registration-dashboard-model.js), [pipeline](../../client/js/tournament/timeline-registration-model.js).
