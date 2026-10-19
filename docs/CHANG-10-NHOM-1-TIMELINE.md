# Chặng 10 — Nhóm 1: Timeline và Phase Resolver

Nhóm này cung cấp dữ liệu lịch và bộ xác định phase phía server. Chưa thay UI, chưa chặn workspace/registration/check-in theo lịch, chưa tự bắt đầu giải hoặc chạy final validation. Các phần đó thuộc nhóm 2–10; không coi cấu hình timeline là luồng thi theo giờ đã hoàn thiện.

## Dữ liệu và API

`TournamentTimeline` lưu các mốc ISO-8601 có timezone: registrationOpenAt, registrationCloseAt, codingOpenAt, codingCloseAt, validationStartAt, validationDeadline, checkInOpenAt, checkInCloseAt, tournamentStartAt, estimatedEndAt; resultPublishAt không bắt buộc. Server đổi về Instant UTC. Không nhận ngày thiếu timezone.

Lịch bắt buộc đúng thứ tự; đăng ký, coding và check-in phải có độ dài dương. Coding kết thúc không muộn hơn validation bắt đầu; validation kết thúc không muộn hơn check-in mở. Thời điểm bằng nhau được phép giữa hai giai đoạn liên tiếp. estimatedEndAt chỉ là dự kiến, không tự kết thúc trận; resultPublishAt chưa kích hoạt trì hoãn công bố ở nhóm này.

- `PUT /api/tournaments/{id}/timeline`: session chủ giải/admin + CSRF, body `{revision, timeline}`. Chỉ cấu hình khi DRAFT, chưa có đăng ký/roster; revision cũ trả 409, người không quản lý trả 403. Chưa có endpoint override lịch đang mở.
- `GET /api/tournaments/{id}/timeline`: public, Cache-Control no-store. Trả timeline, phase, serverNow, phaseEndsAt, revision và timelineConfigured. Chỉ đọc; không tăng revision, không đổi status và không tạo job.

Timeline được lưu trong `tournaments.structure_json` qua TournamentDetails, cùng transaction hiện có và audit `TIMELINE_CONFIGURED` gồm actor, before/after, timestamp. Không thêm/sửa SQL migration: schema vẫn V23 vì đã có cột snapshot. Khi đọc JSON cũ thiếu timeline, giá trị là null và giữ luồng thủ công; không gán lịch giả cho giải cũ. Rollback mutation cũng phục hồi timeline.

## Cách xác định phase

`TournamentPhaseResolver.resolve(status,timeline,serverNow,override,reviewComplete)` là hàm thuần, không phụ thuộc cron hoặc local clock. Các khoảng thời gian là `[mở, đóng)`; đúng giờ đóng đã sang phase tiếp theo.

- DRAFT, CANCELLED, FINISHED, RUNNING, PAUSED, RECOVERY_REQUIRED thực tế có ưu tiên cao nhất.
- Trước đăng ký: UPCOMING; trong đăng ký: REGISTRATION_OPEN.
- Sau đăng ký, trước coding: REGISTRATION_REVIEW nếu còn đơn PENDING; WAITING_FOR_CODING nếu đã review hết.
- Trong coding: CODING_OPEN; trước validationStartAt: SUBMISSION_FROZEN.
- Sau validationStartAt, trước check-in: VALIDATING. validationDeadline không tự giả định mọi bot đã pass.
- Trong check-in: CHECK_IN_OPEN; sau đó READY_TO_START.
- Đến tournamentStartAt vẫn cần lifecycle thực sự start trước khi trả RUNNING; không giả lập trận khi chưa có roster/lịch thi. Đến estimatedEndAt cũng không tự trả FINISHED.

Resolver có tham số override chuẩn bị cho nhóm sau; HTTP chưa nhận override. Không cho override giả RUNNING/FINISHED hoặc bỏ qua PAUSED/recovery. Khi restart, timeline đọc từ snapshot và phase được resolve lại với giờ server hiện tại.

## Kiểm thử và file

File mới: TournamentTimeline, TournamentPhase, TournamentPhaseResolver, TournamentTimelineController; TournamentTimelineTest, TournamentPhaseResolverTest, TournamentTimelineApiTest. File sửa: Tournament, TournamentDetails, OwnedTournamentService, README và tài liệu này.

Kiểm thử gồm thứ tự lịch và từng ranh giới đảo, các giờ bằng nhau hợp lệ, timezone UTC, chính xác giờ đóng/mở, review/waiting, trạng thái terminal/pause/recovery, luồng legacy, override không giả engine, đọc lại snapshot cũ/mới, quyền session/revision, DB snapshot/audit, public no-store.

Kết quả: **41 backend PASS, 0 FAIL, 0 SKIPPED**, gồm 18 Flyway PostgreSQL Docker tests, 1 JPA foundation và 22 timeline/resolver/API tests; **80 client PASS**; bootJar build thành công. Lượt đầu có 18 SKIPPED do Docker tắt; đã bật Docker Linux và chạy lại, toàn bộ đạt. Native PostgreSQL smoke kiểm tra PUT timeline với session/CSRF, serverNow và UPCOMING; sau restart so sánh nguyên timeline/revision rồi hủy giải thử. Không thay đổi schema/migration cũ.

Không sửa GameEngine, RuleSet, Match Engine hoặc BotProtocol. Không thay hành vi thi đấu hiện hành ở nhóm 1.
