# Thuật ngữ

- **READY:** một phiên bản bot đã qua các điều kiện kiểm thử trước khi nộp của giải. Build thành công chỉ tạo `BUILT`. `READY` của bot khác với nút **Sẵn sàng** của người chơi trên bàn cờ.
- **Submission — bài nộp:** phiên bản bot cụ thể đã được người chơi chọn và nộp vào giải, hoặc được server tự chọn theo chính sách deadline. Bản nháp đang gõ không tự trở thành bài nộp.
- **Locked Submission — bài đã khóa:** bài nộp `LOCKED`, gắn với phiên bản và artifact cố định. Không thể sửa bản đang thi bằng cách chỉnh bot gốc hoặc bản nháp.
- **Final Validation — kiểm thử chính thức:** server kiểm tra bài đã khóa sau giờ coding. `PASSED` phải được server áp dụng thành công mới đủ điều kiện tiếp theo; `INFRA_ERROR` cần ban tổ chức xử lý, không tự coi là bot thua.
- **Check-in:** xác nhận bạn thực sự tham gia trong cửa sổ thời gian do server cho phép. Được duyệt đơn hoặc có bot READY chưa thay thế check-in.
- **Roster — danh sách thi đấu:** các suất đủ điều kiện được giải sử dụng khi ghép trận. Đơn đăng ký, phiên bản bot và suất thi đấu là những dữ liệu khác nhau.
- **Waitlist — danh sách chờ:** đơn chưa có suất chính thức. Có thể được chuyển vào khi chính sách/công tác xét duyệt cho phép và còn thời gian.
- **Bracket — nhánh đấu:** sơ đồ các cặp đấu và đường đi tiếp của giải loại trực tiếp hoặc giai đoạn loại trực tiếp. Round Robin và Swiss dùng bảng điểm/ghép cặp thay cho một bracket loại trực tiếp chung.
- **Worker:** máy hoặc tiến trình nhận công việc từ server trung tâm. Remote worker chạy trận bot được phân công; người chơi không cần cài worker để chơi bằng trình duyệt.
- **Artifact:** sản phẩm được runner dùng để chạy bot, ví dụ source Python đã chụp phiên bản, binary C++ hoặc JAR Java. Artifact được nhận diện/kiểm tra bằng hash, khác với nội dung chưa lưu trong editor.
- **Replay:** bản phát lại từ dữ liệu trận đã ghi nhận. Replay không phải video và cũng không phải một lần chạy lại bot.
- **Controller:** bên điều khiển một ghế, là người tự chơi hoặc bot. Chọn **Đấu với bot** quyết định đối thủ dựng sẵn; chọn **Người chơi của bạn** quyết định ai điều khiển ghế của bạn.
- **Turn — lượt:** một lần hành động của một bên; hai bên hành động luân phiên. Giới hạn 200 lượt mặc định không có nghĩa là mỗi bên được 200 nước.
- **Sequence:** số phiên bản trạng thái dùng để đồng bộ hành động. Bot phải trả đúng `sequence` của yêu cầu; không dùng `turn` để thay thế.
- **Series — cặp đấu:** cuộc đối đầu BO1, BO3 hoặc BO5 gồm tối đa số ván được cấu hình. Một ván thắng không luôn đồng nghĩa đã thắng cả cặp đấu.

Đọc tiếp: [Luật chơi](player/game-rules.md) · [Luồng giải đấu](player/tournament-flow.md) · [Bot Protocol](bot/protocol.md).
