# Giao diện tài khoản

## Sử dụng

- Mở `/register.html`, nhập tên đăng nhập, tên hiển thị, email, mật khẩu và xác nhận. Tên đăng nhập có 3–32 chữ cái/chữ số/gạch dưới; server chuẩn hóa thành chữ thường. Tên hiển thị tối đa 60 ký tự. Mật khẩu ít nhất 12 Unicode code points, tối đa 72 byte UTF-8; không tự trim mật khẩu.
- Đăng ký thành công chuyển sang `/login.html` với thông báo rõ ràng. Đăng ký không tự tạo session.
- Đăng nhập bằng username/password. Nếu vào một trang cần tài khoản trước đó, đăng nhập xong quay lại trang đó. Chỉ cho phép đường dẫn cùng origin trong các trang game đã biết; chặn redirect ra ngoài hoặc quay vòng login/register.
- Navbar gọn nằm trong nội dung, không phục hồi header lớn đã bỏ. Khách thấy Trang chủ/Vào trận/Giải đấu/Đăng nhập/Đăng ký. Người đăng nhập có thêm Bot của tôi/Lịch sử/Hồ sơ/tên hiển thị/Đăng xuất.
- Riêng trang chủ, góc phải chỉ có phần tài khoản: Đăng nhập/Đăng ký cho khách, hoặc Lịch sử/Hồ sơ/tên/Đăng xuất sau login. Các nút chơi nằm ở giữa; không lặp Trang chủ/Vào trận/Giải đấu phía trên. Khách bấm Bot của tôi hoặc Giải đấu ở trang chủ được đưa đến login, rồi quay lại đúng trang sau login.
- `/profile.html` hiển thị username, email, role, ngày tạo, lần đăng nhập gần nhất và thống kê. Chỉ sửa tên hiển thị; email/username/role không phải trường cập nhật. Form đổi mật khẩu cần mật khẩu hiện tại và xác nhận mật khẩu mới. Sau khi đổi thành công, tất cả phiên cũ bị thu hồi và người dùng đăng nhập lại.
- `/history.html` hiển thị trận của tài khoản, kể cả khi bot riêng chơi thay: kết quả, chế độ, thời gian, số lượt, controller hai ghế và link replay bằng archive UUID. Filter tất cả/thắng/thua/hòa/hai người tự chơi/có bot; phân trang 20 trận, nút tải lại và trạng thái rỗng/lỗi. Response của filter/page cũ không thay kết quả mới.
- Thống kê thắng/thua/hòa/tỷ lệ thắng chỉ tính trận đã kết thúc; không cộng trận hai bên cùng account hoặc lỗi hạ tầng. Bộ lọc lịch sử không làm thay tổng thống kê account.
- Trận chưa kết thúc không có link replay và không tự tiếp tục sau restart.

## Chính sách khách

Khách vẫn chơi hai người hoặc đấu với bot có sẵn, xem hướng dẫn/giải công khai/spectator/replay. Bot riêng, tạo giải, đăng ký bot vào giải, hồ sơ và lịch sử cần tài khoản. Khách vào Bot của tôi/Hồ sơ/Lịch sử được đưa tới đăng nhập; lobby có link đăng nhập, giải đấu có lời nhắc và không bật tạo/đăng ký bot cho khách. API backend vẫn là nơi quyết định quyền, không dựa vào nút ẩn ở client.

Trận chơi với tư cách khách không được tự nhận làm lịch sử sau khi đăng nhập. Server gắn danh tính tài khoản tại lúc create/join/bind seat; client không gửi userId để nhận quyền hay lịch sử người khác.

## Session và dữ liệu

Identity lấy từ `/api/auth/me`; cookie HttpOnly + session hiện có. Mutation qua API hiện có và CSRF mới trước mỗi lần gửi. Không lưu password, profile, token auth hay user role vào localStorage/sessionStorage. Navbar cache account chỉ trong RAM của một lần tải trang.

Đăng xuất chỉ báo thành công khi API logout thành công, rồi bỏ cache kết quả/ghế/bot ưu tiên của tab và về trang chủ. Form password được xóa sau mỗi lần gửi. Hiển thị tên và lịch sử qua textContent để không chạy HTML từ dữ liệu người dùng. API 401 khi đọc/cập nhật profile/history đưa người dùng về đăng nhập.

Seat capability token vẫn là cơ chế điều khiển ván, độc lập với account session. Đăng xuất/đổi mật khẩu chưa tự ngắt các WebSocket đã mở ở tab khác hoặc hủy trận đang chạy. Quyền API và handshake được backend kiểm tra như các nhóm trước; không thêm JWT hoặc sửa engine ở nhóm này.

## Chạy

Chạy server như README rồi vào [trang chủ local](http://localhost:8080/). Bốn trang mới và JS/CSS được đóng trong bootJar. Không cần build frontend riêng. Sau khi đổi file client, build/restart JAR để thấy bản mới.

Server và client phải cùng origin theo cấu hình hiện tại để cookie SameSite=Lax và credentials=same-origin hoạt động. Deploy client trên domain khác cần reverse proxy cùng origin hoặc thiết kế CORS/cookie/CSRF phù hợp; nhóm này chưa thay cấu hình đó.
