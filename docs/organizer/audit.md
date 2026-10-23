# Đọc hoạt động và quyết định trong giải

[Mục lục](../README.md) · [Vận hành giải](tournament-operation.md) · [Bảo mật](../admin/security.md)

Mở **Audit** của giải để trả lời: ai làm gì, vào thời điểm nào, với đối tượng nào và vì sao. Đây là nhật ký thao tác/quyết định, khác với danh sách nước đi của replay.

## Tra một thay đổi

1. Mở đúng giải và dùng bộ lọc/tìm kiếm hiện trên trang. Xem thêm trang trước/sau nếu sự kiện không nằm ở trang đầu.
2. Đọc tên hành động bằng ngôn ngữ người dùng, người thực hiện, đối tượng và thời điểm.
3. Mở chi tiết để xem lý do, giá trị trước/sau được phép công bố và thông tin kỹ thuật cần đối chiếu.
4. Đối chiếu với trạng thái hiện tại: các sự kiện sau có thể đã thay đổi quyết định trước. Một dòng cũ không chứng minh quyền thao tác hiện tại.

Các nhóm sự kiện gồm đăng ký/xét duyệt, đổi lịch, bài nộp/freeze/validation, check-in, release vòng, review/retry/override và hủy/kết thúc. Các bước tự động có thể không có tài khoản con người làm actor; không suy diễn thành người dùng ẩn.

Audit giải dùng sequence để giữ thứ tự. Revision dùng để chống cập nhật trên snapshot cũ, không phải điểm số hoặc số ván. Khi cần báo lỗi, gửi tournament ID, thời gian và tên hành động; không gửi session cookie, token hoặc source riêng tư.

## Quyền và dữ liệu nhạy cảm

Server quyết định trường nào được đọc theo người xem; biết URL không cấp quyền manager. Admin có nhật ký hệ thống riêng cho thao tác tài khoản/bot/worker và HTTP quản trị. Các nhật ký này liên quan nhưng không thay thế nhau.

Các reason/details được redaction theo quy tắc backend. Nhãn không nhận diện được có fallback an toàn; không hiển thị stack trace hoặc tự đổi raw enum thành một hành động khác. Audit không có nút sửa/xóa dòng qua UI hiện tại.

Khi result bị override, replay giữ nguyên facts; kiểm tra cả dòng mở review, quyết định và đóng review. Dùng [backup](../technical/backup-recovery.md) để lưu cả dữ liệu audit trong DB cùng artifact/replay.

## Nguồn đối chiếu

- [API audit giải](../../server/src/main/java/com/rpsbattle/api/controller/TournamentAuditController.java), [phạm vi đọc và redaction](../../server/src/main/java/com/rpsbattle/admin/TournamentAuditReadService.java).
- [Ghi sự kiện](../../server/src/main/java/com/rpsbattle/tournament/TournamentAudit.java), [redactor](../../server/src/main/java/com/rpsbattle/admin/AuditRedactor.java).
