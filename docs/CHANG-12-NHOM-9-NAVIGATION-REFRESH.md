# Chặng 12 — Nhóm 9: CTA, navigation và refresh

## Thay đổi hành vi

Hub, progression và player shell dùng chung model trong next-action.js. Hub không còn lấy phase CODING_OPEN + đơn APPROVED để hứa người chơi được sửa code, hoặc lấy CHECK_IN_OPEN để hứa được check-in. API discovery chưa có đầy đủ capability cá nhân nên card hiện **Xem giải/Xem trạng thái**, mở trang giải để lấy participant-flow hiện tại. Khi có context flow/me đã xác thực, navigation dùng cùng quyết định với player shell nhưng chuyển command thành liên kết đọc trạng thái, không thực hiện command từ hub.

Thông báo có tính lịch sử: kể cả “Coding đã mở” cũng về detail có `stay=1`, không đưa thẳng vào editor có thể đã hết hạn. Trang detail lấy flow mới rồi mới cho phép mở workspace. Redirect detail → lobby khi giải thực sự RUNNING giữ `stay=1` và tab hợp lệ, không mang theo tham số redirect tùy ý. Form nhập mã/link mời và link mời chủ giải tạo đều về trang người chơi; bookmark organizer cũ vẫn dùng được. History giải dùng cùng route helper. Tab/hash ngoài danh sách cho phép không được đưa vào URL mới.

Progression không còn một bảng hành động độc lập: dùng tournamentNextAction để diễn giải trạng thái. Vẫn chỉ nói đi tiếp khi series/bracket đã xác nhận thắng, không suy loại trực tiếp từ một ván BO hoặc từ trận thua Round Robin/Swiss. Player không ghép capability của một revision khác với tournament/me trước khi đổi CTA.

## Refresh chung

refresh-controller.js quản lý các request đọc của hub, player detail/lobby, organizer shell, My Match/Match Monitor, notifications, xét duyệt đăng ký, bài nộp, final validation và trạng thái coding workspace.

- Poll sau khi request trước hoàn tất: mặc định 5 giây; validation 3 giây; trạng thái coding job 1,5 giây khi job đang hoạt động; notification feed 15 giây.
- Tab ẩn không tiếp tục polling. Khi hiện lại hoặc có online event, tải ngay. Đồng hồ hiển thị được quản lý riêng, không tự mở phase/quyền.
- WS update đánh dấu stale và gom thành một lượt HTTP refresh. Nếu có update trong lúc đọc, một lượt follow-up được giữ lại thay vì bị bỏ qua bởi cờ busy.
- Đổi bộ lọc/trang thay thế request đọc cũ bằng AbortSignal và generation guard. Fetch không hỗ trợ/hủy chưa kịp cũng không được áp dụng response cũ.
- Pagehide/BFCache hủy request đang chạy; restore đọc lại, không nhận kết quả của phiên trước. Content bị gỡ sẽ dọn polling và listeners.
- Đang nhập lý do xử lý ở các trang quản lý không bị background refresh xóa form. Match Monitor giữ filter/trang/chi tiết đang đọc như nhóm 8.

Controller không tự gửi lại mutation. Các POST/PUT/DELETE giữ session, CSRF, reason/revision/confirmation của workflow hiện có. Autosave vẫn do chỉnh sửa nội dung/debounce và cơ chế đối chiếu source/revision hiện có quyết định; không biến refresh thành build/test/submit tự động. Kết quả poll coding job bị bỏ qua nếu người dùng đã đổi language/revision hoặc đang lưu/thao tác, tránh nhận nhầm snapshot cũ.

Các trang player/organizer sử dụng `watchTournament` với `updatesOnly=true`. WS chỉ gửi invalidation id/revision cho HTTP loader; không lấy payload WS làm dữ liệu render/quyền. Stream nhận cả TOURNAMENT_STATE compact và TOURNAMENT_UPDATED, bỏ packet của giải khác/revision cũ, deduplicate update cùng revision và audit event cùng sequence. Subscription dừng khi tab ẩn, nối lại khi hiện/BFCache. Lớp TournamentStream vẫn giữ callback snapshot tương thích cho caller cũ; các trang đã chuyển sang watcher compact không dùng callback đó để áp dụng state.

