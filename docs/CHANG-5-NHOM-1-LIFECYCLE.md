# Chặng 5 — Nhóm 1: vòng đời giải đấu và database

Hoàn thành nền vòng đời giải đấu. Chỉ triển khai nhóm 1 theo thứ tự của [prompt](CHANG-5-TOURNAMENT-PROMPT.md); các nhóm 2–10 chưa triển khai.

## Cách sử dụng

1. Đăng nhập, tạo giải Round Robin và thêm bot như trước.
2. Chủ giải bấm **Mở đăng ký** để chuyển DRAFT → REGISTRATION. Người khác đăng ký/rút bot của mình; chủ giải/ADMIN có quyền gỡ participant.
3. Có ít nhất hai bot, bấm **Khóa đăng ký** và xác nhận. Giải chuyển READY, danh sách không sửa được, chưa tạo lịch hoặc chạy trận.
4. Bấm **Bắt đầu giải** để tạo lịch và chạy qua worker/queue hiện có. Kiểm tra lại bot, artifact và sandbox trước khi bắt đầu.

**Bắt đầu nhanh** từ DRAFT vẫn tương thích luồng cũ. Server thực hiện DRAFT → REGISTRATION → READY → RUNNING trong một transaction, không bỏ qua state machine. Khi đang REGISTRATION phải khóa đăng ký trước khi start. Hủy giải giữ các kết quả đã hoàn tất.

## State machine

- DRAFT → REGISTRATION hoặc CANCELLED.
- REGISTRATION → CHECK_IN, READY hoặc CANCELLED.
- CHECK_IN → READY hoặc CANCELLED.
- READY → RUNNING, CANCELLED hoặc RECOVERY_REQUIRED.
- RUNNING → PAUSED, FINISHED, CANCELLED hoặc RECOVERY_REQUIRED.
- PAUSED → RUNNING, FINISHED, CANCELLED hoặc RECOVERY_REQUIRED.
- RECOVERY_REQUIRED → CANCELLED hoặc FINISHED khi checkpoint đã có toàn bộ kết quả terminal.
- FINISHED/CANCELLED không chuyển tiếp.

CHECK_IN và PAUSED mới là trạng thái nền. Nhóm này **chưa có** thao tác check-in, duyệt participant, pause/resume, bot version lock, bracket hoặc thể thức mới. Điều kiện nghiệp vụ như số bot/quyền/sandbox nằm ở application service; state machine chỉ kiểm soát cạnh chuyển trạng thái và timestamps.

`statusChangedAt` khởi tạo bằng `createdAt` và cập nhật khi chuyển trạng thái. `startedAt` chỉ ghi lần bắt đầu đầu tiên; resume không ghi đè. `finishedAt` ghi khi FINISHED/CANCELLED. Khi DB commit lỗi, trạng thái, timestamps, revision, roster và lịch trong RAM được rollback; không phát notification hoặc chạy lịch chưa commit.

## Database và restart

Migration **V10__tournament_lifecycle** là Java Flyway migration đóng trong JAR. Không sửa V1–V9. V10 thay CHECK status cũ bằng chín trạng thái, thêm `status_changed_at` NOT NULL/default và backfill `COALESCE(finished_at, started_at, created_at)`. Có checksum để Flyway phát hiện sửa migration đã áp dụng. PostgreSQL và H2 đặt tên CHECK cũ khác nhau nên migration tìm CHECK enum qua metadata; không xóa constraint NOT NULL.

- DRAFT, REGISTRATION, CHECK_IN và READY **chưa có lịch** được khôi phục đúng trạng thái/timestamp/roster.
- RUNNING, PAUSED hoặc READY có lịch đang dở chuyển RECOVERY_REQUIRED; không tự chạy lại trận.
- FINISHED/CANCELLED giữ kết quả/ranking; row PRIVATE không trở thành PUBLIC.

Sao lưu DB cùng source/replay trước khi nâng cấp. Không quay lại JAR cũ với DB đã áp dụng V10; rollback triển khai phải khôi phục cả snapshot DB và artifacts tương ứng theo [hướng dẫn vận hành](OPERATIONS.md).

## API

- `POST /api/tournaments/{id}/open-registration`: DRAFT → REGISTRATION.
- `POST /api/tournaments/{id}/close-registration`: REGISTRATION → READY, ít nhất hai bot.
- Add/remove participant cho phép DRAFT hoặc REGISTRATION; READY trở đi trả TOURNAMENT_LOCKED.
- `/start` cho phép DRAFT quick-start hoặc READY; `/cancel` dùng state machine.
- List/detail/export/WebSocket trả thêm `statusChangedAt`.

Mutation dùng session + CSRF; open/close/start/cancel chỉ owner account hoặc ADMIN. Không nhận quyền từ token/body/header do client gửi. Sai cạnh chuyển trạng thái trả `INVALID_TOURNAMENT_TRANSITION`; client không tự retry mutation.

## Kiểm thử và build

Ngày 14/10/2026:

- Backend: **415 passed, 0 failed, 0 skipped**, gồm Docker bot/giải và PostgreSQL thật.
- Client: **22 passed**, gồm session/CSRF/method/encode ID và không retry khi stale-state.
- Migration: **8 passed** trong bộ backend; kiểm tra fresh schema và upgrade dữ liệu cũ, enum mới, status sai/null và checksum.
- `bootJar`: thành công; migration V10 đã chạy trên PostgreSQL cục bộ sau backup.
- Trình duyệt: chủ giải mở đăng ký rồi khóa roster, nút gỡ/đăng ký biến mất, nút bắt đầu giải xuất hiện. Sửa lỗi JavaScript tìm nút export đã bị bỏ khiến trang không tải.
- Server thật: READY giữ hai bot, lịch trống, startedAt null và statusChangedAt không đổi qua hai lần restart. Health, request ID, quyền metrics/admin và giới hạn PING WebSocket vẫn pass.
- Diff các package game/rule/rules: **không thay đổi**.

## File thay đổi

Backend: `TournamentStateMachine`, `TournamentStatus`, `Tournament`, `TournamentService`, `OwnedTournamentService`, `TournamentController`, `TournamentExecutionService`; persistence `TournamentEntity`, `TournamentMetadata`, `TournamentRepositoryAdapter`, `TournamentPersistenceCoordinator`; Java migration V10.

Kiểm thử: `TournamentLifecycleTest`, `TournamentLifecycleApiTest`, `OwnedTournamentRollbackTest`, `TournamentPersistencePostgresTest`, `FlywayMigrationTest`, `JpaFoundationTest` và `client/tests/tournament-lifecycle.test.js`.

Client: `tournament.html`, `tournament-api.js`, `tournament-detail.js`, `tournament-list.js`, `ranking-renderer.js`, `toast.js`. Tài liệu: README, prompt chặng 5, báo cáo này, Deployment và Tournament Ownership.

Nhóm tiếp theo: **Registration + Approval + Check-in**. Giới hạn hiện tại vẫn Round Robin PUBLIC, 2–10 bot và worker/queue của chặng 4.
