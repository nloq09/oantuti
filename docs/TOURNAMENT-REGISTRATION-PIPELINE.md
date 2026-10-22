# Registration pipeline — nhóm 1–6

Ngày: 21/10/2026. Các phần bên dưới ghi nhận từng mốc triển khai; nhóm 4 bổ sung promotion, đóng nhận đơn và chốt danh sách; nhóm 5 bổ sung dashboard cho Organizer; nhóm 6 bổ sung hàng xét duyệt, bộ lọc và bulk review. Tiếp tục dừng sau mỗi nhóm theo mục 52–53 của yêu cầu.

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


## Nhóm 3 — capacity, waitlist và cấp chỗ atomic

### Hành vi mới

- Chỉ APPROVED chiếm suất đăng ký. NEEDS_REVIEW, WAITLISTED, REJECTED và CANCELLED không được tính vào capacity; số đơn có thể lớn hơn maxParticipants.
- Sau đánh giá eligibility, Store đếm APPROVED trong database dưới khóa hàng tournament. Nếu một đơn đủ điều kiện tự duyệt không còn suất, allocation chuyển WAITLIST với CAPACITY_FULL, lưu WAITLISTED và trả response cuối cùng đó. Đơn cần người xác minh tiếp tục NEEDS_REVIEW, không bị tự coi là đã đủ điều kiện.
- Review APPROVED khi hết suất trả HTTP 409 CAPACITY_FULL, không đổi trạng thái hoặc ghi một audit duyệt thành công. Organizer vẫn có thể đưa đơn vào waitlist hoặc từ chối mà không bị giới hạn bởi tổng số đơn.
- Waitlist hiện có policy explicit FIRST_COME_FIRST_SERVED. Chỉ expose policy đã triển khai; không cung cấp lựa chọn random/manual priority chưa có thuật toán.
- Mỗi lần vào waitlist được cấp waitlist_order mới, tăng theo thứ tự transaction đã được cấp khóa. Không dùng UUID ngẫu nhiên để phân thứ tự cho đơn mới có cùng registeredAt. Đơn rút rồi đăng ký lại được đánh giá lại và nếu vẫn phải chờ thì về cuối hàng.
- Position được tính từ những đơn WAITLISTED còn hoạt động có order trước hoặc bằng đơn hiện tại. Khi một người rời hàng, vị trí các người sau tự giảm; Player chỉ nhận vị trí của chính mình, không nhận danh sách hoặc userId người khác.
- Filter WAITLISTED của Organizer trả theo thứ tự chờ. Page vẫn giới hạn 25; position dùng count query được index theo tournament/status/order, không tải toàn bộ waitlist để tính ở client.

### Transaction và khóa

Register, review và withdraw của application đều lấy `SELECT ... FOR UPDATE` trên hàng `tournaments` trước khi đọc/khóa application. Khóa được giữ đến commit/rollback của transaction hiện có; không chỉ đến khi Java method trả về. Review dùng tournament scope trước khi khóa application đích.

Capacity lấy từ `tournaments.max_participants` trong database, không tin số chỗ còn lại do client gửi. Nếu capacity snapshot phía service đã cũ, trả STALE_TOURNAMENT_REVISION. Các nhánh register/review đều kiểm tra số APPROVED sau khóa, nên không thể cùng cấp một suất cuối cho hai đơn. Withdraw dùng cùng thứ tự khóa để giải phóng suất nhất quán. Cơ chế mutation/audit/rollback hiện có tiếp tục được tái sử dụng.

Refresh application từ database dưới khóa để không dùng trạng thái CANCELLED cũ trong persistence context: hai transaction đã đọc cùng đơn hủy chỉ có một transaction được đăng ký lại thành công.

Các kiểm thử dùng transaction PostgreSQL độc lập, cố ý bỏ qua monitor tournament trong JVM. Điều này kiểm chứng primitive cấp suất trong DB; không đồng nghĩa toàn bộ server/phòng chơi đã hỗ trợ nhiều instance.

### API/DTO và migration

Không thêm endpoint.

