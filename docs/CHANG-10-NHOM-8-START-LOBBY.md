# Tự bắt đầu giải và tích hợp lobby

## Luồng sử dụng

Giải có timeline được kiểm tra mỗi giây. Trước `tournamentStartAt`, API quản lý cũng không thể khởi động sớm. Đến hạn, server chốt roster, chuẩn bị luật/seeding, dùng scheduler theo thể thức hiện có, chuyển READY → RUNNING rồi đưa các trận vào ExecutionQueue hiện có. `startedAt` lấy từ Clock server; thời gian dự kiến của lịch khởi tạo cùng mốc này. Việc tới giờ trên trình duyệt không tự tạo RUNNING.

Roster chỉ giữ execution participant có registration CHECKED_IN, đơn người chơi APPROVED, tài khoản ACTIVE và trạng thái thi đấu ACTIVE. Registration APPROVED chưa check-in bị chuyển NOT_CHECKED_IN; người bị vô hiệu hóa sau check-in bị loại khỏi roster. Mỗi bot được kiểm tra lại LOCKED/final PASS/applied đúng freeze/version và hash artifact thực tế trước khi tạo lịch. Không compile lại, không tạo participant giả.

Nếu còn dưới 2 người (4 cho GROUP_KNOCKOUT, 6 cho TEAM_ROUND_ROBIN), giải chuyển CANCELLED với lý do `NOT_ENOUGH_CHECKED_IN_BOTS`, không tạo trận. Nhóm 8 giữ các điều kiện cấu hình bảng/đội của bộ chuẩn bị hiện có; roster đủ số nhưng cấu hình không hợp lệ vẫn bị chặn để chủ giải kiểm tra.

## Tính nhất quán và thử lại

Chốt roster, thay đổi lifecycle, snapshot luật và lịch được lưu cùng transaction. Khi chuẩn bị, xác minh artifact, queue admission hoặc persistence thất bại, khôi phục bộ nhớ và giải phóng reservation mới. Clock/scheduler gọi lặp hoặc manager gửi lại sau khi giải đã bắt đầu không tạo lịch thứ hai.

Lỗi bắt đầu được ghi audit `TIMELINE_START_BLOCKED` với mã đã lọc, chỉ ghi thêm khi mã lỗi thay đổi. Trường `timelineStartIssue` trong detail/summary cho UI giải thích hàng đợi đầy, Docker chưa sẵn sàng hoặc cần chủ giải kiểm tra. Server thử lại các giải chưa bắt đầu; không tự thay bot hay bỏ qua kiểm thử. Mỗi lượt giới hạn 25 giải đến hạn. PAUSED, RECOVERY_REQUIRED, FINISHED, CANCELLED và DRAFT không bị tự khởi động.

Không thêm migration: timeline, roster, luật, audit và lịch dùng cấu trúc lưu bền vững hiện có, schema vẫn V27. Sau restart, giải đã bắt đầu dùng cơ chế recovery của runner hiện có; auto-start không tạo lại lịch.

## Giao diện

Khi flow server trả RUNNING, trang chi tiết tự chuyển sang `/tournament-lobby.html?id=...`. Người đang ở lobby chỉ được cập nhật nội dung, không redirect lặp. Các phase CHECK_IN_OPEN/READY_TO_START chưa cho chuyển sớm. Khi giải bị hủy do thiếu người, trang hiển thị lý do.

Lobby dùng lại My Status, trận tiếp theo, ready check, trận đang chạy, bracket, bảng điểm, lịch và replay của các chặng trước. Người dùng bot theo dõi trận ở vai trò SPECTATOR; không nhận player token hoặc bị ép vào ghế người chơi. Luồng human ready/entry/return-to-tournament giữ nguyên. Khi FINISHED, lobby dùng giao diện kết quả/podium/bracket/bảng điểm/replay hiện có; không mở lại coding, nộp bài hoặc check-in.

## File thêm/sửa

- Thêm `TimelineStartService`, `TimelineStartApiTest`, `client/js/tournament/timeline-lobby.js`, `client/tests/timeline-lobby.test.js` và tài liệu này.
- Sửa `OwnedTournamentService`, `TournamentService`, `StageThreeWebFixture`, `client/js/tournament/player-detail.js`, `client/js/ui/toast.js`, README.
- Không sửa GameEngine/RuleSet, thuật toán scheduler theo thể thức hay quy tắc chiến đấu.

## Vận hành

`rps.timeline-start.initial-delay-ms` mặc định 2000; test fixture đặt 3600000 và gọi processor trực tiếp để kiểm thử Clock. Bản native được sao lưu ở `backups/before-stage10-auto-start` (gitignored) trước khi bật tính năng. Server cần Docker/image và queue còn dung lượng để khởi tạo các trận.

## Kiểm chứng

- Backend hồi quy 41 PASS, 0 FAIL, 0 SKIPPED: TimelineStartApiTest kế thừa các ca coding/deadline/final/check-in và 3 ca TournamentRegistrationApiTest. Sau bổ sung lọc người không ACTIVE, chạy lại 6 ca khởi động trên bản cuối: 6 PASS, 0 FAIL, 0 SKIPPED, gồm ca mới vô hiệu hóa tài khoản sau check-in.
- Các ca mới kiểm tra một nanosecond trước giờ bắt đầu, mốc đúng giờ, loại người không check-in, thiếu roster, manager dùng cùng pipeline, artifact bị thay đổi, queue đầy/rollback/retry/idempotence và không tạo trận trùng.
- Client: 111 PASS, 0 FAIL, 0 SKIPPED; syntax check player-detail và timeline-lobby đạt. bootJar bản cuối build thành công. Không có migration mới nên không chạy lại Flyway suite.
- Native PostgreSQL/Docker: hai tài khoản build/validate Python READY, freeze, final PASS và check-in; manager gọi start sớm bị từ chối; scheduler tự RUNNING đúng hạn, tạo một trận. Entry của người dùng bot trả SPECTATOR và không có player token. Trận thật FINISHED không có failure, có bảng điểm hai người và replay.
- Sau restart, giải vẫn FINISHED, giữ nguyên một match ID, startedAt, một audit TOURNAMENT_STARTED, hai selected version và roster CHECKED_IN; source vẫn khóa. API replay đọc được finalState/events đã lưu.
- Không kiểm tra trực quan trong browser vì công cụ browser bị tắt trong Work settings; không có screenshot UI thực tế. Các kiểm thử client và native API đã chạy.
