# Registration pipeline — nhóm 1

Ngày: 21/10/2026. Phạm vi nhóm này: rà soát kiến trúc, định nghĩa chính sách duyệt và kết quả eligibility. Chưa kích hoạt tự duyệt, chưa đổi trạng thái API, schema hoặc giao diện. Dừng sau nhóm 1 theo mục 52–53 của yêu cầu.

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

Các model này chưa được Controller/Service hiện tại gọi. Giải mới tạo ở phiên bản này vẫn duyệt thủ công; chưa có thiết lập policy hoạt động trên UI/API. Việc nối và lưu policy cùng eligibility engine thuộc nhóm 2, phải hoàn tất trước khi cho người tổ chức chọn AUTO_APPROVE/HYBRID.

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