- `registrationPolicy.waitlistPolicy`: FIRST_COME_FIRST_SERVED. Snapshot cũ thiếu field được bổ sung default này khi khôi phục; constructor hai tham số của model vẫn tương thích.
- `participant-flow.registration.waitlistPosition`: số thứ tự đang chờ của người hiện tại; null khi không chờ.
- Organizer applications: thêm waitlistPosition; thứ tự trang WAITLISTED dùng waitlist_order.
- Organizer metrics registration: thêm capacity và remainingSlots (capacity trừ APPROVED, không âm), giữ các field cũ.
- Lỗi review khi đầy: HTTP 409 CAPACITY_FULL; client giải thích cần giữ waitlist hoặc giải phóng suất.
- Audit của đơn tự vào waitlist lưu CAPACITY_FULL; reason vẫn được che trong API công khai như nhóm 2.

Migration V39 `registration_waitlist_order` thêm waitlist_order BIGINT, backfill các đơn WAITLISTED cũ theo registeredAt/id ổn định, CHECK số dương, UNIQUE(tournament_id,waitlist_order), index(tournament_id,status,waitlist_order). Không đổi migration cũ. Các đơn cũ không thuộc waitlist giữ order null. Snapshot mới không cần cột policy riêng vì đã dùng JSON của giải.

### Phạm vi chưa triển khai

Tự đôn người chờ khi có suất, notification promotion, Registration Close riêng và Roster Lock thuộc **nhóm 4**. Ở nhóm 3, suất được giải phóng chưa tự cấp lại cho hàng chờ; Organizer vẫn dùng review hiện có. Không tuyên bố đã hoàn thành flow FCFS auto-promotion hoặc roster lock. GameEngine không thay đổi.

### Kiểm chứng nhóm 3

- PostgreSQL concurrency: 8 đơn cùng tranh 1 suất còn lại; hai đơn waitlist được duyệt đồng thời tranh một suất trống; hai request cùng đăng ký một account; hai transaction cùng giữ bản CANCELLED cũ; rollback hủy allocation; capacity snapshot sai bị từ chối.
- API: tổng đơn vượt capacity mà không chiếm suất; tự vào waitlist khi đầy; vị trí riêng tư; rút/rejoin về cuối hàng; manual approve không vượt giới hạn và không đổi đơn khi thất bại.
- Migration: upgrade V38→V39 giữ đơn cũ, backfill ổn định, reject order không dương/trùng; toàn bộ suite migration.
- Client: người chờ nhìn thấy vị trí, không có form đăng ký trùng hoặc link vào workspace coding.

**PASS 75/75 backend tests**, bao gồm **5/5 concurrency PostgreSQL tests**; **PASS 34/34 migration PostgreSQL tests**; **PASS 321/321 client tests**; **PASS bootJar**. Tổng backend + migration: **109 tests PASS**. Không có failure hoặc skipped trong các suite này ở lượt cuối. Client được kiểm chứng bằng DOM/component tests; chưa kiểm tra hình ảnh bằng browser.


### File thêm/sửa trong nhóm 3

- `client/js/tournament/application-review.js`
- `client/js/tournament/registration-policy.js`
- `client/js/tournament/timeline-registration.js`
- `client/js/ui/toast.js`
- `client/tests/timeline-registration.test.js`
- `docs/TOURNAMENT-REGISTRATION-PIPELINE.md`
- `server/src/main/java/com/rpsbattle/tournament/OrganizerMetricsService.java`
- `server/src/main/java/com/rpsbattle/tournament/registration/RegistrationEligibilityService.java`
- `server/src/main/java/com/rpsbattle/tournament/registration/RegistrationPolicy.java`
- `server/src/main/java/com/rpsbattle/tournament/registration/WaitlistPolicy.java`
- `server/src/main/java/com/rpsbattle/tournament/timeline/TimelineRegistration.java`
- `server/src/main/java/com/rpsbattle/tournament/timeline/TimelineRegistrationService.java`
- `server/src/main/java/com/rpsbattle/tournament/timeline/TimelineRegistrationStore.java`
- `server/src/main/resources/db/migration/V39__registration_waitlist_order.sql`
- `server/src/test/java/com/rpsbattle/persistence/FlywayMigrationTest.java`
- `server/src/test/java/com/rpsbattle/tournament/RegistrationPipelineApiTest.java`
- `server/src/test/java/com/rpsbattle/tournament/TimelineRegistrationApiTest.java`
- `server/src/test/java/com/rpsbattle/tournament/registration/RegistrationCapacityPostgresTest.java`
- `server/src/test/java/com/rpsbattle/tournament/registration/RegistrationEligibilityServiceTest.java`


