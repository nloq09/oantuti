# Giải đấu BO1 / BO3 / BO5

## Tạo và tham gia

Trong trang Giải đấu, chọn **Số ván mỗi cặp đấu** trước khi tạo giải. Áp dụng cho cả Round Robin và Single Elimination, người tự chơi và bot. API `POST /api/tournaments` nhận `bestOf: 1 | 3 | 5`; bỏ trống giữ BO1. Giá trị khác bị từ chối trước khi tạo giải. Policy đã chọn lưu cùng giải và được đưa vào snapshot luật khi khóa danh sách.

Mỗi ván vẫn là một OnlineMatch hiện có, không có GameEngine hoặc bot protocol mới. Mỗi ván có ready window, phòng, ghế, history và replay riêng. Người chơi quay lại lobby sau ván, xác nhận ready cho ván tiếp theo và tự vào đúng ghế. Màn hình trận hiển thị stage, vòng, BO, số ván và tỷ số series. Bracket hiển thị tỷ số và mở trận/ván replay mới nhất; tab Replay giữ link từng ván.

## Kết quả và hòa

- BO3: đủ 2 thắng kết thúc sớm. BO5: đủ 3 thắng kết thúc sớm.
- Chỉ thi đấu tối đa 3 hoặc 5 ván. Ván hòa không cộng thắng. Đến giới hạn, bên có nhiều ván thắng hơn thắng series.
- Nếu số ván thắng bằng nhau: vòng tròn ghi một kết quả hòa; loại trực tiếp dùng tổng quân còn lại của các ván, sau đó hạt giống như policy loại trực tiếp hiện có. Không sửa winner/replay của ván hòa để giả thành ván thắng.
- Bỏ lỡ ready hoặc rút/bị loại theo policy xử thua cả series. Cả hai cùng bị xử thua thì không có người đi tiếp và không có điểm hòa. Lỗi bot trong một ván vẫn giữ kết quả ván đó; lỗi hạ tầng cần recovery, không tự phát lại.
- Bảng điểm và đối đầu tính **một kết quả mỗi series** (thắng 3, hòa 1, thua 0), không cộng 3 điểm cho từng ván. Tổng quân và thời gian tính nước tổng hợp từ các ván đã chơi.

## Idempotency, review và recovery

Ván đầu dùng ID cặp đấu/bracket cũ làm series ID. Ván sau dùng UUID xác định từ series ID và số ván; tick/reconnect/entry lặp không tạo ván mới. Chỉ mở ván tiếp theo khi mọi ván trước đã kết thúc và không bị review, giải đang RUNNING. Giải PAUSED không mở thêm ván; manager không thể finish khi series chưa có kết quả cuối.

Review một ván chặn diễn tiến series. Retry ván lỗi giữ nguyên ID và số ván, tăng attempt. Không override ván trước khi đã có ván sau của cùng series. Với giải đã FINISHED, override bị từ chối nếu quyết định đó đòi thêm ván; không ngầm mở lại giải. Kết quả/replay từng ván vẫn là dữ kiện riêng.

Khôi phục các giải đã kết thúc giữ nguyên tỷ số, champion và toàn bộ replay. Giải đang chạy chuyển RECOVERY_REQUIRED theo cơ chế hiện có; không tự tạo lại phòng hay phát lại ván cũ.

## Dữ liệu V18

Migration thêm `series_id`, `game_number` vào tournament_matches, backfill các ván BO1 cũ thành ván 1 của chính nó. Ràng buộc unique theo `(tournament, series, game_number)` và `(tournament, participant_a, participant_b, game_number)`, cùng CHECK số ván 1–5, chống ghi trùng ván. Repository kiểm tra series root và hai participant khớp nhau trước khi insert. Trường bestOf và metadata ván lưu trong TournamentDetails JSON; snapshot cũ thiếu trường được hiểu là BO1/ván 1.

Local đã sao lưu DB và bot/replay trước nâng V18 tại thư mục ignored `backups/before-series-v18`. Triển khai tất cả server cùng phiên bản hỗ trợ series; không chạy lẫn server BO1 cũ với DB đã chứa BO3/BO5. Nếu cần quay về bản cũ, khôi phục backup tương ứng thay vì để bản cũ đọc các ván con như các cặp đấu độc lập.

## File chính và kiểm tra

Domain: TournamentSeries, Tournament/TournamentMatch. Tích hợp: TournamentExecutionService mở ván tiếp theo; SingleEliminationScheduler chỉ giải quyết nhánh sau quyết định series; RankingService/TieBreakService tính theo series; TournamentAdministrationService giữ các chốt review/finish. Persistence: V18, TournamentDetails, TournamentMatchEntity/RepositoryAdapter. Client: tạo giải, registration rules, My Status, bracket, schedule/replay và match context.

Kiểm tra tự động bao gồm thắng sớm, lội ngược dòng BO5, giới hạn ván hòa, không advance sau game 1, no-show, pause/review/failure, ID ván ổn định, JSON cũ, migration V17→V18, unique DB, persist/restore nhiều ván, review không mở lại giải ngầm. Native PostgreSQL + hai tài khoản + WebSocket đã kiểm tra BO3 KO 2–1, BO5 KO 3–0 và BO3 RR 2–0: mỗi ván có phòng/replay riêng, ready window mới, entry lặp ổn định, không đi tiếp sớm và ranking một series.

Group Stage, Swiss, multi-stage và Team/School vẫn là các phần tiếp theo; tính năng series không giả lập các thể thức đó.

Nghiệm thu 16/10/2026: **519 backend, 0 failures/errors/skipped; 59 client, 0 fail/skipped; 71 module JS syntax đạt; bootJar thành công.** Đã sửa lỗi JPA ghi giá trị số ván mặc định trước insert; test PostgreSQL lưu BO3 nhiều ván và native WebSocket đều qua sau sửa. Không sửa GameEngine, RuleSet hoặc bot protocol; có thay tích hợp scheduler/ranking để chờ và tính kết quả toàn series, các regression BO1 giữ nguyên.
Native restart đã qua: tỷ số BO3/BO5, ID từng ván, champion, ranking và toàn bộ replay giữ nguyên; hồi quy dữ liệu giải cũ cũng đạt. Fixture lỗi đầu tiên đã hủy sau recovery, không xóa lịch sử/audit.
