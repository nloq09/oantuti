# Chặng 5 — Tổng kết nhóm 7–10

Hoàn thành bốn nhóm cuối trong cùng một đợt: quản trị giải, dashboard, realtime/spectator/replay, recovery/audit/load test. Các chức năng cốt lõi Chặng 5 đã được triển khai; Swiss và UI multi-stage tùy ý vẫn là phần mở rộng tùy chọn.

## Quản trị và chính sách kết quả

Tab **Quản trị** chỉ hiện với chủ giải hoặc ADMIN. Server kiểm tra lại quyền tài khoản/session và CSRF cho từng mutation; không dùng capability token cũ để cấp quyền. Các thao tác mới gửi revision hiện tại. Revision đã cũ trả 409 STALE_TOURNAMENT_REVISION; client không tự retry một quyết định sửa kết quả.

- **Pause:** dừng launch trận mới, cho các trận đang chạy hoàn tất. Bracket và điểm của trận đã xong vẫn được cập nhật.
- **Resume:** từ PAUSED/RECOVERY_REQUIRED, kiểm tra không còn trận FAILED hoặc review mở, kiểm tra frozen artifact và rules snapshot khớp runtime. Hàng đợi nhận lại các trận chưa xong bằng batch reservation; quá tải rollback thao tác.
- **Chuyển vòng:** cấu hình autoAdvanceRounds trước READY. Nếu false, chỉ những vòng đã được mở mới chạy; nút Mở vòng tiếp chỉ được server chấp nhận khi vòng trước hoàn tất. Resume không tự mở vòng chưa được cho phép.
- **Cancel:** ngừng scheduler, dừng room/bot đang chạy theo chính sách hiện tại, đánh dấu phần chưa chạy CANCELLED và giải phóng reservation. Các kết quả đã FINISHED và history/replay cũ được giữ.
- **Xử bỏ cuộc / DQ:** cần pause hoặc recovery và lý do; không xử bot đang còn trận RUNNING. Participant giữ trong roster/history với WITHDRAWN hoặc DISQUALIFIED. Vòng tròn xử các trận chưa chơi thành thua trước đối thủ còn ACTIVE. Loại trực tiếp cho đối thủ đi tiếp. Không sửa lại các trận hợp lệ đã chơi trước quyết định.
- **Double forfeit:** cả hai không đủ điều kiện nhận một trận thua, 0 điểm; không cộng điểm hòa. Nhánh không còn bot đủ điều kiện không tạo game/replay giả. Participant không ACTIVE được xếp sau bot ACTIVE và không được công bố vô địch.

Các trận xử hành chính có decisionReason, không chạy Docker hoặc tạo replay/account match statistics giả. Standings giải tính chúng theo chính sách giải. Trận bot timeout, crash, sai output hoặc nước đi bất hợp lệ giữ chính sách BOT_FAILED và xử thua qua game hiện tại.

Lỗi daemon/runner Docker được phân biệt với lỗi bot: daemon không truy cập được hoặc docker run exit 125/126/127 trở thành BotInfrastructureException; room có infrastructureError, không tự cho đối thủ thắng. Entry FAILED có thể retry. Kiểm tra Docker có timeout 5 giây và cache kết quả khỏe tối đa 5 giây để tránh gọi CLI lặp hàng chục lần khi start roster lớn; không cache kết quả lỗi. Nếu hạ tầng thay đổi trong khoảng cache, runner vẫn phát hiện lỗi khi chạy.

## Review, override và retry

Review nhận trận FINISHED/FAILED, có lý do và audit. Nếu giải RUNNING thì chuyển PAUSED. Người quản lý có thể giữ nguyên kết quả để đóng review, hoặc sửa một kết quả FINISHED qua workflow có audit. Khi review còn mở, chức năng resume bị chặn và nhà vô địch chưa được công bố.

Override lưu actor, timestamp, lý do, kết quả trước/sau và attempt/history ID. Kết quả giải được cập nhật, còn archive/game replay giữ sự kiện và kết quả thật ban đầu; client ghi **Replay gốc** cho trận override. Không viết lại engine events hoặc thống kê trận cá nhân. Loại trực tiếp chặn sửa vòng trước nếu bất kỳ trận vòng sau đã bắt đầu/hoàn tất; các trận vòng sau còn PENDING được dựng lại từ bracket đã sửa. Không âm thầm sửa lịch sử downstream đã chạy.

Retry chỉ nhận FAILED trong PAUSED/RECOVERY_REQUIRED, không nhận kết quả game hợp lệ hoặc trận bị hủy. Trận giữ ID giải, tăng attempt, bỏ room/history capability hiện tại, reset timestamps/counters/result rồi chờ scheduler. Audit giữ history ID của attempt trước. Chủ giải xử lý các entry lỗi rồi resume; không tự resume hoặc retry sau startup.

