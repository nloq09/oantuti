# Chặng 12 — Nhóm 2: Giao diện người chơi trước giải

## Phạm vi hoàn thành

- Upcoming: đồng hồ theo giờ server, timeline chín mốc và luật; chưa mở form đăng ký.
- Registration: tài khoản/tên hiển thị, ngôn ngữ dự kiến, đơn và lý do xét duyệt. Không chọn bot cuối cùng hay gửi source trong form đăng ký theo timeline.
- Waiting Coding: thông tin đơn được duyệt, giờ mở coding, luật, ngôn ngữ và protocol; không mở editor trước giờ.
- Frozen Submission: version/ngôn ngữ/thời điểm/hash của bài khóa. Khi đang chốt hoặc thiếu bài không hiển thị PASS giả. Editor và chọn cách nộp được ẩn khi session hết quyền sửa.
- Validation: tám bước có chữ và ký hiệu trạng thái; lỗi bot khác lỗi hạ tầng. TOURNAMENT READY chỉ khi server đã PASSED và applied; không tự mở quyền nộp khẩn cấp.
- Check-in: hạn đóng, phiên bản, trạng thái bot và thao tác được server cho phép; dùng CTA chung để tránh nút check-in trùng.
- Waiting Room: trạng thái bot, lịch, thông báo có loading/empty/error; không đưa thao tác organizer vào giao diện người chơi.
- Source chỉ đọc: mở chính xác snapshot version của bài khóa/kiểm thử, độc lập với bản nháp workspace. Trang không khởi tạo editor, autosave, build, test hoặc submit. JAR không có source sẽ hiển thị thông báo.

## API / DTO

Thêm `GET /api/tournaments/{id}/workspace/versions/{version}/source`.

Trả về `SourceView`: versionId, version, language, sourceCode, sourceHash, artifactHash, sourceAvailable. Response no-store/Vary Cookie. Yêu cầu đăng nhập và quyền đọc workspace theo timeline; truy vấn đồng thời tournamentId, userId và versionId. Không trả hidden tests, build log, token hoặc source người khác. Với JAR, sourceAvailable=false và sourceCode=null.

Đường dẫn frontend: `/tournament-code.html?id=...&view=source&version=...`. Đường dẫn workspace cũ giữ nguyên. Không đổi schema, GameEngine hoặc RuleSet.

## File thay đổi

File mới:

- client/js/tournament/phase-view.js
- client/js/tournament/source-view.js
- client/js/tournament/workspace-page.js
- client/tests/pre-tournament-view.test.js
- Tài liệu này.

File sửa:

- client/js/tournament/timeline-registration.js, timeline-check-in.js
- client/js/tournament/frozen-submission.js, final-validation-view.js
- client/js/tournament/next-action.js, player-detail.js, code-workspace.js
- client/tournament-code.html, client/css/tournament.css
- client/tests/source-freeze.test.js, validation-screen.test.js
- server/src/main/java/com/rpsbattle/api/controller/TournamentCodeController.java
- server/src/main/java/com/rpsbattle/tournament/coding/CodeWorkspaceService.java, WorkspaceStore.java
- server/src/test/java/com/rpsbattle/tournament/FinalValidationApiTest.java
- README.md

## Kiểm chứng ngày 20/10/2026

- PASS: 145 client tests, gồm Upcoming, bốn trạng thái xét duyệt, chờ coding, thiếu bài không PASS, đường dẫn đúng version, tám bước validation và phòng chờ.
- PASS: 47 backend tests thuộc CodeWorkspaceApiTest (2), FinalValidationApiTest (35), TimelineRegistrationApiTest (4), TournamentPhaseResolverTest (6).
- PASS: test snapshot giữ nguyên source version sau khi draft thay đổi; guest, người khác và version/giải không khớp bị chặn.
- PASS: bootJar cuối cùng; native PostgreSQL/server localhost build bot Python thành BUILT, đọc snapshot đúng version và kiểm tra guest/other-user. Giải chưa mở coding trả 409 trước truy vấn source; test backend kiểm tra 404 khi đủ quyền đọc nhưng version không thuộc giải.
- PASS: browser desktop localhost: Upcoming guest, Waiting Coding với người đã duyệt, source snapshot chỉ đọc. Đã xem bố cục bằng screenshot; không thấy editor/build/submit trên source snapshot.
- SKIPPED: visual mobile do browser không có resize/emulation. Có CSS breakpoint, chưa khẳng định kiểm tra trên thiết bị thật.
- SKIPPED: visual Frozen/Validation/Check-in/Waiting Room trên native fixture; các trạng thái này được kiểm tra bằng client và backend tests, chưa chạy lại một giải hết timeline chỉ để chụp giao diện.

Lệnh backend dùng task `:test` và filter bốn class trên, sau đó `:bootJar`; client dùng `node --test client/tests/*.test.js`. Smoke script và tài khoản fixture chỉ lưu trong thư mục build bỏ qua Git.

## Phần tiếp theo

Nhóm 3 (Full Timeline Create UI) chưa triển khai ở bước này. Organizer shell, metrics/attention, remote read-model, match monitor và Hub/notifications cleanup vẫn thuộc các nhóm sau; không đánh dấu hoàn tất toàn Chặng 12.
