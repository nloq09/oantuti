> Báo cáo lịch sử nhóm 1–5. Trạng thái mới nhất: [BO3/BO5](TOURNAMENT-SERIES.md), [Swiss/đồng đội](TOURNAMENT-SWISS-TEAM.md), [nhiều vòng bảng, rút giữa giải và phân trang server](TOURNAMENT-COMPLETION.md).

# Chế độ giải đấu cho người chơi — Nhóm 1–5

Phạm vi theo [yêu cầu Chặng 8](CHANG-8-TOURNAMENT-MODE-PROMPT.md): Hub/Discovery, Detail/My Status, đăng ký người hoặc bot, check-in/lobby, ready check và tự vào trận. Không triển khai nhóm 6–10 trong lượt này.

## Cách dùng

1. Đăng nhập USER, từ trang chủ mở **Giải đấu**.
2. Chọn Đang diễn ra / Đang mở đăng ký / Sắp diễn ra / Đã tham gia / Đã kết thúc; tìm tên hoặc lọc thể thức. Danh sách phân trang 12 giải, dùng metadata từ server.
3. Mở giải để xem tổng quan, người tham gia, lịch, bracket, xếp hạng, trận và replay. Vùng **Trạng thái của tôi** hiển thị riêng từng đăng ký của tài khoản.
4. Khi mở đăng ký, chọn Tôi tự chơi hoặc Bot của tôi theo policy giải; xác nhận luật rồi gửi. Chủ giải duyệt ở màn hình quản lý hiện có. Có thể hủy đăng ký của mình khi vẫn ở giai đoạn đăng ký/check-in, qua hộp xác nhận hậu quả.
5. Khi được duyệt và check-in mở, bấm **Check-in ngay**. Nếu có hạn, đồng hồ dùng timestamp server và offset; server vẫn là nguồn quyết định cuối cùng.
6. Mở **Lobby giải** để xem trạng thái, đối thủ tiếp theo và trận đang chạy.
7. Với trận có người tự chơi, server mở cửa sổ sẵn sàng mặc định 30 giây. Hai người phải xác nhận; phía bot được server xác nhận tự động. Khi đủ điều kiện và worker có chỗ, server tạo đúng một Match qua executor hiện có; trang nhận ghế rồi tự chuyển vào bàn cờ, không cần mã phòng.
8. Xếp quân và chơi như trận bình thường. Khi trận kết thúc có nút **Quay lại giải**. Liên kết quay lại trong lúc đang chơi mở lobby ở chế độ xem để không lập tức chuyển vào lại; nút Vào trận cho phép trở lại.

ADMIN tiếp tục dùng giao diện quản trị riêng; các đường dẫn player mới cũng được chuyển về admin theo quy tắc hiện tại. Không đổi luồng game thường của USER/khách.

## Policy và tương thích

- Giải cũ mặc định BOT_ONLY. Tạo giải mới có thể chọn BOT_ONLY, HUMAN_ONLY hoặc HUMAN_OR_BOT. Format hiện được hỗ trợ vẫn là Round Robin / Single Elimination, mỗi trận BO1.
- Người thật có danh tính tài khoản riêng, `botId=null`; không tạo bot giả. Mỗi tài khoản có tối đa một đăng ký tự chơi trong mỗi giải, có thể có thêm các bot riêng khi policy cho phép. Backend kiểm tra sở hữu, capacity và trạng thái ở trong cùng mutation có rollback/audit.
- Bot chưa READY bị vô hiệu hóa trong form mới. Bot UPLOADED có nút kiểm tra/biên dịch bằng Docker trước khi chọn. Endpoint đăng ký bot cũ vẫn tương thích và giữ quy trình kiểm tra artifact hiện có.
- Trạng thái registration gồm PENDING, APPROVED, CHECKED_IN, REJECTED, CANCELLED, NOT_CHECKED_IN. Nếu chủ giải không cung cấp lý do từ chối, giao diện ghi rõ thay vì tạo lý do giả.
- Rules confirmation hiển thị luật nền hoặc snapshot đã khóa: BO1, thời gian lượt, check-in và chính sách bot. Snapshot và kiểm tra artifact trước khi chạy giữ cơ chế cũ.
- Hạn và giờ bắt đầu là tùy chọn. Không có hạn thì chủ giải kết thúc giai đoạn bằng tay; giờ bắt đầu là dự kiến, không tự khởi chạy giải. Có hạn đăng ký thì server từ chối đăng ký sau hạn. Khi CHECK_IN hết hạn, server đánh dấu người bỏ lỡ, giữ các participant đã check-in; đủ ít nhất hai thì READY, thiếu thì hủy giải với audit. Chủ giải vẫn quyết định bắt đầu giải.

