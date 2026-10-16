# Chặng 9 — Workspace, build và kiểm thử công khai (nhóm 2–5)

## Cách sử dụng

Chủ giải chọn `CODE_IN_PLATFORM_ONLY` hoặc `BOTH`, chọn ngôn ngữ rồi mở đăng ký. Người chơi đăng nhập, vào chi tiết giải và chọn **Mở trình soạn code bot**. Workspace chỉ cho sửa/build/test trong giai đoạn REGISTRATION, trong cửa sổ nhận bài của policy. Sau khi giải đóng, chủ source vẫn đọc được bản đã lưu.

1. Chọn Python, C++ hoặc Java; mỗi ngôn ngữ có bản nháp riêng và template đúng giao thức hiện tại.
2. Sửa code trong textarea có số dòng, thụt dòng Tab, tìm kiếm, phím lưu và undo/redo gốc của trình duyệt. Chưa có syntax highlighting; có thể thay editor qua lớp client hiện tại sau này, không tải editor từ CDN.
3. Khi policy cho phép, tự lưu sau 3 giây ngừng gõ hoặc bấm **Lưu nháp**. Source nằm trên server, đọc lại được sau đăng nhập ở máy khác. Bản tạm trong sessionStorage được phân theo account/giải/ngôn ngữ, không thay thế server draft.
4. Hai tab cùng sửa sẽ gặp conflict revision; giao diện giữ nội dung đang gõ và cho chọn bản local/server hoặc tải cả hai. Nút **So sánh với server** cũng cho phép chủ động bỏ bản đang sửa. Không tự ghi đè bản khác.
5. Bấm **Build**. Kết quả có log compiler giới hạn, trạng thái và số phiên bản. Sửa/lưu source mới sẽ gỡ liên kết bản build hiện tại; test không chạy trên một bản code khác bản đang hiển thị.
6. Bấm **Chạy test công khai** rồi **Đấu mẫu**. Kết quả do server quyết định, hiển thị PASS/FAIL, số lượt/bước, thời gian, timeout, nước sai và stderr. Thắng trận mẫu không phải điều kiện pass.

Nếu `allowDraftSave=false`, source đang gõ vẫn dùng để build và được chụp vào version, không gọi API lưu nháp. Khi mở lại workspace, client lấy source của bản build hiện tại. Source chưa build chỉ có bản tạm trên browser hoặc bản tải xuống.

## Phạm vi

- Nhóm 2: trang `tournament-code.html`, tài liệu Protocol/Examples/Rules/Public tests ngay trong workspace; bản nháp PostgreSQL riêng theo user/tournament/language; lưu revision, conflict và quyền owner.
- Nhóm 3: snapshot bất biến mỗi build, source SHA-256, compile trong Docker, artifact riêng và SHA-256, không chạy source trực tiếp qua tournament controller. Python syntax check rồi chạy boot/protocol ở bước test; C++17 compile thành binary; Java 21 javac và JAR với entrypoint Main. Java không package; không nhận JAR upload trong workspace.
- Nhóm 4: JSON Lines qua BotProtocol hiện có; sequence/player/action phải hợp lệ. Timeout 2 giây mỗi phản hồi sau khi sandbox boot, build tối đa 25 giây, tổng test 60 giây. Stdout/stderr quá giới hạn bị dừng.
- Nhóm 5: tám trạng thái công khai thay đổi sequence và cả P1/P2, kiểm tra bằng GameEngine hiện tại; trận thật tối đa 200 lượt với ReferenceRandomBot, không ghi official match history. Không thêm luật gameplay.

`READY` của workspace hiện đòi cả public tests và sample match pass trên cùng version và policy không yêu cầu hidden tests. Nếu hidden tests được bật thì dừng ở BUILT; chưa thể nhận READY đầy đủ. Không có nút Submit giả: **hidden tests, submission/lock/deadline promotion và đưa artifact vào giải chính thức thuộc nhóm 6–8, chưa triển khai**. Bot đã upload sẵn vẫn đi theo luồng đăng ký cũ, chưa có pipeline tournament-specific chung.

Trận mẫu không lưu replay trong bản này; chỉ trả kết quả và số đo. CPU time/memory peak không được giả lập: hiện trả giới hạn sandbox và elapsed wall time, chưa có số đo peak thực tế. Public cases là bộ chuẩn của platform, chưa có UI organizer chọn từng case.

## API và dữ liệu riêng tư

Các endpoint dưới `/api/tournaments/{id}/workspace`:

- `POST` với `{language}`: tạo hoặc đọc workspace riêng của người đang đăng nhập.
- `GET /me?language=PYTHON`: source, revision, trạng thái và bản build hiện tại của chính tài khoản.
- `PUT /me` với `{language,revision,sourceCode}`: lưu nháp có optimistic conflict.
- `POST /me/build` với `{language,revision,sourceCode}`: nhận 202, chụp source và xếp build.
- `POST /me/test` và `/me/test-match` với `{language,revision}`: nhận 202, chỉ chạy artifact hiện tại đã build thành công.
- `GET /public-tests`: input công khai và yêu cầu hành vi, không có hidden input.

