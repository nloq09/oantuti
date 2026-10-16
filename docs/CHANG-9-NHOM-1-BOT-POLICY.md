# Chặng 9 — Chính sách nộp bot và giao diện tạo giải

## Phạm vi đã hoàn thành

Nhóm 1 trong tài liệu `CHANG-9-TOURNAMENT-BOT-CODING-TESTING.md`: người tạo giải chọn chính sách nộp bot; server lưu, công khai cấu hình và kiểm tra các đường đăng ký bot hiện có.

- `PRE_UPLOADED_ONLY`: chỉ dùng bot đã tải lên và đạt điều kiện đăng ký hiện tại.
- `CODE_IN_PLATFORM_ONLY`: bắt buộc bot được viết trên hệ thống; hiện chặn bot upload sẵn. Workspace và đường nộp bài tương ứng chưa triển khai.
- `BOTH`: cho phép cả hai nguồn theo chính sách; hiện dùng được nhánh bot upload sẵn.
- Cho phép chọn các họ ngôn ngữ Python, C++, Java. JAR của bot có sẵn thuộc họ Java. Ngôn ngữ được kiểm tra từ metadata bot trên server, không tin trường do client tự gửi.
- Giải cũ mặc định `PRE_UPLOADED_ONLY`, cho phép đủ ba họ ngôn ngữ. Giải chỉ người tự chơi giữ luồng cũ và ẩn cấu hình bot.
- Biểu mẫu tạo giải có cấu hình phương thức, ngôn ngữ, thời hạn, số phiên bản và cờ kiểm thử/khóa bài. Trang quản lý và trang người tham gia hiển thị chính sách. Chế độ chỉ code không mở selector bot upload sẵn.

**Các tùy chọn workspace hiện chỉ được lưu làm cấu hình**: lưu nháp, public/hidden tests, đấu thử, số phiên bản, giới hạn source, thời gian nhận/nộp, tự nộp READY, khóa khi nộp/hết hạn và cho nộp lại. Chúng chưa tạo workspace, chạy pipeline, tự nộp hay khóa bài. Giao diện có thông báo giới hạn này; chưa có trạng thái READY của bài code trên nền tảng.

Profile tài nguyên cố định theo runner hiện tại: 0,5 CPU, 384 MiB RAM, 2.000 ms/nước, 25.000 ms build, 60.000 ms test. Server từ chối thay đổi profile hoặc bật quyền organizer đọc source; không nhận Docker image tùy chọn. Việc áp dụng pipeline test theo chính sách thuộc các nhóm sau.

## API và quyền

- `POST /api/tournaments`: nhận thêm `botPolicy` trong cùng request tạo giải. Bỏ trường này dùng mặc định tương thích cũ.
- `GET /api/tournaments/{id}/bot-policy`: trả `{ tournamentId, revision, policy }`, không cache, không trả source/artifact/credential.
- `PUT /api/tournaments/{id}/bot-policy`: nhận `{ revision, policy }`; owner/admin, session và CSRF; chỉ sửa khi giải còn DRAFT và chưa có participant/registration. Revision cũ bị từ chối. Có audit `BOT_POLICY_UPDATED`.
- Chính sách cũng có trong dữ liệu tóm tắt/chi tiết giải. Các đường quick-add, registration và prepare kiểm tra nguồn/ngôn ngữ tương ứng; đổi `sourceType` hoặc `language` trong request không vượt qua điều kiện.

Server kiểm tra danh sách ngôn ngữ không rỗng, cửa sổ nhận bài hợp lệ, số phiên bản 1–100, source 1–524.288 byte. Tự nộp bản READY cần deadline. Những giới hạn này xác thực cấu hình; chưa phải kiểm thử source thực thi.

## Lưu trữ và nâng cấp

Flyway **V21** thêm `tournament_bot_policies`, khóa chính/khóa ngoại theo tournament. Các cột mode và danh sách ngôn ngữ có constraint, JSON lưu cấu hình đầy đủ; adapter kiểm tra tính nhất quán trước phục hồi. Migration backfill chính sách mặc định cho các giải cũ, chạy trên PostgreSQL và H2.

Policy được lưu cùng transaction giải, revision và audit; snapshot phục vụ rollback giữ cả policy. Không sửa migration đã áp dụng. Đã sao lưu PostgreSQL, bot và replay vào `backups/before-stage9-bot-policy-v21` trước nâng database local; bản sao lưu không đưa vào Git.

## Kiểm tra

- Backend: **566 pass, 0 fail, 0 skipped**, gồm Flyway/H2, PostgreSQL và các kiểm thử Docker hiện có khi Docker khả dụng.
- Client: **71 pass, 0 fail, 0 skipped**; kiểm tra cú pháp 77 module thành công.
- `bootJar`: thành công; đã chạy JAR mới trên PostgreSQL local.
- Smoke API thật: ba phương thức, giới hạn ngôn ngữ, bot Python mẫu được validate bằng Docker, chống giả nguồn/ngôn ngữ, owner/CSRF/revision, khóa sửa cấu hình sau mở đăng ký và dữ liệu public không có source.
- Sau restart: giữ nguyên bốn policy fixture, registration và trạng thái giải; so sánh ngôn ngữ theo tập, không dựa vào thứ tự JSON.
- Hồi quy phục hồi dữ liệu cũ: BO3/BO5, vòng bảng, Swiss, đồng đội, nhiều giai đoạn, rút giữa giải, thứ hạng, vô địch và replay đều pass.
- Giao diện thật: tạo giải chỉ code với Python/C++, 3 phiên bản và hidden-test flag; API xác nhận cấu hình đã lưu. Trang người chơi không hiển thị selector bot cũ. Biểu mẫu và trang chi tiết không tràn ngang ở viewport 390 × 844. Giải thử được hủy, giữ audit.

Không sửa GameEngine, RuleSet, giao thức nước đi hoặc Docker runner.

## File thêm/sửa

- Domain: `BotSubmissionMode.java`, `TournamentBotPolicy.java`, `Tournament.java`.
- API/service: `TournamentBotPolicyController.java`, `TournamentController.java`, `OwnedTournamentService.java`, `TournamentService.java`.
- Persistence: `TournamentBotPolicyEntity.java`, `TournamentBotPolicyConverter.java`, `TournamentDetails.java`, `TournamentRepositoryAdapter.java`, migration `V21__tournament_bot_submission_policies.sql`.
- Client: `js/tournament/bot-policy.js`, `tournament-list.js`, `registration-form.js`, `player-detail.js`, `tournament-detail.js`, `js/ui/toast.js`.
- Kiểm thử: `TournamentBotPolicyTest.java`, `TournamentBotPolicyApiTest.java`, `TournamentPersistencePostgresTest.java`, `FlywayMigrationTest.java`, `JpaFoundationTest.java`, `client/tests/tournament-bot-policy.test.js`.
- Tài liệu: README, DEPLOYMENT và báo cáo này. Script/fixture/ảnh smoke local và backup được giữ ngoài Git.

## Các nhóm tiếp theo

Theo tài liệu yêu cầu, dừng sau nhóm 1 để review. Nhóm 2–10 chưa triển khai: workspace/draft; build Python/C++/Java; protocol/resource tests; legal-action/sample matches; hidden tests; version/submit/lock; tích hợp đầy đủ BOTH; dashboard/queue; security/scale/E2E.
