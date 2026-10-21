# Chặng 12 — Nhóm 8: My Match và Match Monitor

## Chức năng

Người chơi mở detail/lobby của giải đang thi đấu sẽ có thẻ **Trận của tôi** và tab cùng tên. Thẻ tách tên tài khoản với tên bot, hiện ID phiên bản khi có dữ liệu, vòng/ván BO, trạng thái execution, máy thi đấu, số lần nhận việc, thứ tự chờ và thời gian gameplay theo snapshot server. Trận hiện tại/chưa kết thúc được ưu tiên; lịch sử hoàn tất xếp vòng gần nhất trước. Người dùng có nhiều suất đăng ký vẫn xem được mọi trận thuộc các suất của mình, phân trang 20 trận.

QUEUED, ASSIGNED, STARTING, RUNNING_REMOTE, RESULT_PENDING, FINISHED, RETRYING, RECOVERING, ERROR, CANCELLED và UNKNOWN dùng nhãn chữ chung. Local RUNNING có nhãn RUNNING_LOCAL; trận local PENDING giữ trạng thái chờ lịch/đối thủ, không giả vờ đã có remote job. Thời gian chỉ tính từ gameplay signal của remote hoặc startedAt của local, dùng serverNow khi chưa kết thúc; không dựng ETA.

Remote có thông báo chưa có live board và tự cập nhật kết quả. Nút chính chuyển thành theo dõi My Match. Không dùng onlineMatchId cũ để tạo liên kết xem trực tiếp cho execution remote. Replay chỉ mở khi có historyMatchId và trạng thái hoàn tất. Trận human/local giữ workflow vào phòng/sẵn sàng đang có. Bị loại/đi tiếp vẫn do participant state và bracket của server quyết định; không suy bị loại chỉ từ một trận thua ở Round Robin hoặc Swiss.

Chủ giải/admin mở `/tournament.html?id=...&view=matches` sẽ thấy **Match Monitor**, thay danh sách trận cũ trong khu quản lý. Dùng các thẻ có thể mở chi tiết để đọc trên cả màn hình hẹp: vòng/ván, người chơi, bot, trạng thái, worker, queue, số lần nhận việc, kết quả; chi tiết có ID trận/execution, phiên bản, hash artifact, lease, heartbeat, các mốc nhận việc/khởi động/gameplay/receipt, projection và nhóm lỗi chuẩn hóa. Lịch sử nhận việc được tải theo yêu cầu riêng, không tải toàn bộ lịch sử cho mọi thẻ.

Filter gồm chờ máy, đã cấp máy, khởi động, đang chạy, kết quả pending, retry, recovery, lỗi, hoàn tất, review. Các deep link FAILED/REVIEW từ Needs Attention vẫn hoạt động. Không thêm quản trị worker toàn hệ thống hoặc nút retry/override mới; workflow quản trị có reason/revision/confirmation đang có vẫn được giữ nguyên.

## API và quyền

- `GET /api/tournaments/{id}/my-matches?page=0&size=20&status=ALL`: yêu cầu session tài khoản; chỉ trả các trận mà tài khoản sở hữu một trong hai participant. Người không có trận nhận danh sách rỗng. Không có tham số để giả owner/user/participant.
- `GET /api/tournaments/{id}/match-monitor?page=0&size=20&status=ALL`: chủ giải/admin; tài khoản khác 403, guest 401.
- `GET /api/tournaments/{id}/match-monitor/{match}/attempts`: chủ giải/admin, kiểm tra match thuộc đúng giải trước khi đọc. Match ngoài scope trả 404, người không có quyền 403.

Các response no-store và Vary Cookie. page 0–10000, size 1–50; filter/paging sai trả 400. Filter áp dụng trước phân trang, counts trước filter. RUNNING gộp RUNNING_LOCAL và RUNNING_REMOTE; FAILED và REVIEW giữ nghĩa của Needs Attention. Response gồm tournamentId, revision, serverNow, page, size, total, counts, items, queuePositionNote.

Item gồm thông tin vòng/ván, status của trận, executionState, execution của nhóm 7, hai participant đã được chuẩn hóa, onlineMatchId chỉ cho local, historyMatchId, winnerParticipantId, decisionReason, failure category, các mốc trận, số lần nhận việc và reviewRequired. Không trả raw match object hoặc raw BotMetadata. Tên bot lấy từ metadata, ID phiên bản từ registration và hash từ frozen participant; nếu thiếu thì hiển thị thiếu dữ liệu, không đặt tên phiên bản v1/v12 giả. Tên hiển thị bot không phải snapshot lịch sử bất biến nếu bot đã đổi tên.

History chỉ gồm execution ID, trạng thái, worker display name, các mốc nhận việc/start/gameplay/lease/receipt, projection status và cờ terminal recovery. Không trả raw exception/recovery message, spec/result/replay JSON, đường dẫn source/artifact, credential, token hoặc địa chỉ máy. UI render tên và ID bằng textContent.

