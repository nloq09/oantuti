# Phân quyền, secret và xử lý sự cố

[Mục lục](../README.md) · [Người dùng](users.md) · [Worker](workers.md) · [Backup](../technical/backup-recovery.md)

## Quyền được kiểm tra ở server

| Danh tính | Phạm vi |
| --- | --- |
| Khách | Chơi mode được phép, xem dữ liệu công khai; không quản lý bot/giải/tài khoản. |
| USER | Bot/profile/history của mình, đăng ký giải đủ điều kiện, tạo và quản lý giải mình sở hữu. |
| Chủ giải | Là USER có ownership; không phải một role enum riêng, không thi đấu trong chính giải mình tổ chức. |
| ADMIN | Khu quản trị và quyền quản lý được service cho phép; vẫn có guard trạng thái/revision/deadline. |
| Remote worker | Bearer credential riêng, chỉ internal routes và execution được giao; không dùng session ADMIN để chạy agent. |

Account role/status/authVersion được kiểm tra lại từ DB. Disable/đổi mật khẩu làm phiên cũ bị từ chối khi kiểm tra, DB lỗi không mở quyền mặc định. Seat token chỉ là capability cho một ghế phòng, không cấp quyền tài khoản/source/giải. Logout không tự thu hồi mọi seat/socket đã mở; rời phòng nếu muốn chấm dứt quyền điều khiển đó.

## Session, CSRF và origin

Đăng nhập dùng cookie JSESSIONID; đổi session ID khi login. Prod: Secure, HttpOnly, SameSite=Lax, timeout 30 phút. Các mutation tài khoản/bot/giải/admin yêu cầu CSRF lấy từ `/api/auth/csrf`; lấy lại token sau login vì session/CSRF được rotate. Không lưu password hay session token vào localStorage.

Phục vụ client/API/WSS cùng origin. WebSocket `/ws/game` không mở origin wildcard; quyền ghế vẫn phải kiểm tra trong message. CSP hạn chế script/kết nối cùng origin; không bật unsafe-eval hoặc CORS `*` để chữa lỗi deploy. Chỉ tin proxy thuộc `TRUSTED_PROXY_REGEX`; không tin X-Forwarded-For do client tự gửi.

## Giữ kín secret và bài dự thi

- Không đưa `.env.production`, database dump, session cookie, registration token, credential hoặc worker-state lên Git/ảnh chụp/log hỗ trợ.
- Token worker chỉ trả raw một lần; DB lưu hash. Token bootstrap khác credential dùng dài hạn. Thu hồi token không tự thu hồi những máy đã đăng ký; revoke credential máy nếu cần.
- TRUSTED/COMMUNITY là ranh giới cho việc giao artifact riêng. Docker không ngăn chủ máy worker đọc artifact trên host; chỉ giao máy và người vận hành có mức tin cậy phù hợp.
- Bot sandbox không có network, non-root, read-only và có giới hạn; Central được opt-in quyền Docker socket để tạo sandbox. Quyền socket tương đương khả năng kiểm soát host, nên dùng host/Engine dành riêng.
- Source/artifact/replay đi qua kiểm tra quyền, hash/path/assignment; không mở cả thư mục data thành static public.

## Rate limits

Bucket lưu RAM theo instance. Với multiplier mặc định 1, POST login 30/phút, register 10/phút, upload bot 20/phút, tạo phòng 60/phút, tạo giải 20/phút, join 120/phút và validate bot tài khoản 6/phút theo actor; lớp IP có quota gấp đôi. Login sai có bucket 10 lần/15 phút theo IP. 429 trả Retry-After; đọc header và chờ, không retry dồn.

Worker register giới hạn 10/phút/IP, claim 120/phút/worker. WebSocket có hạn toàn socket/IP và bucket loại message; xem [WebSocket](../technical/websocket.md). Đây không phải WAF/DDoS protection hay quota phân tán. Nhiều người dùng chung NAT có thể chung bucket IP.

## Khi nghi ngờ lộ quyền hoặc artifact

Ghi thời gian, ID, request ID và phạm vi ảnh hưởng; giữ log/audit/backup có kiểm soát. Với máy worker, disable để fence việc đang chạy, rồi revoke credential khi xác minh cần cắt quyền lâu dài. Với user, disable đúng tài khoản; không xóa lịch sử. Token chưa dùng bị lộ thì revoke token. Thay secret tại nơi phát hành và cập nhật dịch vụ liên quan; sửa env không tự đổi password DB đang tồn tại.

Kiểm tra các execution/result đang chờ, checksum và audit trước khi resume. Dữ liệu đã tải sang máy không tin cậy không thể bị “thu hồi byte” bằng một nút. Không công bố source/secret trong lý do audit. Chưa có UI xoay mọi secret tự động, distributed rate limiter hoặc chứng nhận pentest; cần vận hành hạ tầng phù hợp.

## Nguồn đối chiếu

- [HTTP security](../../server/src/main/java/com/rpsbattle/auth/SecurityConfig.java), [session filter](../../server/src/main/java/com/rpsbattle/auth/SessionAccountFilter.java), [rate limits](../../server/src/main/java/com/rpsbattle/operations/RequestLimitFilter.java).
- [Worker security](../../server/src/main/java/com/rpsbattle/worker/remote/WorkerSecurityConfig.java), [identity](../../server/src/main/java/com/rpsbattle/worker/remote/WorkerIdentityService.java), [sandbox](../../server/src/main/java/com/rpsbattle/worker/).
