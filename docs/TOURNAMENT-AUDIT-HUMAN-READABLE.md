# Lịch sử quản lý giải dễ đọc

## Giao diện

Tab Audit của chủ giải hiển thị timeline tiếng Việt, chia theo Hôm nay/Hôm qua/ngày cụ thể. Mỗi dòng có thời gian, người thực hiện, hành động, mô tả/tóm tắt thay đổi, kết quả bằng chữ và nút Chi tiết. Không hiển thị số thứ tự audit, UUID, action code, raw enum hoặc JSON dài ở danh sách chính.

Modal hiển thị nội dung dễ hiểu trước. Disclosure Thông tin kỹ thuật mặc định đóng chứa Audit ID, sequence, action, resource, correlation, request/HTTP (nếu có), actor ID, reason và before/after JSON. Dữ liệu trước/sau có disclosure riêng, format monospace và nút copy. Sự kiện xác nhận được gộp vẫn nằm trong phần kỹ thuật của dòng chính.

Hoạt động gần đây ở Tổng quan và lịch sử xử lý trận cũng dùng cùng lớp diễn giải, không còn mapping tiêu đề rải rác hay raw action code.

## Mapping

Catalog tiếng Việt dùng chung được chuyển từ `client/js/admin/audit-presentation.js` sang `client/js/audit/presentation.js`; đường dẫn Admin cũ re-export để tương thích. Catalog hiện có 157 mã. Mapper Tournament thêm cách diễn đạt theo ngữ cảnh riêng: mở/đóng đăng ký, gửi đơn, hạn lập trình, hàng đợi kiểm thử, xác nhận bot và điểm danh.

Các nhóm Tournament: Đăng ký, Lập trình, Bài nộp, Kiểm thử, Điểm danh, Trận đấu, Kết quả, Giải đấu, Quản trị, Hệ thống.

- Actor: username, Chủ giải/người dùng khi thiếu tên, Hệ thống, Bộ lập lịch, Remote Worker. Remote result có source WORKER khi audit ghi reason REMOTE_WORKER_RESULT; không suy đoán danh tính máy cụ thể.
- Resource: tên đăng ký/người chơi được resolve bằng joins; validation có ngôn ngữ, số phiên bản và username chủ bot. Không bịa tên khi thiếu metadata.
- Status: trạng thái đăng ký, submission, validation được diễn giải tiếng Việt. AUTO_SUBMITTED, EXPLICIT_SUBMIT và MISSING có mô tả riêng; không nói “đã tự nộp” khi không có bản hợp lệ.
- Match result: chỉ tóm tắt trạng thái/kết quả có ý nghĩa từ định dạng legacy; không đưa UUID winner/history hoặc toàn bộ chuỗi pipe ra timeline.
- Không lặp tên giải trong mỗi dòng vì lịch sử đã nằm trong một giải cụ thể.
- Mã không biết có fallback “Hoạt động hệ thống”, mô tả chưa được hỗ trợ và warning giới hạn từ catalog chung. Code gốc chỉ nằm trong chi tiết. Không tự thêm enum hoặc sự kiện ghi vào GameEngine.

## AUDIT và ACTIVITY

Mặc định hiển thị Thay đổi quan trọng cùng các mốc nghiệp vụ hữu ích. Có thể chuyển Tất cả hoặc Hoạt động hệ thống.

ACTIVITY gồm:

- TIMELINE_CODING_30_MIN_LEFT, TIMELINE_CODING_10_MIN_LEFT, TIMELINE_CODING_5_MIN_LEFT, TIMELINE_CODING_1_MIN_LEFT (và mã cùng pattern phút còn lại).
- TIMELINE_CODING_STARTING, TIMELINE_REGISTRATION_CLOSING, TIMELINE_CHECK_IN_CLOSING, TIMELINE_TOURNAMENT_STARTING.
- NOTIFICATIONS_READ.

Các mốc mở/đóng đăng ký, bắt đầu/kết thúc lập trình, khóa bài, validation, quyết định quản trị và kết quả vẫn là AUDIT.

## Gộp sự kiện trùng

Gộp cặp FINAL_VALIDATION_{status} và TIMELINE_BOT_VALIDATION_{status} ở server trước khi count/filter/phân trang. Chỉ gộp nếu:

- Primary target resolve được một final validation trong đúng giải.
- Secondary target đúng user của validation đó.
- Cùng code kết quả và cùng after_value, đều do hệ thống ghi.
- Secondary đứng ngay trước primary theo sequence và cách không quá 2 giây.

