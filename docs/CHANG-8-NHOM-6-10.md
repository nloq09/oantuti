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

## Nhóm 8 — thông báo và reconnect

Thông báo riêng theo tài khoản lấy từ audit đã lưu: duyệt/từ chối, check-in, ready, bắt đầu/kết thúc trận, vòng mới, tạm dừng, recovery và kết thúc giải. API chỉ trả tối đa 50 mục gần nhất, không trả token/credential. Trạng thái đã đọc lưu trong TournamentDetails JSON; đọc dữ liệu cũ thiếu trường sẽ dùng map rỗng. Không cần migration SQL, không đổi schema V17. POST read yêu cầu session + CSRF, giới hạn cursor và đọc lặp không tạo mutation.

Navbar có trung tâm thông báo và mark-all-read; toast chỉ xuất hiện cho mục mới sau lần đồng bộ đầu, không lặp khi reconnect. Lobby tải snapshot mới khi WebSocket phục hồi, mạng online, tab trở lại hoặc BFCache khôi phục. Reload game chỉ dùng đúng ghế đã cấp; thiếu/khác ghế quay về lobby để API cấp lại. Home có banner gọn cho giải đang tham gia. Profile trỏ về trang participant.

File chính: TournamentNotificationService/Controller, TournamentDetails/domain Tournament, notifications/reconnect/home-banner, account-session, player-detail và pages/game. Toàn bộ 507 backend, 51 client đạt; không fail/error/skipped; build thành công. Native PostgreSQL đã kiểm tra feed, CSRF, cursor, mark-read và token exclusion. UI thật đã đăng nhập, mở thông báo và mark-all về 0 thành công.

## Nhóm 9 — báo cáo sự cố

Thêm tab Báo cáo sự cố cho trận kết thúc/failed của chính tài khoản. POST nhận matchId, reason enum và description 1–400 ký tự; không nhận winner, score hoặc quyền chủ giải. Thời hạn do server clock tính, mặc định 24 giờ (`rps.tournament.dispute-hours`, 1–168). Trận đang bị review hoặc báo cáo lặp cùng tài khoản bị từ chối. Giải RUNNING chuyển PAUSED để trọng tài kiểm tra; các trận đã chạy vẫn hoàn tất theo cơ chế cũ.

Báo cáo dùng đúng MATCH_REVIEW_REQUESTED/audit/reviews hiện tại, xuất hiện trong quản trị dispute; trạng thái OPEN/UNDER_REVIEW/RESOLVED/REJECTED. Chủ giải/trọng tài xử lý bằng workflow hiện có. Không sửa winner/history/replay khi gửi báo cáo. Rút đăng ký trước khi khóa danh sách vẫn cần xác nhận mạnh; không mở quyền tự sửa kết quả hoặc tự xử thua giữa một trận đang chạy.

File chính: ParticipantDisputeService/Controller, disputes.js, player-detail/player-state/toast và tests. Toàn bộ 508 backend và 52 client đạt, 0 fail/error/skipped; build thành công. Native API kiểm tra session/CSRF, lý do không hợp lệ, duplicate, audit, đóng review và giữ nguyên winner. UI đã gửi báo cáo thật trên fixture, thấy chờ xử lý, sau đó đóng bằng chủ giải và xác nhận champion/replay giữ nguyên.

Phần Team/School UX của nhóm 9 chưa thực hiện được vì backend chưa có team/roster/lineup/fixture. UI ghi rõ chỉ hỗ trợ giải cá nhân, không dựng captain dashboard hoặc fixture giả.
