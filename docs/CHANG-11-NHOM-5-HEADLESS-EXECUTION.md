# Worker headless execution

Worker 0.5.0 chạy được một batch job bằng Docker, giữ bot sống suốt trận và tạo kết quả/replay tại máy worker. Đây là nhóm 5; upload và commit kết quả thuộc nhóm 6. Scheduler Central vẫn chạy giải thật qua local worker, chưa tự đưa trận vào queue remote.

## Chạy worker

Cài Java 21+, Docker Linux và image cho ngôn ngữ cần dùng. Đăng ký/duyệt worker theo hướng dẫn nhóm 1–2. Dừng agent heartbeat trước khi dùng cùng state:

```sh
java -jar rps-worker.jar --central=https://central.example --state-dir=./worker-state --max-matches=2 --max-bots=4 --run-once
```

Local/LAN HTTP cần thêm `--allow-insecure-http`. Không dùng cùng lúc --once, --prefetch, --identity-only hoặc --run-once. Mặc định không có --run-once thì agent chỉ heartbeat.

--run-once kiểm tra state/execution đang chờ, gửi capabilities/heartbeat, claim tối đa min(16, max-matches) job và chạy song song trong giới hạn đó. Central vẫn quyết định reservation và max-bots phải đủ hai bot mỗi trận. Không có job tương thích thì thoát với số trận 0. Không tự tạo job giả hoặc chỉnh kết quả giải để chạy thử.

## Luồng execution

1. Xác minh assignment đúng worker/epoch/slot, core `1`, rules `classic-online-v1`, hash/runtime và lease đủ thời gian.
2. Acquire/pin hai artifact từ cache; tải/xác minh như nhóm 4.
3. Ghi assignment và STARTING vào outbox riêng tư trước request start. Sao chép artifact vào execution directory, kiểm tra lại hash.
4. `POST /internal/workers/{workerId}/jobs/{jobId}/start` với executionId và epoch, Bearer riêng. Central kiểm tra ownership, epoch hiện tại và epoch claim, lease, approval/trust, heartbeat; chuyển CLAIMED → RUNNING idempotent, giữ reservation. Cookie/admin không thay thế credential. RUNNING không được decline để requeue.
5. Khởi động hai container, chạy engine không render và không pacing theo UI. Heartbeat tiếp tục mỗi 5 giây với active matches/bots/cache và slot còn lại.
6. Kết thúc: đóng bot/container, kiểm chứng replay bằng ReplayService, ghi replay + checksum + outcome READY_FOR_UPLOAD. Xóa bản artifact tạm; cache gốc còn pin đến khi tác vụ xong.

## Dùng chung luật và protocol

Worker biên dịch trực tiếp một tập nguồn domain/protocol/replay từ server, không phụ thuộc Spring hoặc đóng gói Central server. Cùng GameEngine, Match, BotAgent, BotProtocol, OnlineMatch placement và ReplayService; không sao chép thuật toán combat/movement/victory sang phiên bản khác.

GameEngine và luật chiến đấu không đổi. OnlineRuleSet có thêm constructor nhận maxTurns của job; constructor cũ giữ mặc định 200, nên hành vi game hiện tại giữ nguyên. Seed 64-bit, định danh piece và random placement theo cùng online setup; maxTurns, turnTimeoutMs và hard timeout được giữ từ assignment.

Bot trả malformed/stale/illegal action, hết thời gian lượt hoặc lỗi process được xử thua bằng SurrenderAction qua engine. Lỗi Docker/hạ tầng và hard timeout không tạo người thắng giả; ghi FAILED với mã an toàn. Thời gian startup được giới hạn tối đa 25 giây mỗi bot và vẫn nằm trong hard deadline toàn trận. Không khởi động lại bot mỗi lượt.

## Docker sandbox

