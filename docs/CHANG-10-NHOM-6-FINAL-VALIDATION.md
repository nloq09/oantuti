# Chặng 10 — Nhóm 6: Kiểm thử chính thức bài đã khóa

## Luồng và quyền

Đến validationStartAt, hệ thống lấy các quyết định freeze có bài nộp của nhóm 5 và tạo FinalValidation. Không lấy draft, không dùng workspace hiện hành thay thế bản đã khóa và không compile lại source. codingCloseAt vẫn chặn source edit/save/build/test/submit. Final validation lưu riêng với source/version và quyết định deadline gốc.

participant-flow thêm finalValidation của chính tài khoản. submissionStatus có thể là VALIDATING, VALIDATION_FAILED, VALIDATION_INFRA_ERROR hoặc BOT_READY. Chỉ PASSED và applied=true mới hiển thị TOURNAMENT READY. Sắp tới check-in theo lịch sẽ nối ở nhóm 7; nhóm này chưa tự mở check-in hoặc chạy giải. requireLocked dùng cho check-in/prepare/execution nay bắt buộc kết quả final PASSED đã apply đúng phiên bản đối với đăng ký theo timeline, không thể dùng nút quản lý cũ để bỏ qua kiểm thử.

## Pipeline và kết quả

Pipeline: BUILD verification → PROTOCOL → TIMEOUT/RESOURCE/LEGAL_ACTION → HIDDEN_TESTS → TOURNAMENT_TESTS → ARTIFACT_HASH. BUILD xác minh hash source và artifact đã build, không biên dịch lại. Public tests thực hiện giao thức và nước đi qua GameEngine hiện có; hidden tests luôn chạy trong final validation; đấu mẫu dùng luật/engine hiện tại và kiểm tra bot hoàn tất một trận hợp lệ. Kiểm tra policy của giải bao gồm chủ sở hữu, application APPROVED, tài khoản ACTIVE, ngôn ngữ, cách nộp và bài đã khóa.

TIMEOUT/RESOURCE nghĩa là bài hoàn tất kiểm thử trong các giới hạn sandbox cố định hiện có (2 giây/nước, 0,5 CPU, RAM 384 MiB, giới hạn PID/tmp/output); không phải số đo peak CPU/RAM hoặc stress benchmark riêng. Đấu kiểm thử theo giải dùng mẫu Classic hiện tại và policy của giải, chưa có chức năng upload bộ test riêng. Hidden state, input, debug và exception text không xuất hiện trong DTO final; chỉ công bố các bước PASS/FAIL/PENDING/RUNNING và mã lỗi đã lọc. Không sửa GameEngine, RuleSet, Match Engine, BotProtocol hoặc DockerBotProcess.

Khi PASS, transaction apply cập nhật con trỏ execution của bài nộp, duyệt execution registration và thêm participant ổn định vào roster. Bản nguồn/artifact được giữ nguyên. FAILED loại bài khỏi roster; INFRA_ERROR giữ source/bài khóa để organizer/admin có thể chạy lại trước validationDeadline với reason và revision. Kết quả đến đúng hoặc sau hạn không được công nhận PASS. Nếu server bỏ lỡ toàn bộ cửa sổ kiểm thử, ghi VALIDATION_DEADLINE và chờ xử lý hạ tầng, không tự coi bot sai hoặc tự mở editor.

## Chính sách bài không đạt

Timeline bổ sung validationFailurePolicy, mặc định DISQUALIFY. Chủ giải/admin chọn khi DRAFT, cùng endpoint PUT timeline có revision/audit và bộ chọn trong trang quản lý. Sau công bố không đổi policy bằng đường cấu hình draft.

- DISQUALIFY: final FAILED khiến bài DISQUALIFIED, execution registration REJECTED và không có participant thi đấu.
- USE_LAST_VALID_VERSION: tìm bản trước có READY facts trước hạn freeze của chính người chơi; đưa bản đó vào hàng đợi kiểm thử chính thức đầy đủ. Chỉ bản thực sự PASS mới được dùng. Quyết định freeze gốc giữ nguyên, final history lưu các lần thất bại và phiên bản thay thế. Nếu không có bản trước đủ điều kiện thì bài bị loại.
- ALLOW_EMERGENCY_RESUBMISSION: không tự mở editor. Organizer/admin cấp một lượt riêng cho tài khoản đã final FAILED và đã apply, bắt buộc reason 1–400 ký tự, revision mới nhất, deadline mới sau giờ server và trước validationDeadline. Mỗi người tối đa một lượt khẩn cấp trong giải. Tài khoản khác không được mở lại. Lượt này có thêm tối đa 3 bản build so với quota ban đầu; các kiểm tra ngôn ngữ, provenance, ownership và sandbox giữ nguyên.

Lượt khẩn cấp tạo capability CODING_OPEN riêng trong participant-flow, countdown dùng hạn mới; không thay timeline chung của cả giải. Hết hạn mới, nguồn lại bị chặn tức thời theo Clock. Hệ thống lưu một freeze mới với codingCloseAt mới, rồi kiểm thử lại. Deadline policy gốc vẫn áp dụng: AUTO_SUBMIT_LATEST_READY tự nộp bản READY đủ điều kiện; REQUIRE_EXPLICIT_SUBMIT vẫn cần bấm nộp. Freeze và kết quả lỗi gốc được giữ lại cho audit; không ghi đè lịch sử hoặc tự unlock mọi người.

## Hàng đợi, transaction và restart

V27 tạo tournament_final_validations, unique freeze_id, lưu selected version/hashes, steps, history, failure policy, current job, thời điểm và metadata emergency. bot_coding_jobs bổ sung final_validation_id; loại job vẫn là VALIDATE, FK này phân biệt final validation với kiểm thử trong giờ code. Không sửa migration cũ hoặc bỏ constraint kind hiện có.