## API / DTO

Không thêm endpoint, đổi DTO backend, schema, worker protocol hoặc GameEngine/RuleSet. Vẫn dùng V36 và worker 0.11.0. Client tournamentRequest/sessionRequest nhận thêm tùy chọn AbortSignal cho request đọc. Sử dụng lại discover, summary, me, participant-flow/timeline, organizer-metrics, my-matches/match-monitor, notification feed, workspace và WebSocket WATCH_TOURNAMENT.

## File

Thêm:

- client/js/tournament/refresh-controller.js
- client/js/tournament/tournament-routes.js
- client/tests/refresh-controller.test.js
- client/tests/tournament-invalidation.test.js
- client/tests/tournament-navigation.test.js
- Tài liệu này.

Sửa:

- client/js/api/auth-api.js
- client/js/tournament/application-review.js
- client/js/tournament/code-workspace.js
- client/js/tournament/final-validation-dashboard.js
- client/js/tournament/hub-model.js
- client/js/tournament/match-monitor.js
- client/js/tournament/my-tournaments.js
- client/js/tournament/next-action.js
- client/js/tournament/notifications.js
- client/js/tournament/organizer-shell.js
- client/js/tournament/player-detail.js, player-state.js
- client/js/tournament/progression.js
- client/js/tournament/reconnect.js: bỏ listener reconnect/reload không còn caller; giữ kiểm tra seat.
- client/js/tournament/submission-dashboard.js
- client/js/tournament/timeline-lobby.js
- client/js/tournament/tournament-api.js
- client/js/tournament/tournament-detail.js, tournament-hub.js, tournament-list.js
- client/tests/progress-stepper.test.js, tournament-hub.test.js, tournament-match-monitor.test.js, tournament-reconnect.test.js
- server/src/test/java/com/rpsbattle/tournament/TimelineCodingApiTest.java: giữ stub Clock giữa test và lúc scheduler shutdown; mỗi test vẫn đặt lại START ở BeforeEach, không thay clock production.
- README.md

## Kiểm chứng

- PASS: 210 client tests, không fail/skip. Có 17 ca mới về queue invalidation, visibility, thay request, BFCache, dispose, recovery, compact WS, AbortSignal, route/capability và pairing revision; bỏ một ca cho listener reconnect đã xóa, thay bằng kiểm thử lifecycle của controller chung.
- PASS: 21 backend regression: OrganizerMetricsApiTest 7, ParticipantTournamentStateTest 1, SpectatorTest 1, TimelineNotificationApiTest 9, TournamentTimelineApiTest 3. Không fail/skip. Lần chạy đầu gặp log scheduler khi Clock mock bị reset về null; sau sửa fixture, chạy lại toàn bộ các suite này thành công, không có lỗi shutdown đó.
- PASS: kiểm tra cú pháp 68 module JavaScript và git diff --check.
- PASS: bootJar; server khởi động trên PostgreSQL với schema V36 không cần migration.
- PASS: native HTTP/compact WebSocket smoke: flow cùng revision với summary, guest không đọc được participant-flow, notification/discovery route an toàn, WATCH_TOURNAMENT updatesOnly chỉ trả id/revision. 30 trang/module phục vụ trên server được đối chiếu nguyên nội dung với source, gồm các entry page và module mới. Không có thao tác tournament mutation trong smoke này.
- SKIPPED: migration tests riêng vì không thay schema.
- SKIPPED: screenshot/layout/browser click E2E; công cụ trả `Local browser use is disabled in Work settings`. DOM tests và HTTP smoke không thay cho kiểm tra trực quan.

## Giới hạn

Không tạo capability cá nhân giả trong discovery; muốn CTA chi tiết ngay trên từng card cần bổ sung read model discovery có capability trong scope riêng. Browser click flow, mạng ngắt thực tế và tải hàng nghìn người chưa benchmark. Cơ chế này áp dụng các trang tournament nêu trên; admin worker và gameplay socket vẫn giữ workflow riêng. Chưa làm logic resultPublishAt hoặc regression UI toàn chặng — đó là nhóm 10. Không tự chuyển nhóm tiếp theo.
