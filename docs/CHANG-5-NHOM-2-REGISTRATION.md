# Chặng 5 — Nhóm 2: đăng ký, duyệt và check-in

Đơn đăng ký tách khỏi roster được thi đấu. Người dùng gửi bot vào giải đang REGISTRATION; owner/ADMIN duyệt trước khi bot trở thành participant. Nếu yêu cầu check-in, chỉ bot đã xác nhận có mặt mới được giữ trong roster READY.

## Cách dùng

1. Tạo giải, chọn số bot tối đa (2–10) và bật **Yêu cầu check-in** nếu cần. Cấu hình được lưu ngay trong transaction tạo giải.
2. Bấm **Mở đăng ký**, gửi mã/link giải cho người khác. Người tham gia đăng nhập và đăng ký bot của mình; đơn hiện **Chờ duyệt**.
3. Chủ giải dùng **Duyệt** hoặc **Từ chối** từng đơn. Người tham gia có thể **Rút đăng ký**. Đơn đã rút/bị từ chối được gửi lại trong giai đoạn đăng ký, dùng lại ID để giữ tính duy nhất của bot trong giải.
4. Có ít nhất hai bot được duyệt, chủ giải **Khóa đăng ký**. Đơn còn chờ bị từ chối; không nhận bot mới nữa.
5. Nếu không yêu cầu check-in, giải chuyển READY. Nếu có, giải chuyển CHECK_IN; mỗi chủ bot bấm **Check-in** cho bot của mình. Owner/ADMIN không check-in thay người khác.
6. Có ít nhất hai bot đã check-in, chủ giải **Hoàn tất check-in**. Bot được duyệt nhưng không có mặt chuyển **NOT_CHECKED_IN**, bị loại khỏi roster. Sau đó bấm **Bắt đầu giải**.

CHECK_IN vẫn cho rút đăng ký, READY trở đi khóa thay đổi. Check-in lặp không ghi đè checkedInAt. Chưa có mở/đóng tự động theo giờ hoặc check-in sau khi giải bắt đầu.

## Tương thích và điều kiện bot

Luồng DRAFT thêm bot/bắt đầu nhanh trước đây vẫn giữ cho giải không yêu cầu check-in. Endpoint `/participants` trong REGISTRATION chuyển thành gửi đơn PENDING, không vượt qua bước duyệt. Giải yêu cầu check-in không được bắt đầu nhanh từ DRAFT. Roster cũ được migration thành đơn APPROVED; không làm mất các participant/kết quả cũ.

Bot phải thuộc đúng account gửi đơn, kể cả ADMIN không được gửi đơn tự nhận bot của người khác. File phải còn tồn tại, không phải symlink; trạng thái BUILD_ERROR/DELETED bị từ chối. Upload chưa READY được kiểm tra cú pháp/biên dịch trong Docker trước khi nhận đơn rồi lưu READY. Python chỉ parse/compile; C++ và Java source biên dịch; JAR kiểm tra cấu trúc archive. Bot READY được kiểm tra lại metadata/file khi duyệt.

Kiểm tra dùng sandbox giới hạn như runner: không network, readonly, nonroot, CPU/RAM/PID/tmp giới hạn, chỉ mount một source. Tối đa hai lượt kiểm tra đồng thời, quá tải trả SERVER_BUSY; mỗi lượt timeout 25 giây và cleanup container theo tên do server tạo. Không chạy compiler/interpreter của bot trên host. READY xác nhận source biên dịch/parse được; lỗi protocol, runtime hoặc nước đi vẫn được xử theo luật bot hiện tại khi chạy trận.

`botVersionId` lưu ID upload bất biến hiện tại. Chưa có hash integrity hoặc hệ thống nhiều phiên bản một bot; **khóa version/hash và seeding thuộc nhóm 3**.

## API

Tạo giải nhận thêm `checkInRequired` (mặc định false) và `maxParticipants` (mặc định 10, khoảng 2–10).

- `POST /api/tournaments/{id}/registrations`, body `{botId}`: chỉ REGISTRATION, bot thuộc user; kiểm tra duplicate/quota/readiness.
- `POST /api/tournaments/{id}/registrations/{registrationId}/approve`: owner/ADMIN, đơn PENDING → APPROVED, thêm participant.
- `POST /api/tournaments/{id}/registrations/{registrationId}/reject`: owner/ADMIN, PENDING/APPROVED → REJECTED, bỏ participant nếu có.
- `DELETE /api/tournaments/{id}/registrations/{registrationId}`: chủ đơn hoặc manager, REGISTRATION/CHECK_IN → CANCELLED.
- `POST /api/tournaments/{id}/check-in`, body `{registrationId?}`: account sở hữu đơn APPROVED, chỉ CHECK_IN. Không truyền ID thì check-in tất cả bot đã duyệt của account trong giải đó.
- `POST /api/tournaments/{id}/close-check-in`: owner/ADMIN; tối thiểu hai CHECKED_IN, loại APPROVED còn lại rồi chuyển READY.