Không gộp chỉ vì cùng thời gian. Khác người chơi, thứ tự, kết quả, thời điểm hoặc thiếu metadata thì giữ riêng. Original events không bị xóa/sửa. Advanced filter theo ID/action/resource của secondary vẫn tìm được dòng primary và có thể xem secondary trong technical detail. Correlation ID được suy ra từ ID phiên validation, không giả lập Request ID lịch sử.

Tổng quan sử dụng preview legacy tối đa 8 mốc nghiệp vụ, bỏ reminder và diễn giải bằng cùng mapper. Việc gộp có đối chiếu validation đầy đủ áp dụng tại endpoint lịch sử chính; không đoán quan hệ các event từ preview thiếu metadata.

## Filter, API và hiệu năng

Endpoint mới: `GET /api/tournaments/{id}/audit` (no-store).

- Kiểm tra owner hoặc Admin bằng TournamentAuthorizationService. Người chơi khác và khách không được đọc audit của giải. Test HTTP xác nhận owner/Admin được phép, guest 401, người khác 403.
- Search, category, loại người thực hiện, outcome, ngày UTC và size 25/50/100 xử lý tại server.
- Advanced filter: action, resource_id, audit_id, correlation_id.
- Trả items, total, page, size; sort created_at/sequence mới nhất trước. DTO có actor/target name, language/version, semantic kind/category/outcome và metadata của secondary đã gộp.
- Hai truy vấn đọc dữ liệu audit: count và page với cùng CTE scoped theo tournament. Không query resource riêng theo từng dòng. Kiểm chứng 31 cặp (62 record gốc): total 31, trang thứ hai có 6 dòng, chỉ hai truy vấn Audit. Việc xác thực tài khoản là luồng bảo mật hiện có.
- Refresh dùng queue hiện có, có abort/generation guard, visibility/BFCache và invalidation. Tab đang ẩn không gọi API. Chuyển sang Audit sẽ tải ngay, không reset filter khi poll; không tạo refresh controller bên trong hàm render dữ liệu thuần.
- Khi mất quyền/đăng nhập, xóa các dòng riêng tư và đóng detail đang mở.

Không thêm schema/migration hoặc ghi snapshot tên mới. Tên lấy từ read-model hiện tại; nếu entity đổi tên/xóa, tên có thể thay đổi hoặc thiếu. UI dùng fallback thay vì bịa dữ liệu lịch sử. Không lấy source code, hash hoặc credential để dựng metadata.

## Redaction

Tái sử dụng AuditRedactor ở server cho before/after/reason của cả primary và secondary. Mở rộng pattern cho signed URL signature/credential, kể cả Azure sig và X-Amz/X-Goog signature. Legacy TournamentService.view cũng che audit trước khi trả snapshot để đường dẫn cũ không làm lộ dữ liệu.

Không sửa domain event hoặc record trong database. JSON key nhạy cảm, JSON lồng trong chuỗi, Bearer, credential/token Worker, DB URI password và signed query được kiểm thử. Copy chỉ dùng response đã che. Văn bản tự do chứa một secret không có key/pattern nhận diện vẫn phải được tránh ghi từ nguồn.

## File đã sửa

- client/css/tournament.css
- client/tournament.html
- client/js/admin/audit-presentation.js (compatibility re-export)
- client/js/tournament/organizer-overview.js
- client/js/tournament/tournament-dashboard.js
- client/js/tournament/tournament-detail.js
- client/tests/organizer-overview.test.js
- server/src/main/java/com/rpsbattle/admin/AuditRedactor.java
- server/src/main/java/com/rpsbattle/tournament/TournamentService.java
- server/src/test/java/com/rpsbattle/admin/AdminConsoleApiTest.java

## File mới/chuyển vị trí

- client/js/audit/presentation.js (catalog chung chuyển từ Admin)
- client/js/tournament/audit-presentation.js
- client/js/tournament/audit-view.js
- client/css/tournament-audit.css
- client/tests/tournament-audit-presentation.test.js
- client/tests/tournament-audit-ui.test.js
- server/src/main/java/com/rpsbattle/admin/TournamentAuditReadService.java
- server/src/main/java/com/rpsbattle/api/controller/TournamentAuditController.java
- server/src/test/java/com/rpsbattle/admin/TournamentAuditReadTest.java
- server/src/test/java/com/rpsbattle/admin/TournamentAuditReadPostgresTest.java
- docs/TOURNAMENT-AUDIT-HUMAN-READABLE.md

## Kiểm chứng

