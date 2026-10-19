# Check-in theo timeline và phòng chờ

Giải có timeline bắt buộc xác nhận check-in trong `[checkInOpenAt, checkInCloseAt)`, kể cả khi cờ `checkInRequired` cũ là false. Giải không có timeline giữ luật check-in cũ. Server dùng Clock để quyết định; sửa giờ máy hoặc gọi API trực tiếp không mở quyền sớm/muộn.

## Điều kiện và API

`POST /api/tournaments/{id}/check-in` dùng session/CSRF, nhận `registrationId` tùy chọn. Người gọi phải có đơn người chơi APPROVED, execution registration APPROVED/CHECKED_IN và bài LOCKED gắn với kết quả kiểm thử chính thức PASSED/applied đúng version của freeze mới nhất. Không thể check-in hộ người khác. Gửi lặp trong cửa sổ mở giữ nguyên checkedInAt và revision.

`GET /api/tournaments/{id}/participant-flow` bổ sung `checkIn`: status, registrationId, checkedInAt, canCheckIn, opensAt, closesAt, startsAt, checkedInCount. Next action CHECK_IN mở nút xác nhận, OPEN_WAITING_ROOM hiển thị phòng chờ, CHECK_IN_MISSED thông báo quá hạn. Bot chưa hợp lệ vẫn ở màn hình kiểm thử, không bị ghi nhầm là bỏ lỡ check-in.

CheckedInAt đã có cột bền vững; không cần migration mới, schema vẫn V27. Check-in và audit được commit cùng transaction với snapshot giải, rollback khôi phục bộ nhớ nếu lưu thất bại.

## Đóng cửa sổ

Scheduler xét deadline mỗi giây, tối đa 50 registration mỗi giải mỗi lượt. APPROVED chưa xác nhận chuyển NOT_CHECKED_IN và bị loại khỏi participants; CHECKED_IN giữ nguyên. Chạy lại không tạo audit/revision thừa. API từ chối trễ ngay theo Clock kể cả scheduler chưa xử lý. Giải timeline không dùng checkInDeadline cũ. Chủ giải không thể khóa đăng ký/check-in hoặc bắt đầu sớm vượt lịch.

Bước này chưa tự ghép trận, tự chuyển RUNNING hay hủy giải thiếu người. Chốt roster/chuẩn bị và bắt đầu giải thuộc nhóm 8; trạng thái thực tế có thể vẫn REGISTRATION trong khi phase được resolver tính CHECK_IN_OPEN/READY_TO_START.

## Phòng chờ

Trang chi tiết/lobby dùng cùng giao diện theo flow: tên participant, ngôn ngữ/version đã qua kiểm thử, thời điểm check-in, số người đã xác nhận, countdown bắt đầu theo giờ server, thông báo cá nhân hiện có, luật và lịch đấu. Khi chưa ghép trận hiển thị trạng thái chờ, không bịa đối thủ. Sau giờ bắt đầu nếu server chưa chuyển RUNNING, giao diện báo chờ server xác nhận. Poll và SSE tải lại trạng thái; reload giữ thông tin đã lưu.

Thông báo `PARTICIPANT_CHECKED_IN` và CHECK_IN_MISSED được lọc theo registration của người xem. Dữ liệu source, token chạy bot và hidden test không được đưa vào projection mới.

## File thêm và sửa

- Thêm `client/js/tournament/timeline-check-in.js`, `client/tests/check-in-phase.test.js` và tài liệu này.
- Backend sửa `OwnedTournamentService`, `TimelineRegistrationService`, `TournamentNotificationService`.
- Client sửa `timeline-registration.js`, `player-detail.js`, `competition-view.js`, `notifications.js`, `ui/toast.js`.
- Kiểm thử sửa `FinalValidationApiTest`, `StageThreeWebFixture`; cập nhật README.

Không sửa GameEngine, RuleSet hay luật bàn cờ.

## Kiểm chứng

- Backend: 36 PASS, 0 FAIL, 0 SKIPPED (33 ca FinalValidationApiTest gồm coding/deadline/final/check-in và 3 ca TournamentRegistrationApiTest cũ). Kiểm tra trước giờ mở một nanosecond, đúng giờ đóng, thiếu final PASS, ownership, idempotence, hết hạn và chặn manager đóng/bắt đầu sớm.
- Client: 107 PASS, 0 FAIL, 0 SKIPPED; syntax check các module điều khiển đạt. bootJar thành công. Không có migration mới nên không chạy lại Flyway migration suite.
- PostgreSQL/Docker thật: hai tài khoản build/validate Python READY, freeze và final PASS; chặn sớm dù bot hợp lệ; chặn check-in hộ 403; check-in đúng cửa sổ, gửi lặp không tăng revision; chặn trễ và loại người chưa xác nhận. Restart giữ version, checkedInAt (so sánh đến millisecond vì precision PostgreSQL), trạng thái CHECKED_IN/NOT_CHECKED_IN và roster. Chưa tạo trận hoặc tự bắt đầu.
- Lượt native đầu bị gián đoạn do build đè JAR đang chạy; đã restart và chạy lại toàn bộ lượt native thành công trên JAR ổn định.
- Chưa kiểm tra trực quan trong browser: công cụ browser bị tắt trong Work settings. Các kiểm thử render và API đã chạy; không có ảnh chứng minh UI thực tế cho nhóm này.
