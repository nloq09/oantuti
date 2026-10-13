# Chặng 4 — Nhóm 2: User domain, register, login, logout

## Thực hiện

- User/UserId/UserRole/AccountStatus tách khỏi JPA Entity và Spring Security.
- UserRepository port với findById/username/email, exists và save; JPA adapter ánh xạ UserEntity vào schema V1 đã có, không thay đổi migration đã chạy.
- RegistrationService transaction với username/email normalized/unique, password policy và BCrypt cost 12; role USER/status ACTIVE được server quyết định.
- Spring Security dùng HttpOnly session cookie; JSON register/login/logout/me, CSRF endpoint, session fixation rotation, explicit SecurityContext persistence và logout invalidation.
- Generic invalid credentials, DTO không lộ hash/session/token; me kiểm tra account status từ DB.
- Client API helper cùng origin + CSRF mới mỗi mutation; chưa có giao diện tài khoản (Nhóm 8).
- Không sửa GameEngine, RuleSet, bot runner, luật chơi hoặc cơ chế ownership của nhóm cũ.

API, cookie, cách gọi và những giới hạn được ghi trong [AUTH.md](AUTH.md). Phân quyền account/profile/ownership đầy đủ, rate limit, session recovery và deployment vẫn ở các nhóm sau. Tài khoản được persist; session vẫn RAM nên phải login lại sau restart.

## File thêm/sửa

Sửa:

- `server/build.gradle`: Spring Security runtime và security-test.
- `server/src/main/resources/application.yml`: HttpOnly, SameSite=Lax, session timeout 30 phút, cookie-only tracking.
- `README.md`: tiến độ Nhóm 2 và link tài liệu.

Thêm:

- `server/src/main/java/com/rpsbattle/account/domain/{User,UserId,UserRole,AccountStatus}.java`.
- `server/src/main/java/com/rpsbattle/account/repository/UserRepository.java`.
- `server/src/main/java/com/rpsbattle/account/persistence/{UserEntity,JpaUserRepository,JpaUserRepositoryAdapter}.java`.
- `server/src/main/java/com/rpsbattle/auth/{AuthException,RegistrationService,AuthService,SecurityConfig}.java`.
- `server/src/main/java/com/rpsbattle/api/controller/AuthController.java`.
- `server/src/main/java/com/rpsbattle/api/error/AuthErrorHandler.java`.
- `server/src/test/java/com/rpsbattle/auth/{UserRepositoryTest,UserRepositoryPostgresTest,RegistrationServiceTest,LoginTest,PasswordHashTest}.java`.
- `client/js/api/auth-api.js` và `client/tests/auth-session.test.js`.
- `docs/AUTH.md` và báo cáo này.

Dừng sau nhóm này theo yêu cầu “Không tự sang nhóm tiếp theo. Chờ tôi review.” trong `CHANG-4-PROMPT-DB-AUTH.md`.

## Xác minh ngày 2026-10-13

- Toàn bộ backend: 350 test pass, 0 failures, 0 errors, 0 skipped.
- Toàn bộ client: 13 test pass, 0 fail, 0 skipped.
- Flyway migration PostgreSQL thật: 4 test pass.
- UserRepositoryPostgresTest: đăng ký qua RegistrationService/JPA, đọc DB bằng kết nối JDBC riêng, xác nhận BCrypt hash và metadata trên PostgreSQL 17 thật.
- LoginTest: HTTP thật với cookie jar, CSRF, normalization, không tự login sau register, session ID đổi, replay cookie cũ vô hiệu, logout, current-user, account disabled và USER không vào admin API.
- Docker integration cho Python/C++/Java/JAR vẫn pass, không skip.
- bootJar build thành công; `git diff --check` pass; GameEngine/RuleSet không sửa.
