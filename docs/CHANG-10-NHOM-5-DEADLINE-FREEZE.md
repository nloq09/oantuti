# Chặng 10 — Nhóm 5: Khóa bài và tự nộp theo deadline

## Chính sách

Timeline bổ sung `deadlinePolicy`: `AUTO_SUBMIT_LATEST_READY` (mặc định) hoặc `REQUIRE_EXPLICIT_SUBMIT`. JSON timeline cũ chưa có trường này nhận mặc định tự nộp. Organizer/admin chọn trong timeline API PUT có revision/audit; trang quản lý giải có bộ chọn khi giải còn DRAFT, sau công bố chỉ hiển thị chính sách đã chọn. Cấu hình lịch vẫn dùng API của nhóm 1.

Đây là chính sách của timeline, độc lập với `botPolicy.deadlineBehavior`, `submissionDeadline` và `lockAtDeadline` của giải legacy. Giải có timeline luôn khóa mutation tại codingCloseAt, kể cả lockAtDeadline legacy tắt. Không thay đổi scheduler hoặc hành vi giải không có timeline.

## Chọn bài

Chỉ xử lý application APPROVED và tài khoản ACTIVE. Tự nộp chọn bản READY mới nhất theo createdAt (ID làm tie-break), từ mọi workspace/ngôn ngữ của chính người đó, với createdAt và updatedAt đều trước codingCloseAt. Bản build/test hoàn tất đúng hoặc sau hạn không đủ điều kiện, kể cả tác vụ deadline bị chậm hoặc server restart. Không build hoặc chạy Docker mới ở deadline.

Bài đã LOCKED giữ nguyên. Bài SUBMITTED được thay bằng bản READY mới hơn nếu allowResubmitBeforeDeadline bật; nếu tắt giữ bài đã bấm nộp. Bản build/test mới chưa xong không che mất bản READY cũ. Policy bắt buộc bấm nộp không biến một bản READY chưa submit thành bài chính thức. Không có bản đủ điều kiện thì ghi MISSING với lý do; không tạo bot hay participant giả. Source draft vẫn được giữ để đọc/tải.

Giữ các kiểm tra ngôn ngữ, source provenance, hidden/public/sample tests và toàn vẹn artifact của pipeline hiện tại. Bản được chọn giữ sourceHash/artifactHash và ID phiên bản; không compile lại. Application APPROVED không tự biến thành quyền check-in hoặc kết quả final validation; việc nối roster chính thức và kiểm thử cuối thuộc nhóm 6–7.

## Tác vụ bền vững

Migration V26 tạo tournament_coding_freezes: một quyết định cho mỗi tournament/user/codingCloseAt, unique ở DB. Lưu decision, reason, phiên bản, ngôn ngữ, hash, submittedAt, frozenAt. Không lưu raw source trong response phase hoặc tournament công khai.

CodingDeadlineService quét mỗi giây, mỗi lượt tối đa 50 người, mỗi giải tối đa 25 người. Query chỉ lấy application chưa có quyết định của deadline đó. Mỗi người có transaction riêng bao gồm promotion, execution registration, khóa bài, snapshot deadline, audit và snapshot giải. Lỗi rollback cả DB và bộ nhớ; lượt sau tiếp tục người chưa xong. Worker không tạo duplicate submission khi chạy lại. Đây là xử lý bounded cho deadline; load test 1–2000 người vẫn thuộc nhóm 10, giới hạn roster hiện tại không đổi.

Không cần cron chạy đúng mili giây để khóa editor: TimelineAccessGuard luôn chặn mutation theo Clock server. Trong lúc journal chưa xong, API hiển thị FREEZE_PENDING. Giải chưa bắt đầu bị PAUSED/RECOVERY vẫn được chốt deadline; không thêm bài vào giải đã bắt đầu, FINISHED, CANCELLED hoặc DRAFT. Các job build/test đang chạy có thể hoàn tất nhưng không được chọn nếu READY sau hạn; bài đã bị khóa không cho worker thay artifact đã chọn.

## API và giao diện