- PASS: 441 client tests, gồm 11 test mới về mapper/context, freeze, reminder, match summary, unknown fallback, day grouping, technical detail, server filter/page/reset, BFCache, lost permission và hidden tab. 0 fail/skip.
- PASS: 28 backend tests: TournamentAuditRead H2 9, PostgreSQL 9 (mỗi suite có 5 test scoped và 4 regression kế thừa), AdminConsoleApi 8, AuditRedactor 2. 0 fail/skip.
- PASS: bootJar và diff whitespace check.
- PASS: sau restart, health/trang giải/catalog JS/Audit JS/CSS trả HTTP 200; endpoint audit của giải hiện có trả 401 khi chưa đăng nhập. Tunnel vẫn tắt.
- SKIPPED: browser visual smoke và screenshot desktop/mobile. Công cụ trình duyệt đã từ chối truy cập URL trong phiên; không dùng công cụ/trình duyệt khác để vượt hạn chế. DOM tests không phải xác nhận hình ảnh thực tế.

Lệnh: `node --test client/tests/*.test.js`; Gradle `:test --tests '*TournamentAuditReadTest' --tests '*TournamentAuditReadPostgresTest' --tests '*AdminConsoleApiTest' --tests '*AuditRedactorTest'`; `:bootJar`.

## Danh sách mã dùng chung đã map

Danh sách bên dưới được xuất từ catalog hiện tại. Các mã lịch sử/mới ngoài danh sách vẫn dùng fallback; chưa có literal TournamentAudit.append nào trong phạm vi coverage test bị thiếu mapping.

**TOURNAMENT**

`CLEANUP_CANDIDATE`, `CLEANUP_CLEARED`, `GROUP_QUALIFICATION_LOCKED`, `NOTIFICATIONS_READ`, `PERSISTENCE_ERROR`, `ROUND_POLICY_UPDATED`, `ROUND_RELEASED`, `ROUND_STARTED`, `STAGE_PLAN_UPDATED`, `SWISS_COMPLETED`, `SWISS_ROUND_PAIRED`, `TEAM_CREATED`, `TEAM_LINEUPS_LOCKED`, `TEAM_LINEUP_AUTO_SELECTED`, `TEAM_LINEUP_SUBMITTED`, `TEAM_LINEUP_WINDOW_OPENED`, `TEAM_MEMBER_JOINED`, `TEAM_MEMBER_LEFT`, `TEAM_REMOVED`, `TIMELINE_CHECK_IN_CLOSING`, `TIMELINE_CHECK_IN_OPENED`, `TIMELINE_CODING_10_MIN_LEFT`, `TIMELINE_CODING_1_MIN_LEFT`, `TIMELINE_CODING_30_MIN_LEFT`, `TIMELINE_CODING_5_MIN_LEFT`, `TIMELINE_CODING_END`, `TIMELINE_CODING_ENDED`, `TIMELINE_CODING_START`, `TIMELINE_CODING_STARTED`, `TIMELINE_CODING_STARTING`, `TIMELINE_CONFIGURED`, `TIMELINE_OVERRIDE_CODING_END`, `TIMELINE_OVERRIDE_CODING_START`, `TIMELINE_OVERRIDE_DELAY_START`, `TIMELINE_OVERRIDE_EXTEND_CHECK_IN`, `TIMELINE_OVERRIDE_EXTEND_CODING`, `TIMELINE_OVERRIDE_EXTEND_REGISTRATION`, `TIMELINE_OVERRIDE_EXTEND_VALIDATION`, `TIMELINE_OVERRIDE_START_CODING_EARLY`, `TIMELINE_REGISTRATION_CLOSING`, `TIMELINE_REGISTRATION_OPENED`, `TIMELINE_START`, `TIMELINE_START_BLOCKED`, `TIMELINE_START_UNBLOCKED`, `TIMELINE_TOURNAMENT_STARTING`, `TIMELINE_VALIDATION_STARTED`, `TIMING_CONFIGURED`, `TOURNAMENT_CANCELLED`, `TOURNAMENT_CHECK_IN`, `TOURNAMENT_CREATED`, `TOURNAMENT_FINISHED`, `TOURNAMENT_PAUSED`, `TOURNAMENT_READY`, `TOURNAMENT_RECOVERY_REQUIRED`, `TOURNAMENT_REGISTRATION`, `TOURNAMENT_RESTORED`, `TOURNAMENT_RESUMED`, `TOURNAMENT_RETAINED`, `TOURNAMENT_RUNNING`, `TOURNAMENT_STARTED`, `TOURNAMENT_UPDATED`.

**REGISTRATION**

