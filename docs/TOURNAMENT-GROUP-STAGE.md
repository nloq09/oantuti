# Vòng bảng → Loại trực tiếp

Chọn **Hai bảng → Loại trực tiếp** khi tạo giải. API dùng `format: "GROUP_KNOCKOUT"`; `bestOf` nhận 1, 3 hoặc 5. Giữ nguyên đăng ký, duyệt, check-in, controller người/bot, ready check, spectator và replay hiện có.

## Luật tổ chức

- Cần 4–32 participant đủ điều kiện sau check-in. Nếu hết hạn check-in còn dưới 4 người, giải bị hủy; thao tác chuẩn bị/bắt đầu thủ công thiếu người bị từ chối.
- Hai bảng A/B cố định, chia theo thứ tự hạt giống đã khóa, kiểu rắn: A–B–B–A, lặp lại. Mỗi bảng thi đấu vòng tròn một lượt; số lẻ có BYE, không tạo phòng cho BYE.
- Điểm mỗi cặp/series: thắng 3, hòa 1, thua 0. BO3/BO5 chỉ tính một kết quả cả series. Tiêu chí phụ dùng cấu hình giải, đối đầu chỉ tính các trận trong bảng; cuối cùng dùng participant ID để ổn định thứ tự.
- Server chỉ chốt top 2 mỗi bảng khi mọi ván vòng bảng đã hoàn tất, mọi series đã có kết quả, giải đang RUNNING và không có review. Trận lỗi chuyển sang RECOVERY_REQUIRED; pause/review ngăn chốt suất.
- Bán kết **A1–B2**, **B1–A2**, sau đó chung kết. Không cộng điểm vòng bảng vào tỷ số trận loại trực tiếp. Trận/series hòa trong nhánh loại trực tiếp dùng tổng quân còn lại rồi hạt giống ban đầu như policy hiện tại.
- Người rút/bị loại trước khi chốt không được chọn. Bảng không đủ hai người hợp lệ để trống suất tương ứng (BYE), không lấy bù từ bảng kia. Không còn ai hợp lệ thì giải kết thúc không có vô địch và không tạo phòng giả.
- Sau khi chốt, người rút/bị loại không được thay bằng người khác; các trận chưa chơi xử theo luật bỏ cuộc hiện có.
- Sau khi chốt suất đi tiếp, không được override kết quả vòng bảng (`GROUP_QUALIFICATION_LOCKED`). Báo cáo vẫn có thể được ghi nhận; trọng tài giữ kết quả, từ chối báo cáo hoặc loại participant qua luồng hiện có. Không tự tính lại danh sách đi tiếp hay xóa các trận đã chơi.

## Giao diện và dữ liệu

Tab Bảng xếp hạng hiển thị riêng bảng A/B, điểm, số series, trạng thái đi tiếp/dừng bước; bên dưới là thống kê toàn giải. Thứ hạng nhánh loại trực tiếp ưu tiên vòng đi tới, không suy người vô địch từ tổng điểm. Bracket chỉ hiển thị bán kết và chung kết, không tạo các cột trống ứng với vòng bảng.

Trạng thái cá nhân có `group`, `groupRank`, `qualified`; người không qua vòng bảng có trạng thái ELIMINATED. Lịch và replay ghi bảng/giai đoạn, số ván. Match context trả đúng stage 1 (ROUND_ROBIN) hoặc stage 2 (SINGLE_ELIMINATION). Thông báo `GROUP_QUALIFICATION_LOCKED` xuất hiện khi chốt bảng.

GET detail/export bổ sung:

- `groupStage`: thành viên các bảng, số vòng bảng, cờ `locked`, danh sách qualifier theo vị trí hạt giống A1/B1/A2/B2. Suất trống là null.
- `groups`: bảng điểm theo từng bảng và danh sách ID được chọn.
- `stages`: hai định nghĩa giai đoạn.
- Mỗi match có `stageNumber`, `group` (null ở loại trực tiếp). Số vòng tăng liên tục qua hai giai đoạn, ID trận/series thuộc namespace riêng để tránh trùng.

## Persistence và triển khai

Migration **V19** cho phép GROUP_KNOCKOUT và bỏ unique theo cặp/số ván vốn ngăn hai đối thủ gặp lại ở chung kết. Unique `(tournament_id, series_id, game_number)` và kiểm tra gốc series V18 vẫn giữ nguyên. Thành viên bảng, qualifier snapshot, bracket, series và audit được commit cùng cấu trúc giải trong `structure_json`; không thêm bảng điểm ghi đè lịch sử trận.

Sao lưu PostgreSQL và bot/replay trước khi nâng; bản local được lưu trong thư mục ignored `backups/before-groups-v19`. Triển khai đồng bộ các server hỗ trợ V19. Bản cũ không nhận enum mới; muốn rollback cần khôi phục bản sao lưu phù hợp. Giải đang dở vẫn chuyển RECOVERY_REQUIRED sau restart, không tự chạy lại phòng cũ.

## Kiểm chứng

- 528 backend tests; 61 client tests; bootJar thành công. Không fail/error/skipped. Bao gồm migration V18→V19, dữ liệu cũ, lưu/khôi phục PostgreSQL, BO3, bảng lẻ, 32 người, lỗi/review/pause, BYE, trường hợp không còn người hợp lệ và khóa sửa kết quả sau qualification.
- Native PostgreSQL + 6 tài khoản giả + WebSocket: 6 cặp vòng bảng, 2 bán kết, 1 chung kết, mỗi cặp BO3 thắng 2–0: **18 ván thật**, ready check/phòng/replay riêng. Hai người dừng bước sau vòng bảng; hai người cùng bảng gặp lại ở chung kết.
- Sau restart: giữ nguyên nhóm, điểm bảng, qualifier, champion, 18 ID ván/replay, đúng một audit chốt bảng. Kiểm tra lại các fixture BO3/BO5 và giải cũ thành công.
- Kiểm tra client tại desktop và viewport 390px; bảng/bracket cuộn trong vùng riêng.
- Không sửa GameEngine, RuleSet hoặc giao thức bot.

## Phạm vi còn lại

Hiện hỗ trợ đúng **hai bảng, top 2 mỗi bảng**. Chưa có cấu hình số bảng/top N tùy ý, Swiss, Team/School hoặc multi-stage tùy biến. Không coi các phần này là đã hoàn tất.