## Ready, ghế chơi và no-show

Ready là lớp admission trước worker, không phải GameEngine mới. Cửa sổ chỉ mở cho vòng đang được phép và trong giới hạn giải; ID cửa sổ ổn định được lưu cùng deadline và confirmations. POST xác nhận dùng ID cửa sổ để từ chối cửa sổ cũ sau pause/resume, tránh xung đột với revision thay đổi do trận khác. Xác nhận lặp của cùng participant là idempotent.

Chỉ người đúng tài khoản và controller HUMAN được xác nhận ghế người. Sau khi worker tạo phòng, endpoint entry kiểm tra cả tournament participant lẫn chủ ghế trong live room. Bot owner chỉ nhận spectator entry. Response private dùng no-store; token không nằm trong URL, discovery, trạng thái giải công khai hoặc thông báo ready. GameSocket gửi đúng schema cũ, bỏ metadata giải/role/history trước khi gửi gameplay; không nới lỏng parser server.

Worker kiểm tra lại confirmations trước launch. Entry đọc lại cùng room/seat khi gọi lặp, không tạo phòng. Refresh với seat còn trong sessionStorage tiếp tục dùng ghế đó; nếu thiếu ghế, URL game đưa về lobby để lấy trạng thái hiện tại. Tab nền không tự chuyển trang; vào lại lobby có thể dùng nút Vào trận.

Hết hạn khi một phía đã sẵn sàng thì phía đó thắng forfeit. Cả hai bỏ lỡ dùng DOUBLE_FORFEIT có sẵn: cả hai thua, không cộng điểm hòa, không tự chọn một champion vòng tròn. Không tạo room/history/replay cho trận chưa diễn ra. Kết quả có audit `MATCH_NO_SHOW`, được lưu trong lịch giải; thuật toán xếp lịch và advancement hiện có xử lý tiếp.

Pause/resume và retry xóa cửa sổ cũ để yêu cầu xác nhận mới. Restart đưa giải đang dở về RECOVERY_REQUIRED theo cơ chế cũ, không cấp lại phòng hoặc tự chạy trận. Resume/retry sau kiểm tra của chủ giải/admin mới cho phép tiếp tục. Cửa sổ mới không dùng lại deadline cũ.

## API bổ sung

- `GET /api/tournaments/discover?category=live|open|upcoming|joined|finished&q=&format=&page=0&size=12`: metadata phân trang, giới hạn size 50; joined yêu cầu tài khoản. Không tải replay hoặc serialize toàn bộ lịch ở Hub.
- `GET /api/tournaments/{id}/me`: registrations/trạng thái/rank/trận kế tiếp hoặc hiện tại của đúng tài khoản; có serverTime và thông tin ready riêng.
- `POST /api/tournaments`: thêm `participantPolicy`, mặc định BOT_ONLY khi không có.
- `POST /api/tournaments/{id}/registrations`: form mới gửi `{controllerType:"HUMAN"|"BOT",botId:null|UUID,rulesAccepted:true}`. Người thật không được gửi kèm botId; danh tính lấy từ session.
- `POST /api/account/bots/{id}/validate`: kiểm tra đăng ký/biên dịch bot theo quyền hiện có rồi trả READY nếu hợp lệ.
- `POST /api/tournaments/{id}/timing`: `{revision,registrationDeadline?,checkInDeadline?,startTime?}`; chỉ manager ở DRAFT chưa có đăng ký, timestamp ISO 8601.
- `POST /api/tournaments/{id}/ready`: `{matchId,windowId}`; participant người thật của chính trận, còn trong hạn.
- `POST /api/tournaments/{id}/entry`: `{matchId}`; thông tin ghế chỉ cho participant sở hữu, response no-store.

Đăng ký/duyệt/hủy/check-in/start vẫn tái sử dụng endpoint cũ; không thêm API song song cho cùng thao tác. Mọi mutation cookie-auth yêu cầu CSRF. Query discovery dùng bind parameters, escape ký tự tìm kiếm, kiểm tra category/format/paging. Client không coi DOM hay local clock là nguồn trạng thái giải.

