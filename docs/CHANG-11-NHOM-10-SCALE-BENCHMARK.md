# Kiểm thử phân phối, metrics và quy mô 4.000 người

Nhóm 10 nâng giới hạn từ 2.000 lên 4.000 cho giải coding Loại trực tiếp/Swiss, đồng bộ API, PostgreSQL và client. Round Robin/các thể thức tạo nhiều cặp vẫn tối đa 32; không mở giới hạn hàng triệu trận. GameEngine và luật chơi giữ nguyên. Worker 0.10.0 tương thích protocol 1.

## Benchmark 4.000 người có thể chạy lại

Từ thư mục project, với Java và Docker sẵn sàng:

```powershell
.\server\gradlew.bat -p server remoteLoadTest --console=plain
```

Task riêng dùng PostgreSQL 16 Testcontainer và được loại khỏi lượt `test` thường bằng tag `remote-load`. Không ghi vào database đang phục vụ người dùng. Có thể chỉ chạy case tải:

```powershell
.\server\gradlew.bat -p server remoteLoadTest --tests '*RemoteDistributedLoadTest.fourThousandEntrantsComplete3999ValidatedReceiptsWithTenWorkersAndEightySlots' --console=plain
```

Kết quả tại build directory của Gradle: `reports/remote-load.json` và `reports/tests/remoteLoadTest`. Trên Windows, dùng `printBuildDir` để tìm thư mục build ngoài OneDrive.

Đây là **benchmark control plane với agent mô phỏng trong cùng JVM**, không phải 10 máy Docker độc lập. Hàng đợi, claim/slot/group/trust, start, validate replay, transaction nhận kết quả và idempotency đều là service thật trên PostgreSQL. Mỗi trận dùng game core tạo replay hợp lệ **một lượt**; không tải artifact hoặc chạy bot trong sandbox. Harness áp dụng kết quả đã được xác nhận vào cây đấu, không đo projection/history/WebSocket của 3.999 trận. Phần Docker + projection/replay đầy đủ được kiểm tra riêng bằng trận thật.

Harness dùng hard timeout 5 phút cho mỗi assignment để việc validate tuần tự tám replay trên một agent mô phỏng không hết lease khi host phát triển bận; không dùng timeout này để suy ra thời gian bot thật. Các assertion: 4.000 seat, 4.095 node bracket, 96 BYE, 1.952 trận vòng đầu, tổng 3.999 trận có receipt hợp lệ, 12 vòng, 10 identity × 8 slot, đỉnh reservation 80, không trùng execution/receipt, gửi lại kết quả idempotent, không rò slot và có nhà vô địch thuộc roster. Từng worker đều nhận việc; không yêu cầu số trận bằng nhau vì các vòng cuối thiếu 80 việc song song.

Snapshot kết quả đo và số trận từng worker tại [benchmark JSON](benchmarks/chang11-control-plane-4000.json). Throughput trong đó là **receipt hợp lệ/giây của trận một lượt**, không dùng để ước lượng tốc độ trận 200 lượt, build bot hoặc năng lực Docker của máy khác. Queue wait đo từ enqueue đến trước start của execution, gồm thời gian backlog trong từng vòng.

## Đo trực tiếp worker thật

Script chỉ đăng nhập admin, đọc metrics/worker rồi đăng xuất; không tự tạo, bắt đầu hoặc hủy giải. Đã kiểm tra với Node 24.11.1. Cấp account qua môi trường, không đưa mật khẩu vào lệnh hoặc Git:

```powershell
$env:RPS_ADMIN_USERNAME='ten-admin'
$env:RPS_ADMIN_PASSWORD='mat-khau-cua-ban'
node scripts/remote-worker-benchmark.mjs --central=https://central.example --seconds=60 --interval=5 --output=benchmark-result.json
```

Có thể thêm `--tournament-id=UUID` để scope số job/receipt/wait theo giải. URL không được chứa credential/query/path; HTTP chỉ cho localhost/loopback. Output dùng create-new, không ghi đè file đã có. Script không ghi password, token hoặc cookie vào report. Thư mục state worker vẫn phải giữ riêng tư như trước.

Report lưu thời gian quan sát thực tế, delta receipt/giây, đỉnh slot, p50/p95/p99 của tối đa 1.024 attempt gần nhất, delta expired, cache hit/miss và CPU/RAM theo heartbeat mới của từng worker. CPU/RAM là **số đo toàn host**, không phải CPU riêng của container. Nhiều agent trên cùng máy không được coi là nhiều máy vật lý. Counter reset sẽ để delta không xác định; không biến số đo thiếu thành 0. Global worker/cache/slot vẫn là toàn cụm khi scope queue theo một giải.

## Baseline trên một máy worker

```sh
java -jar rps-worker.jar benchmark --state-dir=./benchmark-state
```

Không cần Central hoặc registration token. Cần Docker Linux và image `python:3.12-slim` đã có; không tự pull. Lệnh giữ state lock, dùng cùng game core, chạy một trận 32 lượt với hai Python bot mẫu tồn tại suốt trận trong sandbox thật, rồi đóng container và xóa file tạm đã tạo. JSON stdout có thời gian, capabilities và host sample; không enrollment/claim/upload.