## Nhóm 4 — promotion, Registration Close và Roster Lock

### Đóng nhận đơn và chốt danh sách độc lập

Giải mới tạo với registrationPolicy explicit (hoặc cập nhật policy của giải nháp chưa có đơn) được khởi tạo một hàng control riêng trong transaction tạo giải. Hàng này bật workflow chốt danh sách trước coding. Không backfill các giải cũ sang workflow bắt buộc mới để tránh khóa các giải đã có bài nộp/đang chạy. Giải cũ vẫn có thể chủ động dùng thao tác đóng/chốt mới trước giờ coding; đóng sẽ tạo control và yêu cầu chốt riêng.

- **Đóng cửa đăng ký:** không nhận thêm đơn, không thay mốc coding, không khóa submission, không chuyển CHECK_IN/READY và không chốt roster. Các đơn cũ vẫn được xét duyệt hoặc rút trước khi lock, trong thời gian trước codingOpenAt.
- Đến registrationCloseAt chỉ đóng admission theo clock; không ngầm tạo roster lock. Với workflow mới, phase tiếp tục REGISTRATION_REVIEW đến khi chủ giải chốt, dù NEEDS_REVIEW bằng 0.
- **Chốt danh sách thi đấu:** server yêu cầu nhận đơn đã đóng (explicit hoặc theo deadline), không còn NEEDS_REVIEW, đủ số người tối thiểu theo format, chưa bắt đầu giải và chưa đến codingOpenAt. Chốt lưu thời gian, actor/name và số APPROVED.
- Không có override tự bỏ qua đơn cần xét duyệt hoặc action unlock trong nhóm này. Muốn chốt phải giải quyết hàng chờ. Nếu bỏ lỡ giờ coding, cần điều chỉnh lịch trước khi chốt; hệ thống không tự rút ngắn thời gian làm bài bằng cách mở coding muộn.
- Sau lock, register/review/withdraw application đều bị chặn ở service và Store dưới khóa DB; promotion trở thành no-op. Đăng ký execution bot/check-in tiếp tục là đối tượng riêng.
- UI có confirm dialog kèm lý do/revision cho từng thao tác. Card lock hiển thị số người, thời gian và người chốt; không còn nút review khi locked. Overview ưu tiên xử lý đơn, sau đó dẫn đến chốt roster; có cảnh báo còn dưới 10 phút hoặc đã đến giờ coding mà chưa chốt.

### Tự đôn waitlist

Promotion chạy trong cùng transaction của thao tác giải phóng suất (rút đơn hoặc review), khi đóng nhận đơn, trước nhận đơn mới để tránh người mới vượt hàng và khi chốt roster. Chỉ chạy trước coding và trước lock. Các transaction dùng cùng khóa hàng tournament và thứ tự khóa parent→application của nhóm 3.

Xét lại từng người theo waitlist_order: tài khoản còn ACTIVE, ngôn ngữ còn được phép và eligibility/policy hiện hành. Đơn còn cần người xác minh được bỏ qua, vẫn ở waitlist; không giả định action WAITLIST đồng nghĩa đã xác minh. Đơn không còn đủ điều kiện bị REJECTED, có audit nội bộ; tiếp tục tìm người đủ điều kiện phía sau. Các batch đọc giới hạn tối đa 100, dùng order cursor để không lặp lại đơn chưa đủ điều kiện. Policy bắt buộc duyệt thủ công hoặc xác minh đơn vị không có người được tự duyệt nên không quét lại cả waitlist ở mỗi request.

