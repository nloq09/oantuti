# Player Shell thống nhất

Nhóm 1 của Chặng 12 hoàn thành phần khung cho người chơi. Giữ nguyên route, backend, schema, GameEngine và luật thi đấu. Chưa triển khai các nhóm sau.

## Thay đổi

- `tournament-header.js`: tên giải, thể thức, số suất trong roster/maxParticipants, lifecycle, phase server và đồng hồ phase. Đếm roster thi đấu, không giả định đó là tổng đơn đăng ký/approved. Timer dùng serverNow cộng thời gian monotonic; về 0 chỉ báo chờ server, không mở quyền.
- `progress-stepper.js`: dùng chung sáu bước; thêm status COMPLETED/CURRENT/LOCKED/FAILED/OPTIONAL, giữ state cũ tương thích. Các bước coding/validation không bắt buộc ở giải legacy; validation lỗi có chữ/ký hiệu thay vì chỉ màu. Stepper không cấp quyền hoặc tạo navigation.
- `next-action.js`: một model gồm type/label/href/enabled/tone/description/reasonDisabled. Ưu tiên participant-flow.nextAction và capabilities cho timeline; khi thi đấu/legacy đọc seat, ready-window và participant state của server. Không suy quyền cá nhân từ phase toàn giải. Chưa nối Hub/notification vào model mới: thuộc nhóm 9.
- `player-shell.js`: Header → Stepper → “Việc của bạn”. Chỉ một CTA trong card; mutation chống click trùng, dùng API/CSRF cũ. Trạng thái chờ không tạo nút giả.
- Detail/lobby giữ shell trước nhánh return của nội dung phase. Thay đổi quyền/phase bắt buộc render lại; CTA bracket/lịch trỏ đúng lobby và hash tab. Bỏ CTA code/check-in/ready/entry trùng với thao tác chính tương ứng.
- Workspace dùng cùng header/stepper; quyền sửa, autosave, deadline và version giữ nguyên.
- CSS giới hạn trong shell. Ghi đè height 70px của global header bằng height:auto, tránh metadata/timer chồng stepper; có bố cục một cột ở màn hình nhỏ.

## File thêm

- client/js/tournament/tournament-header.js
- client/js/tournament/next-action.js
- client/js/tournament/player-shell.js
- client/tests/tournament-header.test.js
- client/tests/next-action.test.js
- client/tests/player-shell.test.js
- client/tests/helpers/tournament-dom.js
- Tài liệu này.

## File sửa

- client/js/tournament/player-detail.js
- client/js/tournament/code-workspace.js
- client/js/tournament/progress-stepper.js
- client/js/tournament/timeline-registration.js
- client/js/tournament/timeline-check-in.js
- client/css/tournament.css
- README.md

## API / DTO

Không thay API/DTO/migration. Tái sử dụng summary, participant-flow, timeline, me, check-in, ready và entry; shell không lưu credential mới hoặc truy cập source của người khác.

## Kiểm chứng ngày 20/10/2026

- PASS: 139 client tests, gồm quyền cá nhân, terminal/stale states, ưu tiên human seat, một CTA, chống gửi trùng, shell qua các phase, header timer và legacy optional steps.
- PASS: 16 backend tests liên quan: TournamentPhaseResolverTest, TournamentTimelineApiTest, TimelineRegistrationApiTest, ParticipantTournamentStateTest, ReadyCheckTest.
- PASS: bootJar bản cuối; HTTP smoke các page detail/lobby/code và các module mới. Native API fixture xác nhận Upcoming/Registration/Waiting/Coding và chỉ coding được sửa workspace.
- PASS: browser desktop localhost: Upcoming với guest và tài khoản test, Registration, Waiting Coding, Coding detail và workspace. Đã xem screenshot, phát hiện và sửa lỗi global header ép chiều cao. Không thấy stacktrace hoặc header đè stepper ở bản đã sửa.
- SKIPPED: mobile viewport trực tiếp do browser capability hiện không có resize/emulation; có CSS breakpoint và chưa khẳng định đã kiểm tra trên thiết bị thật.
- Không chạy migration tests vì schema không đổi. Không chạy benchmark Docker 4000 người vì nhóm này không đổi execution/worker.

Lệnh kiểm tra backend phải dùng `:test`, không dùng `test --tests ...` ở multi-project: lệnh chưa qualify đã pass 16 backend case nhưng filter bị áp sang worker:test và báo không tìm thấy test. Đã chạy lại task :test đúng scope, BUILD SUCCESSFUL; không sửa test để bỏ qua lỗi.

## Giới hạn của nhóm 1

Các nội dung phase vẫn tái sử dụng renderer cũ; tinh chỉnh bố cục đầy đủ ở nhóm 2. Full timeline create, Organizer Control Center, metrics/attention, remote execution read-model, My Match/Match Monitor, Hub/notification/refresh cleanup và result publish chưa được tính là hoàn thành. Không tạo ETA hoặc trạng thái RUNNING_REMOTE khi chưa có DTO authoritative; đó là nhóm 7–8.
