# Giao thức Remote Worker với Central

[Mục lục](../README.md) · [Cài và chạy worker](../admin/workers.md) · [API](api.md) · [Recovery](backup-recovery.md)

Đây là giao thức HTTPS JSON giữa agent Java và Central, khác [bot JSON Lines](../bot/protocol.md). Protocol version hiện là **1**; phiên bản agent hiện trong worker build là 0.11.0. Ưu tiên chạy JAR phát hành cùng Central thay vì tự dựng client mới từ tài liệu tóm tắt.

## Bootstrap và credential

Registration token được cấp qua session + CSRF, raw secret chỉ trả trong response tạo token:

- Admin cấp token dùng chung tại `POST /api/admin/workers/registration-tokens`. Máy đăng ký ban đầu là PENDING_APPROVAL/UNTRUSTED và cần Admin duyệt trước khi nhận việc.
- Chủ giải hoặc Admin cấp token riêng tại `POST /api/tournaments/{id}/workers/registration-tokens`, body `{"trusted":true}`. Token gắn đúng tournament trên Central, một lần dùng, hạn 30 phút. Máy đăng ký là APPROVED/TRUSTED với phạm vi cố định của giải.

Agent dùng cùng protocol cho cả hai loại token. Nó lấy token từ biến môi trường `WORKER_REGISTRATION_TOKEN` và gọi POST `/internal/workers/register` với:

```json
{"registrationToken":"<TOKEN_NHAP_RIENG>","name":"LAB-PC-01","workerVersion":"0.11.0","protocolVersion":1}
```

Response 201 gồm `worker` Identity (workerId/name/workerVersion/protocolVersion/status/approvalStatus/trustLevel) và `workerCredential`. Agent lưu credential riêng trong state-dir bằng cơ chế quyền owner-only. Token không được lưu lại làm credential dài hạn. Database giữ SHA-256 của token/credential; không dùng hash ở DB như Bearer secret.

Các request sau dùng `Authorization: Bearer <workerCredential>`. Internal chain stateless, không CSRF và không nhận session ADMIN thay credential. Principal phải trùng `{id}` trên đường dẫn. Thu hồi registration token chỉ chặn enrollment mới; thu hồi máy mới chặn credential đã cấp. Không tự enroll lại nhiều lần khi response ban đầu thất lạc.

### Phạm vi của worker

Scope được server ghi khi tiêu thụ token trong cùng giao dịch tạo worker/credential. Không có scope nghĩa là worker dùng chung; có scope nghĩa là chỉ một tournament. Body register, capabilities, heartbeat hoặc claim không đặt/đổi được scope. Đổi `--name`, `--central` hoặc `--state-dir` không mở rộng quyền của credential hiện tại.

Máy riêng chỉ được claim, start, tải artifact, renew/recover lease và gửi kết quả cho đúng giải. Central lọc scope ngay khi chọn job và kiểm tra lại tại các đường thực thi; scope không thay thế guard assignment, epoch, lease, hash hoặc trust. Vi phạm phạm vi có thể trả `WORKER_TOURNAMENT_FORBIDDEN` (403); đoán execution không được giao vẫn bị chặn theo guard tương ứng.

Mỗi giải tối đa 20 worker chưa thu hồi và 5 token còn hiệu lực/còn lượt. Tạo token riêng yêu cầu giải chưa kết thúc/hủy/lưu trữ và dùng BOT_ONLY có coding. Khi enroll, backend còn kiểm tra trạng thái giải, trash, tài khoản chủ giải ACTIVE và hạn mức máy. Thu hồi không xóa scope, không tự chuyển worker riêng thành worker dùng chung. Worker dùng chung vẫn có thể nhận trận phù hợp của giải có máy riêng.

JAR/protocol không đổi để hỗ trợ scope. Trang chủ giải tạo lệnh với state-dir `./worker-state-tournament-<id-giai>`; mỗi máy giữ thư mục riêng và dùng lại khi restart. Không dùng credential của worker chung hoặc của giải khác để chạy lệnh riêng cho giải mới.

## Capabilities và heartbeat

Đặt `I = /internal/workers/{id}`.

| Method / path | Request | Response / điều kiện |
| --- | --- | --- |
| GET `I/identity` | Bearer | Identity của đúng worker. |
| POST `I/capabilities` | cpuCores,memoryMb,os,docker,supportedLanguages,maxConcurrentMatches,maxBotProcesses,workerVersion,protocolVersion | Session: epoch, heartbeatIntervalSeconds=5, offlineAfterSeconds=30. |
| POST `I/heartbeat` | epoch,sequence,activeMatches,freeSlots,activeBotProcesses,cpuUsage,memoryUsage,diskFreeMb,cacheSizeMb,cacheHits,cacheMisses | Worker View có trạng thái và slot effective. |

