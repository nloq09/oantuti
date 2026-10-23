# Hạt giống, nhánh đấu và bảng điểm

Lịch, người đi tiếp và bảng điểm lấy từ server. Không suy ra kết quả chỉ từ một bàn cờ, tên người đang hiện hoặc số quân của một ván chưa được xác nhận. [Các thể thức](formats.md) có quy tắc khác nhau.

## Seeding

Có ba chiến lược trong source:

- `RANDOM`: xáo trộn theo seed đã cấu hình, rồi đánh số hạt giống.
- `MANUAL`: người quản lý cung cấp số thứ tự hợp lệ, không trùng.
- `RANKING`: sắp theo dữ liệu điểm/thắng/quân còn được dịch vụ seeding cung cấp, có khóa ổn định khi bằng nhau.

`seedNumber` quyết định vị trí hạt giống; không phải random seed để bot chọn nước. Hạt giống nhỏ hơn là tốt hơn trong tie-break knockout. Không coi seeding theo ranking là một tính năng matchmaking/ranking tài khoản toàn hệ thống.

## Theo dõi bracket

**Nhánh đấu** có ở loại trực tiếp hoặc phần knockout của vòng bảng. Mỗi node biểu diễn cặp và đường đi tiếp. Vòng tròn/Swiss dùng lịch, ghép cặp và bảng điểm, không tự dựng một bracket loại trực tiếp cho chúng.

BYE của knockout giúp đi tiếp khi thiếu một đối thủ, không tạo trận giả. Phải chờ các node nguồn có kết quả trước khi biết đối thủ vòng sau. Khi trận/cặp đang review hoặc chưa áp dụng kết quả, UI có thể giữ trạng thái chờ thay vì hiện một winner chính thức.

Thắng một ván BO3/BO5 chưa nhất thiết thắng node cặp đấu. Xem cả tỷ số series và từng replay. Hòa cặp knockout được so tổng quân còn rồi hạt giống; không lặp ván vô hạn.

## Điểm và tiêu chí phụ

Thắng cặp **3 điểm**, hòa **1**, thua **0**. BO nhiều ván chỉ được tính một lần khi cặp đã có kết luận. Trận hạ tầng FAILED chưa là một kết quả thắng/thua; double forfeit không tạo một trận hòa một điểm cho mỗi bên.

Các tiêu chí source hỗ trợ: `POINTS`, `WINS`, `HEAD_TO_HEAD`, `REMAINING_PIECES`, `AVERAGE_MOVE_TIME`, `BUCHHOLZ`. Thứ tự cụ thể lấy từ cấu hình/luật snapshot của giải. Đối đầu giữa một nhóm hòa điểm được tính trên các trận giữa những người trong nhóm, không so từng cặp một cách mâu thuẫn. Nếu vẫn bằng tất cả tiêu chí, dùng participant ID làm thứ tự ổn định.

Trong Swiss, BYE được tính như một lượt thắng/3 điểm và Buchholz dùng điểm đối thủ thật đã gặp. Trong vòng bảng, bảng điểm được tính riêng cho từng nhóm/giai đoạn để chọn suất đi tiếp.

## Xếp hạng cuối cùng

Trong knockout, tiến độ bracket được ưu tiên trước các thống kê điểm phụ, nên người lọt vòng sâu không bị một lượt BYE làm xếp dưới người đã bị loại sớm. Danh sách còn xét trạng thái suất hoạt động, rút hoặc bị loại theo quyết định giải.

Giải đội có bảng riêng: điểm cuộc gặp đội và hiệu số điểm bàn; không lấy ranking cá nhân làm podium đội. Xem [thể thức đồng đội](formats.md).

Kết quả cuối phải được server công bố khi giải hoàn tất và không còn review chặn. Không cam kết luôn có đủ ba vị trí podium, trận tranh hạng ba hoặc nhánh thua: chỉ đọc những vị trí/kết quả source thực sự cung cấp.

## Nếu thắng nhưng chưa có trận

Kiểm tra lần lượt: cặp BO đã kết thúc chưa, vòng nguồn của đối thủ đã xong chưa, kết quả có review không, vòng tiếp theo có cần người quản lý mở không, giải có PAUSED/RECOVERY_REQUIRED không. Mở **Trận của tôi** và thông báo; không đăng ký lại để lấy trận.

> Screenshot: bracket có BYE, tỷ số series và bảng điểm theo thể thức. Chưa bổ sung bộ ảnh giải thật.

## Nguồn đối chiếu

- [SeedingService](../../server/src/main/java/com/rpsbattle/tournament/seeding/SeedingService.java), [SeedingStrategy](../../server/src/main/java/com/rpsbattle/tournament/domain/SeedingStrategy.java), [TournamentSeries](../../server/src/main/java/com/rpsbattle/tournament/domain/TournamentSeries.java).
- [RankingService](../../server/src/main/java/com/rpsbattle/tournament/ranking/RankingService.java), [TieBreakService](../../server/src/main/java/com/rpsbattle/tournament/ranking/TieBreakService.java), [SingleEliminationScheduler](../../server/src/main/java/com/rpsbattle/tournament/scheduler/SingleEliminationScheduler.java), [TeamScheduler](../../server/src/main/java/com/rpsbattle/tournament/scheduler/TeamScheduler.java).
- [Bracket UI](../../client/js/tournament/public-bracket.js), [competition view](../../client/js/tournament/competition-view.js).
