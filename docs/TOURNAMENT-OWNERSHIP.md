# Giải đấu theo tài khoản

Giải đấu, roster, lịch đấu và kết quả lưu trong PostgreSQL. Tài khoản đăng nhập sở hữu giải; token trong localStorage không còn cấp quyền quản lý. Bảng xếp hạng được tính từ kết quả đã lưu, không có counter/ranking table phải tăng theo mỗi notification.

## Quyền và đăng ký mở

- Khách được xem/list/export giải PUBLIC, xem ranking và theo dõi WebSocket/spectator.
- Tạo giải cần tài khoản đăng nhập và CSRF. Owner lấy từ principal, không nhận ownerUserId/role từ body.
- Khi DRAFT hoặc REGISTRATION, người dùng khác được đăng ký **bot của họ** vào giải. Owner không được tự dùng bot private của người khác; ADMIN có quyền dùng bot theo chính sách bot ownership.
- Chủ bot có thể rút bot của mình khi DRAFT/REGISTRATION, kể cả bot đã soft-delete hoặc artifact bị mất. Chủ giải hoặc ADMIN có thể gỡ bất kỳ participant trước khi khóa đăng ký.
- Chỉ chủ giải hoặc ADMIN được start/cancel. Các header `X-Tournament-Token`, `X-Bot-Token` và capability cũ không cấp quyền tại API.
- Bản này chỉ hỗ trợ PUBLIC. Yêu cầu tạo PRIVATE bị từ chối rõ ràng; những row PRIVATE có sẵn trong DB không bị nạp thành PUBLIC hoặc lộ qua API/registry. Quyền private đầy đủ có thể bổ sung sau.

Account phải đăng nhập trước khi tạo/tham gia, theo [AUTH.md](AUTH.md). Client nhận ownerUserId từ server và đọc current account để hiển thị nút; backend luôn kiểm tra quyền lại, không tin role/nút phía client.

## Lưu và khôi phục

`tournaments`: UUID, ownerUserId, name, ROUND_ROBIN, status, visibility, revision và timestamps.

`tournament_participants`: UUID, tournamentId, botId, ownerUserId của bot, displayName snapshot và seed. Roster sắp theo participant UUID để không phụ thuộc thứ tự đọc DB sau restart.

`tournament_matches`: UUID ổn định theo tournament/cặp participant, FK tới archive UUID của Nhóm 5, hai participant, winner, outcome, failure, số quân còn và timestamps. Mã phòng ngắn phục vụ live được đọc từ metadata match; link replay dùng historyMatchId (archive UUID), không dùng mã phòng làm FK lịch sử.

Schema hiện tại tới V10. V10 mở rộng lifecycle và thêm status_changed_at; không sửa migration V1–V9 đã áp dụng. Xem [vòng đời chặng 5](CHANG-5-NHOM-1-LIFECYCLE.md).

Create/add/remove/start/cancel ghi transaction trước khi trả thành công hoặc phát notification. Khi commit thất bại, trạng thái quản lý trong RAM được rollback; scheduler không được nhận một lệnh start chưa commit. Transaction snapshot lưu parent/roster/schedule và metadata trận liên quan cùng database transaction; FK composite ngăn participant của giải khác lọt vào cặp đấu.

Kết quả chạy được capture theo revision và ghi qua coordinator, mặc định mỗi 500 ms (`rps.tournament.flush-millis`). Snapshot cũ hơn revision DB bị bỏ qua. DB tạm lỗi giữ pending và thử lại, không ghi token/password vào log. Pending còn RAM, chưa phải durable queue; crash trước commit có thể mất cập nhật chưa ghi. Worker/outbox, retry policy, monitoring và phục hồi đầy đủ thuộc Nhóm 9–10. Chạy **một server/worker** cho database này; chưa hỗ trợ nhiều server cùng scheduler hoặc distributed leadership.

Sau restart:

