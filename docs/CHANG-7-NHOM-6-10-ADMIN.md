# Hoàn thiện quản trị — Nhóm 6–10

Tiếp nối [nhóm 1–5](CHANG-7-NHOM-1-5-ADMIN.md), giao diện `/admin/` có đầy đủ quản lý giải đấu, yêu cầu xem xét trận, kho replay, audit, sức khỏe hệ thống và hồ sơ quản trị. ADMIN đăng nhập vào quản trị; các URL game chuyển về quản trị theo yêu cầu giao diện đã thống nhất. USER và khách vẫn dùng giao diện game hiện có.

## Nhóm 6: giải đấu

Danh sách có tìm kiếm tên/chủ giải, thể thức, trạng thái, ngày và phân trang. Chi tiết gồm tổng quan, participants/đơn đăng ký, giai đoạn, trận, bracket, xếp hạng, lịch, tranh chấp, audit và thao tác quản trị. Các tab hỗ trợ phím mũi tên, Home/End và bookmark bằng hash.

Mở/đóng đăng ký, hoàn tất check-in, bắt đầu, tạm dừng, tiếp tục, chuyển vòng, hoàn tất, hủy giải và duyệt/từ chối đơn dùng workflow hiện có. Loại participant chỉ khi giải tạm dừng/cần recovery và participant không có trận đang chạy. Tất cả thay đổi cần reason, revision hiện tại và CSRF; server kiểm tra lại trạng thái, artifact, quyền và ghi audit. Revision cũ trả 409; client không tự gửi lại thao tác thay đổi.

Không có thao tác sửa engine, tự tính người thắng, xóa lịch sử hoặc sửa cấu trúc giải khi đang chạy. Các giai đoạn hiển thị metadata hiện có; không tạo thêm thể thức chưa được backend hỗ trợ.

## Nhóm 7: tranh chấp và replay

Tranh chấp được dựng từ `MATCH_REVIEW_REQUESTED` và các quyết định audit của giải, dùng cùng nguồn kết quả hiện có. Chủ giải gửi yêu cầu xem xét từ workflow giải; ADMIN nhận xử lý với danh tính phiên hiện tại, giữ kết quả, từ chối, thử lại trận lỗi hạ tầng, sửa quyết định hoặc loại participant khi trạng thái cho phép. Không có phân công cho một role trọng tài riêng vì hệ thống hiện chỉ có USER/ADMIN.

Các trạng thái: OPEN, UNDER_REVIEW, RESOLVED, REJECTED. Yêu cầu đóng hoặc bị thay thế không nhận thêm quyết định. Override không sửa replay gốc và không được viết lại nhánh loại trực tiếp đã bắt đầu ở vòng sau. Retry chỉ dành cho trận FAILED phù hợp; lỗi bot đã được xử thua không tự thử lại.

Kho replay có tìm kiếm và lọc ngày/giải/trạng thái metadata. `VALID/INVALID/MISSING` ở danh sách phản ánh metadata đã lưu; trang chi tiết kiểm tra file hiện diện và nút **Kiểm tra integrity** đọc/kiểm tra archive bằng cơ chế SHA-256 và replay hiện có. File thiếu/hỏng trả mã lỗi an toàn, không mở đường dẫn tùy ý hoặc tự tái tạo kết quả. Khôi phục file phải dùng quy trình backup/restore; không có nút sửa replay.

## Nhóm 8: audit và hệ thống

Audit chỉ đọc, phân trang/lọc theo actor, action, resource, kết quả, ngày và nội dung tìm kiếm. Hợp nhất audit user/bot, audit giải và journal thao tác HTTP quản trị. Audit domain được ghi trong transaction thay đổi tương ứng. HTTP bị từ chối lưu method, nhóm resource, UUID an toàn, trạng thái và actor; không đọc body/query để tránh lưu mật khẩu, cookie, token hoặc source.

`role_source=AT_REQUEST` là role ghi nhận khi request xảy ra. `CURRENT_ACCOUNT` là role hiện tại của actor trên audit domain cũ, không khẳng định role lịch sử. Actor hệ thống có thể trống. Không có API sửa/xóa audit. Đây là tính bất biến qua API ứng dụng; không thay thế quyền DBA hoặc cơ chế lưu trữ chống sửa bên ngoài.

V16 thêm `admin_operation_events`, không sửa V1–V15. Khi DB tạm lỗi, journal HTTP giữ tối đa 100 mục trong RAM và thử ghi lại mỗi 10 giây; đầy sẽ bỏ mục cũ nhất. Không bảo đảm bền vững qua crash khi DB đang lỗi. `pendingAudit` và cảnh báo giúp người vận hành nhận biết tình trạng này.

System hiển thị PostgreSQL, Docker Linux, worker pool cục bộ, queue, WebSocket, phòng hoạt động, bộ nhớ JVM và khả năng đọc/ghi/dung lượng bot/replay storage. Docker probe chỉ chạy `info` và `ps` với label ứng dụng/instance, timeout hữu hạn, cache 15 giây; không liệt kê container ngoài phạm vi hoặc chạy lệnh quản trị container.

Queue hiển thị tối đa 200 mục. RESERVED là suất giữ cho lịch chưa giao worker; thời gian là lịch dự kiến, không phải heartbeat. Hiện có một worker pool local, không giả danh sách máy worker phân tán. Không lấy được số đo thì hiển thị chưa xác định.

