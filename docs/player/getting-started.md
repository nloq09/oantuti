# Bắt đầu với RPS Battle

RPS Battle là trò chơi chiến thuật theo lượt trên bàn cờ **9×9**, dùng Búa, Bao và Kéo. Mỗi bên có 18 quân. Bạn thắng khi chiếm được cứ điểm đối phương hoặc loại hết quân của họ; xem [luật đầy đủ](game-rules.md).

## Chọn việc bạn muốn làm

- **Chơi với bạn bè:** mở website game → **Bắt đầu chơi →** → **Hai người** → **Tạo phòng →**. Gửi địa chỉ website và mã phòng cho bạn của bạn. [Hướng dẫn tạo/join và xếp quân](how-to-play.md).
- **Chơi với bot dựng sẵn:** chọn **Đấu với bot** rồi tạo phòng. Bot chiến thuật được server thêm vào ghế đối thủ.
- **Dùng bot do bạn viết:** đăng nhập → **Bot của tôi** → tải bot lên. Tại lobby chọn bot trong **Người chơi của bạn** trước khi tạo hoặc tham gia phòng. [Viết bot đầu tiên](../bot/bot-guide.md).
- **Tham gia giải:** đăng nhập → **Giải đấu** → chọn giải, xem luật/lịch và đăng ký. [Toàn bộ luồng tham gia](tournament-flow.md).

**Hai người / Đấu với bot** chọn loại đối thủ. **Người chơi của bạn** chọn bạn tự đi hay để bot điều khiển ghế của bạn. Vì vậy hai người đều có thể chọn bot riêng để hai bot đấu nhau trong cùng phòng.

## Bạn cần cài gì?

Người chơi chỉ cần trình duyệt và mạng tới server game. Không cần cài PostgreSQL, Java, Docker hoặc remote worker trên máy để chơi. Những thành phần đó dành cho người vận hành server/worker. Người viết bot có thể dùng workspace trong giải khi chính sách giải cho phép.

Khách có thể chơi tay trận thường. Đăng nhập để dùng bot riêng, lưu lịch sử trận mới theo tài khoản và tham gia/quản lý giải. Tài khoản ADMIN sử dụng khu quản trị riêng.

## Ván đầu tiên

1. Để **Tôi tự chơi**, chọn **Đấu với bot**, bấm **Tạo phòng →**.
2. Bấm **Ngẫu nhiên** để xếp đủ 6 Búa, 6 Bao, 6 Kéo trong vùng hợp lệ.
3. Bấm **Sẵn sàng**.
4. Đến lượt, chọn quân của bạn rồi chọn ô được tô sáng để đi một bước theo 8 hướng.
5. Bảo vệ cứ điểm, ghi nhớ **Búa thắng Kéo → Kéo thắng Bao → Bao thắng Búa**.

Nếu có thông báo hết giờ, chưa đến lượt, mất kết nối hoặc bot chưa sẵn sàng, xem [FAQ](faq.md). Với giải đấu, làm theo **Việc của bạn** do server cấp; chưa được duyệt hoặc chưa đến giờ coding thì chưa có quyền làm bài.

## Nguồn đối chiếu

- [Trang chủ](../../client/index.html), [lobby](../../client/lobby.html), [controller lobby](../../client/js/pages/lobby.js).
- [Điều hướng tài khoản](../../client/js/account/account-session.js), [OnlineMatch](../../server/src/main/java/com/rpsbattle/online/OnlineMatch.java), [player next action](../../client/js/tournament/next-action.js).
