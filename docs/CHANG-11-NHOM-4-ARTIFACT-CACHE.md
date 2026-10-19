# Artifact download, SHA-256 và cache worker

Worker 0.4.0 có thể chuẩn bị artifact cho execution đã được giao. Agent mặc định vẫn chỉ heartbeat; chưa tự claim, chạy trận hoặc gửi result. Scheduler hiện tại tiếp tục dùng worker local. Schema giữ V31, không thay đổi GameEngine, RuleSet hoặc luật chiến đấu.

## Tải artifact theo quyền

`GET /internal/workers/{workerId}/artifacts/{artifactId}?executionId={executionId}` yêu cầu Bearer credential riêng của đúng worker. Cookie đăng nhập/admin không thay thế credential.

Central kiểm tra worker APPROVED, trust còn phù hợp, execution thuộc worker, CLAIMED/RUNNING, job LEASED và lease còn hạn theo giờ server. Artifact phải nằm trong botA/botB của snapshot có SHA-256 hợp lệ. Registry phải có đúng một submission LOCKED cùng giải, cùng artifact hash, ngôn ngữ và runtime. Không cho duyệt artifact tùy ý.

Response là `application/octet-stream`, `Cache-Control: no-store`, có Content-Length, X-Artifact-SHA256, X-Artifact-Language và X-Artifact-Format. Central đọc tối đa 8 MiB, kiểm tra SHA-256 trước khi trả bytes, chặn symlink. Response/error không có source workspace, build log hoặc đường dẫn máy chủ.

Artifact hiện được phục vụ từ kho coding đã khóa:

- Python: file runtime `main.py` đã khóa; đây là source cần thiết cho Python, máy worker phải được tin cậy theo chính sách giải.
- C++: binary build, đang được kho hiện tại lưu dưới tên `main.cpp`.
- Java: JAR build, đang lưu dưới tên `Main.java`; bài nhập JAR dùng `bot.jar`.

Tên lưu nội bộ không phải loại nội dung. Worker nhận opaque `artifact.bin`, không giải nén hay chạy trên host. Endpoint không tải source đang sửa và không cung cấp mọi bot upload thông thường; cần registry submission LOCKED của giải.

## Cache riêng tư

Mặc định `worker-state/cache`, dung lượng 20 GiB. Thay bằng `--cache-dir=PATH`, `--cache-mb=20480` (MiB), hoặc WORKER_CACHE_DIR/WORKER_CACHE_MB. Giới hạn mỗi artifact 8 MiB. Không đưa cache/credential vào Git hoặc chia sẻ cho tài khoản khác.

Cache dùng thư mục SHA-256, artifact.bin và manifest.json chứa hash, size, language, runtime, format, lastUsedAt. Thư mục/file giới hạn quyền owner bằng ACL Windows hoặc POSIX; lock ngăn hai agent cùng dùng cache. Khi khởi động, đọc lại metadata, dọn entry tải dở; kiểm tra lại SHA-256 ở mỗi acquire, không tin manifest đơn thuần.

Cache miss: tải có giới hạn thời gian/kích thước, tính hash trong lúc stream vào file tạm, fsync, xác minh hash rồi atomic move và ghi manifest. Hash sai, file rỗng/quá lớn hoặc tải đứt không tạo entry sử dụng được. Không theo HTTP redirect. Cache hit xác minh lại file; file bị sửa được loại và tải lại nếu không đang được giữ.

LRU giải phóng entry ít dùng nhất khi cần chỗ; mọi `ArtifactCache.Handle` giữ pin cho đến close. Không xóa artifact đang được tác vụ giữ; hết chỗ do pin thì báo CACHE_CAPACITY_PINNED. Heartbeat báo dung lượng cache thực tế (MiB nguyên). Hit/miss/eviction là thống kê của tiến trình, không phải metric tích lũy bền vững.

## Cách kiểm tra

Sau khi agent đã dừng, dùng cùng Central và state directory:

```sh
java -jar rps-worker.jar --central=https://central.example --state-dir=./worker-state --prefetch
```

Với localhost/LAN HTTP, thêm `--allow-insecure-http`. `--prefetch` chỉ đọc assigned execution còn lease rồi tải hai artifact; không tự nhận job hoặc chạy bot. Không có assignment thì trả số lượng 0. SDK `ArtifactClient.acquire(executionId, ArtifactRef)` trả handle; tác vụ tương lai phải giữ cả hai handle đến khi trận kết thúc. `ArtifactCache.hashes()` có thể dùng làm claim hint; cache hit không thay thế quyền server hay reservation.

## Kiểm chứng và giới hạn

- 97 kiểm thử backend PASS: artifact trên H2/PostgreSQL thật, auth/claim/heartbeat, migration, tài khoản/admin và worker local; 0 fail, 0 skip.
- 14 kiểm thử worker PASS: cache hit sau restart, hash/tamper, LRU/pin/quota, tải đứt, lock/quyền file, HTTP và redirect; 0 fail, 0 skip.
- 125 kiểm thử client PASS; setup admin đã cập nhật tham số cache và phạm vi prefetch.
- Native integration PASS: JAR và PostgreSQL đang chạy, artifact Python LOCKED thật; tải đúng assignment, hash đúng, cache hit sau restart tiến trình, cache bị sửa được tải lại, artifact tùy ý/assignment đã decline bị từ chối. Fixture được hủy, không có trận remote được chạy.
- Sau restart Central: cache/hash và lịch sử DECLINED còn đúng, slot được trả; E2E giải cũ xác nhận standings, replay, frozen artifact và audit được giữ nguyên.
- Kiểm tra giao diện trực quan SKIPPED: công cụ trình duyệt bị tắt trong Work settings.

Đây là nhóm 4. Headless execution, upload result/replay, gia hạn/thu hồi lease, recovery và điều phối trận remote đầy đủ thuộc các nhóm sau. Không tuyên bố đã chạy trận phân tán từ luồng prefetch.
