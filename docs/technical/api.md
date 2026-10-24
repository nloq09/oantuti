# REST API reference

[Mục lục](../README.md) · [WebSocket](websocket.md) · [Worker protocol](worker-protocol.md) · [Bảo mật](../admin/security.md)

Base URL là origin của Central. Ví dụ dưới dùng đường dẫn tương đối, không phải endpoint hosting riêng. API không có OpenAPI/Swagger được cấu hình sẵn; không giả định `/swagger-ui` hoặc JWT login tồn tại. Danh mục cuối trang lấy từ annotation source để tránh giữ route cũ.

## Auth, request và lỗi chung

**Session** nghĩa là cookie cùng phiên; **CSRF** lấy từ GET `/api/auth/csrf`, dùng đúng `headerName` trong response cho mutation. Lấy lại sau login. **Manager** là chủ giải hoặc ADMIN qua authorization service; **Self** chỉ dữ liệu của người đăng nhập; **Worker** dùng Bearer credential ở internal chain, không cookie/CSRF.

Mutation JSON gửi Content-Type application/json; upload bot dùng multipart. Response có thể là object, array, 204 không body hoặc file, không có một envelope `data` chung. View có phân trang có size/total và items theo controller; page bắt đầu 0. Timestamp Instant là ISO-8601; gửi offset rõ ràng.

| HTTP / code tiêu biểu | Xử lý |
| --- | --- |
| 400 / INVALID_* hoặc REASON_REQUIRED | Sửa body/filter/điều kiện; xem mã của service tương ứng. |
| 401 / UNAUTHENTICATED, INVALID_CREDENTIALS | Đăng nhập lại hoặc kiểm tra credential; không lặp password liên tục. |
| 403 / CSRF_INVALID, FORBIDDEN | Token/cookie/ownership thiếu hoặc không có quyền; 403 không phải luôn CSRF. |
| 404 / *_NOT_FOUND | Đối tượng không tồn tại/không khả dụng theo cách service đọc. |
| 409 / STALE_TOURNAMENT_REVISION, STALE_WORKER_CONTROL | Đọc snapshot mới rồi quyết định lại; không ghi đè bằng revision tự tăng. |
| 413 / giới hạn body | Giảm kích thước, kiểm tra cả proxy và backend. |
| 422 / checksum/job/replay mismatch | Không retry byte không hợp lệ; đối chiếu nguồn dữ liệu. |
| 429 / RATE_LIMITED, WORKSPACE_QUEUE_FULL | Đọc Retry-After nếu có và chờ; queue đầy khác sai auth. |
| 503 / DATABASE_UNAVAILABLE, WORKER_DOWNLOAD_UNAVAILABLE | Xử lý hạ tầng/distribution, không sửa bot để chữa server. |

Các service có thể dùng status khác nhau cho lỗi nghiệp vụ; bảng này là các trường hợp có trong source, không phải schema lỗi đồng nhất cho mọi đường. Error JSON thường có `error`; không cần và không được trả raw stack trace cho người chơi.

Ví dụ gọi từ browser cùng origin, không lưu secret lâu dài:

```javascript
const csrf = await fetch('/api/auth/csrf', {credentials: 'same-origin'}).then(r => r.json());
const response = await fetch('/api/account/profile', {
  method: 'PATCH', credentials: 'same-origin',
  headers: {'Content-Type': 'application/json', [csrf.headerName]: csrf.token},
  body: JSON.stringify({displayName: 'Người chơi'})
});
if (!response.ok) throw new Error(`HTTP ${response.status}`);
const profile = await response.json();
```

## Account, bot và phòng chơi

| Method / path | Auth / role | Request | Response / lỗi riêng tiêu biểu |
| --- | --- | --- | --- |
| GET `/api/auth/csrf` | Khách/session | Không body | token, headerName; no-store. |
| POST `/api/auth/register` | Khách + CSRF | username, email, password, displayName | 201 UserResponse: id/username/displayName/role; ACCOUNT_ALREADY_EXISTS, INVALID_PASSWORD. |
| POST `/api/auth/login` | Khách + CSRF | username, password | UserResponse và session cookie mới; INVALID_CREDENTIALS, RATE_LIMITED. |
| GET `/api/auth/me` | Tùy phiên | Không body | authenticated và user khi có. |
| POST `/api/auth/logout` | Session/CSRF | Không body | authenticated=false; cookie/session bị vô hiệu. |
| GET `/api/account/profile` | Session/Self | Không body | Profile có email/status/timestamps của chính mình. |
| PATCH `/api/account/profile` | Session/Self + CSRF | displayName | Profile. |
| POST `/api/account/change-password` | Session/Self + CSRF | currentPassword, newPassword | changed=true, authenticated=false; phải login lại. |
| POST `/api/bots` | Session + CSRF | Multipart name, file | 201 bot View: id/name/language/status/timestamps; giới hạn file/sandbox theo service. |
| GET `/api/bots/capabilities` | Đọc công khai | Không body | Ngôn ngữ/runtime khả dụng; không tự pull image. |
| GET `/api/account/bots` | Session/Self | Không body | Array bot View. |
| POST `/api/account/bots/{id}/validate` | Session/owner + CSRF | Không body | Bot đủ điều kiện đăng ký hoặc lỗi; quota 6/phút mặc định. |
| PATCH `/api/account/bots/{id}` | Session/owner + CSRF | name | Bot View. |
| DELETE `/api/account/bots/{id}` | Session/owner + CSRF | Không body | 204, soft delete. |
| POST `/api/matches` | Khách; bot riêng cần owner session/CSRF | mode=HUMAN_VS_HUMAN hoặc HUMAN_VS_BOT, seed tùy chọn, botId tùy chọn | 201 matchId/historyId/mode/playerId/token/seed; token chỉ cho ghế này. |
| POST `/api/matches/{id}/join` | Khách; bot riêng cần owner session/CSRF | botId tùy chọn hoặc không body | Capability ghế; phòng đầy/không còn join bị từ chối. |
| GET `/api/matches/{id}` | Đọc phòng | Không body | matchId/mode/status/players. |
| GET `/api/matches/{id}/replay` | Quy tắc ReplayRegistry | Không body | View replay hoặc lỗi file/hash/đối tượng. Không trả state RAM để resume. |

