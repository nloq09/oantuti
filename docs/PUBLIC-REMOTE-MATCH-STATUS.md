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
