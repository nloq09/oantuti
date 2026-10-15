# Chặng 7 — Admin UI, nhóm 1–5

## Truy cập

Chạy server như bình thường và đăng nhập tài khoản có role `ADMIN`: trình duyệt chuyển thẳng tới `/admin/index.html` (hoặc trang admin đã yêu cầu trước đó). ADMIN dùng riêng khu quản trị; mở trang chủ/lobby/bot/giải của người chơi sẽ được chuyển về quản trị. Role lấy từ DB; đăng ký không cho tự chọn ADMIN. Không thêm tài khoản/mật khẩu quản trị mặc định. USER nhận trang 403 khi mở admin; khách được chuyển tới login với đường dẫn quay lại đã kiểm tra.

Các API `/api/admin/**` kiểm tra quyền ở Spring Security và kiểm tra lại account đang hoạt động/role hiện tại trong service. Mutation dùng cookie session và CSRF, không lấy role hoặc owner từ request. Mọi response quản trị đặt `Cache-Control: no-store`.

## Phạm vi đã làm

1. **Shell:** dùng theme game (nền lâu đài, navy, viền vàng, nút xanh), topbar có tên quản trị viên và đăng xuất, sidebar có thể đóng/mở và đánh dấu trang hiện tại; skip link và focus bàn phím. Không có lối vào chơi game hoặc hồ sơ người chơi trong menu ADMIN. Các trang chưa triển khai ghi “Sắp có”.
2. **Dashboard:** số tài khoản, người đang đăng nhập, bot chưa lưu trữ, phòng hoạt động, giải đang chạy, queue, worker, WebSocket, số trận giải FAILED; cập nhật 10 giây, bỏ qua tab ẩn và không chạy request chồng nhau. Hoạt động gần đây gồm audit giải đấu và audit quản trị user/bot. Quick action chỉ điều hướng.
3. **User:** tìm username/email/display name, lọc role/status/ngày UTC, phân trang server; chi tiết account/thống kê số trận và bot, 20 trận/bot/giải gần đây, 50 audit gần đây. Khóa/mở khóa yêu cầu xác nhận và lý do. Không cho tự khóa; mở khóa không phục hồi phiên cũ. Chưa hỗ trợ đổi role qua UI/API.
4. **Bot:** lọc tên/owner/ngôn ngữ/build state/có lỗi/đã khóa/ngày; chi tiết metadata, SHA-256, tình trạng artifact, trận gần đây, mã lỗi giải đấu và audit. Có khóa/mở khóa và lưu trữ mềm, tất cả yêu cầu lý do. Không sửa hoặc xóa source/replay của người dùng.
5. **Match:** phân trang/tìm/lọc status/tournament/user/bot/ngày; cả trận có history và trận giải chưa tạo room. Chi tiết players/controllers/bots, trạng thái, kết quả, timeline audit, số đo bot trong phiên server hiện tại. Spectator và replay mở tại `/admin/spectator.html` và `/admin/replay.html`, giữ nguyên shell quản trị và dùng lại controller chỉ đọc hiện có. Đường dẫn replay/spectator cũ tự chuyển sang bản admin khi account là ADMIN. Review/retry dùng chính API giải đấu, revision và kiểm tra trạng thái hiện có; không có API điều khiển board hoặc sửa state game từ admin.

## Chính sách khóa bot

`bots.admin_disabled` độc lập với trạng thái build. Bot bị khóa không thể được dùng để nhận chỗ chơi mới hoặc đăng ký mới. Các participant đã khóa phiên bản trong giải tiếp tục dùng artifact đã đóng băng; khóa bot không viết lại lịch sử hoặc kết quả đang chạy. Muốn xử lý participant hiện có dùng workflow giải đấu.

Lưu trữ chuyển metadata sang `DELETED`, giữ artifact/replay/history. Không có khôi phục bot đã lưu trữ trong chính sách hiện tại; nút enable không phục hồi bot này. Hộp xác nhận thông báo việc giữ lịch sử.

## API

- `GET /api/admin/dashboard`: số liệu runtime và 15 hoạt động gần đây.
- `GET /api/admin/users?page=0&size=20&q=...&role=USER&status=ACTIVE&from=YYYY-MM-DD&to=YYYY-MM-DD`: `items`, `total`, `page`, `size`; giữ khóa `users` cho client cũ.
- `GET /api/admin/users/{id}`: chi tiết và dữ liệu liên quan.
- `POST /api/admin/users/{id}/disable|enable`: JSON `{ "reason": "Lý do" }`. Request rỗng không còn được chấp nhận.
- `GET /api/admin/bots`: paging, `q`, `owner` (UUID), `language`, `status` (build), `disabled=true`, `errors=true`, `from`, `to`.
- `GET /api/admin/bots/{id}` và `POST /api/admin/bots/{id}/disable|enable|archive` với `reason`.
- `GET /api/admin/matches`: paging, `q`, `status`, `tournament`, `user`, `bot`, `from`, `to`.
- `GET /api/admin/matches/{id}`: ID history hoặc ID trận giải chưa có history.
- Review/retry vẫn gọi `/api/tournaments/{id}/matches/{matchId}/review|retry`, có `revision` và `reason`. Retry chỉ hiện với trận lỗi trong giải PAUSED/RECOVERY_REQUIRED; server kiểm tra lần nữa.

