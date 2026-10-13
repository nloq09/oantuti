# Chặng 4 — Nhóm 3: Account/Profile, Security và Authorization

## Kết quả chức năng

- API profile đọc/sửa displayName theo account session; ID/email/username/role/hash không được sửa qua profile.
- Change password xác minh mật khẩu cũ, kiểm tra policy mới, không cho giữ nguyên; tăng authVersion và logout mọi phiên theo kiểm tra DB ở request kế tiếp.
- API ADMIN tối thiểu: users phân trang, disable/enable, system summary. USER bị chặn; không xóa account/history; không tự disable mình.
- AccountAuthorizationService tách khỏi controller, luôn xác minh role/status DB. SessionAccountFilter kiểm tra account và authVersion ở mỗi request authenticated, cập nhật role từ DB, fail closed khi DB unavailable.
- Principal session chỉ có ID/username/version; không giữ password hash. Login có kiểm tra version trong transaction để tránh lập phiên bằng credential vừa bị thay đổi.
- Migration V7 thêm auth_version, giữ nguyên V1–V6. Nâng schema V6 có tài khoản cũ không đổi ID/username/password.
- Client helper cho profile/password/admin tự gửi cookie + CSRF đúng phương thức, không lưu password/token localStorage.
- Không sửa GameEngine/RuleSet, luật, bot execution, match/tournament ownership hoặc replay.

API và cách tạo ADMIN đầu tiên xem [AUTH.md](AUTH.md). Chưa có UI profile/admin/login ở nhóm này; UI tài khoản theo Nhóm 8. Chưa có match history/stats (Nhóm 5), chưa claim bot/giải theo account (Nhóm 4/6), chưa đóng WebSocket đang chạy khi disable account và chưa có rate limit (các nhóm tiếp theo).

## File thêm/sửa

Thêm:

- `server/src/main/resources/db/migration/V7__add_account_session_version.sql`.
- `server/src/main/java/com/rpsbattle/account/{AccountAuthorizationService,AccountService,AdminService}.java`.
- `server/src/main/java/com/rpsbattle/auth/{AccountPrincipal,SessionAccountFilter}.java`.
- `server/src/main/java/com/rpsbattle/api/controller/{AccountController,AdminController}.java`.
- `server/src/test/java/com/rpsbattle/account/{AccountAuthorizationTest,AuthorizationServiceTest}.java`.
- `client/js/api/account-api.js`, `client/tests/account.test.js`.
- `docs/CHANG-4-NHOM-3-ACCOUNT.md`.

Sửa:

- User domain/entity/repository/JPA adapter: authVersion, row locking, paging/count.
- AuthService/AuthController: safe principal, credential-version validation.
- SecurityConfig/SessionAccountFilter: session revalidation và CSRF account/admin.
- AuthErrorHandler: áp dụng lỗi domain cho account/admin.
- FlywayMigrationTest/JpaFoundationTest: latest V7, upgrade V6 giữ dữ liệu.
- `client/js/api/auth-api.js`: sessionRequest dùng chung cho account/admin.
- `docs/AUTH.md` và `README.md`: API, phạm vi hoàn thành và những phần còn lại.

Dừng để người dùng review, không tự sang Nhóm 4 theo tài liệu Chặng 4.

## Xác minh ngày 2026-10-13

- Toàn bộ backend: 356 test pass, 0 failures, 0 errors, 0 skipped.
- Toàn bộ client: 14 test pass, 0 fail, 0 skipped.
- Flyway PostgreSQL thật: 5 test pass, gồm upgrade V6 → V7 có dữ liệu cũ.
- PostgreSQL JPA account test tiếp tục pass với schema V7.
- AccountAuthorizationTest: HTTP thật kiểm tra profile identity/field whitelist/CSRF, xác minh password cũ, thu hồi hai phiên khi đổi password, ADMIN vs USER, disable/enable và session cũ, pagination, server kiểm tra lại role khi role DB đổi.
- AuthorizationServiceTest: application service không chấp nhận ROLE_ADMIN tự khai cho account USER; authVersion sai trả UNAUTHENTICATED.
- Docker integration Python/C++/Java/JAR pass; game/WebSocket/tournament/replay regression pass.
- bootJar thành công; `git diff --check` pass; GameEngine/RuleSet không đổi.