Một đơn bị Organizer chủ động chuyển vào waitlist được loại khỏi promotion trong chính thao tác đó, tránh tự đảo lại quyết định thành APPROVED ngay lập tức. Các lần xét suất sau vẫn đánh giá theo policy.

WAITLIST_PROMOTED được audit với target application ID và chiếu thành notification cá nhân: “Bạn đã được chuyển từ danh sách chờ sang được chấp nhận.” Người khác, kể cả organizer không sở hữu đơn đó, không nhận notification cá nhân này. Organizer vẫn xem audit quản lý. WAITLIST_REJECTED có notification công khai riêng; private eligibility reasons không xuất hiện trong detail/summary/export hoặc notification.

### Chặn đường vòng và thời gian

Flow Player, timeline endpoint, discovery và organizer metrics đọc cùng phase projection có roster control. acceptingRegistrations trong tournament detail cũng chuyển false khi đóng sớm. Việc gia hạn timeline không tự mở lại một cửa đăng ký đã chủ động đóng.

Với workflow mới chưa lock: TimelineAccessGuard chặn đọc/ghi workspace, build/test và import/submit qua guard dùng chung; không tạo freeze hoặc final validation; không mở trận kể cả đã qua tournamentStartAt. Không hiển thị FREEZE_PENDING giả khi coding chưa từng được mở. Giải cũ không có control giữ hành vi hiện có.

### API/DTO và migration

- GET `/api/tournaments/{id}/registration-summary`: manager/admin; control, counts, registrationOpen/canClose/canLock, mốc registration/coding, revision, serverNow.
- POST `/api/tournaments/{id}/registration/close`: `{revision,reason}`; manager/admin; đóng admission, trả summary mới.
- POST `/api/tournaments/{id}/roster/lock`: `{revision,reason}`; manager/admin; chốt roster khi đủ điều kiện, trả summary.
- Player flow, applications list, organizer metrics và tournament detail: thêm registrationControl (enabled, closedAt, lockedAt, lockedByName, lockedCount); Player không nhận counts hàng xét duyệt hoặc reason nội bộ.
- Các API cũ vẫn tồn tại cho luồng legacy. Không tái dùng action close-registration cũ (khóa submission/chuyển phase legacy) làm thao tác đóng admission mới.
- Migration V40 `registration_close_and_roster_lock`: bảng tournament_registration_controls, PK/FK tournament_id, closed_at, locked_at, locked_by/name/count. Constraint yêu cầu metadata lock đầy đủ, số người ít nhất 2 và close không sau lock. Bảng ban đầu rỗng, không tự chốt hoặc áp workflow mới cho các giải cũ.
- Không sửa GameEngine.

### Kiểm chứng nhóm 4

Các tests API bao gồm đóng trước hạn vẫn review/rút được, tự đôn sau withdraw, notification chỉ đúng người, guard pending review/minimum/revision/reason/ownership, roster immutable, không tự lock tại deadline, workspace/freeze/validation/start không vượt lock, và explicit WAITLIST không tự đảo quyết định. Concurrency PostgreSQL kiểm tra hai promotion cùng tranh suất, rollback cùng withdraw, revalidation tài khoản bị khóa, bỏ qua affiliation chưa xác minh và khóa roster tại Store. Migration test kiểm tra upgrade từ V39 giữ luồng cũ và constraint lock metadata.

**PASS 268/268 backend/migration tests theo kết quả mới nhất của từng class**, gồm **35/35 PostgreSQL migration tests** và **10/10 PostgreSQL transaction/concurrency tests**. Lượt mở rộng ban đầu có một lỗi thiết lập clock Mockito do tác vụ nền; đã đổi sang doReturn cho stubbing clock và chạy lại class đó cùng các lớp lifecycle/concurrency liên quan, tất cả PASS. **PASS 329/329 client tests**, **PASS bootJar**, **PASS kiểm tra cú pháp application-review.js**. Không có failure/skipped trong bộ kết quả cuối. Browser bị tắt trong Work settings nên chưa kiểm tra hình ảnh; DOM/component tests đã xác minh các trạng thái và confirm dialog.


