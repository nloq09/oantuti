# Trạng thái trận remote trên các màn hình giải đấu

## Hành vi

- Lịch đấu, lobby, bracket, trang quản lý và chi tiết từng bàn đồng đội dùng cùng cách diễn giải trạng thái thực thi. Trận nằm trong queue không còn chỉ hiện `PENDING` như một trận chưa được gửi đi.
- Phân biệt chờ máy, đã cấp máy, khởi động bot, gameplay, chờ xác nhận kết quả, chạy lại, khôi phục và lỗi. `RESULT_PENDING` chưa phải kết quả chính thức.
- Trận remote không mở bàn cờ trực tiếp bằng `onlineMatchId` cũ. Chỉ trận local đang chạy và có phòng hợp lệ mới hiện liên kết xem trực tiếp. Replay chỉ mở từ `historyMatchId` sau khi trạng thái đã hoàn tất; không dùng mã phòng thay replay ID.
- Trận hoàn tất vẫn xem được replay nếu chủ giải hủy giải sau đó. Kết quả đang review có thông báo riêng, không đánh dấu người thắng như một quyết định đã chốt trên bracket và bảng trận quản lý.
- Lobby hiển thị cả job remote đang xử lý. Summary giới hạn 20 trận, có `activeMatchesTruncated` và liên kết sang lịch đầy đủ khi vượt giới hạn.
- Lịch đấu và bracket tự tải lại mỗi 5 giây qua refresh controller chung, kể cả khi revision giải chưa đổi. Giữ bộ lọc/phân trang; bỏ phản hồi cũ khi thay truy vấn; dừng khi trang không còn hoạt động. Bracket cập nhật cây mà giữ bộ điều khiển zoom trên cùng view. Khi focus nằm trong bảng/cây, hoãn thay nội dung để tránh mất vị trí bàn phím.
- Lỗi mạng giữ snapshot và báo dữ liệu có thể cũ. Mất quyền ở lịch riêng sẽ xóa nội dung đang hiển thị. Phần trạng thái cá nhân chỉ dùng projection đã biết để tạo liên kết; không tự tin dùng mã phòng khi monitor tải lỗi.
- Chi tiết các bàn đồng đội lấy trạng thái mới khi bấm xem/cập nhật kết quả; không thêm polling riêng cho từng fixture.

## Dữ liệu và quyền

`matches` trên detail/summary/bracket/fixture và `items` trên `/matches` thêm ba trường: `remote`, `executionState`, `reviewRequired`. Response phân trang thêm `tournamentId` để client kiểm tra phạm vi. Không thêm endpoint, migration hoặc đổi giao thức bot; schema vẫn V36, worker vẫn 0.11.0.

`PublicMatchExecutionService` chỉ đọc các scalar cần phân loại trạng thái, theo tournament ID và tập match ID; chia batch tối đa 500 ID cho endpoint detail cũ. Các nhãn dùng chung policy của `RemoteExecutionReadService`. Không trả worker identity, credential, job/execution ID, lease, heartbeat, receipt, source hay spec cho khách. Monitor và lịch sử attempt giữ phân quyền hiện có. Trạng thái vòng đời trận/giải và kết quả vẫn do scheduler/projector quản lý, không sửa chúng chỉ để hiển thị UI.

## Kiểm chứng

- PASS 235 client tests, gồm 10 ca mới cho trạng thái công khai, liên kết an toàn, review, cập nhật cùng revision, giữ zoom/focus, phân trang, hủy truy vấn cũ và mất quyền.
- PASS 40 ca backend trong lần chạy hồi quy: 28 ca của `PublicMatchExecutionTest` (gồm 25 ca kế thừa về identity/claim/result/read/monitor), 3 ca PostgreSQL thực, 5 ca withdrawal/paging và 4 ca execution/control. Không fail hoặc skip.
- Các ca API đọc bằng guest kiểm tra detail, summary, schedule, bracket và fixture từ STARTING qua RUNNING_REMOTE, RESULT_PENDING đến FINISHED; bảo toàn revision trước projection, không rò credential/worker execution metadata. Có kiểm tra QUEUED, ASSIGNED, RECOVERING, RETRYING, summary có giới hạn và phân biệt phòng local.
- PASS cú pháp 72 module JavaScript, `git diff --check`, đóng gói bootJar và native PostgreSQL HTTP/compact WebSocket smoke, đối chiếu 50 asset được đóng gói với source.
- Chưa kiểm tra bố cục bằng screenshot hoặc browser click E2E: browser bị tắt trong Work settings. DOM/API tests không thay thế kiểm tra trực quan. Không chạy lại trận Docker đầy đủ trong smoke native; các ca remote dùng fixture replay và PostgreSQL Testcontainers.

File chính: `PublicMatchExecutionService`, `TournamentService`, `TournamentMatchPageService`, `RemoteExecutionReadService`; client `match-execution`, `match-pages`, `public-bracket`, `lobby-matches`, `bracket-renderer`, `competition-view`, `player-detail`, `team-view` và các bảng quản lý trận.

## Giữ trạng thái khi màn hình cha cập nhật

Bản tiếp theo bổ sung `player-tab-view`: trang người chơi giữ một khung đọc đang hoạt động cho lịch đấu, replay, bracket hoặc Trận của tôi. Khi header/lobby cập nhật, khung cũ được gắn lại thay vì tạo polling controller mới. Revision mới được truyền xuống view; lịch cập nhật danh sách vòng/giai đoạn mà giữ bộ lọc, bracket giữ zoom và monitor giữ bộ lọc/phân trang. Khi đổi tab, đổi tài khoản/giai đoạn, chuyển về màn hình trước giải, gặp lỗi tải màn hình cha hoặc rời trang thật sự, view cũ được dừng và request đang chờ bị hủy. BFCache vẫn dùng suspend/resume của refresh controller.