Contract cụ thể: [AuthController](../../server/src/main/java/com/rpsbattle/api/controller/AuthController.java), [AccountController](../../server/src/main/java/com/rpsbattle/api/controller/AccountController.java), [AccountBotController](../../server/src/main/java/com/rpsbattle/api/controller/AccountBotController.java), [MatchController](../../server/src/main/java/com/rpsbattle/api/controller/MatchController.java). Field botToken legacy trong request không cấp quyền dùng bot đã thuộc người khác.

## Tournament và registration

Đặt `T = /api/tournaments/{id}`. Mọi `{id}` là đối tượng thực, không gửi nguyên dấu ngoặc nhọn. GET có thể công khai hoặc đòi account/manager theo service; URL đọc được không cấp quyền mutation/source.

| Method / path | Auth / role | Request | Response / guard |
| --- | --- | --- | --- |
| POST `/api/tournaments` | Session USER + CSRF, quyền create | Create: name, format, visibility, checkInRequired, maxParticipants, maxConcurrentMatches, tieBreakOrder, participantPolicy, bestOf, botPolicy, registrationPolicy | 201 manager view; validate cấu hình/giới hạn. `maxConcurrentMatches` chỉ ADMIN được gửi; USER bỏ trường này để dùng mặc định server. |
| GET `/api/tournaments/discover` | Đọc theo viewer | category, q, format, page, size | Discovery page; không lộ giải private của người khác. |
| GET `T` | Viewer/manager | Không body | Manager view hoặc public view đã lọc. |
| GET `T/me` | Session/Self | Không body | State và next action của người chơi. |
| GET `T/timeline` | Đọc | Không body | timeline, phase, serverNow, phaseEndsAt, revision, timelineConfigured. |
| PUT `T/timeline` | Session/Manager + CSRF | revision, timeline | Timeline view; thứ tự mốc hợp lệ. |
| POST `T/timeline/override` | Session/Manager + CSRF | revision, action, value, reason | Timeline view; action đúng phase/cửa sổ. |
| GET `T/participant-flow` | Session/Self | Không body | Đơn và flow timeline của người xem. |
| POST `T/participant-registration` | Session/người chơi + CSRF | rulesAccepted, intendedLanguage, affiliation; bot/source/version không phải điều kiện đăng ký người trước | Flow hiện tại; chủ giải không được đăng ký thi đấu. |
| DELETE `T/participant-registration` | Session/Self + CSRF | Không body | Flow sau rút đơn nếu còn cho phép. |
| GET `T/registration-summary` | Session/Manager | Không body | counts/control/canReview/serverNow/revision. |
| GET `T/participant-applications` | Session/Manager | page,size,status,search,affiliation,language,reason,from,until | Trang đơn đã lọc trên server. |
| POST `T/participant-applications/{application}/review` | Session/Manager + CSRF | revision, decision, reason, publicMessage | status=OK; duyệt theo policy/sức chứa/cửa sổ. |
| POST `T/registration/close` / `T/roster/lock` | Session/Manager + CSRF | revision, reason | Registration summary; đóng nhận đơn khác khóa roster. |
| POST `T/check-in` | Session/người đủ điều kiện + CSRF | registrationId tùy chọn | Tournament view; không thay thế ready từng trận. |
| POST `T/ready` | Session/ghế dự thi + CSRF | matchId, windowId | State người chơi; window hết hạn bị từ chối. |
| POST `T/entry` | Session/ghế dự thi + CSRF | matchId | Quyền vào trận hiện tại. |
| GET `T/matches` | Viewer, mine cần Self | page,size,round,stage,status,mine,order,replay,groupBySeries | Trang trận được phép đọc. |
| GET `T/ranking`, `T/bracket`, `T/summary` | View được phép đọc | Không body | Ranking/bracket/summary, không có source. |

Đăng ký legacy `/registrations` vẫn tồn tại cho mode tương ứng; không thay cho `/participant-registration` của timeline. Đọc [controller đăng ký](../../server/src/main/java/com/rpsbattle/api/controller/TimelineRegistrationController.java) để có records Signup/Review/BulkReview đầy đủ; đừng tự thêm field UI không hỗ trợ.

## Workspace, submission và Final Validation

Đặt `W = T/workspace`. Các đường workspace yêu cầu session của người đủ điều kiện; source chỉ cho Self/đối tượng được service cho phép. Manager dashboard không cấp quyền sửa source của người chơi.

| Method / path | Auth / role | Request | Response / guard |
| --- | --- | --- | --- |
| POST `W` | Session/người dự thi + CSRF | language | Workspace view. |
| GET `W/me` | Session/Self | language tùy chọn | Workspace view/revision/status và dữ liệu được phép đọc. |
| PUT `W/me` | Session/Self + CSRF | language, revision, sourceCode | Workspace mới; stale revision/deadline/locked được kiểm tra. |
| POST `W/me/build` | Session/Self + CSRF | language, revision, sourceCode | 202 job; không phải build hoàn tất ngay. |
| POST `W/me/test`, `W/me/test-match`, `W/me/validate` | Session/Self + CSRF | language, revision; source không được sửa qua đường test | 202 job; busy/queue/deadline guard. |
| GET `W/public-tests` | Session/người được phép | Không body | Public cases, không lộ hidden tests. |
| GET `W/versions/{version}/source` | Session/quyền version | Không body | Source read-only; không đổi bài khóa. |
| POST `W/me/submit` | Session/Self + CSRF | versionId, revision, rulesAccepted | Submission hiện tại; cần READY/policy/cửa sổ hợp lệ. |
| POST `T/submission/preuploaded` | Session/Self + CSRF | botId | Import bản bot mình sở hữu theo policy. |
| GET `T/submission/me`, `T/submission/me/versions` | Session/Self | Không body | Bài hiện tại/versions; không có bài trả status=NO_SUBMISSION. |
| GET `T/submissions` | Session/Manager | view=participants, filter,search,page,size hoặc view=versions | Dashboard tương ứng; default vẫn là versions. |
| GET `T/final-validations` | Session/Manager | view=dashboard,page,status,search,history | Dashboard có summary/current/history; bỏ view giữ legacy response. |
| POST `T/final-validations/{validation}/retry` | Session/Manager + CSRF | revision, reason | status=OK; INFRA_ERROR hiện tại và retry window còn mở. |
| POST `T/final-validations/{validation}/emergency` | Session/Manager + CSRF | revision, reason, deadline | status=OK; policy/phase/deadline và giới hạn grant. |