### File thêm/sửa trong nhóm 4

- `client/js/tournament/application-review.js`
- `client/js/tournament/notifications.js`
- `client/js/tournament/organizer-actions.js`
- `client/js/tournament/registration-lifecycle.js`
- `client/js/tournament/timeline-registration.js`
- `client/js/ui/toast.js`
- `client/tests/registration-lifecycle.test.js`
- `client/tests/timeline-registration.test.js`
- `docs/TOURNAMENT-REGISTRATION-PIPELINE.md`
- `server/src/main/java/com/rpsbattle/api/controller/TimelineRegistrationController.java`
- `server/src/main/java/com/rpsbattle/api/controller/TournamentTimelineController.java`
- `server/src/main/java/com/rpsbattle/tournament/OrganizerMetricsService.java`
- `server/src/main/java/com/rpsbattle/tournament/OwnedTournamentService.java`
- `server/src/main/java/com/rpsbattle/tournament/TournamentDiscoveryService.java`
- `server/src/main/java/com/rpsbattle/tournament/TournamentNotificationService.java`
- `server/src/main/java/com/rpsbattle/tournament/TournamentService.java`
- `server/src/main/java/com/rpsbattle/tournament/coding/CodingDeadlineService.java`
- `server/src/main/java/com/rpsbattle/tournament/coding/FinalValidationService.java`
- `server/src/main/java/com/rpsbattle/tournament/timeline/RegistrationControl.java`
- `server/src/main/java/com/rpsbattle/tournament/timeline/TimelineAccessGuard.java`
- `server/src/main/java/com/rpsbattle/tournament/timeline/TimelineRegistrationService.java`
- `server/src/main/java/com/rpsbattle/tournament/timeline/TimelineRegistrationStore.java`
- `server/src/main/resources/db/migration/V40__registration_close_and_roster_lock.sql`
- `server/src/test/java/com/rpsbattle/persistence/FlywayMigrationTest.java`
- `server/src/test/java/com/rpsbattle/tournament/RegistrationPipelineApiTest.java`
- `server/src/test/java/com/rpsbattle/tournament/TimelineReviewApiTest.java`
- `server/src/test/java/com/rpsbattle/tournament/registration/RegistrationCapacityPostgresTest.java`


## Nhóm 5 — Dashboard đăng ký và hàng cần xử lý

Trang `/tournament-applications.html?id=…` có dashboard riêng; shell không lặp lại bộ số liệu và hộp attention chung ở trang này. Không thay đổi GameEngine hoặc workflow Player.

- Năm thẻ Tổng đơn, Đã duyệt, Cần xử lý, Danh sách chờ, Từ chối lấy toàn bộ aggregate từ server. Bấm thẻ mở bộ lọc tương ứng; số liệu không phụ thuộc trang 25 đơn.
- Hiển thị thời gian server và đếm ngược tới mở/đóng đăng ký, số suất đã duyệt/còn lại và số đơn đã hủy. Đếm ngược dùng serverNow cộng thời gian đơn điệu trên client; tới hạn chờ server xác nhận.
- CTA chính là “Xử lý N đơn”. Khi review đã đóng, đổi thành “Xem N đơn”; không cung cấp thao tác review. Khi hết ngoại lệ và có thể lock, dẫn tới card chốt danh sách.
- Card chính sách hiển thị cách duyệt, giới hạn, thứ tự waitlist và xác minh đơn vị. Link sửa chính sách chỉ hiện khi server xác nhận DRAFT, chưa có đơn và chưa lock. Đóng nhận đơn và chốt danh sách vẫn là hai thao tác riêng có confirm/reason/revision.
- Lần mở đầu ưu tiên NEEDS_REVIEW nếu có ngoại lệ. URL `status=PENDING` vẫn tương thích. `status=ALL` biểu diễn lựa chọn Tất cả rõ ràng, không bị tự đổi lại khi tải trang hoặc polling. Sau khi xử lý hết ngoại lệ, bộ lọc đang chọn được giữ và hiển thị trạng thái trống.
- Mỗi snapshot chỉ đọc detail, summary và một trang applications từ server. Detail/summary/list phải cùng revision; chỉ thử lại read tối đa một lần. Không retry mutation. Trang hiện tại được giới hạn lại khi số đơn giảm.
- Polling không xóa textarea đang được nhập; nút/lý do cũ bị khóa nếu đọc lỗi hoặc không lấy được snapshot khớp. Cập nhật thủ công vẫn khả dụng. Khi mất phiên/quyền hoặc tài khoản bị khóa, xóa snapshot riêng tư khỏi DOM.
- Layout dùng grid co về một cột cho policy/roster và hai cột cho thẻ số liệu trên màn hình nhỏ. Các application card có khoảng cách riêng để không dính viền.

