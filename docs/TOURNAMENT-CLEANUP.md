# Xác minh và tự dọn giải bỏ dở

Server nhận diện bằng dữ liệu và thời gian, không dựa vào tên giải hoặc đoán bằng AI. Giải mới tạo chưa đủ người không bị coi ngay là rác.

## Điều kiện

Chỉ xét giải có tài khoản sở hữu, ở trạng thái DRAFT không hoạt động ít nhất 14 ngày, hoặc CANCELLED không hoạt động ít nhất 7 ngày. Hoạt động cuối tính từ thời điểm tạo, đổi trạng thái và audit nghiệp vụ; cảnh báo cleanup không tự làm mới tuổi giải.

Giải phải đồng thời chưa từng bắt đầu, không có trận, participant, đơn đăng ký, đội hoặc fixture. Không được có bất kỳ mốc lịch tương lai nào, kể cả thời gian mở/hạn nộp bot thủ công. Server còn kiểm tra trực tiếp database: participant, trận, đăng ký thường, đăng ký timeline (kể cả đơn hủy/từ chối), workspace code và bài nộp đều bảo vệ giải khỏi dọn tự động. Chỉ có metadata bật coding không được coi là bài code của người dùng.

Giải RUNNING, PAUSED, READY, REGISTRATION, CHECK_IN, RECOVERY_REQUIRED và FINISHED không bị dọn. Không xóa bot, source, artifact, kết quả hay replay.

## Hai lần xác minh và thùng rác

- Lần đầu đủ điều kiện: ghi thời điểm, revision và lý do trong PostgreSQL, đồng thời lưu audit/cảnh báo cho chủ giải.
- Chờ tối thiểu **72 giờ**. Job chạy theo giờ, tối đa 25 giải mỗi lượt, luân phiên danh sách; thời điểm xử lý thực tế có thể muộn hơn mốc chờ.
- Trước khi đưa vào thùng rác, kiểm tra lại điều kiện, revision và các dữ liệu liên quan trong transaction khóa row. Có thay đổi sẽ hủy đánh dấu hoặc bắt đầu lại thời gian chờ; lỗi database sẽ hoãn xử lý.
- Khi dọn: ghi `tournaments.trashed_at`, giữ nguyên visibility và toàn bộ dữ liệu, gỡ khỏi registry và các danh sách thông thường. Snapshot còn trong hàng đợi không được làm giải xuất hiện lại. Restart không phục hồi giải trong thùng rác.
- Đây là **xóa mềm có thể khôi phục**, không có job xóa vĩnh viễn hoặc tự xóa file trên ổ đĩa. Mục đích là dọn danh sách/quota an toàn; chưa phải chính sách giải phóng dung lượng database.

Chủ giải nhận thông báo và có nút **Giữ lại giải đấu** trên trang quản lý. Xác nhận cần đúng revision, ghi audit và bảo vệ giải thêm 14 ngày. Việc chỉnh cấu hình hoặc có hoạt động mới cũng làm việc xác minh bắt đầu lại.

Admin vào **Quản trị → Giải đấu → Dọn giải bỏ dở** để xem lý do, thời điểm đánh dấu và các giải trong thùng rác; có thể Giữ lại hoặc Khôi phục, bắt buộc nhập lý do. Khôi phục giữ ID/cấu hình, ghi audit và bảo vệ 14 ngày. Không biến PRIVATE thành PUBLIC. Danh sách cleanup hiện hiển thị tối đa 100 mục cũ nhất mỗi lần tải.

## Quota và vận hành

Giới hạn 100 giải trong registry hiện chỉ tính giải chưa FINISHED/CANCELLED và chưa bị đưa vào thùng rác. Giải kết thúc hoặc hủy có dữ liệu vẫn giữ để xem lịch sử mà không chặn tạo mới. Đây là quota tổng cho một instance; chưa thay bằng quota theo account.

Mặc định bật job, chạy lần đầu sau 60 giây, sau đó mỗi giờ. Có thể cấu hình:

```properties
rps.tournament.cleanup.enabled=false
rps.tournament.cleanup.initial-delay-ms=60000
rps.tournament.cleanup.interval-ms=3600000
```

Tắt job không khôi phục các giải đã ở trong thùng rác. Không giảm các mốc 14 ngày / 7 ngày / 72 giờ khi thử trên dữ liệu thật; kiểm thử dùng dữ liệu riêng và mô phỏng thời gian đã qua.

Migration V37 thêm cột nullable `trashed_at` và bảng `tournament_cleanup`; không đổi visibility, status hoặc xóa dữ liệu có sẵn. Cơ chế vẫn theo kiến trúc một instance của registry hiện tại; chưa hỗ trợ các server game cùng ghi một registry phân tán.

## API

- `GET /api/admin/tournament-cleanup`: admin, danh sách chờ xác minh/thùng rác, no-store.
- `POST /api/admin/tournament-cleanup/{id}/restore`: admin, body `{reason}`.
- `POST /api/tournaments/{id}/keep`: chủ giải hoặc admin, body `{revision, reason}`.

Các POST dùng session + CSRF hiện có; người ngoài không được giữ hoặc khôi phục giải. Không có endpoint dọn ngay hoặc bỏ qua thời gian chờ.

## Kiểm chứng triển khai

- 67 ca backend liên quan qua: 12 ca chính sách/lifecycle cleanup, 32 ca migration PostgreSQL và hồi quy ownership, timeline, thông báo, persistence/rollback. Các ca cleanup cuối được chạy lại sau khi bổ sung việc hủy đánh dấu khi xuất hiện code mới.
- 277 ca client qua; có kiểm thử cảnh báo, giữ lại và ẩn cảnh báo đã hủy/khôi phục.
- Native PostgreSQL local áp dụng V37 thành công; kiểm tra khách/USER bị từ chối vào API admin, tạo mới không bị lịch sử cũ chặn, endpoint giữ lại ghi audit và module đóng gói khớp source.
- Đã mở được phần Dọn giải bỏ dở trên giao diện quản trị bằng tài khoản kiểm thử. Dữ liệu local hiện không có giải đủ tuổi cần dọn, không ép giải thật thành rác để thử. Chu trình đánh dấu → chờ → thùng rác → khôi phục được kiểm chứng trên dữ liệu kiểm thử riêng.
