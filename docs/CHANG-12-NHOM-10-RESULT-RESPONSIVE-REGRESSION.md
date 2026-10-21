# Chặng 12 — Nhóm 10: Kết quả, màn hình nhỏ và regression

## Quyết định về công bố kết quả

Chọn phương án trong prompt: **bỏ tùy chọn `resultPublishAt` chưa có tác dụng khỏi UI**. Không thêm trạng thái RESULT_PUBLISHED hay một lịch tự công bố giả.

Form tạo giải còn 10 mốc thực sự dùng được, từ mở đăng ký đến kết thúc dự kiến. Lịch mẫu, preview và validation không còn ô công bố kết quả. Payload vẫn gửi `resultPublishAt: null` để tương thích DTO hiện tại; kể cả đối tượng đầu vào cũ chứa ngày công bố, model form cũng không chuyển nó thành một cấu hình mới.

Giải FINISHED hiển thị kết quả theo read model hiện có. Danh hiệu vô địch phải do server xác nhận; khi còn review, player hiển thị đang chờ trọng tài. Giải đồng đội nay cũng diễn giải trường hợp này thay vì báo ngay không có đội vô địch. Giải không có người/đội vô địch hợp lệ vẫn giữ thông báo tương ứng.

Các bản ghi cũ có `resultPublishAt` được giữ nguyên trong database/API để đọc lại lịch sử. Đây là metadata tương thích, **không trì hoãn kết quả, không ẩn bảng điểm/replay và không có scheduler công bố riêng**. Backend regression xác nhận ngày công bố trong tương lai không ngăn champion xuất hiện đồng nhất ở detail, summary và report sau FINISHED; lúc RUNNING chưa có champion.

## Bàn phím và màn hình nhỏ

- Player giữ focus vào đúng tab hoặc primary action có cùng định danh khi repaint. Đổi loại action sẽ không tự đưa focus sang một command khác. Node còn được giữ lại trong monitor cũng được khôi phục focus sau khi gắn lại vào player shell.
- Match Monitor giữ nguyên node nút phân trang qua các lần cập nhật; pager có tên điều hướng. Nếu đang dùng bàn phím trong danh sách, background read vẫn chạy nhưng chưa thay danh sách đang đọc và có thông báo rõ. Chuyển focus ra ngoài sẽ cho lần refresh tiếp theo cập nhật; đổi bộ lọc là thao tác chủ động được phép thay danh sách.
- Bảng dùng helper player-dom có `role=region`, tên vùng cuộn, Tab focus và header scope. Không ép nhiều cột vào màn hình điện thoại; bảng/bracket cuộn bên trong vùng riêng.
- Các ô ngày giờ bắt buộc có label, liên kết lỗi và vùng báo lỗi polite. Giữ validation/focus ô sai đầu tiên của form.
- Bổ sung min-width/box-sizing cho panel, grid, fieldset và input/select để nội dung dài không kéo rộng trang; badge và tên trong kết quả được xuống dòng. Ở chiều rộng tối đa 480px, menu organizer, thống kê và podium xếp một cột. Giữ tương phản, focus-visible, mục tiêu chạm và reduced-motion hiện có.

Đây là thay đổi CSS/DOM đã kiểm tra bằng source và unit tests. Chưa xác nhận layout thực tế trên điện thoại hoặc trình đọc màn hình, xem phần SKIPPED.

## Kiểm chứng

**PASS**

- 218 client tests, không fail/skip. Thêm regression nối các quyết định player từ upcoming → đăng ký → chờ duyệt/coding → khóa bài → validation → check-in → phòng chờ; remote queued/assigned/starting/running/result-pending/retry/recovery → kết quả. Kiểm tra các dashboard/cảnh báo owner tương ứng, quyền không bị suy từ phase, kết quả cá nhân/đồng đội, focus, table region và form timeline.
- 152 ca backend riêng biệt trong 11 suite, tất cả đã PASS sau khi sửa fixture mới. Các suite: TournamentTimelineApiTest (4), TournamentTimelineTest (14), TimelineRegistrationApiTest (4), TimelineReviewApiTest (4), TimelineNotificationApiTest (9), TimelineOverrideApiTest (41), TimelineStartApiTest (42), OrganizerMetricsApiTest (7), MatchMonitorTest (25), ParticipantTournamentStateTest (1), SpectatorTest (1).
- Regression backend bao gồm giờ server ở biên đăng ký/coding/check-in, quyền source và xét duyệt, freeze/version bất biến, validation và emergency/retry, bỏ lỡ check-in, queue rollback/tự bắt đầu, owner/player scope, lease/attempt, gameplay signal, receipt/projection/idempotency, danh hiệu và public results.
- Lượt đầu có một ca mới lỗi vì fixture Runner thiếu ownerUserId bắt buộc. Đã tạo tài khoản Runner hợp lệ và chạy lại toàn bộ TournamentTimelineApiTest cùng registration/review thành công. Không sửa schema hay production persistence để nới ràng buộc.
- Kiểm tra cú pháp 69 module JavaScript và `git diff --check`.
- Build bootJar sau thay đổi cuối; khởi động lại server với PostgreSQL, schema V36.
- Native HTTP/WebSocket smoke: participant-flow cùng revision summary, guest bị chặn participant-flow, notification/discovery route an toàn, WATCH_TOURNAMENT compact chỉ id/revision. Đối chiếu nguyên nội dung 38 trang, module và stylesheet phục vụ từ bootJar với source mới.

**SKIPPED**

- Migration tests riêng: không thay schema/DTO/backend production; vẫn V36, worker 0.11.0.
- Screenshot, viewport/mobile thực tế và browser click E2E toàn luồng: công cụ báo `Local browser use is disabled in Work settings`. Không dùng DOM/API smoke để tuyên bố các kiểm tra này đã PASS.
- Screen reader thực tế và benchmark tải hàng nghìn người chưa chạy.

Kiểm thử luồng ở đây gồm integration API/worker và client model/DOM. Chưa có một phiên browser tự động đi xuyên suốt toàn bộ kịch bản từ đầu đến cuối.

## File và API

Thêm:

- `client/js/tournament/keyboard-focus.js`
- `client/tests/tournament-final-flow.test.js`
- Tài liệu này.

Sửa:

- `client/css/tournament-mode.css`
- `client/js/tournament/create-timeline-model.js`, `create-timeline-editor.js`
- `client/js/tournament/player-detail.js`, `player-shell.js`, `player-dom.js`
- `client/js/tournament/match-monitor.js`, `competition-view.js`
- `client/tests/create-timeline.test.js`, `tournament-match-monitor.test.js`
- `server/src/test/java/com/rpsbattle/tournament/TournamentTimelineApiTest.java`
- `README.md`

Không thêm endpoint, đổi DTO, schema, worker protocol, GameEngine hay RuleSet. Script và tài khoản smoke chỉ ở thư mục build bị Git bỏ qua.

Đây là nhóm cuối của Chặng 12. Phần code đã triển khai đủ 10 nhóm; kiểm tra trực quan/E2E browser nêu trên vẫn cần thực hiện khi có trình duyệt được bật. Không tự chuyển chặng tiếp theo.
