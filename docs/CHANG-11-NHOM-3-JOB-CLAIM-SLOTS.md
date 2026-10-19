# Chặng 11 — Nhóm 3: job claim và slot Central

## Đã triển khai

Hàng đợi remote bền vững trên PostgreSQL, API pull theo lô tối đa 16 job/request, slot giữ trên Central và SDK `ClaimClient` trong worker JAR 0.3.0. Worker chủ động gọi ra Central; không mở port. Mỗi job gắn một tournament match thật trong DB và giữ snapshot cấu hình/artifact metadata có hash. Không serialize bot token, workspace source/history, đường dẫn server hoặc account vào payload.

**Phạm vi nhóm 3:** API claim/assigned/decline và admission nội bộ đã hoạt động. Scheduler trận đang chơi vẫn dùng local WorkerManager; chưa có producer tự chuyển trận thật sang remote hoặc vòng lặp agent tự claim. Các phần đó cần tải artifact (nhóm 4), headless execution (nhóm 5) và commit result/replay (nhóm 6). Agent mặc định tiếp tục heartbeat; SDK claim là thành phần để nối execution loop sau. Không có HTTP endpoint cho user/admin tùy ý tạo job hoặc gửi source cho worker.

## Admission và claim

`RemoteMatchQueue.enqueue(Spec)` dành cho caller nội bộ đã chọn route remote và xác minh artifact. Nhóm 3 kiểm tra match/tournament tương ứng, trạng thái PENDING, kiểu ngôn ngữ/runtime/hash, giới hạn thời gian và queue; xác minh file artifact/đóng gói/version download thuộc nhóm 4. Gọi lại cùng match và cùng snapshot trả cùng job ID; sửa snapshot trả `REMOTE_JOB_IMMUTABLE`. Tối đa mặc định 10.000 job QUEUED/LEASED, có thể cấu hình `rps.worker.remote-queue-capacity` 1–100.000.

Queue dùng singleton guard row `FOR UPDATE` và transaction để serialize admission/claim/decline qua nhiều Central dùng chung DB. Không dùng mutex RAM làm khóa chính. DB có unique match/job và các constraint: một active execution/match, một active slot/worker; attempt phải trỏ đúng cặp job/match. Slot active chỉ có ở CLAIMED/RUNNING; historical attempt giữ assigned_slot nhưng active_slot NULL. Claim hiện dùng khóa toàn hàng đợi để ưu tiên tính đúng; chưa phải scheduler cân bằng tải hiệu năng cao của nhóm 9.

Chỉ worker APPROVED, TRUSTED/COMMUNITY, protocol tương thích và heartbeat còn mới, Docker/runtime phù hợp mới nhận job. SQL lọc cả hai ngôn ngữ, game-core/ruleset và mức tin cậy. TRUSTED job chỉ đến TRUSTED worker; COMMUNITY job có thể đến COMMUNITY hoặc TRUSTED. FIFO theo created_at/id trong tập tương thích; cachedArtifacts là hint được xác thực định dạng nhưng chưa ưu tiên cache, nên cache miss không bị bỏ đói.

Số slot được cấp là min(availableSlots request, slot Central tính còn, 16). Central lấy max(reservation DB, active matches worker báo) và max(2×reservation, active bot processes worker báo) làm số đã dùng. Vì vậy heartbeat báo rảnh/active=0 hoặc khởi động lại agent không xóa reservation và không cấp vượt cấu hình. Không cộng hai lần reservation đã hiện trong active counts. Dashboard Worker có cột **Slot đang giữ**; effectiveFreeSlots loại các reservation đang giữ.

Đổi capabilities có thể tạo epoch mới nhưng giữ reservation. Không cho giảm capacity xuống dưới các slot đang giữ. Request claim dùng epoch cũ bị từ chối; agent mới có thể đọc assigned và trả lại job chưa chạy bằng credential của chính mình.

## API

Các route dùng worker Bearer credential stateless, ID trong URL phải thuộc credential. Không nhận cookie tài khoản hoặc quyền ADMIN để thay worker. No-store; claim có giới hạn request theo worker (mặc định 120/phút).

- `POST /internal/workers/{id}/jobs/claim`
- `GET /internal/workers/{id}/jobs/assigned`
- `POST /internal/workers/{id}/jobs/{jobId}/decline`

Claim body:

```json
{
  "requestId": "<UUID mới cho một lần pull>",
  "epoch": 1,
  "availableSlots": 2,
  "cachedArtifacts": [],
  "gameCoreVersion": "1",
  "ruleSetVersion": "classic-online-v1",
  "protocolVersion": 1
}
```

Retry cùng request phải dùng nguyên requestId và payload; trả cùng execution IDs, không cấp thêm job. Reuse requestId với nội dung khác trả 409. Empty batch cũng có receipt. Receipt/items lưu bền vững cùng transaction cấp slot; chưa có retention tự động. Khi nối poll loop trong nhóm sau cần chọn tần suất/retention phù hợp, không dùng API này như vòng lặp busy-spin.

Batch trả `jobs`, `occupiedSlots`, `effectiveFreeSlots`, `serverNow`. Mỗi job gồm `jobId`, `executionId`, `workerId`, `epoch`, `slot`, `status`, `leaseUntil` và `match` chứa matchId/tournamentId, botA/botB (artifactId, SHA-256, language, runtime), gameCoreVersion/ruleSetVersion, seed 64-bit, turnTimeoutMs, matchHardTimeoutMs, maxTurns, minimumTrust.

