# Giao thức Remote Worker với Central

[Mục lục](../README.md) · [Cài và chạy worker](../admin/workers.md) · [API](api.md) · [Recovery](backup-recovery.md)

Đây là giao thức HTTPS JSON giữa agent Java và Central, khác [bot JSON Lines](../bot/protocol.md). Protocol version hiện là **1**; phiên bản agent hiện trong worker build là 0.11.0. Ưu tiên chạy JAR phát hành cùng Central thay vì tự dựng client mới từ tài liệu tóm tắt.

## Bootstrap và credential

Admin dùng session/CSRF tạo registration token, raw trả một lần. Agent lấy token từ biến môi trường WORKER_REGISTRATION_TOKEN và gọi POST `/internal/workers/register` với:

```json
{"registrationToken":"<TOKEN_NHAP_RIENG>","name":"LAB-PC-01","workerVersion":"0.11.0","protocolVersion":1}
```

Response 201 gồm `worker` Identity (workerId/name/workerVersion/protocolVersion/status/approvalStatus/trustLevel) và `workerCredential`. Agent lưu credential riêng trong state-dir bằng cơ chế quyền owner-only. Token không được lưu lại làm credential dài hạn. Database giữ SHA-256 của token/credential; không dùng hash ở DB như Bearer secret.

Các request sau dùng `Authorization: Bearer <workerCredential>`. Internal chain stateless, không CSRF và không nhận session ADMIN thay credential. Principal phải trùng `{id}` trên đường dẫn. Ban đầu PENDING_APPROVAL/UNTRUSTED; phải được Admin duyệt trước khi claim việc. Không tự enroll lại nhiều lần khi response ban đầu thất lạc.

## Capabilities và heartbeat

Đặt `I = /internal/workers/{id}`.

| Method / path | Request | Response / điều kiện |
| --- | --- | --- |
| GET `I/identity` | Bearer | Identity của đúng worker. |
| POST `I/capabilities` | cpuCores,memoryMb,os,docker,supportedLanguages,maxConcurrentMatches,maxBotProcesses,workerVersion,protocolVersion | Session: epoch, heartbeatIntervalSeconds=5, offlineAfterSeconds=30. |
| POST `I/heartbeat` | epoch,sequence,activeMatches,freeSlots,activeBotProcesses,cpuUsage,memoryUsage,diskFreeMb,cacheSizeMb,cacheHits,cacheMisses | Worker View có trạng thái và slot effective. |

Capabilities tạo epoch startup mới và xóa mẫu heartbeat cũ. supportedLanguages gồm PYTHON/CPP/JAVA, JAR dùng runtime JAVA; số bot processes phải ít nhất hai lần max matches. Heartbeat sequence tăng trong cùng epoch; packet lặp/cũ không làm mới thời gian sống. Sai epoch trả STALE_WORKER_SESSION.

CPU/RAM là phần trăm finite trong 0–100; thông số tài nguyên là số hợp lệ theo record validation. Central còn giới hạn budget, reservation, approval/trust/group. Worker tự báo freeSlots không được ghi đè slot đã đặt ở Central.

## Claim và artifact

POST `I/jobs/claim` nhận Claim: `requestId`, `epoch`, `availableSlots`, `cachedArtifacts`, `gameCoreVersion`, `ruleSetVersion`, `protocolVersion`. requestId làm khóa idempotency trong phạm vi worker; không đổi body rồi dùng lại cùng ID.

Response Batch có `jobs`, `occupiedSlots`, `effectiveFreeSlots`, `serverNow`. Mỗi Assignment gồm `jobId`, `executionId`, `workerId`, `epoch`, `slot`, `status`, `leaseUntil` và `match` Spec:

- matchId, tournamentId, botA/botB với artifactId/artifactHash/language/runtime;
- gameCoreVersion, ruleSetVersion, seed;
- turnTimeoutMs, matchHardTimeoutMs, maxTurns, minimumTrust.

Các ID/hash/spec được Central cấp và giữ bất biến. Claim chỉ lấy job tương thích policy, group, trust, ngôn ngữ và capacity; không có API để agent tùy chọn giải riêng ngoài assignment.

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
- [Agent sources](../../worker/src/main/java/com/rpsbattle/remote/), [build version](../../worker/build.gradle).
