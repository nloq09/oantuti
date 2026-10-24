# Đăng ký và danh sách thi đấu

Đầu tiên mở giải từ `/tournaments.html`, đọc lịch và luật rồi làm theo **Việc của bạn**. Giải bot có timeline đăng ký **người chơi trước**, chưa yêu cầu một bot/source/version ngay trong đơn.

## Thao tác của người tham gia

1. Đăng nhập tài khoản người chơi.
2. Bấm **Đăng ký tham gia** trong **Việc của bạn** để mở form; chọn ngôn ngữ dự kiến được giải cho phép, khai trường/đội nếu cần.
3. Đồng ý luật và bấm **Xác nhận đăng ký** một lần; chờ server phản hồi.
4. Theo dõi kết quả, lời nhắn công khai hoặc vị trí waitlist nếu có.
5. Chỉ khi `APPROVED` và đến giờ coding mới được chọn/viết bot. [Coding phase](coding-phase.md).

Một thông tin trường/đơn vị do bạn khai không tự trở thành xác minh độc lập. Nếu giải yêu cầu xác minh, đơn có thể cần ban tổ chức kiểm tra.

## Trạng thái đơn

| Trạng thái source | Hiển thị/ý nghĩa | Hành động |
| --- | --- | --- |
| `NOT_REGISTERED` | Chưa có đơn; trạng thái tổng hợp từ flow | Gửi khi cửa đăng ký mở |
| `NEEDS_REVIEW` | Chờ ban tổ chức xác nhận | Không gửi trùng; xem lời nhắn |
| `PENDING` | Giá trị còn hỗ trợ cho dữ liệu cũ | UI cũng trình bày là chờ xác nhận |
| `APPROVED` | Đã có suất tham gia phần tiếp theo | Chờ coding mở |
| `WAITLISTED` | Chưa có suất chính thức | Theo dõi vị trí/quyết định |
| `REJECTED` | Không được chấp nhận | Xem lời nhắn riêng của đơn |
| `CANCELLED` | Người chơi đã hủy đơn | Đăng ký lại chỉ khi còn cửa và được phép |

`Submitted` hay `Evaluating` có thể dùng trong mô tả nghiệp vụ, nhưng **không thêm chúng thành status của đơn**. `SUBMITTED` ở phần bot là trạng thái bài nộp khác, không phải đơn đăng ký người chơi.

## Chính sách duyệt và waitlist

`AUTO_APPROVE` tự xử lý các trường hợp đủ điều kiện; `MANUAL_REVIEW` chờ người quản lý; `HYBRID` kết hợp điều kiện tự động với các trường hợp cần xác nhận. Giải mới mặc định HYBRID; snapshot cũ không có policy được đọc theo manual, không tự đổi thành auto.

Waitlist hiện có thứ tự `FIRST_COME_FIRST_SERVED`. Có chỗ trống chưa chắc được vào ngay nếu đơn còn cần review/xác minh. Việc chuyển waitlist còn bị chặn bởi hạn xét duyệt và trạng thái giải.

## Giờ mở/đóng và khóa danh sách

Sau khi giải được công bố, server mở/đóng theo giờ đã đặt. Chủ giải có thể mở sớm hoặc đóng nhận đơn sớm khi UI cấp thao tác. Đóng nhận đơn không tự có nghĩa mọi đơn đã được duyệt.

Đến giờ coding, server khóa quyền đăng ký và xét điều kiện truy cập theo dữ liệu đã được duyệt; không bắt buộc người quản lý phải bấm chốt thủ công để coding được mở. Cho phép giờ đóng đăng ký bằng giờ mở coding; trường hợp đó không có khoảng xét duyệt bổ sung giữa hai mốc. Đơn chờ/waitlist không tự chuyển thành APPROVED chỉ vì đến giờ.

Chỉ **Hủy đăng ký** khi server còn cấp quyền trước khóa/coding. Sau khi có suất đang thi, luồng rút tham gia khác với hủy đơn ban đầu; xem nút hành động hiện tại và tác động trước khi xác nhận.

## Giải thủ công

Giải không có timeline có thể cho chọn **Tôi tự chơi** hoặc bot ngay khi đăng ký theo participant policy. Nó dùng registration/roster riêng và các bước duyệt/check-in do chủ giải điều khiển. Không dùng form đăng ký người chơi theo timeline để suy ra payload hoặc lịch của nhánh thủ công.

> Screenshot: Đơn đăng ký, kết quả NEEDS_REVIEW/APPROVED và WAITLISTED của tài khoản người chơi. Chưa có bộ ảnh thật cho các trạng thái này.

## Nguồn đối chiếu

- [TimelineRegistrationService](../../server/src/main/java/com/rpsbattle/tournament/timeline/TimelineRegistrationService.java), [TimelineRegistrationStore](../../server/src/main/java/com/rpsbattle/tournament/timeline/TimelineRegistrationStore.java), [RegistrationControl](../../server/src/main/java/com/rpsbattle/tournament/timeline/RegistrationControl.java).
- [RegistrationPolicy](../../server/src/main/java/com/rpsbattle/tournament/registration/RegistrationPolicy.java), [Eligibility](../../server/src/main/java/com/rpsbattle/tournament/registration/RegistrationEligibilityService.java), [WaitlistPolicy](../../server/src/main/java/com/rpsbattle/tournament/registration/WaitlistPolicy.java).
- [Form người chơi](../../client/js/tournament/timeline-registration.js), [status model](../../client/js/tournament/timeline-registration-model.js), [form thủ công](../../client/js/tournament/registration-form.js).
