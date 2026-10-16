# Swiss và giải đồng đội / trường

Hai thể thức mới dùng cùng tài khoản, đăng ký, check-in, luật đã khóa, BO1/BO3/BO5, phòng chơi, bot runner và replay hiện có. Không thay đổi GameEngine, RuleSet hoặc giao thức bot.

## Swiss

Chủ giải chọn **Swiss**, mở đăng ký và check-in như giải cá nhân thông thường. Hỗ trợ 2–32 suất tham gia. Server cố định số vòng bằng `ceil(log2(số người))`; chưa cho cấu hình số vòng tùy ý.

- Vòng đầu theo thứ tự seeding đã khóa. Các vòng sau dùng bảng xếp hạng, ưu tiên đối thủ gần điểm và không ghép lại cặp đã gặp.
- Thuật toán matching tổng quát tìm đủ cặp trên đồ thị đối thủ hợp lệ. Ưu tiên gần điểm là heuristic của project, không phải quy tắc Swiss FIDE hoặc bảo đảm tổng khoảng cách điểm nhỏ nhất.
- Số người lẻ: ưu tiên người xếp thấp nhất chưa nhận BYE, thử ứng viên khác nếu cần để ghép đủ cặp. Mỗi người tối đa một BYE; BYE được 3 điểm và một trận thắng trên bảng điểm, không tạo phòng hay replay giả.
- Thắng 3, hòa 1, thua 0. Mặc định phân hạng: điểm → Buchholz → số thắng → quân còn lại. Buchholz là tổng điểm hiện tại của các đối thủ có series đã hoàn tất; BYE và cặp chưa chơi không cộng điểm đối thủ.
- Chờ mọi ván/series trong vòng hoàn tất và hết review mới ghép vòng tiếp theo. Pause, failed hoặc review chặn tiến vòng. Pairing được lưu trước khi phát trận cho worker.
- Kết thúc khi đủ vòng (`ROUND_LIMIT`), còn dưới hai người hợp lệ (`INSUFFICIENT_ACTIVE_PLAYERS`) hoặc không còn cách ghép đủ cặp mới (`NO_FRESH_PAIRING`). Server công bố lý do; không tự ghép lại đối thủ cũ.
- Không cho sửa kết quả vòng trước nếu vòng sau đã ghép, để giữ lịch sử pairing nhất quán.

Trang chi tiết có bảng Swiss, vòng hiện tại/tổng vòng, Buchholz, BYE và lịch sử cá nhân. Champion lấy từ bảng xếp hạng hợp lệ của server.

## Đồng đội / trường

Chủ giải chọn **Đồng đội / trường**, tối thiểu 6 suất tham gia. Mỗi giải có 2–10 đội, mỗi đội 3–5 thành viên khi khóa roster; tổng số suất vẫn tối đa 32. Tên trường là thông tin tự khai, chưa xác minh tổ chức.

1. Đăng ký và được duyệt theo quy trình của giải. Trong tab **Đồng đội**, dùng suất thuộc tài khoản mình để tạo hoặc gia nhập đội trước khi khóa roster. Người tạo là đội trưởng. Người khác có thể tự gia nhập đội còn chỗ; chưa có lời mời hay duyệt riêng của đội trưởng.
2. Thành viên chỉ có một đội và chỉ được tự thêm/rời suất mình sở hữu. Rời thành viên cuối sẽ giải tán đội rỗng trước khi khóa roster. Server từ chối bắt đầu nếu còn suất chưa thuộc đội hoặc đội thiếu ba người hợp lệ.
3. Khi giải chạy, đội trưởng chọn đúng **ba thành viên khác nhau** theo thứ tự bàn 1–3 cho mỗi fixture. Có thể sửa lineup đến khi cả hai đội gửi đủ; khi đó server khóa hai lineup và tạo ba series thật.
4. Lineup chưa khóa chỉ hiện cho đội trưởng tương ứng qua dữ liệu cá nhân. Public chỉ thấy bên nào đã gửi; sau khóa mới công khai hai lineup. Không thể gửi thay đội trưởng bằng quyền chủ giải.
5. Các đội đấu vòng tròn một lượt. Chỉ mở fixture của vòng tiếp theo sau khi vòng hiện tại đã hoàn tất; mỗi bàn hỗ trợ BO1/BO3/BO5. Người không còn hợp lệ trong lineup bị xử lý forfeit theo cơ chế hiện có, không dựng replay giả.

