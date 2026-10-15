# Chặng 8 — nhóm 6–10

## Phạm vi thực tế

Backend hiện chỉ có Round Robin và Single Elimination, một stage và BO1. Chưa có scheduler series BO3/BO5, Group Stage, Swiss, team/school, roster hoặc lineup. Không thêm dữ liệu giả hoặc đổi thuật toán scheduler để che các phụ thuộc này. Các phần UX của những thể thức đó chưa thể nghiệm thu.

## Nhóm 6 — ngữ cảnh trận

`GET /api/tournaments/{id}/match-context?onlineMatchId=...` trả tên giải, stage thật, round, format, BO1, ván 1/1, trạng thái và replay đã lưu. Không trả token bot/ghế; không tạo phòng. Game và spectator hiển thị context, cập nhật sau kết thúc, quay lại lobby. Tournament không có rematch thường. Series nhiều ván cần backend tương ứng trước khi nối UX.

File chính: TournamentMatchContextService, TournamentPlayerController, match-context.js, pages/game.js. Kiểm thử context kiểm tra không rò capability, không tạo phòng và từ chối phòng thuộc giải khác; client kiểm tra nhãn BO1/vòng thật.
Kiểm thử nhóm 6: toàn bộ 506 backend, 47 client đạt; không fail/error/skipped; bootJar thành công. Native PostgreSQL: context của trận kết thúc khớp replay thật, room ngoài giải bị từ chối. Không sửa GameEngine/RuleSet/scheduler.
