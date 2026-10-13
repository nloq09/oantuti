# Chặng 4 — Nhóm 1: PostgreSQL, JPA và Flyway

Nhóm này dựng nền database, chưa triển khai tài khoản/đăng nhập, chưa chuyển bot, phòng, giải đấu hay replay hiện tại từ RAM/file sang repository DB. Các nhóm tiếp theo mới thêm domain/entity/repository và nối application service. Các bot cũ không bị xóa, đổi tên hoặc tự gán chủ sở hữu.

## Cấu hình

- Không chọn profile thì mặc định `dev`: PostgreSQL tại `DB_URL` (mặc định `jdbc:postgresql://localhost:5432/rpsbattle`), bắt buộc `DB_USERNAME` và `DB_PASSWORD`.
- `prod`: cả `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` bắt buộc; cookie HTTPS được chuẩn bị trong config, nhưng authentication sẽ làm ở Nhóm 2–3. Nếu DB cần SSL, cấu hình vào JDBC URL theo nhà cung cấp.
- `test`: H2 in-memory ở `src/test/resources`; chỉ có trong test classpath, không đóng gói H2 hay profile test vào bootJar. Gradle test tự chọn profile này.
- JPA `ddl-auto=validate`, `open-in-view=false`; Flyway quản lý schema, bật validate và tắt clean. Không dùng Hibernate create/update trong production.
- Database không khả dụng: server dừng startup; không tự chuyển sang RAM/H2 production.
- `PORT` được đọc từ môi trường, mặc định 8080; chưa có Dockerfile hoặc Railway Sandbox adapter ở Nhóm 1.

## Khởi động PostgreSQL local

Mở Docker Desktop, chọn Linux engine. Tại thư mục gốc project:

```powershell
Copy-Item .env.example .env
```

Sửa `.env` để điền `POSTGRES_USER`, `POSTGRES_PASSWORD`; điền `DB_USERNAME`, `DB_PASSWORD` giống hai giá trị trên. Không commit `.env`. Chọn mật khẩu riêng cho môi trường development; không dùng thông tin production để chạy thử.

```powershell
docker compose --env-file .env -f infra/docker-compose.postgres.yml up -d
docker compose --env-file .env -f infra/docker-compose.postgres.yml ps
```

Cổng DB chỉ bind `127.0.0.1:5432`. Dữ liệu ở named volume `postgres-data` do Compose quản lý. Không dùng `down -v` nếu muốn giữ dữ liệu. Nếu cổng 5432 đã có PostgreSQL, dùng DB hiện có hoặc đổi port mapping và `DB_URL` tương ứng.

Gradle không tự đọc `.env`. Tại PowerShell thư mục gốc, nạp các biến từ file của chính bạn (không in giá trị ra terminal):

```powershell
Get-Content .env | ForEach-Object {
    if ($_ -match '^([A-Z][A-Z0-9_]*)=(.*)$') {
        [Environment]::SetEnvironmentVariable($Matches[1], $Matches[2], 'Process')
    }
}
$env:JAVA_HOME = 'C:\Users\hi\.jdks\openjdk-24.0.2+12-54'
cd server
.\gradlew.bat bootRun --console=plain
```

Các giá trị trong `.env` là chuỗi thô không đặt dấu nháy; cách nạp trên không thực thi code trong file. Khi chọn production, đặt `SPRING_PROFILES_ACTIVE=prod` và các biến DB qua secret manager/hosting. JDBC URL cần có tiền tố `jdbc:postgresql://`; không đưa mật khẩu vào URL.

## Migration

- `V1__create_users.sql`: tài khoản, role/status và username/email được chuẩn hóa lowercase, unique.
- `V2__create_bots.sql`: metadata bot gắn owner, path artifact và soft-delete status.
- `V3__create_matches.sql`: metadata trận và seat user/bot/controller; không biến GameState thành entity.
- `V4__create_tournaments.sql`: chủ giải, roster và các cặp trận. FK composite bảo đảm các participant của một trận thuộc đúng giải.
- `V5__create_replays.sql`: metadata replay + path file JSON; mỗi match có tối đa một replay.
- `V6__create_indexes.sql`: index theo ownership/history/tournament; unique constraints có index do DB quản lý.

