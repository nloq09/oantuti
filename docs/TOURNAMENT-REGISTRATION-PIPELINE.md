# Registration pipeline — nhóm 1 và 2

Ngày: 21/10/2026. Các phần đầu ghi nhận nhóm 1; phần nhóm 2 bên dưới mô tả cơ chế đã nối vào server/client. Tiếp tục dừng sau mỗi nhóm theo mục 52–53 của yêu cầu.

## Luồng thực tế đã đối chiếu

- `tournament/timeline/TimelineRegistration`: đơn của **người chơi**, lưu bảng `tournament_timeline_registrations`, một đơn cho mỗi cặp tournament/user. Không có bot, source hay version ở bước đăng ký. Các trạng thái lưu hiện tại: PENDING, APPROVED, WAITLISTED, REJECTED, CANCELLED.
- `TimelineRegistrationService` dùng tài khoản từ session, kiểm tra phase bằng clock server, rules acceptance, ngôn ngữ và affiliation. `register` hiện luôn tạo PENDING. `list` và `review` yêu cầu manager; review kiểm tra revision và dùng transaction/audit có sẵn.
- `TimelineRegistrationStore`: paging cố định 25; filter status; query theo tournament. Hiện capacity tính cả PENDING + APPROVED + WAITLISTED và từ chối đơn mới khi đầy. Đây chưa phải capacity của pipeline mới. Waitlist chưa có thứ tự/promote tự động.
- `domain/TournamentRegistration`: danh sách thực thi với bot/version hoặc controller HUMAN, trạng thái CHECKED_IN/NOT_CHECKED_IN. Tách biệt với đơn của người chơi; không đổi enum này để biểu diễn NEEDS_REVIEW.
- `OwnedTournamentService`: giải có timeline chặn các đường đăng ký legacy để tránh bypass. `submissionMutation` phối hợp transaction DB, monitor của tournament và rollback snapshot RAM. Đây là cơ chế cần tái sử dụng; monitor một JVM không chứng minh an toàn nhiều instance.
- `TournamentPhaseResolver`: mốc đăng ký là cửa sổ nửa mở [open, close). `reviewComplete` hiện kiểm tra không còn PENDING; đến codingOpenAt vẫn chuyển CODING_OPEN. Chưa có roster lock người chơi độc lập.
- `OwnedTournamentService.closeRegistration`: action legacy còn gắn với chuẩn bị danh sách thi đấu, khóa submission, chuyển CHECK_IN/READY; với timeline bị chặn trước checkInCloseAt. Không thể tái dùng nguyên trạng action này làm “chỉ đóng nhận đơn”.
- `OrganizerMetricsStore`/`OrganizerMetricsService`: đã có số liệu registration ngoài trang list 25 đơn; nên mở rộng projection này thay vì tạo dashboard đọc toàn bộ đơn ở client.
- `TournamentNotificationService`: đã có notification cá nhân từ audit, bao gồm review application; có thể mở rộng khi có promotion, phải giữ lọc đúng người nhận.
- Client `timeline-registration-model.js`, `timeline-registration.js`, trang applications và organizer next action đang hiểu PENDING là chờ duyệt. Khi nối pipeline phải đổi đồng bộ để không để NEEDS_REVIEW rơi vào nhánh lỗi hoặc không hiện CTA.
- `maxParticipants` đã hỗ trợ tới 4000 với SINGLE_ELIMINATION/SWISS; các format khác tối đa 32. Migration V35 đã có constraint tương ứng, không cần tăng capacity lần nữa.

## Model đã bổ sung

Package `com.rpsbattle.tournament.registration`:

- `RegistrationApprovalPolicy`: AUTO_APPROVE, MANUAL_REVIEW, HYBRID.
- `defaultForNewTournament()` đề xuất HYBRID cho pipeline mới.
- `fromStored(null)` trả MANUAL_REVIEW để snapshot cũ thiếu field không bị đổi sang tự duyệt. Giá trị explicit được giữ nguyên.
- `EligibilityDecision`: `decision` gồm APPROVE, REVIEW, WAITLIST, REJECT và danh sách `reasonCodes`.
- `EligibilityReasonCode`: DUPLICATE_REGISTRATION, MISSING_REQUIRED_FIELD, LANGUAGE_NOT_ALLOWED, AFFILIATION_NOT_VERIFIED, TEAM_INVALID, CAPACITY_FULL, ACCOUNT_NOT_ELIGIBLE, REGISTRATION_CLOSED, MANUAL_REVIEW_REQUIRED.