`measuredConcurrentMatches=1`, `recommendedMatchSlots=1` có chủ đích: lệnh này chỉ chứng minh một trận chạy được. Muốn tăng lên 8 slot, dùng --serve trên máy đích cùng workload thật và đo bằng observer; admin vẫn phải duyệt máy và đặt giới hạn phù hợp. Các giới hạn CPU/RAM, lease, timeout và source privacy vẫn áp dụng.

## Metrics vận hành

`GET /api/admin/workers/metrics?tournamentId=UUID` yêu cầu session ADMIN, không nhận worker credential thay account. Không có secret trong response. Trang quản trị Worker tự làm mới số online/slot/queue, receipt/giây (cửa sổ 60 giây), p95 và cache hit; từng máy có cache hit/miss cùng CPU/RAM.

- Queue wait dùng nearest-rank trên tối đa 1.024 execution attempt gần nhất, gồm attempt retry; không phải toàn bộ lịch sử hoặc cam kết SLA.
- `receiptsPerSecond60s` là số receipt trong 60 giây chia 60, kể cả lúc cửa sổ chưa đầy một phút.
- `infrastructureExpiryRate` là expired attempt / tổng attempt. Bao gồm lease/restart/disable/revoke; không phải tỷ lệ bot chọn sai nước hoặc toàn bộ lỗi thi đấu.
- Cache counters là thống kê process cache gửi theo heartbeat, không lưu hit rate vĩnh viễn qua restart process. Agent cũ chưa gửi counters được đọc là 0; mẫu trùng/out-of-order vẫn không ghi đè heartbeat mới.
- V35 giữ heartbeat cũ, thêm cache_hits/cache_misses mặc định 0 và index cho sample gần nhất/receipt. Không đổi identity/credential, lease hoặc quyền nhóm.

## Phạm vi kiểm chứng

Tải 10 máy vật lý × 8 trận Docker = 160 bot container đồng thời cần cụm máy thật. Chưa chạy cấu hình đó trên host hiện tại; không đánh dấu nó PASS dựa trên mô phỏng. Công cụ và hướng dẫn đã có để chạy trên cụm đích. UI trực quan vẫn SKIPPED khi browser tools đang tắt; API/DOM tests được chạy.

Kết quả đo ngày 20/10/2026:

- **PASS** tải control plane 4.000 người: 3.999 receipt, 12 vòng, 339,21 giây, 11,79 receipt/giây, đỉnh 80 slot, không rò reservation. p50/p95/p99 queue wait: 44.559 / 134.149 / 145.235 ms. Snapshot dùng job hard timeout 300.000 ms.
- **PASS** hai worker Docker thật trên cùng một host nhận hai giải riêng; mỗi giải một receipt/replay, worker ngoài nhóm không nhận việc. Observer 45,10 giây/16 mẫu ghi đỉnh 2 slot, 2 cache hit/2 miss; CPU/RAM là host chung. [Snapshot Docker](benchmarks/chang11-docker-local.json) có percentile của toàn database test local, gồm job lịch sử, không riêng hai trận này. Throughput 2/45,10 là cả cửa sổ quan sát có thời gian chờ.
- **PASS** CLI baseline: hai Python container, 32 lượt, 1.786 ms, một trận đồng thời, sau chạy không còn container benchmark.
- **PASS** native mất mạng: trận 12 lượt hoàn tất cục bộ, giữ READY_FOR_UPLOAD, reconnect gửi lại đúng một receipt. Native crash: dọn orphan theo execution, attempt cũ EXPIRED/WORKER_RESTART, cấp attempt mới, không tạo winner giả, trả hết slot; replay được đọc sau restart.
- **PASS** 29 worker tests; 129 client/admin tests + 2 observer tests; 13 load-suite tests ở lượt đầu và case 4.000 người chạy lại với cấu hình cuối; Central/worker JAR build thành công.
- Backend regression chạy 243 ca: 242 PASS, một assertion migration cũ còn coi 2.001 là ngoài giới hạn. Đã sửa thành 4.001 và chạy lại hai migration liên quan: **2 PASS**, gồm nâng V34→V35 giữ heartbeat. Không sửa constraint để chiều test cũ.
- Lượt tải trung gian gặp hết lease khi host bận; harness đã đổi hard timeout từ 60 giây lên 5 phút và chạy lại PASS. Fixture recovery ban đầu không nhận việc do sample CPU cao; retry có giới hạn khi chưa có assignment đã chạy PASS, giữ nguyên cơ chế nhường tải production.
- **SKIPPED** tải 10 máy vật lý × 8 trận Docker; **SKIPPED** kiểm tra UI bằng browser vì công cụ browser đang tắt. Không suy ra khả năng chạy 160 container từ benchmark JVM hoặc một host.


## File thêm/sửa

- API và lưu trữ: `WorkerMetricsService`, `WorkerGroupController`, `WorkerTelemetryService`, `OwnedTournamentService`, migration V35.
- Worker: `LocalBenchmark`, `WorkerApplication`, `ExecutionBatch`, `CentralClient`, phiên bản/build JAR.
- Client: capacity theo thể thức, danh sách giải, API/trang quản trị Worker và hướng dẫn benchmark.
- Kiểm chứng: `RemoteDistributedLoadTest`, `DistributedCapacityTest`, `WorkerMetricsTest`/Postgres, migration/bracket tests, `LocalBenchmarkTest`, client capacity test và observer tests.
- Vận hành: `scripts/remote-worker-benchmark.mjs`, các snapshot JSON đã loại identity, README và tài liệu Worker.
