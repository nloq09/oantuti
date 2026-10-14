# Chặng 5 — Round Robin, loại trực tiếp và standings

Hoàn thành liên tục nhóm 4, 5, 6 rồi kiểm thử chung theo yêu cầu. Nhóm 7–10 chưa được đánh dấu hoàn thành.

## Cách dùng

Trong **Giải đấu**, chọn Vòng tròn hoặc Loại trực tiếp; giới hạn roster từ 2 đến 32 bot và từ 1 đến 4 trận chạy đồng thời. Chủ giải/ADMIN sửa thứ tự tie-break ở Tổng quan trước READY. Điểm luôn được xét trước; các tiêu chí phụ được chọn một lần theo thứ tự mong muốn. Luật và cấu hình chạy được khóa khi giải READY.

**Vòng tròn:** dùng circle method, đủ `N*(N-1)/2` cặp không trùng; mỗi bot chỉ đấu một trận mỗi vòng. Với số bot lẻ, mỗi vòng có một BYE và mỗi bot nghỉ đúng một vòng. BYE là metadata vòng, không tạo trận, room, replay, điểm hay thống kê thắng. Chỉ bắt đầu vòng sau khi các trận vòng trước đã kết thúc hoặc báo lỗi hạ tầng. Các trận lỗi không được tính vào standings.

**Loại trực tiếp:** hỗ trợ 2/4/8/16/32 và roster không phải lũy thừa hai. Bracket mở rộng đến lũy thừa hai kế tiếp; BYE thuộc các hạt giống đầu. Ví dụ 6 bot có 2 BYE, 5 trận thật. Seed #1/#2 nằm ở hai nhánh đối diện. Các node lưu slot, nguồn slot, matchId, round, position, winnerTo, winner và lý do đi tiếp. Chỉ tạo TournamentMatch khi đủ hai bot; server cập nhật người đi tiếp một cách idempotent. Giao diện đọc bracket và kết quả từ server, có các cột vòng đấu cùng link xem/replay.

Trận hòa vẫn được ghi DRAW và mỗi bên nhận 1 điểm. Riêng việc đi tiếp trong bracket ưu tiên số quân còn lại; nếu bằng nhau, ưu tiên seedNumber nhỏ hơn. Node lưu DRAW_REMAINING_PIECES hoặc DRAW_SEED và giao diện giải thích chính sách. BYE không được tính thành một trận thắng. Nhà vô địch lấy từ node chung kết. Xếp hạng loại trực tiếp ưu tiên kết quả và vòng đi tới trước thống kê điểm; vì vậy bot có BYE không bị xếp dưới người đã bị loại chỉ vì đấu ít trận.

Lỗi hạ tầng trong loại trực tiếp chuyển giải sang RECOVERY_REQUIRED, không tự gán người thắng. Chức năng sửa/resume giải lỗi sẽ được hoàn thiện trong các nhóm quản trị và recovery tiếp theo. Thua do bot lỗi theo luật sẵn có vẫn là kết quả trận hợp lệ và có thể đi tiếp đối thủ.

## Standings và thời gian tính nước

ScoringRule hiện có giữ thắng 3 / hòa 1 / thua 0. RankingService chỉ tính trận FINISHED, xác định bot bằng participant ID, không phụ thuộc thứ tự input hoặc object identity. Các tiêu chí hỗ trợ:

- POINTS, bắt buộc đầu tiên.
- WINS.
- HEAD_TO_HEAD: tổng điểm trong mini-league của nhóm còn bằng nhau tại tiêu chí này. Chia nhóm rồi xét tiêu chí kế tiếp; không dùng comparator đối đầu từng cặp có thể gây vòng lặp khi A thắng B, B thắng C, C thắng A.
- REMAINING_PIECES: tổng quân còn qua các trận đã kết thúc.
- AVERAGE_MOVE_TIME: tổng thời gian xử lý bot chia tổng số lần trả kết quả; thấp hơn được ưu tiên. Dữ liệu thiếu hiển thị dấu gạch và xếp sau số đo có sẵn.

Nếu hết tiêu chí vẫn bằng nhau, participant ID quyết định thứ tự ổn định. Swiss/Buchholz chưa triển khai; API không nhận một tiêu chí giả không có pairing Swiss.

Thời gian tính nước đo bằng System.nanoTime quanh lời gọi agent. Có cả thời gian khởi chạy/biên dịch/chạy sandbox thuộc lời gọi đó, không gồm thời gian bot-delay hay thời gian chờ hàng đợi trước lời gọi. Đây là thời gian phản hồi server, không phải CPU thuần của thuật toán. Response lỗi gây xử thua vẫn là một mẫu; response đã hết hiệu lực bị bỏ qua không ghi mẫu. Trận cũ không có số đo giữ null, không suy đoán từ duration/turn.

## Lưu dữ liệu và tương thích

Migration **V13** giữ nguyên V1–V12, mở constraint format/capacity và thêm structure_json. Java migration nhận diện tên constraint sinh tự động của PostgreSQL/H2 và giữ các ràng buộc NOT NULL. Cấu trúc version 1 chứa round/BYE, bracket, stage, cấu hình tie-break/concurrency và round/scheduledAt/counters thời gian của từng match. Snapshot bất biến được commit cùng participants, match results và history bằng transaction hiện có; cấu hình rollback trong RAM nếu commit thất bại.