## Migration và vận hành

V17 cho phép `bot_id` rỗng ở participant/registration, thêm `human_user_id` với FK, uniqueness trong mỗi giải và CHECK đối chiếu đúng chủ tài khoản. Bot/version còn bắt buộc khớp nhau với controller BOT. Dữ liệu bot cũ không đổi. Policy, timing và ready windows lưu trong cấu trúc JSON version 1 có default khi đọc dữ liệu cũ.

Nâng toàn bộ server dùng chung DB lên cùng phiên bản. Sao lưu DB cùng bots/replays trước nâng schema; rollback đầy đủ phải dùng bản backup tương ứng. Local đã sao lưu trước V17 vào thư mục ignored `backups/before-stage8-v17`. Không đưa backup, password, token, fixture hoặc screenshot thử vào Git.

Biến cấu hình tùy chọn: `rps.tournament.ready-seconds`, mặc định 30, hợp lệ 1–300. Đây là thời gian xác nhận trước trận, khác thời gian của từng lượt game.

## File chính và kiểm thử theo nhóm

- Nhóm 1: TournamentDiscoveryService, TournamentPlayerController, tournament-hub/hub-model, trang tournaments và CSS mode. **494 backend, 39 client pass**, build thành công; commit `d47eb2e`.
- Nhóm 2: ParticipantTournamentStateService, player-detail/player-state/player-dom, tournament-detail.html, safe return paths. **495 backend, 40 client pass**, build thành công; commit `1011648`.
- Nhóm 3: participant policy, human registration, V17/entities/persistence, executor dùng ghế human có sẵn, registration form/model và bot validate. **499 backend, 41 client pass**, migration PostgreSQL và build thành công; commit `48bfd8f`.
- Nhóm 4: TournamentTiming, check-in deadline/no-show, ServerClock, tournament-lobby.html và vùng Next Match/Live. **501 backend, 42 client pass**, build thành công; commit `c21e76d`.
- Nhóm 5: TournamentReadyService/MatchReadyWindow, admission ở execution/worker, entry private, client auto-entry/return và tests ready/no-show/HTTP/strict socket schema. **505 backend pass, 0 fail/error/skipped; 46 client pass, 0 fail/skipped; 64 module JS syntax pass; bootJar thành công.**

Các bộ toàn server đã chạy sau mỗi nhóm, gồm PostgreSQL/Testcontainers, Docker Python/C++/Java/JAR, Round Robin và knockout 16/32 bot, auth/CSRF, WebSocket, replay và persistence. Không có test bỏ qua trong các lần chạy thành công nêu trên.

Native E2E PostgreSQL: đăng ký/duyệt/check-in/ready → hai ghế riêng và entry lặp ổn định → WebSocket xếp quân/sẵn sàng/đầu hàng → giải kết thúc và replay archive thật. Giao diện thật đã kiểm tra Home → Hub → tìm giải → đăng ký Tôi tự chơi/đồng ý luật → chờ duyệt → check-in → lobby → sẵn sàng → tự vào đúng ghế và kết nối bàn cờ. Native no-show: đợi quá hạn ready thật, cả hai thua và không có champion/room/replay giả. Restart tiến trình Windows: giải human chuyển RECOVERY_REQUIRED, entry bị chặn, không tự tạo trận thứ hai; giải human đã hoàn tất và replay vẫn đọc được. Giải UI thử đã hủy sau kiểm tra. Dữ liệu giải Chặng 5 cũ đã được đối chiếu sau V17: luật, ranking, audit và champion giữ nguyên.

`GameEngine`, `RuleSet`, protocol server và thuật toán scheduler không sửa. Thay đổi seeding chỉ thêm khóa tra thành tích cho danh tính HUMAN; thay đổi executor chỉ gắn controller phù hợp lên cùng OnlineMatch. Đã sửa lời nhắc giao chiến cũ trên trang game để đúng luật cùng loại giữ nguyên quân.

Nhóm 6–10 còn lại: context stage/round/series đầy đủ, series BO3/BO5 khi backend tương ứng có thật, bracket focus/zoom và schedule nâng cao, notification/reconnect mở rộng, team/dispute UX và polish/E2E toàn chặng. Không tạo giả các thể thức hoặc dữ liệu mà backend hiện chưa hỗ trợ.