Mỗi bàn thắng 1 điểm, hòa 0,5 điểm, thua 0. API lưu số nguyên gấp đôi (`boardPoints`, `boardDifference`, score `a`/`b`: 2/1/0); client chia đôi khi hiển thị. Đội hơn điểm bàn thắng fixture và nhận 3 điểm; hòa nhận 1; thua nhận 0. Nếu cả ba bàn đều double-forfeit thì hai đội đều thua, không nhận điểm hòa. Xếp hạng ưu tiên đội còn thành viên hợp lệ, rồi điểm fixture → hiệu số điểm bàn → ID đội. Đội không còn thành viên hợp lệ không được vô địch; giải toàn double-forfeit không dựng champion.

Chưa có chuyển đội trưởng, thời hạn gửi lineup hoặc tự xử thua đội chưa gửi. Chủ giải có thể pause/cancel để xử lý đội trưởng vắng mặt. Đây là vòng tròn đồng đội ba bàn, chưa có Swiss đồng đội hoặc cấu trúc nhiều cấp trường/lớp.

## API và lưu trữ

`format` khi tạo giải nhận thêm `SWISS` hoặc `TEAM_ROUND_ROBIN`. Endpoint ghi dùng session cookie, CSRF và `revision` hiện tại; revision cũ bị từ chối, client không tự retry mutation.

- `POST /api/tournaments/{id}/teams`: `{revision, name, school, participantId}`.
- `POST /api/tournaments/{id}/teams/{teamId}/members`: `{revision, participantId, leave}`.
- `POST /api/tournaments/{id}/teams/{teamId}/lineup`: `{revision, fixtureId, participantIds: [id1, id2, id3]}`.
- Snapshot public bổ sung `swiss`, `swissStandings`, `teamCompetition`; `/me` bổ sung thông tin đội/đội trưởng và `teamLineups` riêng của đội trưởng. Champion đội nằm trong `teamCompetition`, không trộn với champion cá nhân.

Flyway **V20** mở rộng constraint format. Swiss cursor, đội, đội trưởng, roster, fixture và lineup được lưu trong cấu trúc JSON cùng revision/audit của giải; snapshot cũ không có các trường này vẫn đọc được. Không sửa migration cũ.

Sao lưu PostgreSQL và toàn bộ bot/replay trước nâng; bản local dùng thư mục ignored `backups/before-swiss-team-v20` có checksum. Triển khai đồng bộ server hỗ trợ V20. Rollback cần DB và file backup tương ứng, không trộn server cũ với enum mới. Sau restart, giải đang dở vẫn chuyển `RECOVERY_REQUIRED`; chủ giải kiểm tra rồi resume, không tự chạy lại phòng cũ.

## Kiểm chứng

Kiểm thử bao gồm matching so với tìm kiếm vét cạn độc lập trên 540 đồ thị, Swiss 2–32 người không lặp cặp/BYE, Buchholz khi BO1 chưa chơi, barrier BO3/review/pause, quyền đội trưởng, revision/rollback, lineup riêng tư, vòng đồng đội, forfeit, champion hợp lệ, migration và persistence PostgreSQL.

Mã cuối đã qua **547 backend tests**, **67 client tests**, kiểm tra cú pháp **73 module JavaScript** và build `bootJar`; không có test fail/error/skipped. Giao diện Swiss/đồng đội được kiểm tra trên desktop và viewport 390×844, bảng cuộn trong khung và không tràn ngang toàn trang.

Smoke test local qua HTTP/WebSocket đã chạy 5 giải Swiss/đồng đội hoàn tất với 42 ván thật có replay, so sánh pairing, điểm, đội trưởng, lineup và champion sau restart. Kiểm tra lại các fixture vòng bảng, BO3/BO5 và luồng Chặng 5. Một lần thử trước gặp HTTP 503 khi đang chạy nhiều kiểm thử; fixture đó được cancel bằng API và giữ lịch sử. Các lần chạy sau thành công; logging chỉ bổ sung loại lỗi/vị trí gốc, không ghi credential hoặc payload.

Đã bổ sung [số bảng/top N và chuỗi nhiều vòng bảng → loại trực tiếp, rút giữa giải và phân trang server](TOURNAMENT-COMPLETION.md). Swiss và Đồng đội hiện là thể thức độc lập; không trộn vào pipeline vòng bảng.