### API/DTO nhóm 5

GET `/api/tournaments/{id}/registration-summary` giữ quyền manager/admin, bổ sung các trường additive:

- `capacity`: số suất tối đa của giải.
- `remainingSlots`: max(0, capacity − approved), không trừ các đơn đang review hoặc waitlist.
- `policy`: RegistrationPolicy hiện hành (approvalPolicy, requireAffiliationVerification).
- `canEditPolicy`: quyền sửa theo trạng thái giải và số đơn hiện có.
- `canReview`: guard review hiện hành từ server; đóng admission chưa khóa review, lock hoặc đến codingOpenAt khóa review.

Các trường control, counts, revision, serverNow và mốc giờ giữ nguyên. Không trả toàn bộ application hoặc thông tin source/mật khẩu trong summary. Applications vẫn phân trang tại server, 25 đơn/trang; chưa thêm tìm kiếm, bulk action hoặc đổi page size của nhóm 6. Không có endpoint hay migration mới.

### Kiểm chứng nhóm 5

- Backend: PASS 26/26 tests trong RegistrationPipelineApiTest (15), OrganizerMetricsApiTest (7), TimelineReviewApiTest (4). Bao gồm fixture 3.842 đơn (3.701 duyệt, 24 cần xử lý, 105 chờ, 12 từ chối), còn 299/4.000 suất, danh sách chỉ trả 25 hoặc 24 đơn theo bộ lọc; policy/review permissions qua draft/open/closed/deadline và người ngoài bị 403.
- Client: PASS 340/340 tests, không fail/skipped. Có DOM/component và controller integration cho aggregate, filter URL/default, revision mismatch, bounded retry, AbortSignal, giữ draft, review một lần kèm reason/revision, mất kết nối và mất phiên đăng nhập.
- PASS kiểm tra cú pháp JavaScript và git diff --check.
- PASS bootJar; khởi động lại localhost:8080 và kiểm tra HTTP 200 của HTML/module dashboard mới.
- Migration tests: SKIPPED (schema không đổi; giữ V40).
- Browser bị tắt trong Work settings: chưa kiểm chứng layout bằng ảnh hoặc trình duyệt thực tế; responsive CSS và DOM được kiểm tra tự động.

### File thêm/sửa nhóm 5

- `client/css/organizer-control-center.css`
- `client/js/tournament/application-review.js`
- `client/js/tournament/organizer-shell.js`
- `client/js/tournament/registration-dashboard-model.js` (mới)
- `client/js/tournament/registration-dashboard.js` (mới)
- `client/js/tournament/registration-lifecycle.js`
- `client/js/ui/toast.js`
- `client/tests/helpers/organizer-dom.js`
- `client/tests/registration-dashboard-controller.test.js` (mới)
- `client/tests/registration-dashboard-page.test.js` (mới)
- `client/tournament-applications.html`
- `docs/TOURNAMENT-REGISTRATION-PIPELINE.md`
- `server/src/main/java/com/rpsbattle/tournament/timeline/TimelineRegistrationService.java`
- `server/src/test/java/com/rpsbattle/tournament/RegistrationPipelineApiTest.java`


