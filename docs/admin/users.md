# Quản lý người dùng

[Mục lục](../README.md) · [Bảo mật](security.md) · [Vận hành hệ thống](system-operations.md)

Đăng nhập tài khoản ADMIN rồi vào **Người dùng** (`/admin/users.html`). Khu quản trị có điều hướng riêng; dùng tài khoản USER riêng để chơi và tổ chức giải.

## Tìm và kiểm tra tài khoản

Danh sách có tìm kiếm, bộ lọc và phân trang phía server. Mở chi tiết để xem profile an toàn, trạng thái, trận gần đây, bot sở hữu, giải liên quan và audit. Số kết quả là số sau lọc, không phải toàn bộ tài khoản luôn nằm trên trang đầu.

Trước khi khóa, xác minh username/ID đúng người và đọc hoạt động gần đây. Không suy ra danh tính chỉ từ display name vì người dùng có thể thay đổi tên hiển thị.

## Khóa và mở lại

1. Chọn tài khoản và hành động disable/enable được phép.
2. Nhập lý do cụ thể, tránh password/token hoặc dữ liệu cá nhân không cần thiết. API yêu cầu lý do không rỗng, tối đa 500 ký tự.
3. Xác nhận và kiểm tra trạng thái mới cùng dòng audit.

Disable chuyển ACTIVE → DISABLED và làm các phiên bị từ chối khi server kiểm tra lại account. Giữ nguyên lịch sử/bot/giải để điều tra; không hard-delete user. Server không cho Admin tự disable chính mình. Enable không tạo password mới hay tự phục hồi mọi session cũ.

Đổi mật khẩu ở profile cần mật khẩu hiện tại và mật khẩu mới; server tăng authVersion. UI quản trị **chưa có** chức năng reset password tùy ý, mạo danh người chơi hoặc chuyển role bằng nút. Không bịa các quyền này khi hướng dẫn hỗ trợ.

## Cấp quyền Admin lần đầu

Không có tài khoản hay mật khẩu ADMIN mặc định. Đăng ký một tài khoản bình thường, xác minh đúng người; người quản trị database dùng phiên psql riêng trên đúng database:

```sql
BEGIN;
SELECT id, username, role, status FROM users WHERE username = 'verified_admin';
UPDATE users SET role = 'ADMIN' WHERE username = 'verified_admin' AND status = 'ACTIVE';
COMMIT;
```

Thay `verified_admin` bằng username đã xác minh. Kiểm tra SELECT đúng một người và UPDATE đúng một dòng trước khi commit; nếu sai dùng ROLLBACK. Đăng xuất/đăng nhập lại để nhận quyền mới. Đây là thao tác DB do người vận hành thực hiện, không phải public API và không tự chạy khi mở ứng dụng.

Password đăng ký: ít nhất 12 Unicode code point, tối đa 72 byte UTF-8; server không trim password và dùng BCrypt cost 12. Username được chuẩn hóa chữ thường, 3–32 ký tự a–z, số hoặc `_`. Không yêu cầu người dùng gửi mật khẩu cho Admin.

## Tra cứu quyết định

Audit hệ thống nằm ở `/admin/audit.html`; lọc theo đối tượng và thời điểm. Khi cần truy vấn kỹ thuật, API `/api/admin/users/{id}` trả chi tiết được kiểm soát, không có password hash/cookie. Các mutation cần session ADMIN và CSRF.

## Nguồn đối chiếu

- [Users API](../../server/src/main/java/com/rpsbattle/api/controller/AdminController.java), [chi tiết](../../server/src/main/java/com/rpsbattle/api/controller/AdminConsoleController.java), [service](../../server/src/main/java/com/rpsbattle/admin/AdminDirectoryService.java).
- [Đăng ký](../../server/src/main/java/com/rpsbattle/auth/RegistrationService.java), [session](../../server/src/main/java/com/rpsbattle/auth/SessionAccountFilter.java), [đổi profile/password](../../server/src/main/java/com/rpsbattle/api/controller/AccountController.java).
- [Điều hướng quản trị](../../client/js/admin/admin-shell.js).
