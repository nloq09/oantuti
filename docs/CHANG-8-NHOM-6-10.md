# Chặng 8 — nhóm 6–10

## Phạm vi thực tế

Backend hiện chỉ có Round Robin và Single Elimination, một stage và BO1. Chưa có scheduler series BO3/BO5, Group Stage, Swiss, team/school, roster hoặc lineup. Không thêm dữ liệu giả hoặc đổi thuật toán scheduler để che các phụ thuộc này. Các phần UX của những thể thức đó chưa thể nghiệm thu.

## Nhóm 6 — ngữ cảnh trận

`GET /api/tournaments/{id}/match-context?onlineMatchId=...` trả tên giải, stage thật, round, format, BO1, ván 1/1, trạng thái và replay đã lưu. Không trả token bot/ghế; không tạo phòng. Game và spectator hiển thị context, cập nhật sau kết thúc, quay lại lobby. Tournament không có rematch thường. Series nhiều ván cần backend tương ứng trước khi nối UX.

File chính: TournamentMatchContextService, TournamentPlayerController, match-context.js, pages/game.js. Kiểm thử context kiểm tra không rò capability, không tạo phòng và từ chối phòng thuộc giải khác; client kiểm tra nhãn BO1/vòng thật.
Kiểm thử nhóm 6: toàn bộ 506 backend, 47 client đạt; không fail/error/skipped; bootJar thành công. Native PostgreSQL: context của trận kết thúc khớp replay thật, room ngoài giải bị từ chối. Không sửa GameEngine/RuleSet/scheduler.

## Nhóm 7 — bracket, lịch, kết quả và luật

Lịch lọc tất cả/trận của tôi, theo vòng và thời gian, phân trang 20 trận. Nhận diện tất cả participant thuộc cùng tài khoản. Bracket cuộn ngang, chỉnh cỡ 80–150%, tìm nhánh của mình, đánh dấu nhánh bằng outline và giữ điều hướng bàn phím. Kết quả chỉ dùng ranking/champion từ server; loại trực tiếp phân biệt hạng bảng điểm và kết quả bracket. Tab Luật hiển thị snapshot đã khóa, không tự lấy config đang thay đổi. Không dựng bảng Group Stage/Swiss giả.

File chính: competition-view.js, bracket-renderer.js, player-detail/player-state, tournament-mode.css. Toàn bộ 506 backend và 49 client đạt, không fail/error/skipped; bootJar thành công. Kiểm thử client bao gồm focus nhánh xa nhất, nhiều participant cùng chủ, lọc/phân trang và DOUBLE_FORFEIT. Không sửa core game hoặc scheduler.