## Nhóm 6 — Review card, chi tiết, bộ lọc, phân trang và bulk review

Ngày triển khai: 22/10/2026. Tiếp tục trên dashboard nhóm 5; không sửa luồng GameEngine hay Player.

### Giao diện và hành vi

- Review card hiển thị ngay tên, trường, ngôn ngữ, trạng thái, lý do cần xác nhận, vị trí waitlist và quyết định gần nhất. Không cần mở modal để biết vì sao đơn cần con người xử lý.
- “Xem chi tiết” mở dialog ngay trên trang, tải một application được scope theo giải: username, thời gian đăng ký/chấp nhận luật, trạng thái tài khoản, ngôn ngữ được phép, chính sách xác minh, reason codes và tối đa 100 sự kiện review/promotion mới nhất. Không trả email, mật khẩu hoặc source bot. Đóng dialog hủy request đang chờ.
- Bộ lọc chạy ở server: username/display name, trạng thái, affiliation, ngôn ngữ, reason code, thời gian gửi từ/đến. Tìm tên/đơn vị không phân biệt hoa thường; `%`, `_`, `!` được coi là ký tự literal. Thời gian là khoảng [from, until), UI nhập theo giờ máy người dùng và gửi UTC ISO.
- Chọn 25/50/100 đơn mỗi trang. Server đếm total sau khi áp filter, giới hạn size tối đa 100, scope tournament và clamp trang cuối khi số kết quả giảm. Dashboard vẫn hiển thị aggregate toàn giải, danh sách dùng filtered total riêng. Không tải toàn bộ đơn để lọc ở client.
- Checkbox chọn từng đơn hoặc chọn các đơn có thể xét duyệt trên trang hiện tại. Không có lựa chọn toàn bộ hàng nghìn kết quả. Đổi bộ lọc/trang hoặc revision làm mất hiệu lực lựa chọn trước; khi revision đổi, UI thông báo cần chọn lại.
- Bulk chấp nhận/waitlist/từ chối đều có confirm và lý do bắt buộc. Kết quả hiển thị số thành công/thất bại và lỗi theo từng người. Không tự gửi lại mutation khi mạng lỗi. Mất phiên/quyền sẽ xóa dữ liệu riêng tư, đóng dialog và bỏ lựa chọn.
- Form/bulk controls có khoảng cách, layout grid thu về một cột trên màn hình nhỏ. Giữ cách render bằng textContent cho tên, lý do và audit, tránh chèn HTML từ dữ liệu người dùng.

### API/DTO nhóm 6

Các endpoint mới và mở rộng đều manager/admin, cache no-store, Vary Cookie, dùng session/CSRF hiện hành.

1. GET `/api/tournaments/{id}/participant-applications`:
   - Query mới: `size` (25/50/100, mặc định 25), `search` (tối đa 80), `affiliation` (80), `language` (CPP/PYTHON/JAVA), `reason` (enum EligibilityReasonCode), `from`, `until` (UTC Instant).
   - Giữ `page`, `status` và alias PENDING/NEEDS_REVIEW; total sau lọc được bổ sung vào Applications DTO. Response page có thể được clamp xuống trang hợp lệ cuối cùng.
   - HQL parameterized, kiểm tra kích thước/enum/khoảng giờ trước khi query; không nối dữ liệu người dùng vào HQL.
2. GET `/api/tournaments/{id}/participant-applications/{application}`:
   - ApplicationDetails gồm details (application, username, rulesAcceptedAt, accountActive), policy, languageAllowed, history, moreHistory, revision, canReview.
   - Tìm application trong đúng tournament, trả 404 khi không thuộc giải; chỉ lọc audit target application và loại event liên quan review/promotion.