`CHECK_IN`, `CHECK_IN_CLOSED`, `CHECK_IN_MISSED`, `CHECK_IN_OPENED`, `PARTICIPANT_ACTIVE`, `PARTICIPANT_APPLICATION_MESSAGE_UPDATED`, `PARTICIPANT_APPLICATION_REVIEWED`, `PARTICIPANT_APPLICATION_SUBMITTED`, `PARTICIPANT_APPLICATION_WITHDRAWN`, `PARTICIPANT_APPROVED`, `PARTICIPANT_CHECKED_IN`, `PARTICIPANT_CHECK_IN_MISSED`, `PARTICIPANT_DISQUALIFIED`, `PARTICIPANT_NEEDS_REVIEW`, `PARTICIPANT_NOT_CHECKED_IN`, `PARTICIPANT_NOT_ELIGIBLE`, `PARTICIPANT_PENDING`, `PARTICIPANT_REJECTED`, `PARTICIPANT_WAITLISTED`, `PARTICIPANT_WITHDRAWN`, `REGISTRATION_APPLICATIONS_CLOSED`, `REGISTRATION_APPLICATION_APPROVED`, `REGISTRATION_APPLICATION_REJECTED`, `REGISTRATION_AUTO_OPENED`, `REGISTRATION_OPENED`, `REGISTRATION_OPENED_EARLY`, `REGISTRATION_POLICY_UPDATED`, `REGISTRATION_ROSTER_LOCKED`, `REGISTRATION_UPDATED`, `WAITLIST_PROMOTED`, `WAITLIST_REJECTED`, `WAITLIST_REVIEW_REQUIRED`.

**VALIDATION**

`FINAL_VALIDATION_FAIL`, `FINAL_VALIDATION_FAILED`, `FINAL_VALIDATION_FALLBACK`, `FINAL_VALIDATION_INFRA_ERROR`, `FINAL_VALIDATION_PASS`, `FINAL_VALIDATION_PASSED`, `FINAL_VALIDATION_QUEUED`, `FINAL_VALIDATION_RETRY`, `FINAL_VALIDATION_RUNNING`, `TIMELINE_BOT_VALIDATION_END`, `TIMELINE_BOT_VALIDATION_FAILED`, `TIMELINE_BOT_VALIDATION_INFRA_ERROR`, `TIMELINE_BOT_VALIDATION_PASSED`, `TIMELINE_BOT_VALIDATION_QUEUED`, `TIMELINE_BOT_VALIDATION_RUNNING`, `TIMELINE_BOT_VALIDATION_START`.

**BOT**

`BOT_AUTO_SUBMITTED`, `BOT_POLICY_UPDATED`, `BOT_SUBMITTED`, `CODING_DEADLINE_FROZEN`, `EMERGENCY_SUBMISSION_FROZEN`, `EMERGENCY_SUBMISSION_OPENED`, `SUBMISSION_ALLOW-RESUBMIT`, `SUBMISSION_ALLOW_RESUBMIT`, `SUBMISSION_APPROVE`, `SUBMISSION_LOCK`, `SUBMISSION_LOCKED`, `SUBMISSION_REJECT`, `SUBMISSION_RETEST`, `SUBMISSION_SUBMITTED`.

**MATCH**

`MATCH_ERROR`, `MATCH_FINISHED`, `MATCH_NO_SHOW`, `MATCH_PARTICIPANT_READY`, `MATCH_READY_REQUIRED`, `MATCH_RETRIED`, `MATCH_REVIEW_ASSIGNED`, `MATCH_REVIEW_CLOSED`, `MATCH_REVIEW_REJECTED`, `MATCH_REVIEW_REQUESTED`, `MATCH_STARTED`, `RESULT_OVERRIDDEN`, `SERIES_UPDATED`, `VALIDATE_REPLAY`.

**WORKER**

`REGISTRATION_TOKEN_CREATED`, `REGISTRATION_TOKEN_REVOKED`, `REGISTRATION_TOKEN_USED`, `REMOTE_POLICY_CHANGED`, `TOURNAMENT_WORKER_POLICY`, `WORKER_APPROVED`, `WORKER_CAPACITY_CHANGED`, `WORKER_DISABLE`, `WORKER_DRAIN`, `WORKER_GROUP_CHANGED`, `WORKER_GROUP_CREATED`, `WORKER_REGISTERED`, `WORKER_REJECTED`, `WORKER_RESUME`, `WORKER_REVOKE`.

**SECURITY**

`DELETE_REQUEST`, `GET_REQUEST`, `PATCH_REQUEST`, `POST_REQUEST`, `PUT_REQUEST`.
