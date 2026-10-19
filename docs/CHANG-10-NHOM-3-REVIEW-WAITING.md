# Chặng 10 — Nhóm 3: Xét duyệt và chờ coding

## Xét duyệt

Chủ giải mở **Duyệt đăng ký người chơi** từ trang giải hoặc trang quản lý. Admin dùng `/admin/applications.html?id=...`; người tổ chức thông thường dùng `/tournament-applications.html?id=...`. Danh sách phân trang 25 dòng, lọc PENDING/APPROVED/WAITLISTED/REJECTED/CANCELLED. Chỉ manager được đọc trường/đội và thông tin xét duyệt của các đơn.

Có thể chấp nhận, đưa vào danh sách chờ hoặc từ chối khi REGISTRATION_OPEN, REGISTRATION_REVIEW hoặc WAITING_FOR_CODING. Mọi thao tác cần lý do tối đa 500 ký tự và revision hiện tại. Sai revision trả 409, không tự retry. Không xét duyệt sau codingOpenAt, khi giải bị pause/cancel/finish/recovery hoặc khi đơn đã CANCELLED. Không gửi lại cùng quyết định; có thể xét lại đơn REJECTED trước giờ code nhưng phải còn capacity. Xét duyệt không tạo bot hoặc execution roster.

V25 thêm review_reason, reviewed_at, reviewed_by với foreign key user và constraint metadata đầy đủ hoặc cùng null. Đăng ký lại đơn đã hủy xóa metadata review cũ nhưng giữ lịch sử audit. Quyết định, audit actor/reason/before/after và revision giải commit trong transaction chung của mutation hiện có.

Lý do đầy đủ chỉ được trả qua flow của chính người đăng ký và danh sách của manager. Public tournament detail/summary/export/WebSocket giữ sự kiện audit và trạng thái nhưng bỏ reason của PARTICIPANT_APPLICATION_REVIEWED; audit lưu trong DB vẫn giữ nguyên nội dung cho quản trị.

## API

- `GET /api/tournaments/{id}/participant-applications?page=0&status=PENDING`: manager, trả items, page, size=25, revision, reviewOpen, codingOpenAt; bỏ status để xem tất cả.
- `POST /api/tournaments/{id}/participant-applications/{applicationId}/review`: manager + CSRF, `{revision,decision:"APPROVED"|"WAITLISTED"|"REJECTED",reason}`. Kiểm tra application thuộc đúng giải.
- `GET participant-flow`: bổ sung reviewReason/reviewedAt trong đơn của chính mình và nextAction WAIT_FOR_APPROVAL, WAIT_FOR_CODING, WAITLISTED, REGISTRATION_REJECTED. Khi đúng CODING_OPEN và APPROVED, nextAction là OPEN_CODE_WORKSPACE.

## Màn hình người chơi

Sau khi đăng ký đóng, còn PENDING thì phase là REGISTRATION_REVIEW; khi không còn đơn PENDING thì phase là WAITING_FOR_CODING. WAITLISTED không có quyền code dù global phase đã sang waiting.

Người APPROVED thấy xác nhận chấp nhận, giờ bắt đầu, countdown từ server offset, ngôn ngữ cho phép, luật và bot protocol. Không có editor hay nút submit trước giờ. Người REJECTED thấy lý do, không có link code. Người WAITLISTED thấy thông báo chờ; người chưa đăng ký thấy đăng ký đã đóng. API workspace vẫn kiểm tra phase và APPROVED độc lập với UI. Đúng giờ coding, gateway có thể dẫn tới workspace hiện có; timer/freeze và gắn bài nộp vào registration chính thức thuộc các nhóm tiếp theo.

## Kiểm chứng và triển khai

- **69 backend PASS, 0 FAIL, 0 SKIPPED**, gồm 20 Flyway PostgreSQL Docker tests, JPA, timeline/registration/review API và hồi quy workspace/submission/registration legacy.
- **90 client PASS**, gồm model quyết định/lý do/revision, routing admin, waiting trước giờ code và rejection không mở editor.
- bootJar build thành công. Backup local `backups/before-stage10-review-v25` gồm DB/bot/replay và checksum trước khi nâng schema; không sửa migration đã áp dụng.
- Trình duyệt local đã chấp nhận một đơn, chuyển đơn khác vào waitlist rồi từ chối; kiểm tra màn hình approved waiting và rejection reason. Native API xác nhận phase/metadata, quyền manager, reason không rò vào public view và editor vẫn khóa. Kết quả được kiểm tra lại sau restart bản cuối.

File mới: V25, application-review.js/application-review-model.js, hai trang applications, application-review.test.js, TimelineReviewApiTest và tài liệu này. File sửa: entity/store/service/controller timeline registration, TournamentService (redact public reason), model/routing/account UI và các liên kết quản lý, timeline-registration.js, Flyway/JPA/registration tests, README/DEPLOYMENT.

Không sửa GameEngine, RuleSet, Match Engine, BotProtocol hoặc DockerBotProcess. Chưa triển khai notifications thời gian (nhóm 9), coding/freeze/final validation/check-in tự động (nhóm 4–8) hoặc scale 2.000 người (nhóm 10). Vẫn một replica và giới hạn 32 suất chính thức. Dừng sau nhóm 3 để review.