TournamentStage tạo nền tảng một stage hoạt động; qualifyTopN dành cho mở rộng tương lai. Chưa có UI tạo giải nhiều stage. FormatTournamentScheduler chọn chiến lược RoundRobinScheduler hoặc SingleEliminationScheduler, không nhúng pairing trong controller.

Giải cũ chưa có structure_json vẫn đọc được, mặc định các trận thuộc vòng 1 và thiếu scheduledAt/số đo cũ. Không xếp lại lịch đã lưu. Giải đang dở qua restart vẫn RECOVERY_REQUIRED theo chính sách hiện có; giải FINISHED giữ nguyên vòng, bracket, timing và nhà vô địch.

Giới hạn toàn server vẫn 4 trận chạy / 256 suất chờ mặc định. Giới hạn mỗi giải bổ sung bên trong giới hạn đó và được kiểm tra lại tại launch. Loại trực tiếp reserve toàn bộ N-1 ID trận thật ngay khi start, kể cả vòng sau chưa có đủ bot. Vòng tròn 32 bot cần 496 suất; đặt MAX_PENDING_MATCHES ít nhất 496 cộng dung lượng cho các giải khác nếu muốn nhận lịch này. Thiếu chỗ trả SERVER_BUSY và rollback start; không nhận nửa lịch.

## API

POST /api/tournaments nhận thêm format SINGLE_ELIMINATION, maxParticipants tối đa 32, maxConcurrentMatches 1–4 và tieBreakOrder tùy chọn.

POST /api/tournaments/{id}/rules cần session + CSRF + owner/ADMIN; chỉ DRAFT/REGISTRATION/CHECK_IN:

```json
{"maxConcurrentMatches":2,"tieBreakOrder":["POINTS","HEAD_TO_HEAD","WINS","REMAINING_PIECES","AVERAGE_MOVE_TIME"]}
```

List/detail/export/WebSocket trả thêm rounds, bracket, stages, currentRound, maxConcurrentMatches, tieBreakOrder, drawPolicy; từng trận có round/scheduledAt; từng dòng ranking có averageMoveMillis nullable. Progress loại trực tiếp tính cả trận tương lai, không tính BYE.

## Kiểm chứng ngày 14/10/2026

**462 backend passed, 0 failed/errors/skipped; 27 client passed, 0 failed/skipped. Trong backend có 11 migration tests PostgreSQL đều passed. bootJar thành công.**

Đã kiểm tra lịch từ 2 đến 32 bot, không trùng cặp hoặc một bot đấu hai lần trong vòng; loại trực tiếp 16 bot đủ 15 trận và 32 bot đủ 31 trận; BYE/seed/advancement idempotent; lỗi không tạo winner; hai chính sách phá hòa; đối đầu vòng tròn ba bot; thứ tự tie-break tùy chỉnh; weighted timing và missing data; API owner/CSRF/READY lock; per-tournament/global worker limit; persistence JSON/PostgreSQL/restart và migration từ V12.

Docker integration chạy giải vòng tròn 5 bot / 10 trận và loại trực tiếp 6 bot / 5 trận, per-cup concurrency 1; xác nhận vòng trước hoàn tất, history/replay, timestamps, số lần đo thời gian và bracket lưu lại. Bot fixture cố ý thoát để tạo kết quả xử thua nhanh; kiểm thử bot Python/C++/Java/JAR giao thức hợp lệ hiện có cũng passed.

Native server được backup DB + bots/versions + replay vào thư mục ignored trước V13. Hai giải test tương tự chạy trên localhost, finished không lỗi hạ tầng; restart server và đối chiếu nguyên vẹn bracket/round/standings/champion/stage. Giao diện kiểm tra tab bracket, BYE, replay links, standings và cột thời gian; tạo giải loại trực tiếp 6 slot bằng form, đổi concurrency 2→1 và thứ tự tie-break, reload xác nhận đã lưu; đăng xuất tài khoản test. Không có console error. GameEngine, RuleSet và các package game/rule/rules không thay đổi.

## File thêm/sửa

Thêm domain BracketNode/TournamentRound/TournamentStage; scheduler FormatTournamentScheduler/SingleEliminationScheduler; ranking TieBreak/TieBreakService; persistence TournamentDetails/TournamentDetailsConverter; migration V13.

Sửa Tournament/Format/Match, TournamentService/OwnedTournamentService/Controller, RoundRobinScheduler, TournamentExecutionService, WorkerManager, OnlineMatch/Service (chỉ ghi số đo agent), RankingEntry/Service; persistence Entity/Metadata/Coordinator/Adapter.

Client thêm bracket-renderer.js, sửa tournament(s).html, tournament-detail/list/api, ranking-renderer, tournament.css và toast. Thêm tests TournamentRoundsAndBracketTest/TieBreakServiceTest/TournamentRulesApiTest/bracket-renderer.test.js; sửa ranking/execution/worker, Flyway/Jpa và PostgreSQL/Docker persistence tests. Cập nhật README và DEPLOYMENT.

Tiếp theo là **nhóm 7: Pause / Resume / Cancel / Forfeit / DQ**.
