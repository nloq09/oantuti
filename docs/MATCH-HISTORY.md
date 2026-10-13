# Lịch sử trận và thống kê tài khoản — Nhóm 5 Chặng 4

Nhóm 5 lưu metadata trận online vào PostgreSQL. `GameEngine`, `GameState`, `RuleSet` và luật chơi không được chuyển thành JPA entity. Lịch sử đã commit tồn tại qua restart; bàn cờ đang chơi vẫn nằm trong RAM.

## Luồng ghi dữ liệu

1. Tạo/vào phòng qua REST: server lấy tài khoản từ session, gắn `seatUserId` và bot đã được phân quyền vào ghế. Body/query `userId` không được dùng để nhận chủ ghế. Khách có userId null; đăng nhập sau khi đã nhận ghế khách không tự claim lại ghế đó.
2. Mỗi phòng có **archive UUID** riêng, khác mã phòng ngắn. Response tạo/join trả thêm `historyId`; `matchId` vẫn là mã phòng cho WebSocket và UI đang chơi. Mã phòng được dùng lại sau restart không ghi đè lịch sử cũ.
3. Khi lifecycle thay đổi, coordinator chụp metadata bất biến dưới monitor phòng, sau đó ghi DB **ngoài monitor**. Poll mặc định 500 ms (`rps.history.flush-millis`) bổ sung capture cho timeout/bot/lifecycle nội bộ.
4. Một transaction lưu row `matches` và roster `match_participants`. Kết quả FINISHED bất biến; thông báo kết thúc lặp hoặc snapshot cũ không đổi kết quả và không tăng số trận.
5. Thống kê query trực tiếp metadata đã commit, không duy trì table/counter stats riêng. API lịch sử/stats flush pending trước khi query.

DB tạm lỗi: snapshot pending được giữ để thử lại, có cảnh báo không chứa credential. Một row ghi lỗi không ngăn các row khác được thử ghi. API trả `503 MATCH_PERSISTENCE_UNAVAILABLE` nếu chưa flush được, hoặc `DATABASE_UNAVAILABLE` nếu query DB thất bại. Snapshot mới đến trong lúc ghi không bị xóa cùng snapshot cũ. Snapshot kết thúc được chụp trước khi tournament release phòng khỏi registry.

Pending queue hiện ở RAM. Crash hoặc kill tiến trình trong khoảng trước commit có thể mất phần chưa ghi; đây chưa phải durable outbox/worker hay phục hồi trận đang chơi. Shutdown bình thường có flush hook; crash recovery đầy đủ thuộc Nhóm 9–10. Dữ liệu đã commit vẫn ở PostgreSQL, nhưng restart không tiếp tục bàn cờ, ghế, kết nối WebSocket hoặc deadline cũ. Trạng thái chưa FINISHED được giữ trong lịch sử, chưa tự đánh dấu INTERRUPTED/resume ở nhóm này.

## Metadata và ownership

`matches`: archive id, room id, mode, status, player1UserId, player2UserId, winnerUserId, winnerSeat, outcome, turnCount, startedAt, finishedAt, durationMs, ruleSet, createdAt.

`match_participants`: matchId, seat P1/P2, seatUserId, seatBotId, controllerType `HUMAN`, `BUILTIN_BOT`, `UPLOADED_BOT`.

- User sở hữu ghế và agent điều khiển là hai khái niệm khác nhau. Người dùng chọn bot chơi thay thì thắng/thua được tính cho người sở hữu ghế.
- ADMIN dùng bot của người khác trong phòng thường: chủ ghế là ADMIN đang đăng nhập; bot id vẫn trỏ tới bot đó.
- Trận được scheduler giải tạo: ghế gắn với owner của bot đã đăng ký. Artifact legacy chưa claim có owner/botId trong roster DB null, không được tự gán vào một account.
- Bot có sẵn và ghế khách không có user account. Khách/bot sẵn thắng có winnerSeat nhưng winnerUserId null; tài khoản bên thua vẫn nhận LOSS chính xác.
- Xóa mềm bot không xóa FK/metadata trận hoặc thống kê cũ.
- `turnCount` là `turnNumber` mà engine ghi khi chụp/kết thúc, không đếm số JSON/WebSocket message. Duration chỉ có khi đã có startedAt và finishedAt, đơn vị millisecond, tối thiểu 0.