Schema là nền cho các nhóm sau; không có API mới truy cập bảng ở nhóm này. Không sửa file migration đã chạy ở môi trường được giữ dữ liệu; thay đổi tiếp theo phải thêm V7, V8… Flyway checksum phát hiện thay đổi lịch sử.

## Kiểm thử

```powershell
cd server
.\gradlew.bat test bootJar --no-daemon --console=plain
```

Từ thư mục gốc:

```powershell
node --test client/tests/*.test.js
```

`FlywayMigrationTest` dùng Testcontainers PostgreSQL 17, kiểm tra DB rỗng tới schema V6, migrate lặp không ghi lại, unique/normalization/FK, dữ liệu qua kết nối mới, checksum và clean bị chặn. Docker không khả dụng thì test này SKIPPED; phải ghi rõ, không coi là đã kiểm chứng PostgreSQL. `JpaFoundationTest` xác minh Spring Boot tạo EntityManagerFactory sau Flyway bằng H2 test-only. Những test này chưa chứng minh các tính năng game đã persist; scenario account/history/recovery thuộc các nhóm sau.

## Ranh giới nhóm

Không sửa `GameEngine`, `RuleSet`, movement/combat/placement/victory. Chưa thêm Spring Security, chưa bật login, chưa thực thi production hardening hoặc chuyển sang Railway Sandbox. Dừng ở Nhóm 1 để người dùng review theo tài liệu Chặng 4.

## Kết quả xác minh ngày 2026-10-13

- Toàn bộ backend: 337 test, 0 failures, 0 errors, 0 skipped.
- Toàn bộ client: 11 test pass, 0 fail, 0 skipped.
- FlywayMigrationTest: 4 test trên PostgreSQL 17 thật bằng Testcontainers, đều pass.
- JpaFoundationTest: Spring Boot + Flyway + JPA trên H2 test-only pass.
- Docker bot integration: Python, C++, Java source và JAR đều pass, không skip.
- `bootJar` build thành công; đã kiểm tra JAR chứa dev/prod config, PostgreSQL driver và 6 SQL migration; không chứa H2 hoặc application-test.yml.
- `git diff --check` pass; GameEngine/RuleSet không đổi.
- Một assertion ban đầu đếm cả bản ghi tạo schema của Flyway; đã sửa chỉ đếm versioned migrations và chạy lại toàn bộ backend thành công.

## File thêm/sửa trong Nhóm 1

Sửa:

- `.gitignore`: loại trừ secrets, replay artifacts và backups.
- `README.md`: thông báo yêu cầu DB và link hướng dẫn chạy.
- `server/build.gradle`: JPA/Flyway/PostgreSQL, H2 test-only, Testcontainers và profile test.
- `server/src/main/resources/application.yml`: profile mặc định, JPA/Flyway và PORT.

Thêm:

- `.env.example`.
- `infra/docker-compose.postgres.yml` (chỉ PostgreSQL local, full deployment Compose ở Nhóm 10).
- `server/src/main/resources/application-dev.yml` và `application-prod.yml`.
- `server/src/main/resources/db/migration/V1__create_users.sql`.
- `server/src/main/resources/db/migration/V2__create_bots.sql`.
- `server/src/main/resources/db/migration/V3__create_matches.sql`.
- `server/src/main/resources/db/migration/V4__create_tournaments.sql`.
- `server/src/main/resources/db/migration/V5__create_replays.sql`.
- `server/src/main/resources/db/migration/V6__create_indexes.sql`.
- `server/src/test/resources/application-test.yml`.
- `server/src/test/java/com/rpsbattle/persistence/FlywayMigrationTest.java`.
- `server/src/test/java/com/rpsbattle/persistence/JpaFoundationTest.java`.
- `docs/CHANG-4-PROMPT-DB-AUTH.md`: lưu bản yêu cầu người dùng.
- `docs/CHANG-4-NHOM-1-DATABASE.md`: báo cáo và hướng dẫn này.

Các thay đổi giải đấu mở của lượt trước vẫn được giữ trong working tree; chúng đã có từ trước Nhóm 1 và chưa push vì người dùng từ chối yêu cầu commit/push ở lượt đó.