Tất cả đều xác thực tài khoản ở service; mutations có CSRF của hệ thống. Không nhận userId/workspaceId tùy ý để đọc source người khác; organizer/admin cũng không có endpoint đọc source của participant. Response riêng có `Cache-Control: no-store`, `Vary: Cookie`. Public tournament DTO/export không chứa source, artifact path hoặc token.

V22 tạo `tournament_code_workspaces` và `tournament_submission_versions`, unique user/tournament/language, unique số version/workspace, FK tới account/giải và current version. Bản build giữ source riêng, không overwrite khi sửa nháp. Claim và hoàn tất job có transaction; khóa account để chặn hai job đồng thời của một user, khóa workspace/revision để chặn sửa giữa build/test.

Artifact nằm trong thư mục bot `coding/<UUID>/<fixed filename>`, lưu cùng backup bot; không có URL download artifact. Worker kiểm tra hash trước chạy và từ chối symlink. File source tạm phục vụ build được dọn sau job.

## Sandbox và giới hạn vận hành

Dùng cố định `python:3.12-slim`, `gcc:14`, `eclipse-temurin:21-jdk`; cần pull trước khi chạy. Giữ các Docker options của platform: network none, non-root, read-only root, cap-drop ALL, no-new-privileges, 0,5 CPU, 384 MiB RAM/swap, 64 PID, /tmp 64 MiB, nofile 64. Chỉ mount đúng một source/artifact read-only; không mount Docker socket, database credentials, source server hoặc workspace khác.

Compile không chạy trên host, không cài pip/apt/Maven theo source. Artifact tối đa 8 MiB, compiler log/stderr 32 KiB, stdout 64 KiB/dòng và 4 MiB/session. Client không được chọn image hoặc shell command. Java runtime dùng heap 128 MiB. Runtime output được đưa vào DOM bằng textContent.

Có hai worker và tối đa 10 job đã nhận, kể cả đang chạy; một user chỉ có một job active. Queue hiện ở RAM: khi restart, job chưa xong chuyển FAILED để user build/test lại, không tự tiếp tục. **Chưa phải durable job queue, chưa hỗ trợ nhiều replica hoặc thử tải 2.000 người; phần đó thuộc nhóm 9–10.** Deploy một server process cho worker hiện tại.

## Kiểm tra và nâng cấp

- **581 backend tests pass, 0 fail, 0 skipped**, gồm migration PostgreSQL/H2, authorization/conflict, snapshot build, invalidation sau sửa, policy flags, ba compiler và Docker timeout/output/network/filesystem/memory/temp-storage.
- **77 client tests pass**, 79 module kiểm tra cú pháp pass; bộ model kiểm tra lưu snapshot khi còn gõ, conflict, giữ server/local và chỉ test đúng source đã build.
- `bootJar` thành công. Native PostgreSQL/Docker: cả Python/C++/Java lưu source, build artifact, chạy tám case, hoàn tất trận mẫu; kiểm tra private source, CSRF, revision và public DTO.
- Restart giữ ba source, artifact hash, READY và kết quả test/sample. Hồi quy giải cũ BO3/BO5, vòng bảng, Swiss/đồng đội, nhiều giai đoạn/rút giữa giải và replay đều pass.
- UI thật: đăng nhập quay lại đúng workspace; đổi Java → Python, sửa và lưu source, build v2, chạy tám public cases và trận mẫu 200 lượt đều PASS. Viewport 390 × 844 không tràn ngang; ảnh kiểm tra lưu ngoài Git. Giải thử được hủy sau kiểm tra, giữ dữ liệu.
- Backup local trước V22: `backups/before-stage9-workspace-v22` (database dump, bot/replay và checksum manifest), ngoài Git. Triển khai đồng bộ client/server, sao lưu cả DB và thư mục bot `coding/`; không sửa migration đã áp dụng.

Không sửa GameEngine, RuleSet, Match Engine, BotProtocol hoặc DockerBotProcess hiện có. Bộ kiểm thử gọi core qua adapter `codevalidation.BotTestService`; tournament service chỉ điều phối workspace/job.

## File thêm/sửa

Backend: `TournamentCodeController`, package `tournament/coding` (workspace/version/store/service/sandbox), `codevalidation/BotTestService`, ba template trong resources, migration V22. Tests: `CodeWorkspaceApiTest`, `CodeWorkspaceJobTest`, `CodeWorkspaceValidationTest`, `WorkspaceSandboxDockerTest`; cập nhật số migration trong Flyway/JPA tests.

Client: `tournament-code.html`, `css/code-workspace.css`, `code-workspace.js`, `code-workspace-model.js`, liên kết policy, lỗi dịch tiếng Việt và allowlist quay lại sau login; tests workspace/account. Tài liệu: README, DEPLOYMENT và báo cáo này. Fixture, script native, ảnh, credential và backup local không commit.