participant-flow thêm submissionStatus và frozenSubmission của chính tài khoản: FREEZE_PENDING, SUBMISSION_MISSING hoặc SUBMISSION_FROZEN sau hạn. nextAction chuyển VIEW_FROZEN_SUBMISSION. Snapshot cho biết bài tự nộp hay đã bấm nộp, phiên bản, ngôn ngữ, sourceHash, artifactHash, submittedAt và codingCloseAt. Không báo bot đã qua final validation.

Trang chi tiết participant chuyển sang màn hình Phần lập trình đã kết thúc, bỏ countdown chờ code/form đăng ký. Workspace cũng hiện kết quả khóa và giữ editor chỉ đọc; link tải source vẫn dùng quyền sở hữu cũ. Client không tự suy rằng READY đã được nộp khi countdown về 0, mà chờ response server. Poll tiếp cả khi giải không tăng revision để nhận kết quả phase/deadline mới.

## Vận hành và phạm vi

Sao lưu PostgreSQL và toàn bộ bots/replays trước nâng V26. Deploy client/server đồng bộ. Local backup nằm trong backups/before-stage10-freeze-v26 (gitignored). Sau restart, phase dựa vào server time; journal phục hồi từ PostgreSQL, người đã chốt giữ nguyên quyết định/hashes.

Nhóm này chưa chạy final validation, chưa mở check-in hoặc tự khởi động giải. Không sửa GameEngine, RuleSet, Match Engine, BotProtocol hoặc DockerBotProcess. Nhóm tiếp theo là Final Validation.

## File

Mới: CodingDeadlineService, CodingFreeze, migration V26, TimelineDeadlineApiTest, frozen-submission.js, deadline-policy-editor.js, source-freeze.test.js và tài liệu này.

Sửa: SubmissionStore, TournamentTimeline, TimelineRegistrationService, code-workspace.js, timeline-registration.js, player-detail.js, tournament-detail.js, TimelineCodingApiTest (tắt scheduler deadline thật trong fixture Clock), FlywayMigrationTest, README và DEPLOYMENT.

## Kiểm chứng

Backend hồi quy: 89 PASS, 0 FAIL, 0 SKIPPED, bao gồm 21 Flyway PostgreSQL thật. Sau hai kiểm tra bổ sung về biên trước deadline và hash của bài đã nộp, lượt kiểm tra cuối 24 PASS, 0 FAIL, 0 SKIPPED (17 deadline/coding và 7 submission legacy; có các ca trùng lượt hồi quy). Client: 98 PASS, 0 FAIL, 0 SKIPPED. Build bootJar thành công.

Docker thật: CODE_IN_PLATFORM_ONLY, PRE_UPLOADED_ONLY và BOTH build/validation READY nhưng không bấm Submit, đến giờ đều AUTO_SUBMITTED và LOCKED đúng version/hash. Ca REQUIRE_EXPLICIT_SUBMIT có READY nhưng chưa bấm Submit nhận MISSING/EXPLICIT_SUBMIT_REQUIRED. PUT source muộn đều 409. Sau restart bản JAR cuối, snapshot frozen, version/hash và lý do thiếu bài giữ nguyên, không tạo duplicate.

Trình duyệt: trang participant tự chuyển từ coding sang màn hình bài khóa; thấy phiên bản, ngôn ngữ, thời điểm nộp và hashes. Ca explicit missing hiển thị đúng lý do; workspace giữ source chỉ đọc, các mutation bị vô hiệu hóa và nút tải source còn dùng được. Ảnh kiểm chứng local nằm trong server/build/local-run/stage10-deadline-frozen.png (gitignored). Không sửa GameEngine/RuleSet.

Bộ chọn chính sách được thử trên trình duyệt với giải nháp: chuyển AUTO_SUBMIT_LATEST_READY sang REQUIRE_EXPLICIT_SUBMIT, lưu thành công, UI cập nhật lựa chọn và audit TIMELINE_CONFIGURED lưu cả giá trị trước/sau. Ảnh local: server/build/local-run/stage10-deadline-policy.png.