APPROVE phải có reasonCodes rỗng. Các kết quả còn lại phải có ít nhất một business reason. Danh sách immutable, loại mã trùng nhưng giữ thứ tự; không chấp nhận null hay mã JSON không biết. Không chứa exception message, private note hoặc dữ liệu tài khoản. Đây là kết quả nội bộ; không trả thẳng cho Player khi có các kiểm tra nhạy cảm.

Ví dụ hợp lệ:

```json
{"decision":"REVIEW","reasonCodes":["AFFILIATION_NOT_VERIFIED"]}
```

Tại thời điểm hoàn tất nhóm 1, các model này chưa được Controller/Service gọi. Giải mới tạo ở phiên bản này vẫn duyệt thủ công; chưa có thiết lập policy hoạt động trên UI/API. Việc nối và lưu policy cùng eligibility engine thuộc nhóm 2, phải hoàn tất trước khi cho người tổ chức chọn AUTO_APPROVE/HYBRID.

## Kế hoạch tích hợp các nhóm sau

1. **Nhóm 2:** lưu policy qua cấu trúc tournament-owned hiện có, mở rộng cấu hình với authorization/revision/audit; service eligibility nhận context do server dựng. New pipeline mặc định HYBRID; dữ liệu cũ không policy giữ MANUAL_REVIEW. Quyết định APPROVE/REVIEW/WAITLIST/REJECT được map thành APPROVED/NEEDS_REVIEW/WAITLISTED/REJECTED. Đơn PENDING cũ là hàng chờ người duyệt, cần projection/migration tương thích; không đổi ý nghĩa thành “đang đánh giá” mà mất đơn cần xử lý. Ngôn ngữ cho phép, tài khoản active, affiliation và team phải dựa vào dữ liệu/chính sách thực, không coi chữ người dùng tự gõ là đã xác minh.
2. **Nhóm 3:** capacity chỉ cấp chỗ cho APPROVED; tách số đơn khỏi số slot. Tái kiểm tra và cấp chỗ trong cùng transaction, dùng khóa DB trên cùng tournament cho register/review/promote, có thứ tự khóa nhất quán. Không dựa riêng `synchronized(t)` để tuyên bố an toàn nhiều JVM. Unique tournament/user tiếp tục chống trùng. Bổ sung thứ tự waitlist và index đúng query, default FIRST_COME_FIRST_SERVED.
3. **Nhóm 4:** lưu Registration Close độc lập Roster Lock. Rút đơn trước lock giải phóng chỗ rồi xét người tiếp theo trong cùng transaction; promotion tạo audit/notification cá nhân. Chặn review/promote thường sau lock; còn NEEDS_REVIEW phải chặn lock hoặc yêu cầu override riêng, có lý do/audit. Không tự lock tại deadline.
4. **Nhóm 5–6:** dashboard summary, CTA ưu tiên NEEDS_REVIEW, filter mặc định khi còn ngoại lệ; search/filter/paging phía server với giới hạn page size. Bulk theo application ID trong phạm vi giải, validate từng đơn và trả partial conflict; reject phải có reason.
5. **Nhóm 7:** projection Player chỉ đơn của chính mình, trạng thái rõ, vị trí waitlist riêng tư, message công khai riêng với ghi chú nội bộ. Auto-approve render response cuối cùng, không flash PENDING.
6. **Nhóm 8:** kiểm chứng authorization, transaction/rollback, hai request tranh slot cuối, hai promotion tranh chỗ; E2E xuyên các phase. Không sửa GameEngine.

## API, DTO, migration

- Không thêm/sửa endpoint trong nhóm 1; giữ nguyên participant-flow, participant-registration, participant-applications và review.
- Không thay DTO đang được trả qua HTTP. `EligibilityDecision` là model kết quả nội bộ mới, chưa expose.
- Không thay schema, enum lưu DB hoặc Flyway migration. Migration tests: SKIPPED vì không có schema change.
- Chưa làm concurrency/E2E của pipeline mới; các kiểm thử đó phải đi cùng hành vi thực được nối vào service ở nhóm sau.

## Kiểm chứng nhóm 1

- Unit tests mới kiểm tra defaults/legacy policy, JSON round-trip, unknown policy/code, cấu trúc decision, reason bắt buộc, null, tính immutable và deduplication.
- Backend regression: TimelineRegistrationApiTest, TournamentRegistrationApiTest, RegistrationFlowTest, TournamentPhaseResolverTest, OrganizerMetricsApiTest.
- Client: toàn bộ `client/tests/*.test.js`.
- Build: `:bootJar`.