Các lần render phải hoãn vì người dùng đang nhập hoặc đang gửi form được ghi nhớ; polling tiếp theo thử render lại sau khi hết chặn, kể cả khi server không tăng revision lần nữa. Lịch sử attempt trong Match Monitor có AbortSignal riêng; đổi bộ lọc, thay danh sách hoặc dừng monitor sẽ hủy việc tải và bỏ phản hồi muộn.

Kiểm chứng bổ sung: PASS 244 client tests, trong đó 9 ca mới kiểm tra reparent màn hình thật bằng DOM harness, giữ filter/zoom/chi tiết đang mở, một controller đang hoạt động, truyền revision mới, abort khi đổi tab/quyền/giai đoạn, hủy trước lần tải đầu và phản hồi history đến muộn. PASS cú pháp 73 module, bootJar, diff check và smoke HTTP/WebSocket cùng đối chiếu 51 asset của bản đóng gói. Không đổi backend hoặc schema trong bản tiếp theo; không chạy lại các suite backend đã qua ở trên. Browser click/screenshot vẫn chưa kiểm tra vì browser bị tắt trong Work settings.

## Chọn đúng trận cho nút chính và mất quyền khi xem lịch sử

Nút chính dùng trạng thái của chính suất được chọn, ưu tiên suất chơi tay đã có trận hoặc đang cần ready check trước bot. Một bot khác cùng tài khoản không được chiếm nút Vào trận/Sẵn sàng. Khi chọn bot, lấy projection đúng currentMatch/nextMatch ID, hoặc đối chiếu participant ID khi chưa có assignment; không lấy tùy ý execution đầu tiên trong tài khoản. Dữ liệu monitor riêng ưu tiên hơn snapshot công khai của cùng trận.

Nếu monitor lỗi, trạng thái remote công khai vẫn cho phép theo dõi queue/gameplay/xác nhận kết quả. Mã phòng cũ trong participant state không đủ để tạo nút xem trực tiếp cho bot. Phòng local phải được projection xác nhận RUNNING_LOCAL; dữ liệu thiếu, kết quả pending/terminal/không rõ sẽ dẫn tới Trận của tôi để đồng bộ. Luồng chơi tay vẫn dùng seat do server cấp và endpoint entry kiểm tra quyền. Ready check không được mở mới khi giải đang PAUSED/RECOVERY_REQUIRED.

Khi tải lịch sử attempt trả UNAUTHENTICATED, TOURNAMENT_FORBIDDEN hoặc ACCOUNT_DISABLED, monitor xóa ngay dữ liệu riêng, vô hiệu bộ lọc/nút cập nhật và dừng request đang chờ. Phản hồi cũ đến sau không được khôi phục dữ liệu. Lỗi mạng thông thường vẫn giữ dữ liệu cũ và cho thử lại.

Kiểm chứng: PASS 251 client tests (7 ca mới, trong đó ca mất quyền kiểm tra cả ba mã lỗi); PASS 73 module syntax, diff check, bootJar và native PostgreSQL HTTP/compact WebSocket smoke đối chiếu 51 asset. Cập nhật fixture hành trình remote với assignment/participant ID thật thay vì execution không có danh tính. Bản này chỉ đổi client và tài liệu; không thay backend, schema hoặc protocol bot. Chưa có kiểm tra screenshot/browser click mới; giới hạn browser như ghi ở trên vẫn còn.

## Phục hồi lỗi tải trang và phân trang khi chi tiết đang mở

Khi reader của trang người chơi gặp lỗi và thay nội dung bằng màn hình lỗi, nó ghi nhận cần render lại. Snapshot hợp lệ tiếp theo phục hồi giao diện dù cùng revision; phản hồi lỗi đã bị thay thế không được làm hỏng màn hình mới. Snapshot revision cũ không được dùng để tiêu thụ trạng thái chờ phục hồi.

Match Monitor tách trang yêu cầu khỏi trang đã hiển thị. Bấm Trước/Sau là yêu cầu điều hướng rõ ràng, được thay danh sách dù chi tiết cũ đang mở; polling nền vẫn giữ focus/chi tiết như trước. Điều hướng hủy history đang tải và khóa hai nút phân trang trong lúc chuyển trang. Nếu request lỗi, nội dung và nhãn trang giữ nguyên; lần thử lại tính từ trang đang nhìn, tránh bỏ qua trang. Polling thử lại cũng có thể hoàn tất lần điều hướng bị gián đoạn. Bộ lọc mới đưa cả trạng thái phân trang về đầu.

PASS 259 client tests, gồm 8 ca hồi quy mới cho phục hồi cùng revision, lỗi ban đầu/lỗi liên tiếp, bỏ lỗi đã lỗi thời, từ chối revision cũ, chuyển hai chiều khi chi tiết mở, retry không nhảy trang, hủy history/chống click lặp và retry nền. Các ca lỗi đã được chạy trước bản sửa để xác nhận tái hiện. Test reader thực thi hàm đọc thật của trang với transport/render được tiêm vào, không sao chép logic production. PASS cú pháp 73 module, diff check, bootJar và native PostgreSQL HTTP/compact WebSocket smoke đối chiếu 51 asset. Không đổi server Java/schema; kiểm tra trực quan bằng browser vẫn chưa thực hiện.