Nhật ký xử lý trận trong Quản trị lấy các event MATCH_STARTED/MATCH_FINISHED/MATCH_ERROR/REVIEW/RETRY/OVERRIDE theo match ID. Chỉ hiển thị mã lỗi an toàn và audit; không trả Docker socket/path, stderr nội bộ hay token.

## Dashboard và lịch sử

Chi tiết giải có Tổng quan, Người tham gia, Các trận, Bracket, Xếp hạng, Replay và Quản trị. Bracket hiện cho loại trực tiếp. Tổng quan có trạng thái, start time, vòng hiện tại, tiến độ, trận đang diễn ra/turn count, nhà vô địch, export và báo cáo. Khung mời chỉ hiện khi còn đăng ký/check-in.

Người tham gia có seed, tên bot, username công khai của owner, ACTIVE/WITHDRAWN/DISQUALIFIED và trạng thái check-in. Không công bố email hoặc thông tin hồ sơ riêng. Live cards đi vào spectator game.html hiện có; replay cards sử dụng archive UUID của replay.html hiện có.

Trang danh sách giải nhóm Sắp diễn ra, Mở đăng ký/Check-in, Đang diễn ra và Đã kết thúc; lọc format/status/owner. Backend cũng hỗ trợ các query filter này. Hồ sơ tài khoản có Giải đấu của tôi: tất cả, đã tạo, tham gia, vô địch, kết thúc. API dùng session user, không nhận user ID để đọc lịch sử người khác. Metadata giải và audit được lưu PostgreSQL nên lịch sử không phụ thuộc browser localStorage.

GET /api/tournaments/{id}/report trả champion, runnerUp, totalMatches, averageMatchDurationMillis, shortestMatch, longestMatch, totalBotErrors, infrastructureErrors và finalStandings. Thời gian trận chỉ tính game có startedAt/finishedAt; trận xử hành chính không tạo duration giả. Export JSON có rulesSnapshot, participants/seeds/status, rounds/stages/bracket, attempts/decisions, standings, audit và champion.

## Realtime

WATCH_TOURNAMENT vẫn gửi TOURNAMENT_STATE để tương thích client cũ. Bổ sung TOURNAMENT_UPDATED, REGISTRATION_UPDATED, ROUND_STARTED, MATCH_STARTED, MATCH_FINISHED, STANDINGS_UPDATED, BRACKET_UPDATED và TOURNAMENT_FINISHED. Event có revision, sequence và audit data; client dùng snapshot đầy đủ làm nguồn sự thật, deduplicate notification theo sequence và không tự advance bracket.

Live turn count cập nhật khoảng một giây, không tăng revision hoặc ghi audit cho mỗi nước. Việc lấy dữ liệu giữ hướng khóa tournament→room, tránh callback giữ room rồi chờ tournament. Toast thông báo duyệt bot, mở check-in, start vòng/trận/giải và kết thúc. Không gửi email.

## Rules snapshot, database và recovery

Khi READY hoặc quick-start, lưu ruleSet, turnSeconds, maxTurns, pieceQuota, boardRows/Columns, scoring, tieBreakRules và autoAdvanceRounds. Các cấu hình giải bị khóa. Resume kiểm tra các tham số runtime game đang áp dụng; nếu đổi cấu hình server giữa giải, cần đưa runtime về snapshot trước khi tiếp tục.

**V14** thêm tournament_audit_events với FK tournament/actor, unique tournament+sequence, thời điểm và before/after; thêm lock_version cho @Version. Audit append-only trong repository, lưu cùng tournament/matches/structure bằng một transaction. V1–V13 không sửa. Round/stage/bracket/standing vẫn dùng cấu trúc versioned trong structure_json và tính từ các entry lưu trong DB; không duy trì một bảng standings thứ hai có thể lệch kết quả. Dữ liệu V13 không có operations sử dụng defaults.

Mutation quản trị snapshot cả match fields và operations; lỗi commit rollback ngay trong object hiện tại, để request worker cũ không trỏ vào một match đã bị thay thế. Snapshot/parent row dùng khóa, revision và @Version; cùng revision có cấu trúc khác bị từ chối. Entry kết thúc, advancement và audit được checkpoint trước khi phát notification/launch vòng sau. Nếu commit completion thất bại, giải RECOVERY_REQUIRED và ngừng launch mới; persistence giữ snapshot để retry lưu.