Cảnh báo gồm DB/Docker/storage chưa sẵn sàng, queue dùng ít nhất 80%, worker draining, giải cần recovery, replay invalid, trận chạy quá 15 phút, lỗi bot tăng trong 15 phút gần đây và audit chờ ghi. Chỉ DB mất kết nối làm overall DOWN; các cảnh báo khác làm DEGRADED. Khi DB lỗi ngay ở bước xác thực tài khoản, request có thể trả lỗi dịch vụ trước khi dựng health snapshot. Dashboard, System và menu cảnh báo polling 10 giây, dừng khi rời trang và không gửi request khi tab ẩn. Không có email/push hoặc subscription WebSocket admin riêng.

## Nhóm 9: giao diện và tài khoản

Theme lâu đài, xanh đậm và vàng đồng bộ game. Màn hình nhỏ thu menu mặc định, mở bằng nút có aria-expanded/controls, Escape đóng menu. Bảng chỉ cuộn trong vùng riêng, form co theo chiều rộng; notification không vượt viewport. Có skip link, focus rõ, table headers, tab keyboard, dialog có nhãn/lý do bắt buộc, chặn gửi lặp và thông báo kết quả. Khi đóng dialog, focus trở về nút hoặc nội dung sau khi tải lại.

Hồ sơ `/admin/profile.html` dùng API tài khoản hiện có để sửa tên hiển thị và đổi mật khẩu; không có quyền tự sửa role. Đổi mật khẩu thu hồi tất cả phiên và yêu cầu đăng nhập lại. ADMIN không cần đi qua trang game để dùng hồ sơ.

## API bổ sung

- `GET /api/admin/tournaments` và `GET /api/admin/tournaments/{id}`.
- `POST /api/admin/tournaments/{id}/actions/{action}` với `{revision,reason,registrationId?}`.
- `GET /api/admin/disputes` và `GET /api/admin/disputes/{id}`.
- `POST /api/admin/disputes/{id}/{assign|uphold|reject|retry|override|disqualify}` với `{revision,reason,winnerParticipantId?,participantId?}`.
- `GET /api/admin/replays` và `GET /api/admin/replays/{historyMatchId}`.
- `POST /api/admin/replays/{historyMatchId}/validate` với `{reason}`.
- `GET /api/admin/audit` và `GET /api/admin/system`.

Các endpoint trên yêu cầu ADMIN thực từ tài khoản trong DB; authority tự khai không cấp quyền. Mutation cần CSRF. Response dùng no-store; không trả password hash, token seat/bot, source path hoặc raw Docker output. Paging 1–100 dòng, page tối đa 100.000; filters dùng bind parameters và kiểm tra giá trị.

## Nhóm 10: kiểm thử và nâng cấp

Chạy từ `server/` bằng JDK 21/24 với Docker Linux đang hoạt động:

```powershell
.\gradlew.bat test bootJar --no-daemon --console=plain
```

Từ thư mục gốc:

```powershell
node --test client/tests/*.test.js
```

Test bổ sung bao phủ revision/reason/CSRF, guest 401, USER 403, authority ADMIN giả, lifecycle, quyết định tranh chấp đóng, replay integrity, không ghi payload bí mật, Docker unavailable, JDBC PostgreSQL/union audit thật và nâng V15 lên V16 giữ dữ liệu.

Native E2E sử dụng tài khoản/giải/bot thử riêng: tạo giải → thêm hai bot → mở/đóng đăng ký → start/pause/resume → Docker chạy trận → review → nhận xử lý → giữ kết quả → chặn quyết định lặp → validate replay → đối chiếu audit và replay gốc. Kiểm tra trình duyệt: login ADMIN, danh sách/chi tiết giải và tab bằng bàn phím, hồ sơ quản trị, modal hủy/xác nhận có reason, đóng review thật kèm toast/audit, menu mobile và bảng tại 375px. Không đổi mật khẩu qua tự động hóa trình duyệt; hành vi đổi mật khẩu và thu hồi phiên được kiểm thử ở backend. Đã đối chiếu giải cũ sau restart: ranking, luật, champion, audit và quyết định DQ giữ nguyên. Backup DB cùng toàn bộ bot/replay đã tạo trước nâng V16; fixtures, mật khẩu và backup không đưa vào Git.

Kết quả cuối ngày 15/10/2026: **493 backend pass, 0 fail/error/skipped; 38 client pass, 0 fail/skipped; 55 module JavaScript kiểm tra cú pháp thành công; bootJar thành công.** Full regression bao gồm PostgreSQL/Testcontainers, Docker Python/C++/Java/JAR, replay thiếu/hỏng, round robin và knockout 16/32 bot. Lần chạy đầu có timeout container PostgreSQL; chạy riêng migration và sau đó toàn bộ regression đều qua. Các test Testcontainers tự skip nếu không có Docker; lần kiểm tra này Docker hoạt động và không có test bị skip.

## Cấu trúc thay đổi

- `client/admin/`: tournaments/tournament-detail, disputes/dispute-detail, replays/replay-detail, audit, system, profile.
- `client/js/admin/`: catalog/tabs, tournament-model/tournaments, dispute-model/reviews, operations, profile; dùng lại shell, components, API, viewer và renderer bracket.
- `server/.../admin/`: catalog, disputes, replay, audit, operation journal/filter, system và Docker probe.
- Controllers: `AdminTournamentsController`, `AdminReviewsController`, `AdminOperationsController`; cập nhật System controller.
- Workflow giải hiện có thêm audit cho lifecycle admin, nhận/từ chối review và đóng review khi DQ; worker queue thêm snapshot chỉ đọc.
- V16 cùng tests admin/Flyway/JPA và client model/navigation. `GameEngine`, `RuleSet`, giao thức bot và thuật toán xếp lịch không sửa.