- **DRAFT/REGISTRATION/CHECK_IN và READY chưa có lịch:** nạp lại owner, roster, tên/seed và thời điểm đổi trạng thái. READY chờ start không chuyển thành recovery.
- **FINISHED/CANCELLED:** nạp lại lịch, kết quả và ranking, không chạy lại trận đã hoàn tất.
- **RUNNING/PAUSED hoặc READY có lịch đang dở:** chuyển thành **RECOVERY_REQUIRED**, giữ kết quả đã commit; cặp RUNNING cũ đánh dấu FAILED/SERVER_RESTART. Các cặp PENDING không tự được chạy. Owner/ADMIN có thể cancel và tạo giải mới; chưa có endpoint resume.

Không đoán thắng/thua cho trận gián đoạn, không tự cộng điểm hoặc chạy lại để bù. Metadata/account history đã commit giữ nguyên. Live room không tồn tại qua restart. Nhóm 7 đã lưu replay trận kết thúc bằng file/metadata; xem [REPLAY-PERSISTENCE.md](REPLAY-PERSISTENCE.md).

Token chạy bot chỉ được lấy từ artifact metadata trên server khi nạp roster; không lưu trong bảng giải, không trả qua list/detail/export/WebSocket. Thiếu artifact vẫn đọc được metadata/ranking; start thất bại với lỗi bot phù hợp. Không tự xóa roster, bot hoặc các kết quả cũ.

Giới hạn hiện có: mỗi giải 2–10 bot để start, tối đa 100 giải trong registry, 4 trận đồng thời toàn server. Quota/pagination/retention production là phần các nhóm sau.

## API

- `POST /api/tournaments`: `{name, format:"ROUND_ROBIN", visibility:"PUBLIC"}`; format/visibility có thể bỏ qua để dùng mặc định. Trả 201 với ownerUserId, không trả ownerToken.
- `GET /api/tournaments`, `GET /api/tournaments/{id}`: public metadata, roster, schedule, progress, ranking/champion.
- `POST /api/tournaments/{id}/participants`: `{botId, displayName?, seed?}`; account sở hữu bot hoặc ADMIN, DRAFT/REGISTRATION.
- `DELETE /api/tournaments/{id}/participants/{participantId}`: owner giải/ADMIN hoặc account sở hữu bot, DRAFT/REGISTRATION.
- `POST /api/tournaments/{id}/open-registration`, `/close-registration`: owner/ADMIN; mở đăng ký rồi khóa roster thành READY khi có ít nhất hai bot.
- `POST /api/tournaments/{id}/start`, `POST /api/tournaments/{id}/cancel`: owner giải/ADMIN.
- `GET /api/tournaments/{id}/ranking`, `GET /api/tournaments/{id}/export`: public, source of truth là kết quả persisted.

Mọi mutation cần session và CSRF. GET vẫn public. Helper `tournament-api.js` tự gửi cookie same-origin/CSRF; không đọc hoặc ghi capability của giải vào localStorage. Trình duyệt/thiết bị khác đăng nhập cùng account vẫn quản lý giải được.

Lỗi chính: `401 UNAUTHENTICATED`, `403 CSRF_INVALID/TOURNAMENT_FORBIDDEN/BOT_ACCESS_DENIED`, `400 TOURNAMENT_LOCKED/NOT_ENOUGH_BOTS/UNSUPPORTED_TOURNAMENT_VISIBILITY`, `503 DATABASE_UNAVAILABLE/TOURNAMENT_PERSISTENCE_UNAVAILABLE/BOT_SANDBOX_UNAVAILABLE`.

## Kiểm thử

`TournamentOwnershipTest` dùng HTTP/session thật để kiểm tra public reads, owner/ADMIN, token cũ/claimed role không vượt quyền, account khác đăng ký/rút bot và CSRF. `TournamentPersistencePostgresTest` dùng PostgreSQL 17 thật kiểm tra restore draft/finished, ranking, duplicate writes, recovery, transaction rollback qua FK và không lộ row PRIVATE. `OwnedTournamentRollbackTest` xác nhận start commit lỗi không thay đổi DRAFT hoặc phát notification. `DockerTournamentPersistenceTest` chạy bot Python thật trong Docker, xử forfeit rồi xác nhận kết quả giải, match history và stats đã commit và restore được.
