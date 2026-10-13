# Bot theo tài khoản — Chặng 4, Nhóm 4

Metadata bot nằm trong PostgreSQL, bảng `bots` đã được tạo bởi migration V2. Source và metadata runner vẫn nằm trong `rps.bots.directory` (mặc định `server/data/bots` khi chạy từ thư mục server). Cần giữ **cả database lẫn thư mục artifact** khi restart, chuyển máy hoặc sao lưu. Không thêm migration và không sửa checksum V1–V7 trong nhóm này.

## Đăng nhập trước khi dùng bot riêng

Chơi tay và đấu với bot có sẵn vẫn dùng được cho khách. Upload, quản lý, dùng bot riêng trong hai chế độ và gửi bot vào giải cần tài khoản đăng nhập. Chỉ chủ bot hoặc ADMIN có quyền dùng/đổi tên/xóa bot đó. Chủ giải có capability quản lý roster của giải, nhưng không được lấy quyền dùng riêng bot của người tham gia.

Trang login/register sẽ được nối vào client ở Nhóm 8. Hiện có thể đăng ký/đăng nhập bằng API theo [AUTH.md](AUTH.md), hoặc dùng helper trên **cùng origin của server** trong DevTools Console:

```js
const auth = await import('/js/api/auth-api.js');
// Chỉ đăng ký nếu chưa có tài khoản, thay các placeholder bằng dữ liệu riêng.
await auth.register({username: '<username>', email: '<email>',
  displayName: '<tên hiển thị>', password: '<mật khẩu riêng dài tối thiểu 12 ký tự>'});
await auth.login('<username>', '<mật khẩu riêng>');
location.href = '/bots.html';
```

Helper lấy CSRF mới và gửi cookie same-origin; không lưu mật khẩu hoặc session vào localStorage. Sau khi đăng nhập, trang Bot của tôi/lobby/giải đấu tải danh sách từ server. Đăng nhập cùng tài khoản trên thiết bị khác sẽ nhận cùng danh sách. Không cần chuyển token bot sang thiết bị đó.

## API và dữ liệu trả về

- `GET /api/bots/capabilities`: public, trạng thái Docker/image, giới hạn file.
- `POST /api/bots`: multipart `name`, `file`, session và CSRF; trả **201**.
- `GET /api/account/bots`: bot chưa xóa của tài khoản hiện tại. ADMIN cũng chỉ nhận danh sách của chính mình ở endpoint này.
- `GET /api/account/bots/{id}`: metadata, owner hoặc ADMIN.
- `PATCH /api/account/bots/{id}`: JSON `{ "name": "Tên mới" }`, owner hoặc ADMIN, CSRF. Không sửa owner/language/source/status bằng body.
- `DELETE /api/account/bots/{id}`: owner hoặc ADMIN, CSRF; trả **204**, đánh dấu `DELETED`.
- `POST /api/account/bots/import`: JSON `{ "botId": "UUID cũ", "botToken": "token cũ" }`, session và CSRF; nhập artifact cũ chưa có owner.

DTO bot chỉ có `id`, `name`, `language` (`PYTHON`, `CPP`, `JAVA`, `JAR`), `status`, `createdAt`, `updatedAt`. Không trả token nội bộ, source path, lệnh Docker, mật khẩu/hash hoặc source. Status `UPLOADED` nghĩa là đã lưu, **chưa đảm bảo compile/chạy thành công**. Biên dịch vẫn diễn ra khi khởi động runner trong trận; không thêm endpoint build/log riêng trong nhóm này.

Tên 1–60 ký tự sau khi strip, không có control character; source tối đa 512 KiB, JAR 5 MiB. Kho artifact giữ giới hạn 100 thư mục, bao gồm bot đã xóa mềm. Không tự xóa file để giải phóng dung lượng; vận hành/quota/retention sẽ thực hiện trong nhóm sau.

`POST /api/matches`, `POST /api/matches/{id}/join`, `POST /api/tournaments/{id}/participants` gửi **botId**. Server lấy account từ session và kiểm tra owner/ADMIN. `botToken` cũ trong các body này không còn là quyền dùng bot; kể cả biết token thật của người khác cũng bị từ chối.

