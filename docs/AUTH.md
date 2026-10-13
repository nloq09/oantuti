# Account authentication — Chặng 4 Nhóm 2–3

Project dùng **HTTP session cookie HttpOnly**, không dùng JWT. Tài khoản được lưu trong bảng PostgreSQL `users`; session hiện ở RAM của server, hết hạn sau 30 phút không hoạt động và mất sau khi server restart. Người dùng đăng nhập lại, tài khoản không mất. Không có tài khoản/password ADMIN mặc định.

## Chính sách input

- Username: 3–32 ký tự ASCII `a-z`, `0-9`, `_`; strip khoảng trắng ngoài, normalize lowercase với `Locale.ROOT`, unique không phân biệt hoa/thường.
- Email: strip và lowercase toàn bộ, tối đa 254 ký tự, kiểm tra cấu trúc email cơ bản, unique. Chưa có xác minh email hoặc chức năng quên mật khẩu.
- Display name: 1–60 ký tự sau strip, không chứa control characters.
- Password: tối thiểu 12 Unicode code points, tối đa 72 **UTF-8 bytes**, không được toàn khoảng trắng. Giữ nguyên password, không strip/normalize. Giới hạn bytes tránh BCrypt cắt/trùng password dài; chưa bắt buộc mẫu chữ hoa/ký tự đặc biệt.
- Mật khẩu băm bằng BCrypt cost 12 với salt ngẫu nhiên. DTO không trả password/hash, session ID hoặc internal token. Các request DTO và domain User có `toString` redacted.
- Register luôn tạo role USER/status ACTIVE, không nhận role/status từ client. Login sai username/password, disabled account đều trả chung `401 INVALID_CREDENTIALS`.

## API và CSRF

Client cùng host với server. Trước mỗi POST auth, gọi:

```http
GET /api/auth/csrf
```

Response có `token`, `headerName` (mặc định `X-CSRF-TOKEN`); browser giữ session cookie. Gửi token trong header tương ứng, không đưa token/password vào URL. Phải lấy token mới sau login/logout vì session/CSRF được đổi hoặc hủy. Responses auth dùng `Cache-Control: no-store`.

```http
POST /api/auth/register
Content-Type: application/json
X-CSRF-TOKEN: <token vừa lấy>
```

```json
{
  "username": "player_a",
  "email": "player_a@example.com",
  "password": "Replace with your own password",
  "displayName": "Player A"
}
```

Trả 201 với `id`, `username`, `displayName`, `role`; **không tự đăng nhập**. Input sai trả 400, trùng username/email trả 409 `ACCOUNT_ALREADY_EXISTS`. DB unique constraint bảo vệ cả đăng ký đồng thời, không chỉ pre-check.

```http
POST /api/auth/login
```

Body `username`, `password`; header CSRF như trên. Trả 200 với thông tin tài khoản an toàn, ghi `lastLoginAt`, đổi session ID chống session fixation, lưu SecurityContext server-side. Không trả bearer token.

```http
GET /api/auth/me
```

Anonymous trả `{"authenticated":false}`. Session hợp lệ trả `authenticated:true` và `user` với 4 field DTO trên. Identity lấy từ Spring Security principal, không dùng userId tự khai ở request. Nhóm 3 kiểm tra status, authVersion và role DB trên mỗi request có account session: account disabled/mất hoặc session version cũ sẽ bị hủy; role được cập nhật từ DB trước khi kiểm tra quyền. Principal lưu trong session không chứa password/hash.

```http
POST /api/auth/logout
```

Body `{}`, cần CSRF. Hủy session và SecurityContext, hết hạn cookie JSESSIONID; replay cookie cũ không đăng nhập được. GET logout không được hỗ trợ.

## Cookie và production

- `HttpOnly`, `SameSite=Lax`, cookie-only session tracking; không truyền session ID qua URL.
- Profile prod bật `Secure`; browser chỉ gửi cookie qua HTTPS. Reverse proxy/forwarded headers và HTTPS sẽ được hoàn thiện ở nhóm deployment. Không chạy profile prod qua HTTP rồi coi login thất bại là lỗi mật khẩu.
- CSRF bật cho mọi mutation POST/PATCH/DELETE `/api/auth/**`, `/api/account/**`, `/api/admin/**`; thiếu/sai token trả 403 `CSRF_INVALID`.
- CORS không mở wildcard hoặc cho phép frontend cross-origin. Giữ client/server cùng origin.
- Chưa có brute-force rate limit; phải thêm ở Nhóm 9 trước khi coi hệ thống đã harden production.

## Account/Profile và Admin — Nhóm 3

