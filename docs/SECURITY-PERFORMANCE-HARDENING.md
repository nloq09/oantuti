# Gia cố bảo mật và giảm tải server

## Phạm vi

Rà mã và chạy hồi quy cho session/CSRF, quyền account/owner/admin, upload bot, source workspace, artifact theo assignment/lease, giới hạn request và các đường đọc trạng thái giải. Đây là đợt sửa trong project, không phải chứng nhận an toàn hoặc pentest toàn hệ thống. Không đổi schema, giao thức bot hoặc quyền truy cập hiện có.

## Các thay đổi

1. **Giảm nguy cơ cạn tài nguyên từ capabilities công khai.** Trước đây mỗi ngôn ngữ có thể gọi lại Docker khi daemon/image lỗi, và các request đồng thời không dùng chung kết quả đang kiểm tra. `DockerCapabilities` chỉ cho một lượt probe trên mỗi instance; các reader khác không chờ lượt probe đó và trả unavailable nếu chưa có snapshot còn hạn. Kết quả thành công và thất bại đều có TTL 5 giây. Một lượt kiểm tra daemon một lần và mỗi image khác nhau một lần; JAVA/JAR dùng cùng image. Khi timeout hoặc interrupt, tiến trình probe đang sống được dọn trong `finally`. Không chạy hoặc pull image từ endpoint capabilities.

2. **Giới hạn endpoint xác thực bot.** POST `/api/account/bots/{id}/validate` dùng bucket chung theo tài khoản, không theo ID bot, tối đa 6 lần/60 giây và bucket IP gấp đôi, theo cấu hình multiplier hiện có. Đổi bot ID hay giả `X-Forwarded-For` không tạo quota tài khoản mới. Trả 429 + `Retry-After` trước khi controller thực thi. CSRF, quyền sở hữu và kiểm tra tài khoản vẫn bắt buộc. Đây là giới hạn cho endpoint validate, không phải quota tổng cho mọi đường chạy bot; sandbox và admission vẫn có giới hạn riêng.

3. **Chính sách nội dung client.** CSP chỉ cho script và kết nối cùng origin, chặn object, base URL, nhúng trang trong frame và form gửi sang origin khác. `Referrer-Policy: no-referrer` giảm truyền URL trang sang website ngoài. Không cho inline script hoặc eval; style inline và ảnh data vẫn được phép để tương thích giao diện bàn cờ hiện tại. Không mở CORS/WebSocket origin wildcard.

4. **Giảm đọc lặp trong Match Monitor.** Metadata participant và tên bot được dùng lại trong phạm vi một response. Với 50 trận lặp cùng bot, lookup bot giảm từ một lần cho từng bên của từng trận xuống một lần cho bot đó. Lập index phiên bản từ registrations một lần thay vì quét lại cho mỗi bên. Chỉ đọc metadata của trang sau khi lọc/phân trang; trang rỗng không đọc bot. Cache tên không tồn tại cũng có hiệu lực trong response, và bị bỏ sau response để không giữ tên cũ sau rename.

## Kiểm chứng

- 259 client tests PASS; không thay JS client trong đợt này.
- 81 lượt test backend trong 14 suite PASS, không fail/skip (có các test kế thừa chạy lại giữa suite). Phạm vi: đăng nhập/logout/rotation, thu hồi session sau đổi mật khẩu hoặc khóa tài khoản, CSRF, owner/admin, workspace source riêng tư, artifact đúng assignment/lease/hash, public remote read model, upload/storage/path/sandbox command, WebSocket và giới hạn request.
- Test mới kiểm tra negative TTL và phục hồi Docker, dùng chung runtime Java, 100 reader đồng thời không tạo probe trùng, quota validate không theo bot ID/header giả, header bảo mật trên trang/module và số lần lookup metadata theo trang.
- PostgreSQL Testcontainers chạy các ca public remote, không skip.
- Kiểm tra cuối gồm `git diff --check`, bootJar, khởi động server PostgreSQL, HTTP/compact WebSocket smoke và đối chiếu 51 asset, kiểm tra CSP/Referrer-Policy và guest denial trên bản đóng gói.

## Giới hạn và vận hành

- Chưa chạy browser click/screenshot để xác minh CSP trên mọi trình duyệt; công cụ browser hiện bị tắt. Kiểm thử HTTP không chứng minh JavaScript đã thực thi trong browser.
- Không quét CVE/dependency registry, fuzz toàn bộ endpoint hoặc benchmark tải production. Giảm số probe/lookup là kết quả đo trong test, không phải cam kết số người dùng đồng thời.
- Rate limit vẫn lưu RAM theo instance. Khi scale nhiều Central cần cơ chế quota dùng chung; IP proxy chỉ được tin khi khớp `TRUSTED_PROXY_REGEX` đã cấu hình đúng hạ tầng.
- Kết quả capabilities có thể chậm tối đa một TTL sau khi Docker thay đổi; request đến trong lúc probe đầu tiên đang chạy có thể nhận unavailable và cần kiểm tra lại. Các bước chạy bot vẫn kiểm tra thực thi và không tự bỏ sandbox.
- Các đường monitor còn dựng scalar state của toàn bộ tập trận được phép trước khi lọc/phân trang; đợt này tối ưu metadata trên trang, chưa chuyển toàn bộ aggregation sang SQL.
