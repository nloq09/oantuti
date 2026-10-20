# Phân phối giải theo năng lực và nhóm worker

Nhóm 9 nối scheduler giải hiện tại vào durable remote queue. Central tạo lịch, chọn frozen artifact, kiểm tra quyền và áp dụng receipt/replay; máy worker chạy cùng game core đã dùng ở nhóm 5–8. Không đổi luật chơi hoặc GameEngine. Schema mới V34; worker JAR 0.9.0.

## Cấu hình và chạy

1. Trong `/admin/workers.html`, tạo nhóm, ví dụ LAB_A. Thêm worker bằng UUID trong danh sách máy. Worker vẫn cần được duyệt TRUSTED hoặc COMMUNITY; thành viên nhóm không tự có quyền chạy bot.
2. Tạo giải BOT_ONLY dùng cơ chế coding/submission. Bài đem thi đấu phải LOCKED và có artifact/hash đã kiểm chứng. Nhóm này không xuất source của bot upload cũ sang máy khác; giải dùng bot upload đơn thuần tiếp tục chạy local.
3. Tại phần cấu hình giải trên trang Worker, nhập Tournament UUID rồi **Đọc cấu hình giải**. Chọn REMOTE_REQUIRED, Group UUID, độ tin cậy tối thiểu và số trận remote tối đa (1–256). Mặc định TRUSTED; chọn COMMUNITY nghĩa là admin đồng ý cho các máy thuộc nhóm ở mức đó nhận artifact. Lưu cần lý do và revision mới nhất.
4. Chỉ đổi routing khi giải DRAFT/REGISTRATION và chưa tạo lịch/job. Giải chưa cấu hình vẫn LOCAL. Group không có máy khả dụng, bị tắt, sai runtime hoặc hết slot thì remote job chờ; không chuyển sang local để vượt chính sách nhóm/trust.
5. Khởi động worker liên tục:

```sh
java -jar rps-worker.jar --central=https://central.example --name=LAB-PC-01 --state-dir=./worker-state --max-matches=2 --max-bots=4 --serve
```

Lần đầu đặt WORKER_REGISTRATION_TOKEN riêng trong môi trường tiến trình; các lần sau dùng lại state và bỏ token. HTTP local/LAN cần thêm `--allow-insecure-http`. Không chia sẻ state giữa máy. `--serve` không kết hợp với các mode một lần.

Agent mặc định không có `--serve` vẫn heartbeat/recovery như cũ. `--run-once` tiếp tục chạy một batch, `--once` chỉ heartbeat. Chế độ liên tục đối chiếu journal, nhận batch tối đa 16 execution, chạy trong giới hạn cấu hình, gửi kết quả, rồi nhận tiếp. Lúc nhàn rỗi poll sau 5 giây; lỗi tạm thời backoff tới 30 giây. Credential bị revoke thì dừng; không đăng ký lại tự động. Bài chưa được xác nhận vẫn qua luồng recovery trước batch mới.

## Ngân sách nhận việc

Central tính mức trần bằng giá trị nhỏ nhất của maxConcurrentMatches khai báo, CPU cores, `floor((memoryMb - 512) / 896)` và giới hạn admin nếu có. Đây là ngân sách bảo thủ theo tài nguyên khai báo, không phải kết quả benchmark thực nghiệm. Mỗi trận dự trù hai bot 384 MiB và 128 MiB cho engine/overhead, chừa 512 MiB cho hệ thống.

CPU từ 90% giảm ngân sách còn khoảng một phần tư, ít nhất một nếu ngân sách vốn lớn hơn không. CPU từ 98% hoặc RAM từ 90% ngừng cấp việc mới. Mức thấp hơn 0 được chặn về 0. Heartbeat cũ, chưa duyệt, drain, disabled hoặc runtime thiếu vẫn không có slot nhận việc.

Số slot rảnh là mức nhỏ nhất giữa ngân sách còn lại, heartbeat, giới hạn bot process và reservation bền vững trên Central. Worker không thể tăng quota admin bằng heartbeat; hạ quota không giết trận đã nhận. Các cột **Ngân sách CPU/RAM**, **Trần admin**, slot đang giữ và slot sẵn sàng cho biết trạng thái thực tế. Bỏ giới hạn admin trả về ngân sách tự động, vẫn không vượt cấu hình của agent/tài nguyên.

Pull model phân phối theo số slot máy thực sự cung cấp; không hứa chia đều số trận giữa các máy khác tốc độ. Không chọn worker bằng số thread local của Central. Local launch executor chỉ thực hiện admission ngắn, không chạy bot remote.

## Routing và tính đúng

