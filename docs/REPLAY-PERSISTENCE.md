# Lưu replay — Chặng 4, Nhóm 7

Trận thường và trận giải đấu đã kết thúc được lưu thành file JSON trong `REPLAY_DIRECTORY` (mặc định `server/data/replays` khi chạy ở thư mục server). PostgreSQL lưu metadata trong bảng `replays`: UUID, match archive ID, tên file tương đối, số event, trạng thái valid, thời gian tạo và SHA-256. Migration V9 bổ sung digest; không sửa checksum migration cũ.

## Đọc replay

`GET /api/matches/{id}/replay` giữ nguyên payload cho web player: matchId (mã phòng), ruleSet, seed, initialState, events, result, finalState, players. `id` nhận archive UUID từ `historyId` lúc tạo/join trận hoặc `id` trong API lịch sử. Trang kết quả giữ historyId để mở replay đúng trận; link replay của giải dùng `historyMatchId`; live spectator tiếp tục dùng mã phòng.

Mã phòng cũ vẫn được hỗ trợ qua metadata trận mới nhất. Nếu mã phòng được dùng lại mà trận mới chưa có replay, server không trả replay trận cũ. Archive UUID luôn chỉ đúng một trận, nên nên dùng UUID cho đường dẫn được chia sẻ/lưu lâu dài.

Replay và spectator tiếp tục là chức năng đọc công khai của game/giải PUBLIC hiện tại, không yêu cầu đăng nhập. Không trả token seat/bot, đường dẫn file, digest hay thông tin đăng nhập. API lịch sử tài khoản vẫn chỉ cho chính tài khoản đọc. Bản này chưa có trận/giải PRIVATE; cần thêm kiểm tra quyền cả REST và WebSocket trước khi hỗ trợ PRIVATE.

## Luồng lưu và kiểm tra

1. Khi trận kết thúc, listener chụp MatchLog, snapshot cuối và metadata lịch sử dưới khóa phòng; không ghi file/DB tại listener.
2. Tác vụ mỗi 500 ms ghi file tạm, kiểm tra bằng ReplayService áp dụng event facts và so trạng thái cuối, thêm snapshot/version/archiveId, tính digest, rồi đổi tên file atomic trên cùng volume.
3. Transaction lưu metadata trận và replay cùng nhau để đảm bảo FK. Kết thúc lặp không tạo nhiều replay. Queue giữ bản chụp khi ghi thất bại để thử lại dù phòng đã được giải phóng.
4. Khi đọc, server flush queue, tra DB, kiểm tra file/path/digest/version/identity/eventCount và dựng lại trạng thái cuối từ events. Dữ liệu được nạp theo yêu cầu; không cần khôi phục bàn cờ hay bot trong RAM. API không cache response.

File dùng MatchLog JSON hiện có; snapshot web là phần bổ sung, không chứa agent. ReplayService thêm overload xuất MatchLog, đọc bytes và sửa validator để nhận surrender ngoài lượt đã được game hỗ trợ: chỉ cho chuỗi kết thúc, không cho event move/combat/turn-change ngoài lượt. GameEngine và luật chơi không thay đổi.

## Lỗi

- `400 REPLAY_NOT_READY`: chưa có replay cho ID, gồm trận chưa kết thúc hoặc ID không tồn tại.
- `404 REPLAY_FILE_MISSING`: metadata có nhưng file không còn; đánh dấu valid=false.
- `422 REPLAY_INVALID`: file đổi digest, sai schema/lifecycle/kết quả, filename/path không hợp lệ hoặc metadata đã invalid. File hỏng không được phục vụ từ cache RAM.
- `503 REPLAY_PERSISTENCE_UNAVAILABLE`: còn archive chưa ghi được; queue giữ lại để retry. Lỗi database khi đọc trả `503 DATABASE_UNAVAILABLE`.

Metadata V5–V8 chưa có digest được giữ nhưng đánh dấu invalid khi nâng V9. Replay của bản cũ chỉ nằm trong RAM không thể tự khôi phục sau restart. Trận mới từ bản này có đủ file và metadata.

## Vận hành

Đặt `REPLAY_DIRECTORY` tới thư mục trên ổ/volume bền vững và có quyền ghi, không qua symlink/junction. Tên file là `<archive UUID>.json`; giá trị ID client không được dùng làm đường dẫn file. File đọc giới hạn 16 MiB.

Backup PostgreSQL và thư mục replay cùng nhau. Sau khi phục hồi file đúng từ backup, đối chiếu SHA-256 trong DB rồi mới đặt valid=true; không bỏ kiểm tra digest để làm file hỏng đọc được. Không tự xóa file hoặc row khi đọc lỗi.

Chạy một server cho thư mục/DB hiện tại. Queue retry vẫn ở RAM: kill/power loss trước commit có thể mất replay chưa lưu; file ghi xong nhưng DB chưa commit có thể trở thành orphan. Atomic rename không thay thế fsync/backup. Durable queue, quota/retention và quy trình recovery đầy đủ thuộc Nhóm 9–10. Trận đang chơi vẫn không tiếp tục được sau restart.