Ví dụ decline body: `{"executionId":"<UUID>","reason":"EXECUTION_NOT_AVAILABLE"}`. Các reason khác: `SHUTDOWN_BEFORE_START`, `ARTIFACT_UNAVAILABLE`. Chỉ được trả job CLAIMED của chính worker; RUNNING không thể trả bằng API này. Trả lại giữ attempt lịch sử, giải phóng slot và đưa job về QUEUED; retry cùng execution/reason thành công, reason khác bị conflict. Lượt claim tiếp theo có execution ID mới. Đây là xác nhận chưa bắt đầu chạy, không phải upload failure/result.

Lease ban đầu ít nhất 60 giây, bình thường hard timeout +30 giây. Lease hết hoặc heartbeat mất **chưa tự requeue/giải phóng slot** trong nhóm 3; tránh duplicate execution. Gia hạn, expiry recovery, crash/network protocol thuộc nhóm 7. `assigned` vẫn giữ lượt đang active để phục hồi; executor sau này phải kiểm tra/start lease hợp lệ trước khi chạy. Retry một receipt cũ có thể trả trạng thái lịch sử; không mặc định coi mọi payload trả lại là job mới để chạy.

## DB, build và SDK

V31 thêm `remote_match_queue_guard`, `match_execution_jobs`, `match_execution_attempts`, `worker_claim_receipts`, `worker_claim_items`; không sửa migration cũ hoặc GameEngine. Mọi reservation/receipt được giữ sau restart Central; telemetry V30 và credential V29 giữ nguyên.

`ClaimClient.request(...)` tạo request ID và copy danh sách cache bất biến. Dùng lại chính request đó khi retry mạng. SDK gửi Bearer credential đến đúng Central origin, kiểm tra worker/epoch/slot/execution/lease trong response, hỗ trợ decline trước start; không log credential hoặc tự chạy job. CLI vẫn hỗ trợ `--once`, `--identity-only`, heartbeat mặc định như nhóm 2.

Build từ root: `server/gradlew.bat -p server bootJar :worker:workerJar`; kiểm thử `:test` và `:worker:test`. Bản tải từ admin chứa worker 0.3.0, có SDK nhưng chưa tự claim/execution.

## Kiểm thử và giới hạn

- **PASS 91 backend**, 0 skipped: RemoteJobClaimTest/Postgres mỗi lớp 12 ca (gồm 5 contract danh tính kế thừa), WorkerHeartbeatTest/Postgres mỗi lớp 11 ca, 26 Flyway migrations, 4 AccountAuthorization, 7 AdminConsoleApi, 3 OperationsSecurity, 5 local WorkerManager.
- **PASS 8 worker**, 0 skipped: claim SDK qua HTTP, retry cùng request, cache snapshot bất biến, chặn assignment sai worker; cùng các ca startup/heartbeat/resource cũ.
- **PASS 125 client/admin**, 0 skipped. Build Central/worker JAR PASS.
- Backend thử 8 request song song từ 2 worker, mỗi worker 2 slot: chỉ 4 execution khác nhau, không trùng slot/job. Có kiểm tra DB unique/check bằng SQL sai, snapshot bất biến, queue đầy, cache miss FIFO, match đã kết thúc không được claim, trust/ngôn ngữ, request conflict, epoch cũ, decline sở hữu/idempotent và giữ reservation sau lease hết.
- Native integration sử dụng worker JAR heartbeat thật và API claim trên PostgreSQL. Job protocol fixture riêng được chèn SQL với metadata artifact tổng hợp; không tải artifact hoặc chơi trận. Các assertion claim/retry/slot/agent restart/decline đã đạt. Dọn fixture ban đầu gặp 409 do bản SQL chưa có trong snapshot RAM; đã hủy riêng fixture qua SQL rồi restart Central để nạp lại. Không thay đổi trận của người dùng.
- **PASS sau restart Central:** lịch sử receipt/decline giữ nguyên, job fixture đã hủy, không còn slot bị giữ; đọc lại E2E giải đấu chặng 10 vẫn đúng lịch/standings/replay/frozen artifact và audit cũ.
- Đã backup DB/bot/replay vào `backups/before-stage11-claims-v31` trước upgrade. Các script/state/credential native ignored, không commit.
- **SKIPPED** browser trực quan (local browser đang bị tắt), Docker image build, máy vật lý thứ hai và multi-worker load benchmark. Chưa có tuyên bố end-to-end battle phân tán.

Không sửa GameEngine, RuleSet, combat, tournament scheduler hoặc local WorkerManager. Nhóm 4 tiếp theo làm phân quyền download, hash và cache artifact; nhóm 5–7 nối thực thi/result/recovery. Không dùng queue như producer production trước khi những phần đó hoàn chỉnh.

## File thêm/sửa

- Backend: V31, RemoteMatchQueue, WorkerTelemetryService, WorkerSecurityConfig, RemoteWorkerController.
- Worker: ClaimClient, CentralClient, WorkerApplication, build.gradle.
- Admin: workers.js (cột reservation).
- Tests: RemoteJobClaimTest/Postgres, ClaimClientTest, WorkerRegistrationTest (route chưa triển khai vẫn deny), FlywayMigrationTest.
- README và tài liệu nhóm 3 này.