Paging giới hạn 1–100 dòng; filter và UUID/date được kiểm tra. Query dùng parameters, tìm kiếm escape `%`, `_`, `!`; thứ tự ổn định theo thời gian và ID. Dữ liệu hiển thị qua text DOM, không chèn HTML từ tên hoặc lý do người dùng. Không trả password hash, auth version, token bot/seat, đường dẫn artifact, Docker socket hoặc raw stdout/stderr.

## Dữ liệu chưa có trong backend

Prompt giả định nhiều tính năng của Chặng 6. Repository hiện tại chưa có backend dispute, đếm container, phân loại riêng TIMEOUT/CRASH/INVALID_OUTPUT/ILLEGAL_ACTION hoặc số đo mọi lần chạy bot đã lưu vĩnh viễn. UI ghi rõ chưa hỗ trợ/không có dữ liệu; không tạo số 0 giả hoặc action không hoạt động. `BUILD_ERROR` và mã lỗi trận giải hiện có vẫn xem được. “Trận có lỗi” của bot tính các trận liên quan có failure, không quy kết bot đó là thủ phạm.

`activeUsers` là gauge các tài khoản có session; `enabledAccounts` là số tài khoản ACTIVE trong DB. `activeMatches` là phòng chưa kết thúc trong registry. `systemErrors` là số trận giải đang có status FAILED, không phải tổng tất cả lỗi ứng dụng. Metadata trận được flush định kỳ, có thể trễ khoảng 500 ms; số đo live chỉ có khi room còn trong phiên server hiện tại. Replay được mở qua endpoint kiểm tra integrity hiện có.

## Migration và vận hành

V15 tạo `admin_audit_events` và cột `bots.admin_disabled`, không sửa V1–V14. Khóa/mở/lưu trữ cùng audit nằm trong một transaction. Audit lưu actor, target, action, reason, before/after và timestamp. JDBC dùng `Timestamp` để tương thích cả PostgreSQL và H2.

Đã sao lưu DB và toàn bộ bot/replay trước khi nâng V15 trên máy local. Không đưa fixture/password/backup/screenshot vào Git. Tài khoản ADMIN thử nghiệm được hạ lại USER sau kiểm tra và thu hồi session.

## Kiểm thử

- Nhóm 1: 480 backend pass, 32 client pass, bootJar thành công.
- Nhóm 2: test HTTP dashboard ADMIN/USER/guest pass, 33 client pass, bootJar thành công.
- Nhóm 3: 19 test backend tập trung (account/admin/Flyway/JPA) pass, 33 client pass, bootJar thành công.
- Nhóm 4: 9 test API admin/bot pass, 33 client pass, bootJar thành công.
- Cuối nhóm 5: **486 backend pass, 0 fail/error/skipped; 34 client pass, 0 fail/skipped; 47 module JS syntax pass; bootJar thành công.** Bao gồm PostgreSQL/Testcontainers, Docker Python/C++/Java/JAR, RR8, knockout 16/32 bot và hồi quy WebSocket/game.
- Kiểm tra native PostgreSQL: projections/filter/live metadata; API guest 401/USER 403; khóa/mở account và bot có lý do + audit; date bindings; không lộ artifact path/token.
- Kiểm tra trình duyệt: redirect login/return path, ADMIN vào dashboard, USER nhận 403, search user, user/bot detail, hộp xác nhận và đổi trạng thái thật, audit, lọc trận, match detail và mở replay chỉ đọc. Đã kiểm tra menu game USER không hiện login/register và chỉ ADMIN có Quản trị.
- `GameEngine`, `RuleSet`, protocol và thuật toán giải đấu không sửa. Các test core tiếp tục pass.

Điều chỉnh giao diện theo yêu cầu tiếp theo: 11 test backend về login/account/admin pass, 35 test client pass, 48 module JS kiểm tra cú pháp thành công, bootJar thành công. Đã kiểm tra trình duyệt thật: ADMIN login từ URL bình thường vào dashboard; mở lobby chuyển về dashboard; replay và spectator giữ shell ADMIN, board bị vô hiệu hóa thao tác, WebSocket spectator kết nối thành công; USER vẫn vào trang game bình thường. Đây là phân luồng giao diện theo role; quyền ADMIN và CSRF ở API tiếp tục được kiểm tra như trước.

## File và cấu trúc

- `client/admin/`: index, users/user-detail, bots/bot-detail, matches/match-detail, replay và spectator.
- `client/css/admin.css`: theme, shell, bảng/form/dialog và breakpoint cơ bản.
- `client/js/admin/`: shell, auth guard, API client, dashboard/views, directory, matches, components.
- `client/js/account/account-ui.js`, `account-session.js`: return path admin và menu theo role; `client/js/api/account-api.js`: reason khi đổi status.
- `server/src/main/java/com/rpsbattle/admin/`: read projections, directory/actions, bot policy, match projections.
- Controllers: `AdminController`, `AdminConsoleController`, `AdminMatchesController`; `OwnedBotService` chặn bot bị khóa trong execution/registration mới.
- Migration `V15__admin_console_audit.sql`; tests admin API/PostgreSQL và cập nhật tests account/Flyway/JPA; client tests admin auth/API/navigation.

Nhóm 6–10 đã được triển khai tiếp theo: [phạm vi, API, vận hành và kiểm thử](CHANG-7-NHOM-6-10-ADMIN.md). Phần mô tả dữ liệu chưa có bên trên là giới hạn tại thời điểm kết thúc nhóm 5; báo cáo mới ghi rõ các phần đã bổ sung.