Kết quả: **PASS 32/32 backend tests** (9 unit tests mới và 23 regression tests), **PASS 315/315 client tests**, **PASS bootJar**, không có test fail hoặc test bị bỏ qua trong hai suite đã chạy. Migration tests: **SKIPPED / không áp dụng**, vì không thay schema. Đây chưa phải kiểm thử auto-approval, tranh slot hoặc promotion; các hành vi đó chưa triển khai.

## File thêm/sửa

Thêm:

- `server/src/main/java/com/rpsbattle/tournament/registration/RegistrationApprovalPolicy.java`
- `server/src/main/java/com/rpsbattle/tournament/registration/EligibilityReasonCode.java`
- `server/src/main/java/com/rpsbattle/tournament/registration/EligibilityDecision.java`
- `server/src/test/java/com/rpsbattle/tournament/registration/RegistrationApprovalPolicyTest.java`
- `server/src/test/java/com/rpsbattle/tournament/registration/EligibilityDecisionTest.java`
- `docs/TOURNAMENT-REGISTRATION-PIPELINE.md`

Sửa: `README.md` để liên kết tài liệu này.

## Nhóm 2 — eligibility engine và chính sách hoạt động

Đã nối `RegistrationEligibilityService` vào đăng ký người chơi theo timeline. Context được server dựng từ account/session, đơn hiện có, bot policy, lịch giải và thông tin đăng ký. Không nhận account eligibility hoặc kết quả xét duyệt từ body do Player gửi.

- AUTO_APPROVE/HYBRID: đơn hợp lệ và không có yêu cầu xác minh được lưu APPROVED ngay trong transaction đăng ký, response cuối cùng trả APPROVED.
- MANUAL_REVIEW: đơn cần người duyệt, reason MANUAL_REVIEW_REQUIRED.
- Nếu giải bật `requireAffiliationVerification`, trường/đơn vị là bắt buộc. Thông tin tự khai chưa có verifier tin cậy, nên chuyển cần xét duyệt với AFFILIATION_NOT_VERIFIED kể cả khi dùng AUTO_APPROVE. Organizer có thể xác nhận bằng action review hiện có.
- Basic validation tiếp tục chặn đơn sai rules acceptance, ngôn ngữ, source/botId không được gửi ở giai đoạn này hoặc affiliation không hợp lệ. Engine trả REJECT với business reason cho context không đủ điều kiện; request không hợp lệ không tạo một application rác.
- Model lưu PENDING cũ được giữ nguyên để không phá CHECK constraint và query phase cũ; API của application project thành NEEDS_REVIEW. Frontend hiển thị “Đang chờ ban tổ chức xác nhận”, giữ thao tác hủy đơn trong cửa sổ đăng ký và không cho gửi trùng.
- `reasonCodes` của lần đánh giá được lưu riêng, chỉ trả trên endpoint applications dành cho manager. Audit vẫn lưu reason, nhưng public detail/summary/export loại reason của application và policy update; Player flow không có eligibility reasonCodes.
- Chưa tạo WAITLIST tự động khi đầy, chưa thay cách tính capacity hoặc tự đôn danh sách chờ. Đây là nhóm 3–4; nhóm 2 vẫn trả lỗi capacity hiện có nếu đạt giới hạn admission cũ.

### Policy, tương thích và UI

`RegistrationPolicy` gồm `approvalPolicy` và `requireAffiliationVerification`, lưu trong JSON `TournamentDetails` cùng snapshot của giải. Thiếu toàn bộ policy ở snapshot/API cũ tiếp tục MANUAL_REVIEW; không tự thay chính sách của giải cũ.

Wizard tạo giải theo timeline gửi policy explicit, mặc định HYBRID, xác minh đơn vị tắt. Bước xem lại cấu hình hiển thị chính sách này. Giải thủ công không dùng pipeline mới. API client cũ không gửi policy được giữ hành vi duyệt thủ công để tương thích.

Trong Settings của giải có timeline còn DRAFT, chủ giải có thể sửa policy với revision mới nhất và lý do bắt buộc. Server kiểm tra manager/admin, DRAFT, chưa có application; commit cùng audit/revision, phục hồi snapshot nếu mutation lỗi. Sau mở đăng ký, UI chỉ hiển thị chính sách đã cố định. Chưa tự đánh giá lại đơn cũ khi đổi policy.

### API/DTO thay đổi ở nhóm 2

