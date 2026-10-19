# Thông báo, tiến trình và CTA theo phase

## Thông báo trong ứng dụng

Người có đơn đăng ký timeline được đọc thông báo ngay cả khi chưa có bot/execution registration. Đơn duyệt/từ chối/waitlist và kết quả kiểm thử chính thức được lọc theo người dùng; projection không chứa lý do duyệt riêng, source, hash artifact, hidden test hay token. Người ngoài giải bị từ chối 403. Feed tài khoản và đánh dấu đã đọc dùng session/CSRF hiện có.

TimelineNotificationService xét Clock mỗi giây và ghi audit bền vững cho các mốc: mở đăng ký, sắp đóng đăng ký một giờ, coding sắp mở 30 phút, coding mở, còn không quá 30/10/5/1 phút, coding kết thúc, validation bắt đầu, check-in mở, check-in sắp đóng 10 phút và giải sắp bắt đầu 10 phút. Thông báo giải đã bắt đầu tiếp tục dùng sự kiện TOURNAMENT_STARTED hiện có. FinalValidationService ghi thông báo PASS/FAIL/INFRA_ERROR riêng cùng transaction áp dụng kết quả.

Khóa chống trùng gồm loại mốc và thời điểm trong timeline. Audit và revision được commit cùng snapshot giải; sau restart scheduler không phát lại cùng mốc. GET notifications không tạo thông báo hoặc sửa giải. Nhắc theo lịch chỉ áp dụng giải đã công bố, chưa bắt đầu; không gửi cho DRAFT, RUNNING, PAUSED, RECOVERY_REQUIRED, FINISHED hay CANCELLED.

Các mốc nhắc không được phát sớm hơn cửa sổ giai đoạn. Giải có coding ngắn dưới 30/10/5 phút không nhận cảnh báo dài hơn thời gian coding. Khi cập nhật muộn, chỉ mốc cảnh báo phù hợp khoảng thời gian hiện tại được tạo, không phát một loạt 30/10/5 phút đã lỗi thời. Không dựng lại lịch sử thông báo mở đăng ký/coding khi cửa sổ đó đã kết thúc. Countdown/cảnh báo cho lượt nộp khẩn cấp vẫn theo flow và deadline cá nhân trong workspace hiện có; không thay đổi timeline chung để phát nhắc cho người khác.

Người pending/waitlist chỉ nhận mốc đăng ký và kết quả xét đơn của mình; mốc coding/validation/check-in dành cho người APPROVED và chủ giải. Người có đơn CANCELLED/REJECTED không nhận thêm nhắc giai đoạn timeline. Các thông báo điều hành/trận đấu cũ giữ phạm vi hiện có. Đây là thông báo trong ứng dụng, không gửi email hay tin nhắn ra ngoài.

## Tiến trình và thẻ giải

Trang chi tiết/lobby và workspace có sáu bước: Đăng ký → Lập trình → Kiểm thử → Check-in → Thi đấu → Kết quả. Bước hiện tại dùng aria-current; các bước trước đánh dấu hoàn thành, bước tương lai khóa. Không có liên kết/nút trên stepper để truy cập giai đoạn tương lai. Phase lấy từ server; thanh không tự suy đoán RUNNING chỉ vì đồng hồ tới giờ. Giải CANCELLED được hiển thị riêng, không giả là đã hoàn thành các bước.

Discovery trả phase, applicationStatus, lịch đăng ký/bắt đầu theo timeline và registered cho người mới đăng ký chưa có bot. Tab giải đã tham gia cũng bao gồm đơn người chơi. CTA đổi theo phase; chỉ người APPROVED nhận đường dẫn trực tiếp vào phòng code. Trạng thái/ràng buộc bảo mật vẫn do server kiểm tra, không dựa vào nhãn nút.

Thông báo coding dẫn đến workspace; thông báo duyệt/validation/check-in dẫn đến chi tiết giải; trận đấu/khởi động dùng lobby hiện có. Chuông thông báo tiếp tục dùng read cursor theo tài khoản và bộ chống toast trùng khi reconnect.

## Lưu trữ và vận hành

Không cần migration mới, schema vẫn V27. Sử dụng audit, notificationReads và structure snapshot hiện có. Scheduler giới hạn 25 giải có thông báo cần ghi mỗi lượt; các giải đã xử lý không chiếm ngân sách lượt sau. `rps.timeline-notifications.initial-delay-ms` mặc định 2000; fixture test đặt một giờ để điều khiển Clock chủ động.

## File thêm/sửa

- Thêm TimelineNotificationService, TimelineNotificationApiTest, TimelineReminderTest; progress-stepper.js, progress-stepper.test.js và tài liệu này.
- Backend sửa TournamentNotificationService, TournamentDiscoveryService, FinalValidationService; test sửa StageThreeWebFixture và FinalValidationApiTest.
- Client sửa notifications.js, hub-model.js, tournament-hub.js, player-detail.js, code-workspace.js, tournament.css; cập nhật README.
- Không sửa GameEngine/RuleSet, thuật toán ghép trận hoặc pipeline chạy bot.

## Kiểm chứng

- Backend: 47 PASS, 0 FAIL, 0 SKIPPED (34 FinalValidationApiTest, 9 TimelineNotificationApiTest, 3 TimelineReminderTest, 1 TournamentDiscoveryTest, 1 TournamentNotificationTest). Bao gồm quyền trước khi có bot, riêng tư, read cursor bền vững, dedupe, boundary, cảnh báo bị lỡ, discovery và final PASS.
- Client: 115 PASS, 0 FAIL, 0 SKIPPED; syntax check workspace/player-detail/notifications đạt. Kiểm thử stepper không tạo liên kết tương lai, aria-current, CTA theo phase và chống toast trùng. bootJar thành công. Không có migration mới nên không chạy lại Flyway suite.
- Native PostgreSQL/Docker: hai người nhận thông báo duyệt trước khi roster có bot; joined discovery có đơn người chơi; bot Python build/validate READY, freeze và final PASS. Thông báo coding mở, còn một phút, coding kết thúc, validation, PASS và check-in xuất hiện đúng; không phát cảnh báo 30 phút cho cửa sổ ngắn. Người còn lại không nhận thông báo PASS của bot không thuộc mình, feed không lộ lý do duyệt riêng/source. Đánh dấu đã đọc thành công.
- Sau restart: giữ ID thông báo và read cursor, không tạo thông báo trùng, quyền riêng tư và selected frozen version không đổi.
- Chưa kiểm tra trực quan bằng browser: công cụ báo Local browser use is disabled in Work settings. Không có screenshot UI thực tế; các kiểm thử render/client và native API đã chạy.