Dùng chung queue guard, capacity mặc định 200 và tổng cộng 2 worker với build/test. Final không tạo pool Docker riêng. Lease 240 giây, tối đa 3 lần reclaim mỗi job; token cũ không được ghi đè kết quả của lease mới. Job sau gián đoạn có thể phải đợi lease hết hạn trước khi được lấy lại. Lỗi quá số lần phục hồi chuyển INFRA_ERROR, owner/admin retry có audit. Một replica vẫn là cấu hình hỗ trợ của project.

Scheduler xử lý tối đa 50 thao tác mỗi lượt, mỗi giải giới hạn lượng admission/apply; transaction từng bài gồm job/decision và audit/snapshot giải. Apply rollback DB và bộ nhớ khi commit lỗi. Queue đầy thì admission rollback và lượt sau thử tiếp. Worker không sửa source hoặc trạng thái kiểm thử trong giờ code; kết quả final tách riêng. Sau restart, journal/job/result/roster được đọc lại từ DB và không duyệt hoặc tạo bài trùng. Giải đã bắt đầu, DRAFT, CANCELLED hoặc FINISHED không được thêm participant từ scheduler này.

## API và giao diện

- GET /api/tournaments/{id}/participant-flow: finalValidation riêng của người gọi, gồm tên người chơi, status, selected/original version, hashes, các bước, history đã lọc, applied và hạn emergency nếu có.
- GET /api/tournaments/{id}/final-validations?page=0: dashboard organizer/admin, 25 mục/trang.
- POST /api/tournaments/{id}/final-validations/{validationId}/retry: revision và reason; chỉ INFRA_ERROR trước hạn.
- POST /api/tournaments/{id}/final-validations/{validationId}/emergency: revision, reason, deadline; chỉ policy/điều kiện nêu trên.

Người chơi thấy màn hình kiểm thử thay cho đăng ký/chờ code, tiến độ lấy từ server và có BOT READY sau commit. Workspace chỉ đọc cũng có trạng thái final. Chủ giải dùng tournament-final-validation.html; admin dùng admin/final-validation.html, giữ điều hướng trong khu quản trị. Dashboard ghi rõ tên participant, phiên bản, từng bước và thao tác hợp lệ. Các nút retest/allow-resubmit legacy bị ẩn và server từ chối trên giải có timeline; dùng luồng final có audit thay thế.

## Vận hành và phạm vi

Backup PostgreSQL cùng bots/replays trước nâng V27; local có backups/before-stage10-validation-v27 (gitignored). Deploy client/server đồng bộ. Lịch, quota roster hiện tại và giới hạn một replica không đổi; kiểm thử tải 1–2000 người thuộc nhóm 10. Nhóm 7 tiếp tục check-in theo lịch và waiting room; nhóm này không tự start giải.

## File thêm/sửa

Mới: FinalValidation, FinalValidationStore, FinalValidationWorker, FinalValidationService, FinalValidationController, V27__final_validation_jobs.sql, FinalValidationApiTest; final-validation-view.js, final-validation-dashboard.js, validation-screen.test.js, hai trang tournament-final-validation.html/admin/final-validation.html và tài liệu này.

Sửa: TournamentTimeline; CodingJob/QueueStore/Worker, SubmissionStore/Service, CodeWorkspaceService, PreuploadedSubmissionService; TimelineAccessGuard/TimelineRegistrationService; BotTestService (mã lỗi hạ tầng hidden đã lọc); StageThreeWebFixture và FlywayMigrationTest. Client sửa timeline-registration, player-detail, code-workspace, deadline-policy-editor, tournament-detail, submission-dashboard, admin/tournaments, account-ui, toast và tournament.css. README/DEPLOYMENT cập nhật.

## Kiểm chứng

Backend hồi quy 121 PASS, 0 FAIL, 0 SKIPPED, gồm 22 Flyway PostgreSQL thật và các ca boundary, queue full, lease stale, ownership, hidden privacy, fallback, emergency và rollback đã có. Lượt cuối 29 ca final/coding/deadline chạy lại sau hoàn thiện API. Client 103 PASS, 0 FAIL, 0 SKIPPED; syntax check các controller đạt. bootJar build thành công.

Docker thật: CODE_IN_PLATFORM_ONLY, PRE_UPLOADED_ONLY và BOTH đều dùng đúng frozen artifact, chạy public/hidden/trận mẫu, thành PASSED/applied và roster APPROVED. Ca fallback chủ động thay đổi artifact của bot test sau freeze: phát hiện ARTIFACT_CHANGED, bản cũ được kiểm thử lại và dùng thành công, freeze gốc không đổi. Ca emergency phát hiện lỗi artifact, manager cấp reason/deadline, build replacement READY, hết hạn mới lại freeze/final PASS. Các file artifact test đã được khôi phục; không tác động bot của người dùng khác.

Trình duyệt: màn hình người chơi hiển thị đủ các bước và Bot đã được xác nhận sau apply; dashboard chủ giải hiển thị đúng tên participant/phiên bản/PASSED, không hiện thao tác retry/unlock cho bài đã đạt. Ảnh local: server/build/local-run/stage10-final-ready.png và stage10-final-dashboard.png (gitignored).

Sau restart bản JAR cuối, cả năm ca CODE/PRE/BOTH/fallback/emergency vẫn giữ nguyên selected version, hashes, history, trạng thái PASSED/applied và roster APPROVED; nguồn vẫn khóa. Chủ giải đọc dashboard được, tài khoản đối thủ thường bị từ chối 403. Route admin được kiểm tra riêng ở client; backend dùng TournamentAuthorizationService chung cho owner/admin.