- Dùng SubmissionStore hiện tại để lấy đúng version LOCKED, kiểm tra artifactHash của seat, chọn fixed runtime theo ngôn ngữ. Payload chỉ có artifact ID/hash; không chứa token bot, workspace source hoặc build log.
- Seed giữ phép XOR hai seed người chơi như executor local; game core 1, classic-online-v1, 200 lượt, turn timeout 2 giây và hard timeout 15 phút.
- Job và nhóm đích commit chung transaction/admission guard. Queue giới hạn toàn cục; khi đầy giữ match PENDING để scheduler thử lại, không xử thua.
- Scheduler tính cả job đang chờ/đang thực thi/đã có receipt nhưng chưa áp dụng vào số trận giữ chỗ của giải. Giải remote không phải giữ toàn bộ lịch trong quota pending của executor local.
- Claim chỉ cấp routed job khi giải RUNNING, nhóm còn bật, worker thuộc nhóm, trust/runtime/version phù hợp và còn quota giải. Mọi worker cùng dùng database guard và unique constraints; không vượt slot hoặc cấp trùng match.
- Gỡ thành viên/tắt nhóm chỉ chặn claim mới. Assignment đã nhận được hoàn tất theo lease; muốn hủy ngay dùng Disable/Revoke.
- Cache preference xét tối đa 256 ứng viên tương thích mỗi lượt. Job đủ 30 giây được xếp trước job mới và theo FIFO; job mới ưu tiên cache hit. Đây là ngưỡng ưu tiên, không phải cam kết khởi chạy trong 30 giây khi thiếu tài nguyên.
- Giải hủy hoặc match đã được quyết định sẽ giải phóng queued job và execution đang giữ qua reaper, không chờ hết hard lease. Lịch sử execution được giữ; không ghi đè quyết định admin hoặc tạo winner giả.
- Khi admin retry trận lỗi qua chức năng hiện có, attempt của match tăng. Job CANCELLED chưa có receipt được mở lại với execution mới và ngân sách retry mới; các execution/ledger cũ được giữ. Không thể tự mở lại job hết lượt thử bằng heartbeat hoặc gửi lại payload cũ.
- Receipt/replay, ranking, bracket, manual round, lease và retry tiếp tục dùng cơ chế nhóm trước. Remote match giữ PENDING cho tới receipt được áp dụng; không tạo phòng WebSocket giả trên Central.

## API quản trị

Tất cả API dưới đây dùng session ADMIN; POST cần CSRF, reason và revision khi sửa. Client không retry mutation tự động.

- GET/POST `/api/admin/worker-groups`: xem/tạo nhóm; tối đa 100 nhóm, tên duy nhất.
- POST `/api/admin/worker-groups/{id}`: revision, enabled hoặc workerId + member để thêm/bỏ máy.
- POST `/api/admin/workers/{id}/capacity`: revision của worker, maxMatches (null để bỏ trần), reason.
- GET/POST `/api/admin/tournaments/{id}/worker-policy`: mode, groupId, minimumTrust, maxMatches, revision của policy và reason. Revision policy tách khỏi revision dữ liệu giải.

V34 thêm worker_groups, worker_group_members, worker_capacity_limits, tournament_worker_policies và remote_job_routes. Cấu hình và audit lưu PostgreSQL; restart không xóa nhóm/quota/routing. Quy tắc khôi phục giải vẫn như cũ: giải đang chạy khi Central restart chuyển RECOVERY_REQUIRED, không tự mở vòng mới.

## Phạm vi và kiểm chứng

Benchmark thông lượng và tải 4.000 người thuộc nhóm 10; chưa dùng cấu hình này để khẳng định năng lực thực tế của một máy. Python runtime cần source đã khóa để interpreter chạy; TRUSTED/group là ranh giới tin cậy của máy nhận artifact.

- **PASS backend:** 198 bài trong lượt hồi quy chung và hai case bổ sung hủy giải trên H2/PostgreSQL (200 case khác nhau). Có migration từ V33, quyền ADMIN/CSRF/revision, membership/disable group, CPU/RAM/admin budget, cache fairness, local scheduler, lease/revoke và đúng frozen artifact. Các case routing/admin retry, hủy giải và upgrade V34 được chạy lại sau chỉnh sửa cuối, đều PASS.
- **PASS worker:** 28 bài, gồm Docker sandbox, journal/recovery/upload, chế độ --serve nhận batch rồi dừng khi credential bị thu hồi, và không cho kết hợp các mode.
- **PASS client/admin:** 128 bài, gồm DOM của trang worker, nhóm/membership/routing/quota requests có CSRF, reason/revision và encode ID.
- **PASS build:** Central bootJar và worker JAR 0.9.0.
- **PASS native Docker:** tạo ba identity riêng (hai trong nhóm, một ngoài nhóm). Chuẩn bị hai roster/submission LOCKED cô lập, nạp vào Central rồi gọi API start thật; không tự tạo execution job trong fixture. Scheduler tự enqueue, hai agent --serve nhận và chạy hai lịch giải. Worker ngoài nhóm không có execution; mỗi game đúng một receipt/replay, hai worker khác nhau thực sự thực thi, slot về 0. Sau thử nghiệm revoke cả ba credential test; giữ nguyên worker chính.
- Backup PostgreSQL + bot/replay trước V34. Nhóm, routing, membership, receipt/replay được kiểm tra lại sau restart; regression giải cũ và quyền điều khiển nhóm 8 được kiểm tra lại. Không stage state, credential hoặc backup.
- **SKIPPED:** xem giao diện bằng trình duyệt do browser tools đang tắt trong Work settings. Các bài API/DOM vẫn PASS. Tải 4.000 người và benchmark năng lực thực tế chưa chạy, thuộc nhóm 10.

File thêm/sửa chính: migration V34; WorkerGroupService/Controller; RemoteRoutingService; WorkerManager, TournamentExecutionService, OwnedTournamentService; RemoteMatchQueue/LeaseService/TelemetryService; WorkerConfig/Application/Serve; admin worker group API/form và trang workers; các test Group/Routing/Serve/Migration/client; README và hướng dẫn vận hành.