RemoteExecutionReadService tái sử dụng projection nhóm 7 bằng tập match ID đã được MatchMonitorService phân quyền. Điều kiện tournament ID và match IDs được áp dụng ngay trong SQL; không tải job của tài khoản/giải khác rồi lọc ở trình duyệt. Vị trí chờ trong My Match chỉ tính trong tập trận của tài khoản và ghi rõ phạm vi; trong Match Monitor là tập job chờ của giải. Cả hai không phải hàng đợi global, thứ tự worker chắc chắn claim hay ETA.

## Refresh và giới hạn

My Match cập nhật cùng vòng tải player hiện có, so sánh cả execution snapshot chứ không chỉ revision của giải. Tab Trận của tôi giữ nguyên instance monitor khi shell cập nhật để không reset filter/trang/chi tiết đang đọc. Monitor poll 5 giây, không chồng request; bỏ response cũ sau đổi filter/trang và từ chối snapshot có revision cũ hơn giải. Khi tab trình duyệt ẩn thì dừng polling monitor; khi hiện lại tải ngay. Pagehide dừng timer, BFCache restore khởi động lại kể cả khi request cũ chưa kết thúc, content bị gỡ sẽ dọn timer/listener. Mất mạng giữ snapshot cũ có cảnh báo; mất quyền xóa nội dung riêng tư. Lỗi API không dump raw exception. Khi mở chi tiết organizer, snapshot giữ nguyên và báo có thể cũ cho đến khi đóng/nhấn Cập nhật, tránh phá nội dung đang đọc.

Chỉ xử lý refresh cần thiết cho nhóm này; chuẩn hóa CTA/navigation/polling toàn ứng dụng thuộc nhóm 9. Không thay GameEngine, RuleSet, scheduler, chính sách kết quả, schema hoặc worker protocol. Vẫn dùng schema V36 và worker 0.11.0; worker cũ chưa báo gameplay giữ STARTING như nhóm 7.

Monitor đọc scalar execution facts của các trận trong scope để lọc/count rồi phân trang; metadata bot chỉ đọc cho các dòng của trang trả về. Chưa benchmark 4.000 người/đủ vòng Swiss; không công bố SLA hay ETA. HTTP smoke không thay cho kiểm tra bố cục trình duyệt hoặc một trận remote end-to-end trên server người dùng.

## File thêm/sửa

Thêm:

- client/js/tournament/match-execution.js
- client/js/tournament/match-monitor.js
- client/tests/my-match-card.test.js
- client/tests/tournament-match-monitor.test.js
- server/src/main/java/com/rpsbattle/tournament/MatchMonitorService.java
- server/src/main/java/com/rpsbattle/api/controller/MatchMonitorController.java
- server/src/test/java/com/rpsbattle/worker/remote/MatchMonitorTest.java
- server/src/test/java/com/rpsbattle/worker/remote/MatchMonitorPostgresTest.java
- Tài liệu này.

Sửa:

- client/js/tournament/player-detail.js, player-state.js, next-action.js, tournament-detail.js
- client/css/tournament-mode.css
- server/src/main/java/com/rpsbattle/tournament/RemoteExecutionReadService.java
- server/src/main/java/com/rpsbattle/tournament/TournamentService.java: cho phép service cùng package tái sử dụng tên tài khoản công khai.
- README.md

## Kiểm chứng

- PASS: 25 MatchMonitorTest trên H2, 25 MatchMonitorPostgresTest trên PostgreSQL Testcontainers, 1 ParticipantTournamentStateTest và 5 ParticipantWithdrawalAndPagingTest — tổng 56 backend, không failure/skip. Hai suite monitor gồm kiểm thử kế thừa từ remote read/claim/result/identity, không phải 50 ca mới hoàn toàn. Quyền admin khác chủ giải được kiểm tra bằng tài khoản test riêng.
- PASS: toàn bộ 194 kiểm thử client, không failure/skip; gồm 16 ca mới cho My Match, remote state/live/replay, filter response race, mất quyền, attempt history, visibility/unmount, giữ filter/trang và BFCache khi request đang chạy.
- PASS: bootJar, kiểm tra cú pháp hai entry module và git diff --check.
- PASS: HTTP smoke trên PostgreSQL/server thật với tài khoản fixture chủ giải, guest và tài khoản khác; API summary rỗng, paging/filter sai, quyền history/ID ngoài giải, trang detail/lobby/organizer và các asset sửa đổi. Tài khoản native fixture có tên admin hiện mang role USER nên không dùng để tuyên bố test admin thành công; không đổi quyền của tài khoản đó.
- Dữ liệu có job/attempt, RUNNING_REMOTE dù Central vẫn PENDING, RESULT_PENDING trước projection, replay sau FINISHED và nhiều execution trong retry history được kiểm chứng trong integration tests; smoke native không khởi chạy một trận remote worker thật.
- Test fixture read-only tắt timer checkpoint nền để tránh lưu các snapshot giả lập khác DB vào lúc đang kiểm tra trạng thái; projector/persistence cần thiết trong lifecycle test vẫn được gọi trực tiếp.
- SKIPPED: migration regression riêng vì không đổi DDL; các integration test khởi tạo schema hiện hành V36.
- SKIPPED: screenshot/layout trên browser — công cụ trả `Local browser use is disabled in Work settings`. Không dùng HTTP 200 hoặc DOM test để tuyên bố đã kiểm tra bố cục thực tế.