- `GET /api/account` hoặc `/api/account/profile`: profile của session hiện tại, gồm ID, username, email, displayName, role/status và timestamps; không có hash/authVersion/token. Chưa có stats/history vì chúng được làm ở Nhóm 5.
- `PATCH /api/account/profile` body `{"displayName":"Tên mới"}`: chỉ đổi display name; gửi thêm id/username/email/role/passwordHash không thay đổi các field đó. Không nhận userId để chọn profile.
- `POST /api/account/change-password` body `currentPassword`, `newPassword`: kiểm tra password hiện tại, policy mới, không cho giữ nguyên password. Thành công tăng authVersion trong DB, hủy phiên hiện tại và trả `changed:true, authenticated:false`; **mọi phiên khác phải đăng nhập lại** khi gửi request tiếp theo. Password cũ không đăng nhập được nữa.
- `GET /api/admin/users?page=0&size=20`: ADMIN, page >=0, size 1–100; danh sách DTO không lộ hash/version. `total` là tổng số tài khoản.
- `POST /api/admin/users/{id}/disable` và `/enable`: ADMIN, cần CSRF. Chỉ đổi status và tăng authVersion khi status đổi; không xóa account/history. Bật lại không làm cookie cũ hợp lệ. Không cho admin tự disable chính mình; ID không tồn tại trả 404 `USER_NOT_FOUND`.
- `GET /api/admin/system`: ADMIN; trả số users, phòng đang hoạt động, giải RUNNING và thời điểm kiểm tra; không trả secrets/command/path. Metrics sâu và bot failure counters ở Nhóm 9.
- Authorization ở application service kiểm tra role/status DB, không dựa vào role/userId do client khai. Row lock trong transaction bảo vệ cập nhật profile/password/status khỏi ghi đè trạng thái bảo mật mới.
- Nếu DB lỗi khi xác minh session, filter trả 503 `DATABASE_UNAVAILABLE`, không cho request đi tiếp với quyền cache.

Nhóm 4 đã chuyển bot upload/dùng bot riêng sang account ownership: owner hoặc ADMIN, danh sách từ PostgreSQL, CSRF cho upload/account mutations và mọi request match/tournament có session đăng nhập. Token bot client chỉ dùng nhập artifact cũ, không cấp quyền chơi. Guest vẫn chơi tay/đấu bot sẵn bằng seat token. Tournament creator còn dùng capability tới Nhóm 6. WebSocket game đang mở chưa gắn account với ghế hay tự đóng khi đổi password/disable; phần đó thuộc các nhóm tiếp theo. Xem [BOT-OWNERSHIP.md](BOT-OWNERSHIP.md).

## Tạo ADMIN đầu tiên

Đăng ký tài khoản bình thường, sau đó người quản trị DB chủ động chạy SQL sau qua kết nối PostgreSQL có quyền quản trị, thay username bằng tài khoản đã chọn:

```sql
UPDATE users SET role = 'ADMIN', auth_version = auth_version + 1,
    updated_at = CURRENT_TIMESTAMP
WHERE username = 'your_admin_username' AND status = 'ACTIVE';
```

Kiểm tra đúng một row được cập nhật rồi đăng nhập lại. Không có endpoint public nâng role, không có admin password mặc định hoặc secret bootstrap được commit. Chỉ người có quyền DB dùng thao tác này; API profile không thể nâng quyền.

Account session xác định **ai là user**. Seat token xác định **quyền điều khiển ghế P1/P2**. Session không thay thế seat token; WebSocket account binding sẽ thực hiện ở nhóm authorization.

## Client helper và kiểm tra

`client/js/api/auth-api.js` có `register`, `login`, `currentUser`, `logout`. Helper gửi cookie same-origin, tự lấy CSRF mới cho từng mutation, không ghi password/session/token vào localStorage. Chưa nối vào home/navbar hay thêm trang login/register/profile; đó là Nhóm 8.

Khởi động DB/server theo `CHANG-4-NHOM-1-DATABASE.md`. Với PowerShell, thử API mà không cần giao diện:

```powershell
$base = 'http://localhost:8080'
$csrf = Invoke-RestMethod "$base/api/auth/csrf" -SessionVariable authSession
$headers = @{ $csrf.headerName = $csrf.token }
# Dùng mật khẩu riêng, không ghi thông tin thật vào repository hoặc log.
$account = @{ username='player_a'; email='player_a@example.com'; displayName='Player A'; password='<your password, 12+ characters>' }
Invoke-RestMethod "$base/api/auth/register" -Method Post -WebSession $authSession -Headers $headers -ContentType 'application/json' -Body ($account | ConvertTo-Json)
$csrf = Invoke-RestMethod "$base/api/auth/csrf" -WebSession $authSession
$headers = @{ $csrf.headerName = $csrf.token }
$credentials = @{ username=$account.username; password=$account.password }
Invoke-RestMethod "$base/api/auth/login" -Method Post -WebSession $authSession -Headers $headers -ContentType 'application/json' -Body ($credentials | ConvertTo-Json)
Invoke-RestMethod "$base/api/auth/me" -WebSession $authSession
$csrf = Invoke-RestMethod "$base/api/auth/csrf" -WebSession $authSession
$headers = @{ $csrf.headerName = $csrf.token }
Invoke-RestMethod "$base/api/auth/logout" -Method Post -WebSession $authSession -Headers $headers -ContentType 'application/json' -Body '{}'
```

Tests backend thực hiện flow HTTP thật với CookieManager, verify cookie flags/session rotation/logout replay, CSRF, duplicate input, disabled login, hash và PostgreSQL JPA.
