# Chặng 4 — Nhóm 8: Giao diện tài khoản

## Hoàn thành

- Thêm login.html, register.html, profile.html, history.html, dùng API account/auth/history hiện có và CSRF/session cookie.
- Navbar gọn trong nội dung, đổi theo trạng thái đăng nhập; không khôi phục header lớn trước đây.
- Khách vẫn chơi tay/bot có sẵn và xem giải công khai/replay. Bot riêng/profile/history cần login; tạo giải/đăng ký bot không bật cho khách.
- Register có xác nhận/password Unicode/UTF-8 validation, thông báo thành công rồi sang login. Login/alternate links giữ đường dẫn đích cùng origin, không redirect ra ngoài hay tạo vòng lặp auth.
- Profile xem thông tin/thống kê, sửa displayName, đổi mật khẩu và chuyển về đăng nhập khi session bị thu hồi. Không có trường gửi owner/role/userId để tự nhận quyền.
- History có stats, filter, phân trang 20 trận, empty/error/loading/retry, controller hai ghế và replay archive UUID. Race giữa requests cũ/mới không làm ghi đè kết quả mới.
- Không lưu credential/profile/auth token vào browser storage. Xóa cache ghế/kết quả/bot ưu tiên của tab sau khi logout thành công; không xóa dữ liệu account/bot/history khỏi server.
- Giao diện tiếng Việt theo theme hiện tại; panel chuyển một cột trên màn hình hẹp.

## Kiểm thử

Ngày 13/10/2026:

- Backend: **393 pass, 0 fail/error, 0 skipped**. Bao gồm Flyway PostgreSQL **7 pass**, Docker bot bốn ngôn ngữ **4 pass**, tournament Docker + replay **1 pass**. Nhóm này không thêm migration.
- Client: **20 pass, 0 fail, 0 skipped**. Test mới: safe return URL, password Unicode code points/UTF-8 bytes, stale response/error khi đổi filter/page nhanh.
- bootJar **PASS**; bản cuối chạy local cổng 8080.
- Smoke API/WS: tạo riêng một account test, 21 trận thật kết thúc, history page 1 có 20/page 2 có 1, filter/stats/replay đúng. Wrong current password, rotate password, thu hồi session, từ chối mật khẩu cũ, login bằng mật khẩu mới và logout **PASS**.
- Browser: guest Bot của tôi chuyển login giữ next; sai password báo lỗi/clear password; login đúng trở về bot; profile/stats/load và update displayName **PASS**. Tên có markup hiển thị literal, không chạy HTML. Lịch sử 20/1, filter rỗng và mở replay **PASS**. Session bị thu hồi chuyển history về login **PASS**. Register mismatch và đăng ký thành công chuyển login giữ next **PASS**.
- Responsive bản cuối **PASS** ở viewport 390×844: login/profile/history không tràn ngang, profile một cột. Trả viewport về kích thước ban đầu sau kiểm tra.
- Browser bản cuối: navbar login/register giữ next, login trở về history sau server restart, logout về home, khách không bật tạo giải, lobby vẫn chọn được HUMAN_VS_BOT; profile sau logout redirect login **PASS**.
- Runtime final: /api/health, /login.html, /register.html, /profile.html, /history.html HTTP 200; tài khoản test/history giữ qua restart. Ảnh UI mobile lưu local, không commit.
- Diff GameEngine/RuleSet/core/backend: **không sửa**. Chỉ frontend và tài liệu.

## Giới hạn và điểm dừng

Hoàn thành Nhóm 8; dừng trước Nhóm 9 (worker/queue/rate limit/monitoring). Backend vẫn quyết định quyền. Seat capability/WS đã mở độc lập với session; logout/đổi password chưa tự hủy trận/đóng socket tab khác. Deploy khác origin cần cấu hình proxy/cookie/CSRF phù hợp, chưa thay ở nhóm này. API admin đã có nhưng không thêm admin UI.

Account/trận test dùng tên riêng trong DB local, không sửa tài khoản của người dùng. Script và fixture test, screenshot, password thử nghiệm nằm trong server/build/local-run đã được Git ignore; không đưa credential hoặc runtime data lên GitHub.

Hướng dẫn: [ACCOUNT-UI.md](ACCOUNT-UI.md).

## File thêm/sửa
- `client/bots.html`
- `client/css/account.css`
- `client/history.html`
- `client/index.html`
- `client/js/account/account-session.js`
- `client/js/account/account-ui.js`
- `client/js/account/history-loader.js`
- `client/js/pages/account-nav.js`
- `client/js/pages/auth.js`
- `client/js/pages/bots-entry.js`
- `client/js/pages/history.js`
- `client/js/pages/lobby.js`
- `client/js/pages/profile.js`
- `client/js/tournament/tournament-detail.js`
- `client/js/tournament/tournament-list.js`
- `client/js/ui/toast.js`
- `client/lobby.html`
- `client/login.html`
- `client/profile.html`
- `client/register.html`
- `client/replay.html`
- `client/result.html`
- `client/tests/account-ui.test.js`
- `client/tournament.html`
- `client/tournaments.html`
- `docs/ACCOUNT-UI.md`
- `docs/CHANG-4-NHOM-8-ACCOUNT-UI.md`
- `README.md`