3. POST `/api/tournaments/{id}/participant-applications/bulk-review`:
   - Body: `{revision,decision,reason,items:[{id,status,registeredAt}]}`; 1–100 ID không trùng, lý do không rỗng. `status` dùng public status (NEEDS_REVIEW).
   - Global guard yêu cầu revision mới, quyền quản lý và review còn mở trước khi xử lý bất kỳ row nào.
   - Mỗi row dùng transaction submissionMutation riêng cùng khóa parent/child hiện có. Đối chiếu expected status và registeredAt dưới child lock để tránh áp quyết định lên đơn đã thay đổi hoặc đã rút rồi đăng ký lại. Không tự bỏ qua validation vì cùng batch.
   - Kiểm tra scope, trạng thái, capacity, roster lock và giờ coding cho từng row. Chấp nhận còn xác nhận tài khoản đang hoạt động và ngôn ngữ hợp lệ; áp dụng cho cả single review. Audit riêng từng quyết định với actor, reason, before/after; waitlist promotion giữ transaction hiện có.
   - Lỗi nghiệp vụ của một row không rollback những row đã commit. Response `{items:[{id,success,error}],revision,succeeded,failed}`. Lỗi hạ tầng bất ngờ có thể trả lỗi request sau một số commit; client yêu cầu cập nhật và không tự retry.

Không có migration mới; giữ schema V40. Không mở rộng nhóm 7 (Player status/notifications) trong lần này.

### File thêm/sửa nhóm 6

- `client/css/organizer-control-center.css`
- `client/js/tournament/application-details.js` (mới)
- `client/js/tournament/application-queue-model.js` (mới)
- `client/js/tournament/application-review-card.js` (mới)
- `client/js/tournament/application-review.js`
- `client/js/tournament/registration-dashboard-model.js`
- `client/js/ui/toast.js`
- `client/tests/application-queue.test.js` (mới)
- `client/tests/registration-dashboard-controller.test.js`
- `client/tournament-applications.html`
- `docs/TOURNAMENT-REGISTRATION-PIPELINE.md`
- `server/src/main/java/com/rpsbattle/api/controller/TimelineRegistrationController.java`
- `server/src/main/java/com/rpsbattle/tournament/timeline/ApplicationFilter.java` (mới)
- `server/src/main/java/com/rpsbattle/tournament/timeline/TimelineRegistrationService.java`
- `server/src/main/java/com/rpsbattle/tournament/timeline/TimelineRegistrationStore.java`
- `server/src/test/java/com/rpsbattle/tournament/RegistrationQueueApiTest.java` (mới)


### Kiểm chứng nhóm 6

- PASS 37/37 API/backend tests: RegistrationQueueApiTest (7), RegistrationPipelineApiTest (15), TimelineReviewApiTest (4), TimelineRegistrationApiTest (4), OrganizerMetricsApiTest (7).
- PASS 10/10 PostgreSQL capacity/concurrency tests của RegistrationCapacityPostgresTest. Lượt đầu bị SKIPPED vì Testcontainers không tìm thấy default Docker pipe; chạy lại với DOCKER_HOST=npipe:////./pipe/dockerDesktopLinuxEngine trong môi trường lệnh test, không sửa cấu hình Docker toàn máy. Kết quả cuối tổng 47/47 backend tests theo XML mới nhất của từng class, không failure/skipped.
- API tests kiểm tra lọc trước paging, total, size cap/clamp, username/display name, literal wildcard, date interval, reason fallback, partial capacity failure, audit từng row, stale/foreign/changed/rejoined applications, bắt buộc lý do, duplicate/empty/oversized batch, closed vs locked, tài khoản bị khóa và quyền manager.
- PASS 346/346 client tests. Bổ sung component/model/controller tests cho bộ lọc, page size/filtered total, select page, confirm không gửi sớm, một lần bulk kèm reason/revision/expected rows, partial results, detail modal/checks/history, text-only rendering và abort khi đóng.
- PASS bootJar, kiểm tra cú pháp application-review.js và git diff --check. Server đã khởi động lại tại localhost:8080; HTTP 200 cho HTML/bulk controller/details module mới.
- Migration tests: SKIPPED (schema không đổi).
- Kiểm tra trực quan bằng trình duyệt: SKIPPED (Browser bị tắt trong Work settings). Không coi DOM/CSS tests là kiểm tra hình ảnh.