Capabilities tạo epoch startup mới và xóa mẫu heartbeat cũ. supportedLanguages gồm PYTHON/CPP/JAVA, JAR dùng runtime JAVA; số bot processes phải ít nhất hai lần max matches. Heartbeat sequence tăng trong cùng epoch; packet lặp/cũ không làm mới thời gian sống. Sai epoch trả STALE_WORKER_SESSION.

CPU/RAM là phần trăm finite trong 0–100; thông số tài nguyên là số hợp lệ theo record validation. Central còn giới hạn budget, reservation, approval/trust/ngôn ngữ. Worker tự báo freeSlots không được ghi đè slot đã đặt ở Central.

## Claim và artifact

POST `I/jobs/claim` nhận Claim: `requestId`, `epoch`, `availableSlots`, `cachedArtifacts`, `gameCoreVersion`, `ruleSetVersion`, `protocolVersion`. requestId làm khóa idempotency trong phạm vi worker; không đổi body rồi dùng lại cùng ID.

Response Batch có `jobs`, `occupiedSlots`, `effectiveFreeSlots`, `serverNow`. Mỗi Assignment gồm `jobId`, `executionId`, `workerId`, `epoch`, `slot`, `status`, `leaseUntil` và `match` Spec:

- matchId, tournamentId, botA/botB với artifactId/artifactHash/language/runtime;
- gameCoreVersion, ruleSetVersion, seed;
- turnTimeoutMs, matchHardTimeoutMs, maxTurns, minimumTrust.

Các ID/hash/spec được Central cấp và giữ bất biến. Claim lấy job từ hàng đợi chung của các giải, lọc theo scope, policy, trust, ngôn ngữ và capacity; không dùng membership nhóm worker. Agent không chọn một giải khác bằng payload. Trần đang chạy của từng giải được xét trước giới hạn danh sách ứng viên để một giải đầy slot không che các giải khác. FIFO có ưu tiên cache cho job mới; job chờ từ 30 giây bỏ ưu tiên cache.

`artifactId` và `artifactHash` trong job lấy từ phiên bản bài nộp LOCKED được phép thi đấu, kể cả sau nộp lại hoặc dùng lại bản đạt kiểm thử trước đó. Hash bản upload gắn với danh tính bot trong roster không dùng để so sánh với artifact: source C++/Java khác binary/JAR đã build. Central vẫn kiểm tra khóa bài, kết quả kiểm thử chính thức, danh tính bot và hash của phiên bản; khi tải artifact còn kiểm SHA-256 của file thực tế.

### AUTO và thời điểm quyết định nơi chạy

Giải chưa lưu policy riêng mặc định AUTO. Với trận BOT_ONLY có coding và artifact LOCKED hợp lệ, Central kiểm tra có worker đúng phạm vi, đã duyệt, còn heartbeat và hỗ trợ các ngôn ngữ cần dùng. Máy tương thích đang BUSY vẫn tính là có máy. AUTO không có máy phù hợp thì chạy local trước khi nhận vào queue; nếu có máy nhưng chưa có slot thì chờ.