## API

Các endpoint sau cần cookie session; luôn dùng account hiện tại, cả ADMIN cũng chỉ thấy lịch sử của chính mình qua các endpoint `/api/account` này.

### GET /api/account/matches?filter=all&page=0&size=20

Trả `{matches, total, page, size}`, thứ tự createdAt giảm dần, id tăng dần để phân trang ổn định. Page 0–100000, size 1–100. Filter hợp lệ:

- `all`: tất cả metadata còn tham gia, gồm cả trận chưa kết thúc và self-play.
- `win`, `loss`, `draw`: trận FINISHED đã biết kết quả, theo góc nhìn account, loại self-play.
- `human`: toàn bộ ghế do người điều khiển.
- `bot`: có ít nhất một bot, kể cả bot có sẵn hoặc bot của đối thủ; không chỉ xét mode phòng.

Trường một trận: `id` (archive UUID), `roomId`, `mode`, `status`, `outcome` theo account (`WIN`, `LOSS`, `DRAW`, `SELF_PLAY`, `UNKNOWN` hoặc null), winnerSeat, turnCount, timestamps, durationMs, ruleSet và participants. Participant gồm seat, userId, botId, controllerType. DTO không có token ghế/bot, password/email/hash, source path, Docker command, board, legal actions hoặc toàn GameState.

`UNKNOWN` dành cho metadata WIN cũ không xác định được ghế thắng; không tự đoán thắng/thua hoặc cộng stats. Null outcome dành cho trận chưa FINISHED hoặc lỗi hạ tầng.

### GET /api/account/matches/{historyId}

Đọc một archive theo UUID `historyId`; owner của ít nhất một ghế mới được đọc. Account khác hoặc id không có trả `404 MATCH_HISTORY_NOT_FOUND`. Không dùng mã phòng ngắn làm khóa archive mới.

### GET /api/account/stats

Trả `matchesPlayed`, `wins`, `draws`, `losses`, `winRate`, `totalTurns`. Chỉ tính FINISHED có kết quả hợp lệ. Không tính WAITING/PLACEMENT/PLAYING, lỗi hạ tầng, kết quả UNKNOWN hoặc hai ghế cùng một account. Mỗi archive chỉ tính một lần.

Win rate = wins / matchesPlayed × 100%, làm tròn 2 chữ số; 0 nếu chưa có trận. Ví dụ 1 thắng, 1 hòa, 1 thua: matchesPlayed 3, winRate 33.33.

## Thử bằng client helper

Đăng nhập trước khi tạo/vào trận theo [AUTH.md](AUTH.md), dùng chính browser/session đó để chơi. Sau khi kết thúc, DevTools Console trên origin server:

```js
const account = await import('/js/api/account-api.js');
await account.accountMatches('all', 0, 20);
await account.accountMatches('win', 0, 10);
await account.accountStats();
// Lấy id từ matches hoặc historyId trong response tạo/join:
await account.accountMatch('<historyId>');
```

Helper dùng cookie same-origin, GET cache no-store, không gửi account id hoặc lưu thêm token. Giao diện Login/Profile/History sẽ làm ở Nhóm 8; nhóm này triển khai API/helper và kiểm thử.

## Migration V8

Giữ nguyên V1–V7. V8 thêm room_id và winner_seat, backfill dữ liệu cũ có thể xác định được từ winner_user_id/player1_user_id/player2_user_id, bổ sung index history theo account/time. Không xóa bảng, dữ liệu hoặc dùng Hibernate ddl-auto update. Backfill room_id bằng id cũ để giữ correlation cho dữ liệu đã có.

Tournament ownership/persistence đã có ở Nhóm 6; [replay persistence](REPLAY-PERSISTENCE.md) ở Nhóm 7 lưu file và metadata DB, đọc bằng archive UUID trong lịch sử sau restart. Metadata trận chưa kết thúc không chứa replay hoàn chỉnh. Session/seat binding lưu owner cho history nhưng chưa thay thế capability token hoặc cơ chế đóng WebSocket đang mở khi disable/đổi password.
