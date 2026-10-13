# Tài khoản và phân quyền

## Đăng ký, đăng nhập và session

Client có login.html, register.html, profile.html, history.html. Khách vẫn chơi tay hoặc bot có sẵn; quản lý bot riêng, tạo/tham gia giải và lịch sử cá nhân cần đăng nhập. Home chuyển Bot của tôi/Giải đấu qua login và giữ đường dẫn quay lại cùng origin.

REST:

- GET `/api/auth/csrf`: lấy token/headerName, giữ cookie cùng phiên.
- POST `/api/auth/register`: username/email/password/displayName, tạo USER/ACTIVE. Không nhận role/owner từ browser.
- POST `/api/auth/login`: username/password, đổi session ID khi thành công, trả account an toàn.
- GET `/api/auth/me`: trạng thái authenticated và user; không trả hash/session ID.
- POST `/api/auth/logout`: vô hiệu session và xóa cookie; không xóa account/bot/history.

POST/PATCH/DELETE tài khoản, bot và giải dùng header CSRF từ GET csrf. Cookie JSESSIONID do server quản lý; production Secure/HttpOnly/SameSite=Lax, timeout 30 phút. Không lưu mật khẩu hoặc auth token vào localStorage. Same-origin deployment giúp cookie/CSRF hoạt động thống nhất.

Username 3–32 chữ thường a–z, chữ số hoặc gạch dưới; strip/lowercase khi tìm. Email chuẩn hóa và unique. Password không trim, tối thiểu 12 Unicode code points, tối đa 72 byte UTF-8, BCrypt cost 12/salt riêng. Đăng nhập sai trả INVALID_CREDENTIALS chung; quota/brute force có thể trả RATE_LIMITED. Xem [WORKER-OPERATIONS.md](WORKER-OPERATIONS.md).

## USER, ADMIN và ownership

USER được quản lý source/metadata bot của mình và xem profile/history của mình. Chủ giải hoặc ADMIN được start/cancel giải; người khác đăng ký/rút bot của chính họ khi giải DRAFT. Giải hiện PUBLIC, mọi người xem được ranking/spectator/replay công khai. Quyền tính từ DB và session đã xác thực, không từ userId/role/token browser gửi.

ADMIN có `/api/admin/users`, `/api/admin/users/{id}/disable`, `/enable`, `/api/admin/system` và Actuator metrics/info. Không có admin mặc định hoặc password mặc định. Nếu cần bootstrap admin: đăng ký account bình thường, rồi người quản trị DB mở psql riêng và đổi đúng account đã xác minh:

```sql
UPDATE users SET role='ADMIN' WHERE username='ten_admin_da_xac_minh';
```

Lệnh này chỉ là hướng dẫn, không được tự chạy cho account của người dùng. Kiểm tra đúng một row, đăng xuất/đăng nhập lại. Không cấp ADMIN bằng API đăng ký, không chia sẻ account quản trị hoặc secrets env. Disable giữ lịch sử/bot metadata; không hard-delete user. Admin không được disable chính mình qua API.

Đổi mật khẩu cần currentPassword/newPassword; tăng authVersion và thu hồi các session cũ. SessionAccountFilter kiểm tra account ACTIVE/version/role từ DB trước mutation; DB lỗi trả 503, không mở quyền mặc định. Session ở RAM servlet không được giữ qua restart, nên phải login lại; account trong DB vẫn còn.

## Account session và seat token

Account session xác định danh tính/ownership. Seat token chỉ cho phép điều khiển một ghế trong một phòng RAM, dùng cho JOIN/RESUME/ACTION qua WebSocket. Token ghế không cấp quyền sửa bot, đọc lịch sử account khác hoặc quản lý giải. Bot capability cũ được giữ nội bộ server; DTO mới không trả token/path.

Ghế được bind user/bot tại server khi REST create/join hoặc worker tạo trận giải; history/stats theo danh tính đó. Khách không có userId để gán tự động. Hai ghế có thể được bot của mỗi chủ ghế điều khiển; không thêm chế độ Bot đấu bot riêng vào lobby.

Logout/đổi mật khẩu chưa tự đóng tất cả socket hoặc xóa seat capability của một trận đang mở ở tab khác. WSS same-origin và seat capability vẫn quyết định quyền trong phòng; người chơi nên rời phòng nếu muốn dừng điều khiển. Không gửi password/token trong URL hoặc chia sẻ log/fixture có credential.

Bot cũ chưa có owner chỉ nhập qua `/api/account/bots/import` với proof token hợp lệ; không được dùng token để chiếm bot đã thuộc account khác. Soft-delete bot giữ source cho audit/replay, không tự xóa artifact đã tham gia giải.

Hướng dẫn giao diện: [ACCOUNT-UI.md](ACCOUNT-UI.md). Deployment/HTTPS: [DEPLOYMENT.md](DEPLOYMENT.md).
