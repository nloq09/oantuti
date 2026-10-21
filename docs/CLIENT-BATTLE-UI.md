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


## Khoảng cách giữa khung và hàng nút

`client/css/layout-spacing.css` dùng chung qua import trong `global.css` và `admin.css`. Chỉ áp dụng khoảng cách cho các khối xếp dọc: tournament, organizer, bài nộp, kiểm thử và workspace. Lưới thẻ, sidebar replay và bàn cờ tiếp tục dùng gap riêng, tránh thêm margin làm lệch hàng/cột.

- Các stack dùng 24 px trên desktop, 18 px khi màn hình ≤650 px. Wrapper workspace/frozen-summary tạo flow-root để giữ khoảng cách kể cả khi work-area bị ẩn.
- Hồ sơ có khoảng cách sau cụm hai cột; nội dung đầu/cuối panel có margin gọn.
- `ui-actions` nhóm các nút sinh động và phân trang, hỗ trợ xuống dòng. Hai nút Xuất JSON/Xem báo cáo và các thao tác bài nộp không dựa vào khoảng trắng HTML để tạo khoảng cách.
- Workspace dùng cùng quy tắc ở trạng thái khóa, thiếu bài hoặc đã kiểm thử; không thay quyền chỉnh sửa, timer hoặc hành vi nộp bài.

Đã rà source của 43 trang hiện tại và kiểm tra trong trình duyệt theo nhóm public/account, bot, các phần giải đấu và quản trị ở 1440 px/390 px. Các dữ liệu thử có sẵn được dùng cho lịch sử, workspace khóa, bài nộp, kiểm thử và replay; bàn cờ được kiểm tra bằng phòng thử rồi rời phòng. Các trang không có đối tượng thử phù hợp chỉ được kiểm tra trạng thái rỗng/lỗi, không tạo tranh chấp hoặc thay cấu hình hệ thống để thử bố cục.

Khoảng cách giữa khung khóa bài và tài liệu đã đo từ 0 px thành 24 px desktop / 18 px mobile. Kiểm tra hình học các màn hình đã tải không phát hiện khung dính hoặc tràn ngang trang; nội dung bảng/bracket vẫn có vùng cuộn riêng. 277 kiểm thử client qua; bootJar và kiểm tra tài nguyên đóng gói hoàn tất. Việc rà giao diện không thay thế kiểm thử mọi trạng thái dữ liệu có thể phát sinh.