Startup không tự chạy lại giải RUNNING/PAUSED. Chuyển RECOVERY_REQUIRED, audit SERVER_RESTART; entry RUNNING bị đánh dấu SERVER_RESTART. Chủ giải review/retry lỗi rồi resume. Kết quả FINISHED, rule snapshot, quyết định hành chính và audit được giữ qua restart. Reservation của trận kết thúc/xử thua/nhánh không còn game được giải phóng, không giữ slot chờ vô hạn.

Backup vẫn cần DB + toàn thư mục bots/versions + replay. Đã tạo backup native trước V14. Quy trình này hỗ trợ một server điều phối đang hoạt động; @Version/revision chống ghi snapshot xung đột, không biến registry/hàng đợi trong RAM thành hệ thống worker phân tán nhiều server.

## API quản trị

Các POST dưới /api/tournaments/{id} cần owner/ADMIN, session, CSRF; request gửi revision:

```text
/control/pause
/control/resume
/control/next-round
/control/finish            (reason)
/round-policy              (autoAdvanceRounds)
/participants/{pid}/status (status, reason)
/matches/{mid}/review      (reason, keepResult)
/matches/{mid}/retry       (reason)
/matches/{mid}/override    (reason, winnerParticipantId nullable cho DRAW)
```

GET /review cần manager. GET /report và export là dữ liệu giải công khai. GET /api/account/tournaments?filter=created|participated|finished|won|all cần đăng nhập. GET /api/tournaments nhận format/status/owner tùy chọn.

## Kiểm thử ngày 14/10/2026

**480 backend passed, 0 failed/errors/skipped; 31 client passed, 0 failed/skipped. Có 12 migration tests PostgreSQL trong bộ backend, đều passed. Cú pháp 40 module JavaScript được kiểm tra; bootJar build thành công.**

- Pause cho trận hiện tại kết thúc nhưng không launch mới; round manual, resume và retry gates.
- DQ/forfeit cả hai thể thức, double forfeit, giữ participant/history và không tạo replay giả.
- Review/override có old/new/reason/actor, stale revision, chặn downstream, commit rollback khôi phục match/audit.
- Session ownership, các API mới trả đúng 401/403/409; không nhận actor/owner giả từ client.
- Audit append-only, unique sequence, actor FK, nâng từ V13, @Version/revision conflict, PostgreSQL restore.
- Infrastructure failure không trở thành bot loss; source/game/timeout/protocol tests hiện có tiếp tục passed.
- **Demo 8 tài khoản thật:** tạo SE8, mở đăng ký, mỗi user gửi bot, duyệt, check-in, lock version/rules, start, spectator read-only, đủ 7 trận, champion, replay tất cả trận, export và account history.
- **Docker load:** RR8 đủ 28 trận, SE16 đủ 15, SE32 đủ 31; capacity tối đa 4, không double advancement/trận kẹt, lưu bracket/history/replay và timestamps. Cùng bộ test còn RR5/SE6 để kiểm tra BYE. Bot fixture cố ý trả lỗi/thoát để kiểm tra xử thua nhanh; không phải benchmark chất lượng chiến thuật.
- **Native:** pause sau vòng 1, restart thành recovery, resume qua UI, mở từng vòng, đủ 6 trận; review/override giữ replay gốc; DQ một bot ở loại trực tiếp. Restart lần nữa đối chiếu standings/rules/audit/history. Kiểm tra các tab dashboard, replay, report, hồ sơ và bộ lọc.

GameEngine, RuleSet và các package game/rule/rules không sửa.

## File chính thêm/sửa

Thêm TournamentAdministrationService, TournamentAudit, ParticipantStatus, TournamentOperations, TournamentAuditEvent, TournamentAuditEntity, TournamentAdministrationController, AccountTournamentController, BotInfrastructureException và migration V14.

Sửa OwnedTournamentService/TournamentService/StateMachine/Match, execution/WorkerManager/ExecutionQueue, TournamentDetails/Converter/Entity/RepositoryAdapter/Coordinator, SpectatorService; BotUploadService/DockerBotProcess/LocalProcessRunner/OnlineMatch/Service; AuthErrorHandler và TournamentController.

Client thêm tournament-dashboard.js, tournament-list-model.js, my-tournaments.js; sửa tournament.html/tournaments.html/profile.html, tournament-detail/list/api, bracket renderer, CSS và toast. Tests thêm Administration/API/EightUserDemo/ExecutionControl/DockerFailureClassification và client admin/list/live-update; mở rộng Docker/PostgreSQL/Flyway/UploadedBotMatch/lifecycle tests. Cập nhật README, DEPLOYMENT và báo cáo này.
