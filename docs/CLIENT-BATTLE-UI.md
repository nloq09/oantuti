# Giao diện đấu trường

- `/`: trang chủ, Vào trận, Bot của tôi, Cách chơi, Cài đặt âm thanh/nhạc/hiệu ứng.
- `/lobby.html`: Hai người và Đấu với bot chiến thuật có sẵn. Trong cả hai chế độ, người dùng có thể tự chơi hoặc chọn bot riêng điều khiển quân khi tạo/vào phòng. Bàn cờ cố định 9×9; mỗi bên 6 Búa, 6 Bao, 6 Kéo. Thời gian lượt do server cấu hình (`rps.turn-seconds`, mặc định 30 giây).
- `/bots.html`: tải `.py`, `.cpp`, `.java`, `.jar`; chọn bot, xem khả năng Docker, dùng bot trong hai chế độ và đấu thử với bot chiến thuật. Nhật ký ghi kết quả thao tác thực tế. Danh sách và quyền sử dụng bot lưu trong trình duyệt hiện tại; file bot lưu trên server.
- `/game.html`: mã phòng, trạng thái tham gia/sẵn sàng, thời gian, số quân hai bên, xếp quân, bỏ lượt, đầu hàng, rời phòng, diễn biến. Quân của người chơi luôn ở dưới kể cả P2. Nút Xóa chỉ xóa diễn biến đang hiển thị.
- `/result.html`: kết quả, số lượt/quân, tạo ván mới và xem sự kiện trong trạng thái cuối trận. Nhật ký này không phải replay đầy đủ.

ZIP, manifest riêng, sửa source và build độc lập chưa có API nên không hiển thị thành nút hoạt động. C++/Java được biên dịch trong Docker khi bắt đầu trận thật. Chặng 3 bổ sung Giải đấu tại `/tournaments.html`, chi tiết/ranking tại `/tournament.html`, khán giả chỉ đọc trong game.html và replay đầy đủ tại `/replay.html`; xem [CHANG-3.md](CHANG-3.md).

## Nền minh họa

File: `client/assets/castle-courtyard.png`, tạo bằng built-in imagegen; khung và nút được dựng bằng CSS. Prompt tạo ảnh:

Use case: stylized-concept. Create a wide 16:9 landscape game background asset for a browser-based fantasy tactical rock-paper-scissors board game. Hand-painted polished casual strategy game illustration, similar to a cheerful medieval castle courtyard mobile game. Two stone castle walls and tall towers flank the far left and far right edges; a blue medieval banner with a simple gold crown on the left and a red banner with a gold crown on the right. Warm sandstone and slate stone blocks, a few small torch flames at edges, blue sky and soft clouds, distant castle roofs. Center 65 percent of the image must be quiet and uncluttered with an open stone courtyard and distant pale atmospheric castle so live interface panels can overlay it. Balanced symmetrical framing, restrained blue/red accents and warm gold, detailed illustrated stones with painterly shading and crisp silhouettes. No text, no letters, no logos, no buttons, no interface frames, no board, no game pieces, no people, no characters. This is only an environmental backdrop for a live game UI, not a mockup. Bright daylight without excessive saturation.