Khi job đã được nhận vào remote queue, mất kết nối worker không làm phát sinh thêm execution local. Job dùng lease/retry/recovery hiện có. LOCAL luôn chạy Central; REMOTE_REQUIRED chờ worker phù hợp. Chỉ Admin thay policy khi giải ở DRAFT/REGISTRATION, chưa có match và chưa có execution job. Thêm máy riêng không đổi policy. Xem [điều phối trong hướng dẫn Admin](../admin/workers.md#chế-độ-điều-phối-của-giải).

GET `I/jobs/assigned` đọc các assignment active của máy. GET `I/artifacts/{artifact}?executionId=…` trả byte artifact kèm `X-Artifact-SHA256`, `X-Artifact-Language`, `X-Artifact-Format`. Service kiểm đúng execution/worker/lease/quyền; không tải source bất kỳ bằng artifact ID đoán được. Cache chỉ dùng khi SHA-256 hợp lệ; Python cần runtime file khóa, C++/Java dùng binary/JAR đã build.

## Start, thực thi và kết quả

| Method / path | Request | Response / ý nghĩa |
| --- | --- | --- |
| POST `I/jobs/{job}/start` | executionId, epoch | Assignment đã RUNNING sau khi guard hợp lệ. |
| POST `I/jobs/{job}/running` | executionId, epoch | Ghi gameplay_started_at sau chuẩn bị; không phải stream bàn cờ. |
| POST `I/jobs/{job}/decline` | executionId, reason | Từ chối assignment hợp lệ, trả slot/đưa job về queue theo guard. |
| POST `I/jobs/{job}/complete` | result, replayBase64 | Receipt bền vững hoặc lỗi integrity/assignment/lease. |

Agent chạy game core headless, hai bot mỗi bot trong một Docker container suốt ván, áp dụng seed/limits của Spec và ghi journal trước khi upload. Không gửi secret hoặc source trong result.

`result` có executionId,matchId,winner,loser,reason,turnCount,durationMs,artifactHashA,artifactHashB,seed,replayChecksum. winner/loser là P1/P2 đối nhau hoặc cùng null khi hòa; reason hiện ENGINE_RESULT/BOT_FAILED. replayBase64 chứa byte replay, checksum SHA-256 lowercase 64 ký tự.

Backend giới hạn replay 15 MiB và request 22 MiB, tối đa hai upload đồng thời ở controller. **Proxy mẫu chỉ nhận 6 MiB**; muốn hỗ trợ giới hạn backend đầy đủ cần cấu hình proxy riêng và kiểm tra upload lớn theo [deployment](deployment.md). 413 ở proxy chưa đi vào RemoteResultService.

Central kiểm hash/seed/spec, replay events và kết quả tái dựng bằng engine, placement khởi đầu tương ứng seed, trạng thái giải/trận và trust/lease. Receipt `ACCEPTED` có executionId/jobId/matchId/resultHash/status; lưu receipt và projection vào giải là hai bước. Upload lại cùng kết quả nhận receipt cũ, dữ liệu khác trả RESULT_CONFLICT. Không bỏ journal trước khi nhận xác nhận.

## Lease, fence và recovery

POST `I/jobs/renew` với `{epoch, executions:[…]}` tối đa 16 execution, trả Grants với executionId/jobId/status/leaseUntil và serverNow. Lease đầu ít nhất 60 giây hoặc hard timeout +30 giây. Renewal không vượt cap tính từ execution start/claim +hard timeout +120 giây; lease không vô hạn dù worker còn heartbeat.

POST `I/jobs/recover` nhận `{executionId, operation}`:

- `RESULT_READY`: xin phép upload kết quả đã có nếu execution vẫn hợp lệ trong cửa sổ recovery.
- `ABANDON`: bỏ attempt bị gián đoạn để Central xử lý retry, không chạy lại bot trong cùng attempt.

Response Grant có thể DONE, SUPERSEDED, ABANDONED, LEASE_LOST hoặc UPLOAD_ALLOWED theo trạng thái. Cửa sổ grace 30 giây phục vụ recovery có kiểm soát, **không** cho complete thông thường bỏ qua lease hết hạn. Reaper mặc định chạy mỗi giây, retry hạ tầng tối đa ba attempt theo policy; hết lượt chuyển review/lỗi hạ tầng, không forfeit người chơi.

Disable/revoke fence việc cũ qua cùng queue lock; attempt cũ không được thắng race để ghi kết quả mới. Drain chỉ ngừng nhận việc mới. Agent `--serve` liên tục heartbeat/claim/recover; `--run-once`, `--upload-pending`, `--recover`, `--prefetch` là chế độ riêng, dùng khi agent đã dừng và cùng state-dir.

## Nguồn đối chiếu

- [Worker security](../../server/src/main/java/com/rpsbattle/worker/remote/WorkerSecurityConfig.java), [identity](../../server/src/main/java/com/rpsbattle/worker/remote/WorkerIdentityService.java), [telemetry records](../../server/src/main/java/com/rpsbattle/worker/remote/WorkerTelemetryService.java).
- [Queue/records](../../server/src/main/java/com/rpsbattle/worker/remote/RemoteMatchQueue.java), [artifact service](../../server/src/main/java/com/rpsbattle/worker/remote/RemoteArtifactService.java), [result](../../server/src/main/java/com/rpsbattle/worker/remote/RemoteResultService.java), [lease/recovery](../../server/src/main/java/com/rpsbattle/worker/remote/RemoteLeaseService.java).
- [Scope](../../server/src/main/java/com/rpsbattle/worker/remote/WorkerScope.java), [policy/định tuyến](../../server/src/main/java/com/rpsbattle/worker/remote/RemoteRoutingService.java), [quản lý worker của giải](../../server/src/main/java/com/rpsbattle/worker/remote/TournamentWorkerService.java).
- [Agent sources](../../worker/src/main/java/com/rpsbattle/remote/), [build version](../../worker/build.gradle).

### Thống kê thời gian tính nước

Kết quả từ JAR mới có thêm `moveTiming` gồm `nanosA`, `nanosB` (tổng nano giây chờ bot chọn nước) và `countA`, `countB` (số lần gọi bot, kể cả lần lỗi giao thức). Outbox giữ nguyên các số liệu khi gửi lại; Central kiểm tra giới hạn và lưu cùng kết quả trận để tính `AVERAGE_MOVE_TIME`. Thời gian phản ánh máy thực thi, không phải điểm benchmark CPU.

Cập nhật Central trước, sau đó dừng worker, tải JAR đi kèm Central và chạy lại với state-dir cũ. Kết quả từ worker/outbox cũ không có trường này vẫn được nhận với hash xác nhận cũ; thời gian là chưa có dữ liệu, không quy thành 0. Các trận cũ không thể bổ sung số đo chính xác từ replay.