Đối chiếu [Workspace controller](../../server/src/main/java/com/rpsbattle/api/controller/TournamentCodeController.java), [Submission controller](../../server/src/main/java/com/rpsbattle/api/controller/TournamentSubmissionController.java), [FinalValidation controller](../../server/src/main/java/com/rpsbattle/api/controller/FinalValidationController.java). Quy trình vận hành ở [Bài nộp](../organizer/submission-management.md) và [Kiểm thử](../organizer/validation-management.md).

## Điều khiển, audit và Admin

| Method / path | Auth / role | Request | Response / guard |
| --- | --- | --- | --- |
| POST `T/control/{action}` | Session/Manager + CSRF | revision, reason | Tournament view; action=pause/resume/next-round/finish, guard theo trạng thái. |
| POST `T/matches/{matchId}/retry` | Session/Manager + CSRF | revision, reason | View; FAILED retryable, pause/recovery. |
| POST `T/matches/{matchId}/review` | Session/Manager + CSRF | revision, reason, keepResult | Mở/đóng review; không sửa replay. |
| POST `T/matches/{matchId}/override` | Session/Manager + CSRF | revision, winnerParticipantId hoặc null, reason | View; review/downstream guards. |
| GET `T/match-monitor`, `T/my-matches` | Theo viewer / Self | page,size,status,groupBySeries | Monitor page theo phạm vi. |
| GET `T/audit` | Theo quyền service | page,size và filters của read service | Audit an toàn, thứ tự sequence. |
| GET `/api/admin/users`, `/api/admin/users/{id}` | Session/ADMIN | Query filters/page/size hoặc ID | Trang/chi tiết user. |
| POST `/api/admin/users/{id}/disable`, `/enable` | Session/ADMIN + CSRF | reason | Profile; không tự disable chính mình. |
| GET `/api/admin/bots`, `/api/admin/bots/{id}` | Session/ADMIN | Query filters/page/size hoặc ID | Trang/chi tiết bot và metadata storage. |
| POST `/api/admin/bots/{id}/{action}` | Session/ADMIN + CSRF | reason | Detail; action=enable/disable/archive, bot DELETED không khôi phục bằng enable. |
| GET `/api/admin/dashboard`, `/api/admin/system`, `/api/admin/audit` | Session/ADMIN | Query theo controller | Tổng hợp hệ thống/audit; no-store. |
| POST `/api/admin/workers/registration-tokens` | Session/ADMIN + CSRF | uses, lifetimeMinutes | 201 id/registrationToken/expiresAt/remainingUses, raw secret một lần. |
| GET `/api/admin/workers/download` | Session/ADMIN | Không body | Binary rps-worker.jar hoặc 503 distribution unavailable. |
| POST `/api/admin/workers/{id}/review` | Session/ADMIN + CSRF | revision, decision=APPROVE/REJECT, trustLevel, reason | Worker view; trust hợp lệ. |
| POST `/api/admin/workers/{id}/control` | Session/ADMIN + CSRF | revision, action=DRAIN/RESUME/DISABLE/REVOKE, reason | Worker view; stale/control guard. |
| POST `/api/admin/tournaments/{id}/worker-policy` | Session/ADMIN + CSRF | Configure record của RemoteRoutingService | Policy; LOCAL/REMOTE_REQUIRED và khóa sau tạo lịch. |

Nhóm/team, tranh chấp, replay moderation, cleanup, notifications, group membership/capacity và các legacy routes nằm trong danh mục nguồn dưới đây. Body/filter/response của chúng nằm ngay trong record/method/service được liên kết; quyền service vẫn là chuẩn. Internal worker request/response được mô tả riêng ở [Worker protocol](worker-protocol.md).

## Danh mục mapping trong source

Phần này kiểm tra tự động với controller, bao gồm alias/redirect; **không** tự suy luận auth từ tên URL. Khi đổi route chạy `node tools/documentation-routes.mjs --write`, cập nhật contract phía trên nếu hành vi đổi, rồi `node tools/check-documentation.mjs`.

Thay đổi `maxConcurrentMatches` khi tạo giải hoặc qua `POST /api/tournaments/{id}/rules` yêu cầu ADMIN, kể cả người gửi là chủ giải. Chủ giải vẫn sửa `tieBreakOrder` theo quyền/giai đoạn hiện có bằng cách bỏ `maxConcurrentMatches`; server giữ nguyên mức đang áp dụng.

<!-- ROUTES:START -->
176 mappings khai báo trong các API controller (bao gồm redirect UI; không bao gồm Actuator và WebSocket). Auth/guard không được suy ra từ annotation.