List/detail/export/WebSocket nhận thêm cấu hình và danh sách `registrations`: id, tournamentId, userId, botId, botVersionId, displayName, status, registeredAt, approvedAt, checkedInAt. Không trả token/source path. Tất cả mutation cần session + CSRF, không lấy quyền từ ID user/role/token do client khai báo. ID registration luôn tra trong đúng giải.

## Database và lỗi ghi

Migration **V11__tournament_registrations.sql** thêm cấu hình giải và bảng đơn đăng ký. UNIQUE tournament/bot; FK tới tournament, user và bot; FK composite bot/user bảo vệ ownership; CHECK enum/capacity/version ID. Backfill roster cũ thành APPROVED với ID participant cũ. Không sửa V1–V10.

Quyết định duyệt, check-in, loại vắng mặt, roster và revision lưu cùng transaction giải; DB lỗi rollback các snapshot immutable trong RAM, không phát notification. Thời điểm duyệt/check-in và cấu hình giữ sau restart. Validation READY của bot là transaction metadata bot riêng; nếu bot đã kiểm tra thành công nhưng transaction giải thất bại, bot vẫn có thể READY nhưng đơn/roster không được nhận.

Backup cả PostgreSQL/source/replay trước migration; bản cục bộ có backup trước V11 trong thư mục ignored. Sau upgrade không dùng JAR cũ với DB mới; rollback cả snapshot theo [Operations](OPERATIONS.md).

## Kiểm thử

Kết quả ngày 14/10/2026:

- Backend: **425 passed, 0 failed, 0 skipped**.
- Client: **23 passed, 0 failed, 0 skipped**.
- Migration: **9 passed** trong bộ backend, PostgreSQL thật và JPA/H2 validation đều thành công.
- `bootJar`: thành công; server localhost đã chạy bản mới.
- Trình duyệt: duyệt ba đơn, mở check-in, xác nhận hai bot, hoàn tất; bot thứ ba hiện Không check-in và không nằm trong roster. Không có JavaScript error.
- Server thật: dữ liệu/quyết định/timestamps giữ đúng qua hai lần khởi động; source hợp lệ chuyển READY, source lỗi cú pháp trả BOT_BUILD_ERROR và không tạo đơn. Health/request ID/private metrics/admin/WebSocket rate limit vẫn pass.
- Diff GameEngine/RuleSet và package game/rule/rules: không đổi.

Kiểm tra API nhiều account, spoof quyền, duplicate/quota, đăng ký đồng thời, duyệt/rút/gửi lại, ID của giải khác, soft-delete/file thiếu, check-in của chủ bot, lặp check-in, bỏ bot vắng mặt và khóa READY. PostgreSQL kiểm tra backfill/FK/config/timestamps qua restore; mô phỏng DB lỗi để xác nhận rollback review/close/check-in. Docker kiểm tra source hợp lệ và lỗi cú pháp Python/C++/Java, JAR mẫu; Python readiness không thực thi code người dùng.

GameEngine, RuleSet và các package game/rule/rules không thay đổi. Round Robin, scoring, worker/queue và replay hiện có giữ nguyên.

Regression phát hiện race cũ: history và replay cùng ghi archive chưa tồn tại có thể gây trùng primary key. Bổ sung khóa theo archive ID giữ tới commit/rollback transaction; khóa chỉ tồn tại khi còn writer đang dùng/chờ, không giữ một map cho mọi trận trong lịch sử. Kiểm thử PostgreSQL giữ transaction đầu chưa commit, cho writer thứ hai chờ rồi kiểm tra cả commit và rollback đều tạo đúng một lịch sử/kết quả. Chính sách vẫn một JVM/server như triển khai hiện tại.

## File thêm/sửa

Thêm domain `TournamentRegistration`, persistence `TournamentRegistrationEntity`, migration V11, `TournamentRegistrationApiTest` và `client/tests/tournament-registration.test.js`.

Sửa `Tournament`, `TournamentService`, `OwnedTournamentService`, `TournamentController`; persistence `TournamentMetadata`, `TournamentEntity`, `TournamentRepositoryAdapter`, `TournamentPersistenceCoordinator`; readiness `OwnedBotService`, `BotUploadService`; tests `DockerBotIntegrationTest`, `TournamentLifecycleApiTest`, `OwnedTournamentRollbackTest`, `TournamentPersistencePostgresTest`, `FlywayMigrationTest`, `JpaFoundationTest`.

Client: `tournament.html`, `tournaments.html`, `tournament-api.js`, `tournament-detail.js`, `tournament-list.js`, `toast.js`. Tài liệu: README, Deployment, Tournament Ownership và báo cáo này.

Sửa regression lịch sử: thêm `history/persistence/MatchWriteLocks`, sửa `MatchHistoryRepositoryAdapter` và `MatchHistoryPostgresTest`.

Nhóm tiếp theo là **Bot version lock + Seeding**. Dừng sau nhóm 2 theo kế hoạch chặng 5.