- POST `/api/tournaments`: thêm tùy chọn `registrationPolicy: {approvalPolicy, requireAffiliationVerification}`.
- Tournament read-model: thêm `registrationPolicy`.
- PUT `/api/tournaments/{id}/registration-policy`: `{revision, policy, reason}`, manager/admin, chỉ DRAFT chưa có application, trả `{status:"OK"}`.
- Participant flow và `registration.status`: PENDING lưu cũ được trả thành NEEDS_REVIEW; participantStatus tương ứng REGISTRATION_NEEDS_REVIEW. Các trạng thái APPROVED/REJECTED/WAITLISTED/CANCELLED giữ nguyên.
- Applications list: thêm `reasonCodes`, filter NEEDS_REVIEW; nhận PENDING như alias tương thích. Không gửi reasonCodes trong `participant-flow`.
- Organizer metrics: thêm `registration.needsReview`, giữ `pending` làm alias cho client cũ. Paging vẫn 25; filter/bulk nâng cao chưa thuộc nhóm này.

### Migration nhóm 2

V38 `registration_eligibility_reasons`: thêm `eligibility_reasons VARCHAR(500) NOT NULL DEFAULT ''` vào bảng applications. Không đổi trạng thái lưu của đơn cũ, không sửa migration cũ. Đơn cũ thiếu reason metadata được Organizer projection giải thích bằng MANUAL_REVIEW_REQUIRED. Policy không cần cột riêng do đã có JSON snapshot tournament-owned.

### Kiểm chứng nhóm 2

Các tests mới bao gồm auto/hybrid approval, manual review, affiliation exception, các lỗi eligibility kết hợp, trạng thái response cuối cùng, thời gian coding không bị bypass, alias PENDING, privacy public API, manager/revision/reason/phase guards, audit và snapshot backward compatibility. Client kiểm tra editor default/disabled, PUT payload, màn policy đã khóa và NEEDS_REVIEW không tạo form đăng ký lại.

PostgreSQL migration tests: **PASS 33/33**, bao gồm upgrade V37→V38 giữ đơn PENDING và metadata mặc định. Client tests: **PASS 320/320**. Backend regression/unit/API: **PASS 67/67**, **PASS bootJar**, không có test fail/skipped ở lượt cuối. Tổng backend + migration: **100 tests PASS**. Không kiểm tra hình ảnh bằng browser vì browser bị tắt trong Work settings; client được kiểm chứng bằng DOM/component tests và API integration.


### File thêm/sửa trong nhóm 2

- `client/js/tournament/application-review.js`
- `client/js/tournament/deadline-policy-editor.js`
- `client/js/tournament/phase-view.js`
- `client/js/tournament/registration-policy.js`
- `client/js/tournament/timeline-registration-model.js`
- `client/js/tournament/timeline-registration.js`
- `client/js/tournament/tournament-create-form.js`
- `client/js/tournament/tournament-create-summary.js`
- `client/js/ui/toast.js`
- `client/tests/registration-policy.test.js`
- `client/tests/timeline-registration.test.js`
- `client/tournament-applications.html`
- `docs/TOURNAMENT-REGISTRATION-PIPELINE.md`
- `server/src/main/java/com/rpsbattle/api/controller/TimelineRegistrationController.java`
- `server/src/main/java/com/rpsbattle/api/controller/TournamentController.java`
- `server/src/main/java/com/rpsbattle/tournament/OrganizerMetricsService.java`
- `server/src/main/java/com/rpsbattle/tournament/OwnedTournamentService.java`
- `server/src/main/java/com/rpsbattle/tournament/TournamentService.java`
- `server/src/main/java/com/rpsbattle/tournament/domain/Tournament.java`
- `server/src/main/java/com/rpsbattle/tournament/persistence/TournamentDetails.java`
- `server/src/main/java/com/rpsbattle/tournament/registration/RegistrationEligibilityService.java`
- `server/src/main/java/com/rpsbattle/tournament/registration/RegistrationPolicy.java`
- `server/src/main/java/com/rpsbattle/tournament/timeline/TimelineRegistration.java`
- `server/src/main/java/com/rpsbattle/tournament/timeline/TimelineRegistrationService.java`
- `server/src/main/java/com/rpsbattle/tournament/timeline/TimelineRegistrationStore.java`
- `server/src/main/resources/db/migration/V38__registration_eligibility_reasons.sql`
- `server/src/test/java/com/rpsbattle/persistence/FlywayMigrationTest.java`
- `server/src/test/java/com/rpsbattle/tournament/RegistrationPipelineApiTest.java`
- `server/src/test/java/com/rpsbattle/tournament/TimelineRegistrationApiTest.java`
- `server/src/test/java/com/rpsbattle/tournament/registration/RegistrationEligibilityServiceTest.java`
