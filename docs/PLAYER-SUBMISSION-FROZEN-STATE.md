# Player UI: hết hạn nộp bài, bản khóa và kiểm thử

Triển khai ngày 22/10/2026 theo PROMPT-FIX-PLAYER-SUBMISSION-FROZEN-STATE (1).md.

## Phạm vi

Sửa phần hiển thị của người chơi trên chi tiết giải, workspace và màn hình source chỉ đọc dùng chung shell. Không thay GameEngine, luật thi đấu, chính sách deadline, eligibility, cấp emergency, khóa bài hoặc kết quả kiểm thử. Không thiết kế lại Organizer UI; header chung có chế độ `audience: player`, còn cách hiển thị mặc định của organizer được giữ và có regression test.

## Ba lớp trạng thái độc lập

`resolvePlayerTournamentViewState({tournament, flow, workspace, submission})` trong player-view-state.js là pure resolver:

- Phase: dùng flow.phase của server; chưa có phase timeline thì SYNCING, không đoán từ lifecycle REGISTRATION.
- Submission: đọc frozenSubmission, submissionStatus và finalValidation thật. Phase SUBMISSION_FROZEN không tạo ra artifact.
- Action: chỉ cho phép thao tác khi có dữ kiện và capability phù hợp. Không có việc cần làm thì card hướng dẫn chờ, không tạo nút disabled hoặc link giả.

Resolver trả phase/phaseLabel, stepperState, submissionState, primaryAction/title/description, thông tin mốc tiếp theo và các cờ để chọn card chi tiết. next-action.js dùng cùng resolver; timeline-registration.js và code-workspace.js dùng các cờ để chọn màn hình bài khóa hoặc validation.

### Mapping dữ kiện

- NO_WORKSPACE, DRAFT, READY, SUBMITTED, LOCKED trong giờ coding chỉ hiện khi có trạng thái tương ứng từ server/đầu vào. NO_SUBMISSION không đủ để kết luận chưa tạo workspace nên giữ UNKNOWN.
- Bản khóa thật: frozenSubmission có versionId và decision AUTO_SUBMITTED hoặc EXPLICIT_SUBMIT.
- MISSING + NO_READY_VERSION_BEFORE_DEADLINE: NO_READY_AT_DEADLINE.
- MISSING vì chưa bấm nộp, tài khoản không hợp lệ, artifact lỗi hoặc bài bị từ chối: NO_VALID_SUBMISSION_AT_DEADLINE và lý do riêng; không nói sai rằng không có READY.
- Chưa có quyết định freeze: FREEZE_PENDING, không nhận đã khóa thành công.
- QUEUED/PENDING: VALIDATION_PENDING; RUNNING: VALIDATION_RUNNING; PASSED và applied: VALIDATION_PASSED; FAILED: VALIDATION_FAILED; INFRA_ERROR được giữ riêng. PASSED chưa applied vẫn chờ xác nhận.
- Phase VALIDATING không có job cá nhân thì không tự suy đang chạy, đạt hoặc thất bại.
- Dữ liệu validation cũ không được đưa lên màn hình đang ở SUBMISSION_FROZEN.

### Stepper

Vẫn 6 bước. SUBMISSION_FROZEN là mốc trung gian 1.5: Đăng ký và Lập trình hoàn tất, chưa bước nào CURRENT; Kiểm thử chưa được highlight. VALIDATING mới active bước Kiểm thử. Kết quả validation cá nhân nằm trong card, không thay giai đoạn hiện tại của stepper.

### CTA

- SUBMISSION_FROZEN + bản khóa thật: Xem bài đã khóa, cuộn đến card chi tiết có ID ổn định.
- SUBMISSION_FROZEN + không có READY/bài hợp lệ: không có thao tác; hướng dẫn chờ kiểm thử chính thức.
- FREEZE_PENDING: chờ server chốt, không có CTA xem snapshot.
- Validation QUEUED/RUNNING/FAILED: liên kết đến chi tiết công việc thực tế; lỗi hạ tầng có hướng dẫn riêng.
- CHECK_IN cần nextAction CHECK_IN và checkIn.canCheckIn đúng; không suy từ phase hoặc PASSED.
- Nộp lại khẩn cấp chỉ hiện khi server cấp cờ canEmergencyResubmit hoặc cờ tương đương đang có là emergencyCoding, đồng thời canAccessWorkspace/canModifyWorkspace đúng và đang CODING_OPEN. Policy ALLOW_EMERGENCY_RESUBMISSION hoặc FAILED không cấp quyền. Card cũ không che editor đang được server mở khẩn cấp.

