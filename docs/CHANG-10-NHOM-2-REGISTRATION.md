# Chặng 10 — Nhóm 2: Đăng ký người chơi theo timeline

## Phạm vi đã có

Giải đã cấu hình timeline dùng đơn đăng ký người chơi riêng, không cần có bot READY, source hay workspace. Trong cả PRE_UPLOADED_ONLY, CODE_IN_PLATFORM_ONLY và BOTH, form chỉ nhận ngôn ngữ dự kiến, trường/đội không bắt buộc và xác nhận luật. Danh tính lấy từ session; không nhận quyền sở hữu từ client. Mặc định đơn PENDING, không tự duyệt.

Trước registrationOpenAt chỉ hiển thị lịch và luật. Trong `[registrationOpenAt, registrationCloseAt)` mở form; sau khi gửi hiển thị trạng thái chờ duyệt và countdown đến codingOpenAt. Đơn PENDING/APPROVED/WAITLISTED có thể hủy trước khi đóng đăng ký, rồi gửi lại với cùng application ID. Không cho gửi trùng, vượt capacity hoặc đăng ký lại đơn đã REJECTED. Duyệt/từ chối/waitlist và giao diện waiting chuyên biệt thuộc nhóm 3.

Không tạo `TournamentParticipant`, bot identity hoặc đăng ký controller HUMAN để thay thế người chưa chọn bot. Vì vậy đăng ký thi không bị engine hiểu thành người tự chơi. Liên kết ứng viên đã duyệt với submission/roster chính thức được tích hợp ở các nhóm sau; không dùng endpoint duyệt bot cũ để duyệt đơn mới.

## API và persistence

- `GET /api/tournaments/{id}/participant-flow`: session của người đọc; trả phase do server resolve, trạng thái/đơn của chính mình, nextAction, serverNow, phaseEndsAt, codingOpenAt, allowedLanguages và revision. Các trường submission/check-in sẽ bổ sung khi tích hợp phần tương ứng.
- `POST /api/tournaments/{id}/participant-registration`: session + CSRF, `{rulesAccepted:true,intendedLanguage:"CPP",affiliation:"UET"}`. Không nhận botId/sourceCode/versionId; nếu gửi các trường này, server reject.
- `DELETE /api/tournaments/{id}/participant-registration`: session + CSRF, hủy đơn của chính mình trong giờ đăng ký. Không nhận userId/applicationId của người khác.

Response riêng tư có no-store và Vary Cookie. GET timeline vẫn public để khách xem lịch. Người khác không đọc được trường/đội hay đơn của bạn qua flow API. Application mutation, audit và revision tournament cùng transaction hiện có; monitor tournament giữ admission không vượt capacity trên deployment một replica. Bảng có unique(tournament_id,user_id), foreign keys và status/language constraints.

V24 thêm `tournament_timeline_registrations`, tách khỏi bảng roster/registration thực thi cũ. Các giải không có timeline giữ luồng đăng ký/bot/workspace cũ. Migration không sửa dữ liệu hoặc SQL migration cũ. Đã backup local ở `backups/before-stage10-registration-v24` gồm DB, bot/replay và checksum trước áp dụng.

## Giao diện và bảo vệ phase

`tournament-detail.html` và `tournament-lobby.html` khi giải còn ở bước đăng ký hiển thị một màn hình phù hợp phase, không có các tab trận/bracket/standings/editor. Form có phần xem luật. Countdown dùng offset serverNow; server kiểm tra giờ lại ở mọi request. Polling có thể cập nhật phase mà revision không đổi và buộc thay màn hình khi đóng đăng ký dù người dùng đang focus input.

Giải có timeline không nhận đăng ký bot/human hoặc thêm participant qua endpoint legacy. Workspace read/create/save/build/test/public-cases, import bot và submit được chặn ngoài CODING_OPEN; trong CODING_OPEN còn cần application APPROVED. Truy cập URL editor sớm được chuyển về trang giải. Đây là hàng rào cho nhóm sau, chưa phải toàn bộ coding/freeze/check-in flow. Trang quản lý cũ của giải có timeline chỉ giữ cho manager; người chơi dùng màn hình đăng ký mới.

## Kiểm chứng

Backend: **64 PASS, 0 FAIL, 0 SKIPPED**, gồm timeline/resolver/API, đăng ký người chơi mới, đăng ký legacy, workspace, submission, JPA và 19 Flyway PostgreSQL Docker tests. Kiểm tra đủ ba submission mode, exact open/close, duplicate/capacity, hủy/gửi lại giữ ID, quyền theo account, chống gửi bot/source ở registration và chặn các API editor trực tiếp. Client: **85 PASS** gồm 5 test mới cho form/phase/payload/countdown; build bootJar thành công.

Trình duyệt local: UPCOMING chỉ hiện lịch/luật; REGISTRATION_OPEN có form người chơi; gửi form chuyển PENDING; nhập trực tiếp URL code quay về màn hình trạng thái. Hủy qua dialog và gửi lại thành công, giữ nguyên application ID; màn hình trạng thái không tràn ngang ở viewport 390px. Native API xác nhận application đã persist mà không sinh bot/roster; sau restart bản cuối giữ nguyên đơn. Ba fixture bài nộp CODE/PRE/BOTH của chặng 9 vẫn giữ LOCKED/version/hash và replay.

File mới: TimelineRegistrationController; timeline/TimelineRegistration, TimelineRegistrationStore, TimelineRegistrationService, TimelineAccessGuard; V24; TimelineRegistrationApiTest; timeline-registration.js, timeline-registration-model.js và timeline-registration.test.js. File sửa: TournamentService, OwnedTournamentService, TournamentTimelineController, ba service coding/submission/import, FlywayMigrationTest/JpaFoundationTest, player-detail.js, code-workspace.js, registration-form.js, tournament-detail.js, bot-policy.js, toast.js, tournament-mode.css, README và DEPLOYMENT.

Không sửa GameEngine, RuleSet, Match Engine, BotProtocol hoặc DockerBotProcess. Giới hạn 32 suất chính thức và một replica giữ nguyên; chưa thực hiện yêu cầu scale 2.000 người của nhóm 10. Dừng sau nhóm 2 để review theo prompt.