| Method | Path | Nguồn contract |
| --- | --- | --- |
| GET | `/admin/workers` | [RemoteWorkerController:56](../../server/src/main/java/com/rpsbattle/api/controller/RemoteWorkerController.java#L56) |
| GET | `/admin/workers/setup` | [RemoteWorkerController:57](../../server/src/main/java/com/rpsbattle/api/controller/RemoteWorkerController.java#L57) |
| GET | `/api/account` | [AccountController:20](../../server/src/main/java/com/rpsbattle/api/controller/AccountController.java#L20) |
| GET | `/api/account/bots` | [AccountBotController:13](../../server/src/main/java/com/rpsbattle/api/controller/AccountBotController.java#L13) |
| DELETE | `/api/account/bots/{id}` | [AccountBotController:17](../../server/src/main/java/com/rpsbattle/api/controller/AccountBotController.java#L17) |
| GET | `/api/account/bots/{id}` | [AccountBotController:14](../../server/src/main/java/com/rpsbattle/api/controller/AccountBotController.java#L14) |
| PATCH | `/api/account/bots/{id}` | [AccountBotController:16](../../server/src/main/java/com/rpsbattle/api/controller/AccountBotController.java#L16) |
| POST | `/api/account/bots/{id}/validate` | [AccountBotController:15](../../server/src/main/java/com/rpsbattle/api/controller/AccountBotController.java#L15) |
| POST | `/api/account/bots/import` | [AccountBotController:19](../../server/src/main/java/com/rpsbattle/api/controller/AccountBotController.java#L19) |
| POST | `/api/account/change-password` | [AccountController:26](../../server/src/main/java/com/rpsbattle/api/controller/AccountController.java#L26) |
| GET | `/api/account/matches` | [AccountHistoryController:19](../../server/src/main/java/com/rpsbattle/api/controller/AccountHistoryController.java#L19) |
| GET | `/api/account/matches/{id}` | [AccountHistoryController:26](../../server/src/main/java/com/rpsbattle/api/controller/AccountHistoryController.java#L26) |
| GET | `/api/account/profile` | [AccountController:20](../../server/src/main/java/com/rpsbattle/api/controller/AccountController.java#L20) |
| PATCH | `/api/account/profile` | [AccountController:23](../../server/src/main/java/com/rpsbattle/api/controller/AccountController.java#L23) |
| GET | `/api/account/stats` | [AccountHistoryController:31](../../server/src/main/java/com/rpsbattle/api/controller/AccountHistoryController.java#L31) |
| GET | `/api/account/tournament-notifications` | [TournamentNotificationController:11](../../server/src/main/java/com/rpsbattle/api/controller/TournamentNotificationController.java#L11) |
| GET | `/api/account/tournaments` | [AccountTournamentController:14](../../server/src/main/java/com/rpsbattle/api/controller/AccountTournamentController.java#L14) |
| GET | `/api/admin/audit` | [AdminOperationsController:10](../../server/src/main/java/com/rpsbattle/api/controller/AdminOperationsController.java#L10) |
| GET | `/api/admin/bots` | [AdminConsoleController:13](../../server/src/main/java/com/rpsbattle/api/controller/AdminConsoleController.java#L13) |
| GET | `/api/admin/bots/{id}` | [AdminConsoleController:14](../../server/src/main/java/com/rpsbattle/api/controller/AdminConsoleController.java#L14) |
| POST | `/api/admin/bots/{id}/{action}` | [AdminConsoleController:15](../../server/src/main/java/com/rpsbattle/api/controller/AdminConsoleController.java#L15) |
| GET | `/api/admin/dashboard` | [AdminConsoleController:11](../../server/src/main/java/com/rpsbattle/api/controller/AdminConsoleController.java#L11) |
| GET | `/api/admin/disputes` | [AdminReviewsController:12](../../server/src/main/java/com/rpsbattle/api/controller/AdminReviewsController.java#L12) |
| GET | `/api/admin/disputes/{id}` | [AdminReviewsController:13](../../server/src/main/java/com/rpsbattle/api/controller/AdminReviewsController.java#L13) |
| POST | `/api/admin/disputes/{id}/{decision}` | [AdminReviewsController:14](../../server/src/main/java/com/rpsbattle/api/controller/AdminReviewsController.java#L14) |
| GET | `/api/admin/matches` | [AdminMatchesController:11](../../server/src/main/java/com/rpsbattle/api/controller/AdminMatchesController.java#L11) |
| GET | `/api/admin/matches/{id}` | [AdminMatchesController:12](../../server/src/main/java/com/rpsbattle/api/controller/AdminMatchesController.java#L12) |
| GET | `/api/admin/replays` | [AdminReviewsController:15](../../server/src/main/java/com/rpsbattle/api/controller/AdminReviewsController.java#L15) |
| GET | `/api/admin/replays/{id}` | [AdminReviewsController:16](../../server/src/main/java/com/rpsbattle/api/controller/AdminReviewsController.java#L16) |
| POST | `/api/admin/replays/{id}/validate` | [AdminReviewsController:17](../../server/src/main/java/com/rpsbattle/api/controller/AdminReviewsController.java#L17) |
| GET | `/api/admin/system` | [AdminController:33](../../server/src/main/java/com/rpsbattle/api/controller/AdminController.java#L33) |
| GET | `/api/admin/tournament-cleanup` | [TournamentCleanupController:11](../../server/src/main/java/com/rpsbattle/api/controller/TournamentCleanupController.java#L11) |
| POST | `/api/admin/tournament-cleanup/{id}/restore` | [TournamentCleanupController:12](../../server/src/main/java/com/rpsbattle/api/controller/TournamentCleanupController.java#L12) |
| GET | `/api/admin/tournaments` | [AdminTournamentsController:11](../../server/src/main/java/com/rpsbattle/api/controller/AdminTournamentsController.java#L11) |
| GET | `/api/admin/tournaments/{id}` | [AdminTournamentsController:12](../../server/src/main/java/com/rpsbattle/api/controller/AdminTournamentsController.java#L12) |
| POST | `/api/admin/tournaments/{id}/actions/{action}` | [AdminTournamentsController:13](../../server/src/main/java/com/rpsbattle/api/controller/AdminTournamentsController.java#L13) |
| GET | `/api/admin/tournaments/{id}/worker-capacity` | [WorkerGroupController:15](../../server/src/main/java/com/rpsbattle/api/controller/WorkerGroupController.java#L15) |
| GET | `/api/admin/tournaments/{id}/worker-policy` | [WorkerGroupController:26](../../server/src/main/java/com/rpsbattle/api/controller/WorkerGroupController.java#L26) |
| POST | `/api/admin/tournaments/{id}/worker-policy` | [WorkerGroupController:27](../../server/src/main/java/com/rpsbattle/api/controller/WorkerGroupController.java#L27) |
| GET | `/api/admin/users` | [AdminController:17](../../server/src/main/java/com/rpsbattle/api/controller/AdminController.java#L17) |
| GET | `/api/admin/users/{id}` | [AdminConsoleController:12](../../server/src/main/java/com/rpsbattle/api/controller/AdminConsoleController.java#L12) |
| POST | `/api/admin/users/{id}/disable` | [AdminController:23](../../server/src/main/java/com/rpsbattle/api/controller/AdminController.java#L23) |
| POST | `/api/admin/users/{id}/enable` | [AdminController:26](../../server/src/main/java/com/rpsbattle/api/controller/AdminController.java#L26) |
| GET | `/api/admin/worker-assignments/tournaments` | [WorkerGroupController:13](../../server/src/main/java/com/rpsbattle/api/controller/WorkerGroupController.java#L13) |
| GET | `/api/admin/worker-groups` | [WorkerGroupController:22](../../server/src/main/java/com/rpsbattle/api/controller/WorkerGroupController.java#L22) |
| POST | `/api/admin/worker-groups` | [WorkerGroupController:23](../../server/src/main/java/com/rpsbattle/api/controller/WorkerGroupController.java#L23) |
| POST | `/api/admin/worker-groups/{id}` | [WorkerGroupController:24](../../server/src/main/java/com/rpsbattle/api/controller/WorkerGroupController.java#L24) |
| GET | `/api/admin/worker-groups/capacity` | [WorkerGroupController:14](../../server/src/main/java/com/rpsbattle/api/controller/WorkerGroupController.java#L14) |
| GET | `/api/admin/workers` | [RemoteWorkerController:37](../../server/src/main/java/com/rpsbattle/api/controller/RemoteWorkerController.java#L37) |
| GET | `/api/admin/workers/{id}` | [RemoteWorkerController:39](../../server/src/main/java/com/rpsbattle/api/controller/RemoteWorkerController.java#L39) |
| POST | `/api/admin/workers/{id}/capacity` | [WorkerGroupController:25](../../server/src/main/java/com/rpsbattle/api/controller/WorkerGroupController.java#L25) |
| POST | `/api/admin/workers/{id}/control` | [RemoteWorkerController:43](../../server/src/main/java/com/rpsbattle/api/controller/RemoteWorkerController.java#L43) |
| GET | `/api/admin/workers/{id}/events` | [RemoteWorkerController:45](../../server/src/main/java/com/rpsbattle/api/controller/RemoteWorkerController.java#L45) |
| POST | `/api/admin/workers/{id}/review` | [RemoteWorkerController:41](../../server/src/main/java/com/rpsbattle/api/controller/RemoteWorkerController.java#L41) |
| GET | `/api/admin/workers/download` | [RemoteWorkerController:47](../../server/src/main/java/com/rpsbattle/api/controller/RemoteWorkerController.java#L47) |
| GET | `/api/admin/workers/metrics` | [WorkerGroupController:17](../../server/src/main/java/com/rpsbattle/api/controller/WorkerGroupController.java#L17) |
| GET | `/api/admin/workers/operations` | [WorkerGroupController:11](../../server/src/main/java/com/rpsbattle/api/controller/WorkerGroupController.java#L11) |
| GET | `/api/admin/workers/operations/{id}` | [WorkerGroupController:12](../../server/src/main/java/com/rpsbattle/api/controller/WorkerGroupController.java#L12) |
| GET | `/api/admin/workers/registration-tokens` | [RemoteWorkerController:25](../../server/src/main/java/com/rpsbattle/api/controller/RemoteWorkerController.java#L25) |
| POST | `/api/admin/workers/registration-tokens` | [RemoteWorkerController:23](../../server/src/main/java/com/rpsbattle/api/controller/RemoteWorkerController.java#L23) |
| POST | `/api/admin/workers/registration-tokens/{id}/revoke` | [RemoteWorkerController:27](../../server/src/main/java/com/rpsbattle/api/controller/RemoteWorkerController.java#L27) |
| GET | `/api/auth/csrf` | [AuthController:28](../../server/src/main/java/com/rpsbattle/api/controller/AuthController.java#L28) |
| POST | `/api/auth/login` | [AuthController:38](../../server/src/main/java/com/rpsbattle/api/controller/AuthController.java#L38) |
| POST | `/api/auth/logout` | [AuthController:48](../../server/src/main/java/com/rpsbattle/api/controller/AuthController.java#L48) |
| GET | `/api/auth/me` | [AuthController:56](../../server/src/main/java/com/rpsbattle/api/controller/AuthController.java#L56) |
| POST | `/api/auth/register` | [AuthController:33](../../server/src/main/java/com/rpsbattle/api/controller/AuthController.java#L33) |
| POST | `/api/bots` | [BotUploadController:26](../../server/src/main/java/com/rpsbattle/api/controller/BotUploadController.java#L26) |
| GET | `/api/bots/capabilities` | [BotUploadController:21](../../server/src/main/java/com/rpsbattle/api/controller/BotUploadController.java#L21) |
| GET | `/api/health` | [HealthController:10](../../server/src/main/java/com/rpsbattle/api/controller/HealthController.java#L10) |
| POST | `/api/matches` | [MatchController:25](../../server/src/main/java/com/rpsbattle/api/controller/MatchController.java#L25) |
| GET | `/api/matches/{id}` | [MatchController:53](../../server/src/main/java/com/rpsbattle/api/controller/MatchController.java#L53) |
| POST | `/api/matches/{id}/join` | [MatchController:38](../../server/src/main/java/com/rpsbattle/api/controller/MatchController.java#L38) |
| GET | `/api/matches/{id}/replay` | [ReplayWebController:10](../../server/src/main/java/com/rpsbattle/api/controller/ReplayWebController.java#L10) |
| GET | `/api/tournaments` | [TournamentController:16](../../server/src/main/java/com/rpsbattle/api/controller/TournamentController.java#L16) |
| POST | `/api/tournaments` | [TournamentController:14](../../server/src/main/java/com/rpsbattle/api/controller/TournamentController.java#L14) |
| GET | `/api/tournaments/{id}` | [TournamentController:17](../../server/src/main/java/com/rpsbattle/api/controller/TournamentController.java#L17) |
| GET | `/api/tournaments/{id}/audit` | [TournamentAuditController:13](../../server/src/main/java/com/rpsbattle/api/controller/TournamentAuditController.java#L13) |
| GET | `/api/tournaments/{id}/bot-policy` | [TournamentBotPolicyController:12](../../server/src/main/java/com/rpsbattle/api/controller/TournamentBotPolicyController.java#L12) |
| PUT | `/api/tournaments/{id}/bot-policy` | [TournamentBotPolicyController:13](../../server/src/main/java/com/rpsbattle/api/controller/TournamentBotPolicyController.java#L13) |
| GET | `/api/tournaments/{id}/bracket` | [TournamentPlayerController:19](../../server/src/main/java/com/rpsbattle/api/controller/TournamentPlayerController.java#L19) |
| POST | `/api/tournaments/{id}/cancel` | [TournamentController:21](../../server/src/main/java/com/rpsbattle/api/controller/TournamentController.java#L21) |
| POST | `/api/tournaments/{id}/check-in` | [TournamentController:31](../../server/src/main/java/com/rpsbattle/api/controller/TournamentController.java#L31) |
| POST | `/api/tournaments/{id}/close-check-in` | [TournamentController:32](../../server/src/main/java/com/rpsbattle/api/controller/TournamentController.java#L32) |
| POST | `/api/tournaments/{id}/close-registration` | [TournamentController:24](../../server/src/main/java/com/rpsbattle/api/controller/TournamentController.java#L24) |
| GET | `/api/tournaments/{id}/coding-jobs` | [TournamentSubmissionController:26](../../server/src/main/java/com/rpsbattle/api/controller/TournamentSubmissionController.java#L26) |
| POST | `/api/tournaments/{id}/control/{action}` | [TournamentAdministrationController:11](../../server/src/main/java/com/rpsbattle/api/controller/TournamentAdministrationController.java#L11) |
| GET | `/api/tournaments/{id}/disputes` | [ParticipantDisputeController:9](../../server/src/main/java/com/rpsbattle/api/controller/ParticipantDisputeController.java#L9) |
| POST | `/api/tournaments/{id}/disputes` | [ParticipantDisputeController:10](../../server/src/main/java/com/rpsbattle/api/controller/ParticipantDisputeController.java#L10) |
| POST | `/api/tournaments/{id}/entry` | [TournamentPlayerController:24](../../server/src/main/java/com/rpsbattle/api/controller/TournamentPlayerController.java#L24) |
| GET | `/api/tournaments/{id}/export` | [TournamentController:39](../../server/src/main/java/com/rpsbattle/api/controller/TournamentController.java#L39) |
| GET | `/api/tournaments/{id}/final-validations` | [FinalValidationController:11](../../server/src/main/java/com/rpsbattle/api/controller/FinalValidationController.java#L11) |
| POST | `/api/tournaments/{id}/final-validations/{validation}/{action}` | [FinalValidationController:12](../../server/src/main/java/com/rpsbattle/api/controller/FinalValidationController.java#L12) |
| GET | `/api/tournaments/{id}/fixtures/{fixtureId}/matches` | [TournamentPlayerController:20](../../server/src/main/java/com/rpsbattle/api/controller/TournamentPlayerController.java#L20) |
| POST | `/api/tournaments/{id}/keep` | [TournamentCleanupController:13](../../server/src/main/java/com/rpsbattle/api/controller/TournamentCleanupController.java#L13) |
| GET | `/api/tournaments/{id}/match-context` | [TournamentPlayerController:22](../../server/src/main/java/com/rpsbattle/api/controller/TournamentPlayerController.java#L22) |
| GET | `/api/tournaments/{id}/match-monitor` | [MatchMonitorController:11](../../server/src/main/java/com/rpsbattle/api/controller/MatchMonitorController.java#L11) |
| GET | `/api/tournaments/{id}/match-monitor/{match}/attempts` | [MatchMonitorController:10](../../server/src/main/java/com/rpsbattle/api/controller/MatchMonitorController.java#L10) |
| GET | `/api/tournaments/{id}/matches` | [TournamentPlayerController:21](../../server/src/main/java/com/rpsbattle/api/controller/TournamentPlayerController.java#L21) |
| POST | `/api/tournaments/{id}/matches/{matchId}/override` | [TournamentAdministrationController:16](../../server/src/main/java/com/rpsbattle/api/controller/TournamentAdministrationController.java#L16) |
| POST | `/api/tournaments/{id}/matches/{matchId}/retry` | [TournamentAdministrationController:15](../../server/src/main/java/com/rpsbattle/api/controller/TournamentAdministrationController.java#L15) |
| POST | `/api/tournaments/{id}/matches/{matchId}/review` | [TournamentAdministrationController:14](../../server/src/main/java/com/rpsbattle/api/controller/TournamentAdministrationController.java#L14) |
| GET | `/api/tournaments/{id}/me` | [TournamentPlayerController:28](../../server/src/main/java/com/rpsbattle/api/controller/TournamentPlayerController.java#L28) |
| GET | `/api/tournaments/{id}/my-matches` | [MatchMonitorController:11](../../server/src/main/java/com/rpsbattle/api/controller/MatchMonitorController.java#L11) |
| GET | `/api/tournaments/{id}/notifications` | [TournamentNotificationController:9](../../server/src/main/java/com/rpsbattle/api/controller/TournamentNotificationController.java#L9) |
| POST | `/api/tournaments/{id}/notifications/read` | [TournamentNotificationController:10](../../server/src/main/java/com/rpsbattle/api/controller/TournamentNotificationController.java#L10) |
| POST | `/api/tournaments/{id}/open-registration` | [TournamentController:23](../../server/src/main/java/com/rpsbattle/api/controller/TournamentController.java#L23) |
| GET | `/api/tournaments/{id}/organizer-metrics` | [OrganizerMetricsController:10](../../server/src/main/java/com/rpsbattle/api/controller/OrganizerMetricsController.java#L10) |
| GET | `/api/tournaments/{id}/participant-applications` | [TimelineRegistrationController:20](../../server/src/main/java/com/rpsbattle/api/controller/TimelineRegistrationController.java#L20) |
| GET | `/api/tournaments/{id}/participant-applications/{application}` | [TimelineRegistrationController:28](../../server/src/main/java/com/rpsbattle/api/controller/TimelineRegistrationController.java#L28) |
| POST | `/api/tournaments/{id}/participant-applications/{application}/review` | [TimelineRegistrationController:30](../../server/src/main/java/com/rpsbattle/api/controller/TimelineRegistrationController.java#L30) |
| POST | `/api/tournaments/{id}/participant-applications/bulk-review` | [TimelineRegistrationController:29](../../server/src/main/java/com/rpsbattle/api/controller/TimelineRegistrationController.java#L29) |
| GET | `/api/tournaments/{id}/participant-flow` | [TimelineRegistrationController:17](../../server/src/main/java/com/rpsbattle/api/controller/TimelineRegistrationController.java#L17) |
| DELETE | `/api/tournaments/{id}/participant-registration` | [TimelineRegistrationController:19](../../server/src/main/java/com/rpsbattle/api/controller/TimelineRegistrationController.java#L19) |
| POST | `/api/tournaments/{id}/participant-registration` | [TimelineRegistrationController:18](../../server/src/main/java/com/rpsbattle/api/controller/TimelineRegistrationController.java#L18) |
| POST | `/api/tournaments/{id}/participants` | [TournamentController:18](../../server/src/main/java/com/rpsbattle/api/controller/TournamentController.java#L18) |
| DELETE | `/api/tournaments/{id}/participants/{participantId}` | [TournamentController:19](../../server/src/main/java/com/rpsbattle/api/controller/TournamentController.java#L19) |
| POST | `/api/tournaments/{id}/participants/{participantId}/status` | [TournamentAdministrationController:13](../../server/src/main/java/com/rpsbattle/api/controller/TournamentAdministrationController.java#L13) |
| GET | `/api/tournaments/{id}/ranking` | [TournamentController:38](../../server/src/main/java/com/rpsbattle/api/controller/TournamentController.java#L38) |
| POST | `/api/tournaments/{id}/ready` | [TournamentPlayerController:23](../../server/src/main/java/com/rpsbattle/api/controller/TournamentPlayerController.java#L23) |
| PUT | `/api/tournaments/{id}/registration-policy` | [TimelineRegistrationController:16](../../server/src/main/java/com/rpsbattle/api/controller/TimelineRegistrationController.java#L16) |
| GET | `/api/tournaments/{id}/registration-summary` | [TimelineRegistrationController:13](../../server/src/main/java/com/rpsbattle/api/controller/TimelineRegistrationController.java#L13) |
| POST | `/api/tournaments/{id}/registration/close` | [TimelineRegistrationController:14](../../server/src/main/java/com/rpsbattle/api/controller/TimelineRegistrationController.java#L14) |
| POST | `/api/tournaments/{id}/registrations` | [TournamentController:25](../../server/src/main/java/com/rpsbattle/api/controller/TournamentController.java#L25) |
| DELETE | `/api/tournaments/{id}/registrations/{registrationId}` | [TournamentController:30](../../server/src/main/java/com/rpsbattle/api/controller/TournamentController.java#L30) |
| POST | `/api/tournaments/{id}/registrations/{registrationId}/approve` | [TournamentController:28](../../server/src/main/java/com/rpsbattle/api/controller/TournamentController.java#L28) |
| POST | `/api/tournaments/{id}/registrations/{registrationId}/reject` | [TournamentController:29](../../server/src/main/java/com/rpsbattle/api/controller/TournamentController.java#L29) |
| GET | `/api/tournaments/{id}/remote-executions` | [RemoteExecutionReadController:10](../../server/src/main/java/com/rpsbattle/api/controller/RemoteExecutionReadController.java#L10) |
| GET | `/api/tournaments/{id}/report` | [TournamentAdministrationController:18](../../server/src/main/java/com/rpsbattle/api/controller/TournamentAdministrationController.java#L18) |
| GET | `/api/tournaments/{id}/review` | [TournamentAdministrationController:17](../../server/src/main/java/com/rpsbattle/api/controller/TournamentAdministrationController.java#L17) |
| POST | `/api/tournaments/{id}/roster/lock` | [TimelineRegistrationController:15](../../server/src/main/java/com/rpsbattle/api/controller/TimelineRegistrationController.java#L15) |
| POST | `/api/tournaments/{id}/round-policy` | [TournamentAdministrationController:12](../../server/src/main/java/com/rpsbattle/api/controller/TournamentAdministrationController.java#L12) |
| POST | `/api/tournaments/{id}/rules` | [TournamentController:33](../../server/src/main/java/com/rpsbattle/api/controller/TournamentController.java#L33) |
| POST | `/api/tournaments/{id}/seeding` | [TournamentController:36](../../server/src/main/java/com/rpsbattle/api/controller/TournamentController.java#L36) |
| POST | `/api/tournaments/{id}/stage-plan` | [TournamentPlayerController:14](../../server/src/main/java/com/rpsbattle/api/controller/TournamentPlayerController.java#L14) |
| POST | `/api/tournaments/{id}/start` | [TournamentController:20](../../server/src/main/java/com/rpsbattle/api/controller/TournamentController.java#L20) |
| GET | `/api/tournaments/{id}/submission/me` | [TournamentSubmissionController:18](../../server/src/main/java/com/rpsbattle/api/controller/TournamentSubmissionController.java#L18) |
| POST | `/api/tournaments/{id}/submission/me/attempts` | [TournamentSubmissionController:21](../../server/src/main/java/com/rpsbattle/api/controller/TournamentSubmissionController.java#L21) |
| GET | `/api/tournaments/{id}/submission/me/attempts/latest` | [TournamentSubmissionController:20](../../server/src/main/java/com/rpsbattle/api/controller/TournamentSubmissionController.java#L20) |
| GET | `/api/tournaments/{id}/submission/me/versions` | [TournamentSubmissionController:19](../../server/src/main/java/com/rpsbattle/api/controller/TournamentSubmissionController.java#L19) |
| POST | `/api/tournaments/{id}/submission/preuploaded` | [TournamentSubmissionController:23](../../server/src/main/java/com/rpsbattle/api/controller/TournamentSubmissionController.java#L23) |
| GET | `/api/tournaments/{id}/submissions` | [TournamentSubmissionController:24](../../server/src/main/java/com/rpsbattle/api/controller/TournamentSubmissionController.java#L24) |
| POST | `/api/tournaments/{id}/submissions/{submissionId}/action` | [TournamentSubmissionController:25](../../server/src/main/java/com/rpsbattle/api/controller/TournamentSubmissionController.java#L25) |
| GET | `/api/tournaments/{id}/summary` | [TournamentPlayerController:18](../../server/src/main/java/com/rpsbattle/api/controller/TournamentPlayerController.java#L18) |
| POST | `/api/tournaments/{id}/teams` | [TournamentTeamController:10](../../server/src/main/java/com/rpsbattle/api/controller/TournamentTeamController.java#L10) |
| POST | `/api/tournaments/{id}/teams/{team}/lineup` | [TournamentTeamController:12](../../server/src/main/java/com/rpsbattle/api/controller/TournamentTeamController.java#L12) |
| POST | `/api/tournaments/{id}/teams/{team}/members` | [TournamentTeamController:11](../../server/src/main/java/com/rpsbattle/api/controller/TournamentTeamController.java#L11) |
| GET | `/api/tournaments/{id}/timeline` | [TournamentTimelineController:21](../../server/src/main/java/com/rpsbattle/api/controller/TournamentTimelineController.java#L21) |
| PUT | `/api/tournaments/{id}/timeline` | [TournamentTimelineController:27](../../server/src/main/java/com/rpsbattle/api/controller/TournamentTimelineController.java#L27) |
| POST | `/api/tournaments/{id}/timeline/override` | [TournamentTimelineController:28](../../server/src/main/java/com/rpsbattle/api/controller/TournamentTimelineController.java#L28) |
| POST | `/api/tournaments/{id}/timing` | [TournamentPlayerController:26](../../server/src/main/java/com/rpsbattle/api/controller/TournamentPlayerController.java#L26) |
| POST | `/api/tournaments/{id}/withdraw` | [TournamentPlayerController:12](../../server/src/main/java/com/rpsbattle/api/controller/TournamentPlayerController.java#L12) |
| POST | `/api/tournaments/{id}/workspace` | [TournamentCodeController:15](../../server/src/main/java/com/rpsbattle/api/controller/TournamentCodeController.java#L15) |
| GET | `/api/tournaments/{id}/workspace/me` | [TournamentCodeController:16](../../server/src/main/java/com/rpsbattle/api/controller/TournamentCodeController.java#L16) |
| PUT | `/api/tournaments/{id}/workspace/me` | [TournamentCodeController:18](../../server/src/main/java/com/rpsbattle/api/controller/TournamentCodeController.java#L18) |
| POST | `/api/tournaments/{id}/workspace/me/build` | [TournamentCodeController:19](../../server/src/main/java/com/rpsbattle/api/controller/TournamentCodeController.java#L19) |
| POST | `/api/tournaments/{id}/workspace/me/submit` | [TournamentSubmissionController:22](../../server/src/main/java/com/rpsbattle/api/controller/TournamentSubmissionController.java#L22) |
| POST | `/api/tournaments/{id}/workspace/me/test` | [TournamentCodeController:20](../../server/src/main/java/com/rpsbattle/api/controller/TournamentCodeController.java#L20) |
| POST | `/api/tournaments/{id}/workspace/me/test-match` | [TournamentCodeController:21](../../server/src/main/java/com/rpsbattle/api/controller/TournamentCodeController.java#L21) |
| POST | `/api/tournaments/{id}/workspace/me/validate` | [TournamentCodeController:22](../../server/src/main/java/com/rpsbattle/api/controller/TournamentCodeController.java#L22) |
| GET | `/api/tournaments/{id}/workspace/public-tests` | [TournamentCodeController:23](../../server/src/main/java/com/rpsbattle/api/controller/TournamentCodeController.java#L23) |
| GET | `/api/tournaments/{id}/workspace/versions/{version}/source` | [TournamentCodeController:17](../../server/src/main/java/com/rpsbattle/api/controller/TournamentCodeController.java#L17) |
| GET | `/api/tournaments/discover` | [TournamentPlayerController:29](../../server/src/main/java/com/rpsbattle/api/controller/TournamentPlayerController.java#L29) |
| GET | `/api/v1/health` | [HealthController:10](../../server/src/main/java/com/rpsbattle/api/controller/HealthController.java#L10) |
| GET | `/internal/workers/{id}/artifacts/{artifact}` | [RemoteWorkerController:54](../../server/src/main/java/com/rpsbattle/api/controller/RemoteWorkerController.java#L54) |
| POST | `/internal/workers/{id}/capabilities` | [RemoteWorkerController:33](../../server/src/main/java/com/rpsbattle/api/controller/RemoteWorkerController.java#L33) |
| POST | `/internal/workers/{id}/heartbeat` | [RemoteWorkerController:35](../../server/src/main/java/com/rpsbattle/api/controller/RemoteWorkerController.java#L35) |
| GET | `/internal/workers/{id}/identity` | [RemoteWorkerController:31](../../server/src/main/java/com/rpsbattle/api/controller/RemoteWorkerController.java#L31) |
| POST | `/internal/workers/{id}/jobs/{job}/complete` | [RemoteResultController:14](../../server/src/main/java/com/rpsbattle/api/controller/RemoteResultController.java#L14) |
| POST | `/internal/workers/{id}/jobs/{job}/decline` | [RemoteWorkerController:55](../../server/src/main/java/com/rpsbattle/api/controller/RemoteWorkerController.java#L55) |
| POST | `/internal/workers/{id}/jobs/{job}/running` | [RemoteWorkerController:53](../../server/src/main/java/com/rpsbattle/api/controller/RemoteWorkerController.java#L53) |
| POST | `/internal/workers/{id}/jobs/{job}/start` | [RemoteWorkerController:52](../../server/src/main/java/com/rpsbattle/api/controller/RemoteWorkerController.java#L52) |
| GET | `/internal/workers/{id}/jobs/assigned` | [RemoteWorkerController:51](../../server/src/main/java/com/rpsbattle/api/controller/RemoteWorkerController.java#L51) |
| POST | `/internal/workers/{id}/jobs/claim` | [RemoteWorkerController:50](../../server/src/main/java/com/rpsbattle/api/controller/RemoteWorkerController.java#L50) |
| POST | `/internal/workers/{id}/jobs/recover` | [RemoteLeaseController:14](../../server/src/main/java/com/rpsbattle/api/controller/RemoteLeaseController.java#L14) |
| POST | `/internal/workers/{id}/jobs/renew` | [RemoteLeaseController:13](../../server/src/main/java/com/rpsbattle/api/controller/RemoteLeaseController.java#L13) |
| POST | `/internal/workers/register` | [RemoteWorkerController:29](../../server/src/main/java/com/rpsbattle/api/controller/RemoteWorkerController.java#L29) |
<!-- ROUTES:END -->
