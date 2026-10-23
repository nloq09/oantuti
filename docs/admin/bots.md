# Quản lý và kiểm duyệt bot

[Mục lục](../README.md) · [Người dùng](users.md) · [Giới hạn bot](../bot/limits.md)

Vào **Bot** (`/admin/bots.html`), tìm theo bộ lọc hiện trên trang và mở chi tiết. Mục này quản lý bot trong thư viện tài khoản; bài nộp workspace, phiên bản freeze và Final Validation là các đối tượng riêng.

## Kiểm tra một bot

Xem chủ sở hữu, ngôn ngữ, trạng thái, SHA-256 của artifact/source khi còn đọc được, tình trạng storage, trận gần đây, thất bại liên quan và audit. `UNAVAILABLE` ở storage cần kiểm tra volume/quyền file; không tự coi đó là hành vi gian lận.

Bot tài khoản thuộc người sở hữu do server xác định. Client không được chọn ownerUserId để chuyển quyền. Source là dữ liệu riêng, không phải đường dẫn public static hoặc bằng chứng có thể gửi lên kênh hỗ trợ công khai.

## Disable, enable và archive

- **Disable:** đánh dấu khóa hành chính, ngăn sử dụng bot theo các kiểm tra của service; không sửa nội dung bot thành phiên bản khác.
- **Enable:** bỏ khóa hành chính nếu bot chưa bị archive và còn đáp ứng các điều kiện khác.
- **Archive:** soft-delete metadata. Không có thao tác enable để hồi sinh một bot DELETED qua API này.

Các hành động cần lý do, session ADMIN và CSRF. Xem lại ID/tên/chủ bot trước khi xác nhận; lưu audit với trước/sau. Không xóa file vật lý ngay sau archive vì history, replay hoặc bài khóa có thể còn tham chiếu.

## Phiên bản giải và validation

Giải dùng snapshot/hash của bài đã khóa; disable/rename bot trong thư viện không phải cách sửa source của bài LOCKED. Khi có bài sai protocol hoặc timeout, xem [troubleshooting](../bot/troubleshooting.md), không coi một lỗi bot là lý do reset queue hạ tầng.

Quyết định bài dự thi đi qua [quản lý bài nộp](../organizer/submission-management.md) và [Final Validation](../organizer/validation-management.md). Admin quản lý giải được phân quyền như manager, nhưng vẫn chịu deadline/revision/policy của thao tác tương ứng.

Chưa có UI chuyển ownership bot, sửa artifact đã khóa, quét malware được chứng nhận hoặc quota disk riêng theo account. Docker hạn chế tài nguyên và truy cập; không phải bảo đảm mọi mã không tin cậy đều vô hại.

## Nguồn đối chiếu

- [Admin bot API](../../server/src/main/java/com/rpsbattle/api/controller/AdminConsoleController.java), [moderation và archive](../../server/src/main/java/com/rpsbattle/admin/AdminDirectoryService.java).
- [Bot tài khoản](../../server/src/main/java/com/rpsbattle/api/controller/AccountBotController.java), [upload](../../server/src/main/java/com/rpsbattle/api/controller/BotUploadController.java).
- [Phiên bản bài nộp](../../server/src/main/java/com/rpsbattle/tournament/coding/SubmissionStore.java).