Chỉ mount một artifact readonly, không mount cache root, credential, repo hoặc Docker socket vào bot. Không chạy source/compiler/interpreter bot trên host, không tự pull image hoặc nhận command/image tùy ý từ job.

- Network none, root filesystem readonly, UID/GID 65534, drop all capabilities, no-new-privileges.
- Mỗi bot 0.5 CPU, RAM/swap 384 MiB, 64 PID, 64 file descriptor; tmpfs 64 MiB.
- Python chạy file locked; C++ copy binary đã build vào tmpfs rồi exec; Java chạy JAR đã build. Không build lại C++/Java trong trận.
- stdout mỗi dòng tối đa 65.536 ký tự, tổng 4 Mi ký tự, queue 4 dòng; stderr tối đa 64 KiB và không ghi nội dung vào result/log.
- Hết lượt/hard timeout, process failure hoặc shutdown đều đóng transport và force-remove container có tên ngẫu nhiên riêng execution.

Cache gốc giữ owner-only. Trên POSIX, bản artifact tạm được đặt 0644 để UID container đọc qua file mount; thư mục cha execution vẫn 0700 nên tài khoản host khác không thể đi vào. Windows tiếp tục dùng ACL owner-only. File tạm được dọn sau tác vụ; nếu tiến trình bị kill cứng thì state được giữ và chặn chạy lại, việc recovery/cleanup bổ sung ở nhóm 7.

## Outbox và giới hạn hiện tại

`worker-state/executions/{executionId}/` chứa assignment.json, outcome.json và replay.json khi thành công. Ghi JSON qua file tạm + fsync + atomic move; outcome READY_FOR_UPLOAD chỉ xuất hiện sau khi replay đã ghi. Replay có SHA-256, seed, artifact hash A/B, winner/loser seat P1/P2 hoặc draw, số lượt và thời gian. Đây chưa phải participant ID hoặc kết quả chính thức của giải.

Nếu execution/state đã tồn tại, worker chặn batch tiếp theo và không tự chạy lại, kể cả mất phản hồi start. Giữ state để nối upload/recovery; không xóa state để ép claim. Central vẫn giữ RUNNING/reservation sau khi local đã xong, và bracket/standings không thay đổi. Lỗi trước khi có intent/start có thể decline; request start đã gửi hoặc mơ hồ thì giữ reservation. Không có cơ chế trả slot bằng cách tin worker tự báo idle.

## Kiểm chứng

- 176 kiểm thử backend PASS, 0 fail/skip: start API H2/PostgreSQL, artifact authorization, bot protocol, online placement, luật classic, replay/persistence và local worker.
- 22 kiểm thử worker PASS, 0 fail/skip: deterministic engine/replay, process lifetime, bot fault so với infrastructure/hard deadline, journal/restart và sandbox.
- 125 kiểm thử client PASS, 0 fail/skip; trang setup admin cập nhật cách chạy batch và giới hạn upload/reservation.
- Docker thật đã chạy Python, C++ binary, Java JAR, giữ process qua nhiều request; xác nhận non-root, readonly, timeout, stdout/stderr flood và container cleanup.
- Native Central + PostgreSQL + worker JAR: claim/start, artifact Python LOCKED thật, đủ 12 lượt với ENGINE_RESULT, replay/checksum hợp lệ, reservation giữ tới acknowledgement; chạy lại bị chặn và không tạo attempt thứ hai. Fixture thử được hủy bằng SQL, không commit vào bảng đấu.
- Sau restart Central: lịch sử execution, outcome/replay/checksum vẫn đúng, artifact tạm đã dọn; E2E giải cũ xác nhận standings, replay, frozen versions và audit giữ nguyên.
- Kiểm tra UI trực quan SKIPPED do browser tools bị tắt trong Work settings; đây là chức năng headless, không cần browser chạy trận.

Tiếp theo: nhóm 6 nhận result/replay, idempotency, xác nhận và giải phóng reservation; nhóm 7 lease và crash/network recovery.