## Header và số liệu

Public summary/detail thêm `approvedParticipantCount`, một scalar query đếm APPROVED trong tournament_timeline_registrations. Không đếm bot execution roster và không trả danh sách đơn hay ghi chú nội bộ. Không thêm endpoint hoặc migration; không đổi logic phê duyệt.

Header player dùng trường này. Chưa có số liệu: Người được duyệt: Đang tải…; có dữ liệu: 1 / 10 người được duyệt. SUBMISSION_FROZEN ghi Đã hết hạn nộp bài, không nhận đã khóa một bài cụ thể.

Nhãn mốc và đồng hồ là hai phần riêng, ví dụ Kiểm thử bắt đầu sau / 44:00:46. Đồng hồ tiếp tục dùng serverNow, phaseEndsAt và thời gian monotonic; đến mốc vẫn chờ server xác nhận. Timer organizer giữ nguyên kiểu cũ.

## Card và refresh

Card Việc của bạn chỉ nói hành động hoặc hướng dẫn chờ. Card Trạng thái bài nộp trình bày kết quả freeze, hạn, phiên bản và quyền chỉnh sửa. Hash nằm trong details thứ cấp. Xem source chỉ có khi bản khóa thật và canAccessWorkspace đúng, trỏ đến snapshot bất biến đã có, không khởi tạo editor/autosave.

Refresh key bổ sung frozenSubmission và quyền emergency, nên dữ kiện đổi cùng phase/revision vẫn cập nhật màn hình. Test bộ điều khiển trang thật chạy các lần đọc cùng revision để xác nhận.

CSS chỉ bổ sung các class player header/frozen card: khoảng cách rõ giữa card, thông tin kỹ thuật nhỏ hơn, metadata xuống một cột và nút đủ chiều rộng ở màn hình <=700px.

## Files

Mới:
- client/js/tournament/player-view-state.js
- client/tests/player-submission-frozen.test.js
- client/tests/player-header-countdown.test.js
- client/tests/player-frozen-controller.test.js
- docs/PLAYER-SUBMISSION-FROZEN-STATE.md

Sửa:
- client/js/tournament/progress-stepper.js, next-action.js, player-shell.js: phase, action resolver và header player.
- client/js/tournament/frozen-submission.js, final-validation-view.js, timeline-registration.js: chọn card dựa trên dữ kiện thật, wording và chi tiết snapshot.
- client/js/tournament/code-workspace.js: cùng resolver để không hiện card sai trong workspace.
- client/js/tournament/player-state.js, player-detail.js: nhận thay đổi freeze/emergency và tránh CTA code trùng.
- client/js/tournament/tournament-header.js, tournament-routes.js: count, countdown và anchor chi tiết.
- client/css/tournament.css: phân cấp thông tin và responsive.
- client/tests/next-action.test.js, player-shell.test.js, tournament-final-flow.test.js: fixture cung cấp artifact/job thật, không giả định nextAction đủ chứng minh có bài khóa.
- server/src/main/java/com/rpsbattle/tournament/TournamentService.java, timeline/TimelineRegistrationStore.java: aggregate đọc số APPROVED.
- server/src/test/java/com/rpsbattle/tournament/RegistrationPipelineApiTest.java: count người được duyệt khác roster thực thi và không lộ private fields.

## Xác minh

- Client: **PASS 383/383**, không failure hoặc skip; bao gồm test renderer, resolver, header/countdown và bộ điều khiển trang thật.
- Backend: **PASS 73/73**, không failure/error/skip. Gồm RegistrationPipelineApiTest, TimelineCodingApiTest và FinalValidationApiTest; kiểm tra count, quyền workspace, deadline, freeze và validation không đổi.
- Browser smoke, screenshot desktop và mobile: **SKIPPED**. Công cụ trình duyệt đã từ chối quyền quan sát URL trong phiên làm việc này; không đi vòng chính sách bằng browser khác hoặc Playwright ngoài công cụ. Chưa xác nhận bố cục bằng hình ảnh thật.
- Desktop/mobile: đã kiểm tra DOM, class và CSS responsive, không coi đó là screenshot verification.
- Log nằm tại server/build/local-run/player-frozen-*.txt (ignored).
