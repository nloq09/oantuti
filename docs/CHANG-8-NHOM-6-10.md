# Chặng 8 — nhóm 6–10

> Cập nhật: BO3/BO5 đã được bổ sung sau báo cáo nhóm 6–10 bên dưới. Xem [TOURNAMENT-SERIES.md](TOURNAMENT-SERIES.md). Những mô tả BO1 dưới đây ghi lại thời điểm nghiệm thu trước đó.

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

## Nhóm 10 — responsive và hồi quy

Giao diện ưu tiên My Status/Ready, nút tối thiểu 44px, focus rõ, tabs và bracket cuộn ngang trong khung; kiểm tra viewport 390×844 không làm tràn toàn trang. Kích thước bracket 80–150% và tìm tôi hoạt động trên điện thoại. Context trận chiếm một hàng riêng trên bàn cờ, bỏ link quay lại lặp. Giải kết thúc không còn hai panel trống chờ trận/live. Tab đang xem giữ qua reload bằng URL hash; bộ lọc lịch giữ khi snapshot cập nhật. Chỉ force refresh khi kết nối thực sự phục hồi, tránh dựng lại UI với mọi snapshot WebSocket.

Thông báo được tải lại khi WebSocket báo revision mới, có polling dự phòng và online/visibility recovery. Mark-read chỉ tiến đến event thuộc feed cá nhân, không sinh audit lặp vì chính event đánh dấu đã đọc. Kết quả thể hiện đang review khi trọng tài chưa chốt; podium dùng champion/bracket/ranking từ server, không dựng hạng ba knockout hoặc champion của giải double-forfeit. Snapshot JSON cũ không có notificationReads vẫn đọc được và snapshot mới không dùng chung map mutable.

File chính: tournament-mode.css, game.html/pages/game, progression/competition-view/player-detail, tournament-api/notifications, TournamentNotificationService và tests tương thích dữ liệu cũ. Hồi quy: 509 backend, 56 client, 71 module JS syntax đạt; không fail/error/skipped. Không thêm SQL migration (vẫn V17), không sửa GameEngine/RuleSet/thuật toán scheduler.

Native PostgreSQL/WebSocket đã chạy lại cả Round Robin và Single Elimination với hai tài khoản: đăng ký → duyệt → check-in → ready → hai ghế riêng ổn định → xếp quân → thi đấu/đầu hàng → giải kết thúc → champion và replay thật. Bracket knockout khớp winner cuối, không tạo thêm trận. Đã kiểm tra notification CSRF/cursor/mark-read và dispute tới workflow trọng tài; báo cáo thử đã đóng, kết quả/replay giữ nguyên.

## Phần chưa nghiệm thu

- BO3/BO5, Group Stage, Swiss, multi-stage qualification, Team/School/captain/lineup/fixture chưa có backend. Không coi toàn bộ Chặng 8 đã hoàn thành theo Definition of Done gốc.
- Discovery có phân trang server. Lịch hiện phân trang/lọc ở client từ snapshot giải hiện tại; endpoint detail/stream vẫn dùng snapshot đầy đủ của core hiện có. Replay chỉ tải khi mở link. Muốn mở rộng giải lớn cần projection/pagination server cho detail thay vì chỉ đổi giao diện.
- Trạng thái session HTTP hiện hết hiệu lực khi server restart; người chơi đăng nhập lại, sau đó vào lobby lấy trạng thái giải. Trận đang chạy khi restart vẫn chuyển RECOVERY_REQUIRED như policy hiện có, không tự phát lại.
- Không có E2E BO3 hoặc Team vì các luồng tương ứng chưa tồn tại. Các test RR/KO BO1, Docker, PostgreSQL, auth/CSRF, WebSocket, replay và recovery đã chạy thực tế.

Xác minh cuối 16/10/2026: đã chạy lại bắt buộc toàn bộ bằng `test bootJar --rerun-tasks` sau khi Docker healthy: **509 backend, 0 failures/errors/skipped**, build thành công. Lần khởi chạy trước đó khi Docker chưa sẵn sàng có test bị bỏ qua; số liệu nghiệm thu lấy từ lần chạy lại đầy đủ. Client cuối **56 pass, 0 fail/skipped**, gồm hồi quy BFCache giữ pageshow listener để reload luồng đã dừng. Giao diện 390px đo pageWidth 375px (không tràn toàn trang), đã reset viewport sau thử.