CSRF áp dụng cho upload/account mutations và mọi mutation match/tournament khi request có session đăng nhập. Client tự lấy token; script HTTP phải lấy `GET /api/auth/csrf` rồi gửi header được trả về. Guest chơi tay/bot sẵn vẫn giữ flow seat token. `401 UNAUTHENTICATED`, `403 BOT_ACCESS_DENIED/CSRF_INVALID`, `404 BOT_NOT_FOUND`, `409 BOT_ALREADY_OWNED/RESOURCE_CONFLICT`, `503 BOT_SANDBOX_UNAVAILABLE` là các lỗi chính.

## Nhập bot cũ không mất dữ liệu

Không quét thư mục rồi tự gán tất cả bot cho người đăng nhập đầu tiên.

1. Giữ nguyên thư mục artifact và localStorage cũ `rps-uploaded-bots`.
2. Đăng nhập tài khoản sẽ sở hữu bot, mở Bot của tôi trên trình duyệt cũ.
3. Bấm **Nhập bot cũ từ trình duyệt**. Mỗi bot gửi id/token cũ tới `LegacyBotImportService`; chỉ sau khi server nhập thành công mới bỏ entry capability tương ứng trong localStorage.
4. Nhập lại cùng bot cho cùng owner là idempotent khi token đúng. Bot đã có owner khác không thể chuyển quyền bằng token. Bot đã xóa không thể được import để phục hồi.

Có thể gọi `POST /api/account/bots/import` trực tiếp khi đã giữ id/token ở bản sao lưu. Nếu metadata/source thiếu hoặc token sai, import thất bại và không tạo row mới. Nếu đã mất token/trình duyệt cũ, cần người quản trị kiểm tra bản sao lưu và chứng cứ sở hữu; không có API public khôi phục token/chuyển owner. Không gán đại hoặc xóa artifact không nhận diện được. Source và metadata legacy không bị di chuyển hay ghi đè khi import.

Hai tài khoản claim cùng artifact đồng thời được bảo vệ bằng **INSERT** và primary key UUID; không dùng merge để ghi đè owner. Rename/delete khóa row trong transaction. Upload mới có callback dọn đúng thư mục mới nếu transaction rollback. Đây là compensation khi tiến trình còn hoạt động; crash giữa ghi artifact và commit DB có thể để lại orphan, phải đối soát bằng backup/quy trình phục hồi ở Nhóm 10.

## Xóa mềm và giải đấu

Xóa chỉ đặt status `DELETED`, giữ row, owner và source. Bot không xuất hiện trong My Bots và không được dùng để tạo trận/đăng ký giải mới. Bot đã được giải đăng ký trước đó giữ artifact/capability nội bộ và tên snapshot; giải có thể tiếp tục thi đấu. Chủ bot vẫn có thể rút participant của bot đã xóa khỏi giải còn DRAFT bằng account, hoặc chủ giải gỡ bằng capability quản lý giải.

Ownership/persistence của bản thân giải đấu sẽ chuyển sang account ở Nhóm 6. Seat token và WebSocket đang mở chưa gắn account đầy đủ; đổi password/disable thu hồi HTTP session và ngăn dùng bot trong request mới, chưa đóng trận/socket đang chạy. Không thêm Bot vs Bot thành chế độ UI; giữ Hai người và Đấu với bot có sẵn.

## Kiểm thử

Backend gồm list theo account, owner/ADMIN, stolen token, guest flow, CSRF qua MockMvc và HTTP thật, legacy proof/idempotency/conflict, soft delete giữ file/roster. `BotRepositoryPostgresTest` dùng PostgreSQL 17 trong Docker kiểm tra commit, claim đồng thời và rollback/missing source. Bộ Flyway V1–V7 và Docker runner C++/Python/Java/JAR tiếp tục chạy trong full suite. Script `verify-uploaded-bots.mjs` và `verify-tournaments.mjs` tự đăng ký account riêng, giữ cookie và CSRF; chỉ dùng server kiểm thử riêng vì tạo dữ liệu thật.


## Lịch sử khi bot chơi thay (Nhóm 5)

Metadata ghế lưu riêng userId sở hữu và botId/controllerType điều khiển. Kết quả trận bot chơi thay được tính cho account nhận ghế khi tạo/join. Với trận scheduler của giải, owner ghế lấy từ account sở hữu bot. Self-play cùng account không tăng wins/losses; xóa mềm bot không mất lịch sử đã commit. Xem [MATCH-HISTORY.md](MATCH-HISTORY.md).
