# Sửa các điểm bất hợp lý trong luồng giải đấu

## Thay đổi hành vi

1. **Đồng đội trước giờ thi:** player timeline hiển thị phần tạo/gia nhập đội ngay trong màn hình trước giải, kể cả link `#teams`. Sau check-in, người chưa có đội đủ ba người nhận CTA hoàn tất đội. Check-in vẫn được ưu tiên trước CTA xếp đội; giải cá nhân không nhận phần đồng đội.

2. **Hạn nộp đội hình:** khi một vòng thực sự được mở, mỗi fixture có cửa sổ 300 giây. Hết hạn, server giữ đội hình đã nộp, tự chọn ba thành viên theo thứ tự roster cho bên chưa nộp, ưu tiên người còn hoạt động. Trường hợp roster chỉ còn các vị trí bị loại vẫn dùng cơ chế xử thua vị trí hiện có; không tạo replay giả. API từ chối nộp muộn tại đúng deadline. Đội hình chưa khóa vẫn được giữ kín. Các vòng chưa được chủ giải mở không chạy deadline; khi resume/recover, fixture chưa khóa được mở cửa sổ mới. Không sửa GameEngine/RuleSet.

3. **Validation chặn check-in vì hạ tầng:** frozen submission mới nhất chưa có kết luận đã áp dụng, đang xếp hàng/chạy hoặc INFRA_ERROR sẽ chặn việc đánh dấu no-show và tự bắt đầu giải. Bài MISSING, đơn đã hủy/từ chối và tài khoản bị vô hiệu không được dùng để giữ giải chờ. Khi có bài lỗi hạ tầng, giải không bị hủy bằng lý do thiếu check-in. Owner nhận cảnh báo và link tới validation. Luồng khôi phục: gia hạn kiểm thử → retry các bài lỗi → người chơi check-in trong cửa sổ mới → bắt đầu giải. Không tự gia hạn lặp vô hạn hoặc tự chạy lại mutation của chủ giải.

4. **Discovery:** nhóm mở đăng ký/sắp diễn ra tính theo cửa sổ timeline và giờ server trước khi SQL đếm, phân trang. Giải timeline đang coding, validation hoặc check-in không còn nằm trong “Đang mở đăng ký”. Luồng thủ công vẫn phân theo lifecycle hiện có.

5. **Khôi phục bản nháp:** Cài đặt của bản nháp BOT_ONLY chưa có roster/registration có form thêm hoặc sửa toàn bộ timeline; không phải tạo giải mới khi lưu lịch thất bại. Form điền lại được lịch đã lưu, giữ độ chính xác đến giây, validation/revision/CSRF và lỗi từ server. Poll cùng revision giữ nguyên form đang nhập. Lưu timeline đồng bộ luôn cửa sổ và chính sách nộp bot. Bản nháp có roster và giải đã công bố vẫn bị khóa cấu hình theo ràng buộc backend hiện có.

6. **Kết quả đang review:** Match Monitor và thẻ trận hiện “Kết quả đang được trọng tài xem xét” nếu `reviewRequired`; không diễn giải người thắng như kết quả đã chốt trong trạng thái này.

7. **Refresh:** so sánh monitor bỏ các clock vận chuyển `serverNow`/`serverTime`. Trạng thái execution, heartbeat, review, nội dung trận và lỗi tải vẫn làm cập nhật UI. Đồng hồ header dùng cơ chế cập nhật riêng hiện có.

8. **Xếp hạng:** cộng thống kê từ scored matches một lần thay vì duyệt toàn bộ lịch cho từng người chơi. Read model của giải được quản lý cache bảng xếp hạng theo revision, hủy cache khi changed. Không cache kết quả đang nằm trong transaction/deferred notifications; rollback không để lại bảng điểm của trạng thái chưa commit. Summary, report và `/me` dùng lại cùng kết quả; `/me` không gọi tính hạng lại trong mỗi registration.

## API và dữ liệu lưu

- Không thêm endpoint hoặc SQL migration; schema vẫn V36, worker vẫn 0.11.0.
- `teamCompetition` thêm `lineupSeconds`, `lineupFallback`; fixture public thêm `lineupDeadline`. Deadline được lưu trong TeamState thuộc structure JSON. Snapshot cũ thiếu trường này vẫn đọc được; server mở cửa sổ ở lần advance phù hợp đầu tiên.
- Rule snapshot mới của giải đồng đội ghi thời hạn và chính sách fallback. Audit có `TEAM_LINEUP_WINDOW_OPENED`, `TEAM_LINEUP_AUTO_SELECTED`; vẫn giữ `TEAM_LINEUPS_LOCKED`.
- Endpoint `/timeline/override` có thêm action `EXTEND_VALIDATION`, yêu cầu owner/admin, revision mới và reason. Nó dời validationDeadline cùng các mốc sau đó, giữ nguyên cửa sổ coding và frozen source.
- `timelineStartIssue=VALIDATION_INFRA_BLOCKED` phân biệt hạ tầng/validation với thiếu người. Audit hold được chống trùng; `TIMELINE_START_UNBLOCKED` gỡ cảnh báo sau khi giải quyết xong validation. Lỗi roster được giữ tên `TEAM_ROSTER_INCOMPLETE` thay vì thông báo chuẩn bị chung chung.
- PUT timeline cho draft đồng bộ botPolicy trong cùng mutation/transaction.

## Kiểm chứng

**PASS**

- 225 client tests: gồm phần đồng đội trước giải, CTA check-in/xếp đội, form phục hồi và điền lại lịch, giữ form qua polling, phân biệt clock với dữ liệu execution/review, cảnh báo giữ giải và kết quả đang review.
- 147 ca backend riêng biệt trong 13 suite, không fail/skip. TeamTournamentTest 13; RankingServiceTest 4; TimelineStartApiTest 43; TimelineOverrideApiTest 41; TournamentDiscoveryTest 2; TournamentTimelineApiTest 5; ParticipantTournamentStateTest 1; OrganizerMetricsApiTest 7; MatchMonitorTest 25; TournamentExecutionControlTest 2; TournamentExecutionServiceTest 2; hai ca PostgreSQL bên dưới.
- PostgreSQL Testcontainers: phân nhóm timeline trước đếm/phân trang; frozen validation bị lỗi → giữ roster/no-show/start → owner gia hạn → retry PASS → check-in → RUNNING. Cả hai thực sự chạy, không skip vì thiếu Docker.
- Đội hình: trước hạn một nano giây, đúng hạn, từ chối nộp muộn, giữ thứ tự do đội trưởng chọn, không trùng trận khi advance lại, deadline qua JSON restore, snapshot cũ thiếu deadline, pause/resume và vòng chưa được mở.
- Xếp hạng: dữ liệu 2.000 người/1.999 trận cho đúng tổng điểm và số trận; cache theo revision, bỏ cache khi changed và không giữ kết quả đọc trong transaction đã rollback.
- Kiểm tra cú pháp 70 module JavaScript; `git diff --check`; bootJar sau sửa cuối.
- Native PostgreSQL HTTP/WebSocket smoke: participant flow, phạm vi guest, discovery/notification routes, WS compact, đối chiếu 45 trang/module/stylesheet đóng gói với source mới.

**SKIPPED / giới hạn**

- Chưa chạy browser click E2E, screenshot mobile và screen reader: công cụ browser bị tắt trong Work settings. DOM/API tests không thay thế kiểm tra trực quan.
- Không benchmark hàng nghìn kết nối đồng thời. Ca 2.000 người xác minh tính đúng dữ liệu, không phải số người online mà server chắc chắn phục vụ được.
- Discovery quét metadata timeline trong registry để chọn ID trước SQL pagination; không tải toàn lịch trận. Khi có rất nhiều giải cùng tồn tại, có thể cần thêm cột/index projection cho timeline.
- Bước tạo draft và lưu lịch vẫn là các request riêng; bản sửa cung cấp đường phục hồi lịch, không chuyển toàn bộ tạo giải thành một endpoint nguyên tử.
- Bản build đã khởi động lại trên PostgreSQL để dùng thử tại localhost:8080. Script và tài khoản smoke chỉ lưu trong thư mục build bị Git bỏ qua.

## File chính

Client: player-detail, team-view, next-action, match-execution, read-snapshot (mới), organizer-attention, create-timeline-editor, deadline-policy-editor, timeline-overrides và timeline-lobby. Thêm tournament-repairs.test.js.

Server: OwnedTournamentService, TimelineStartService, TimelineOverrideService, FinalValidationStore/Service, TeamState, TeamScheduler, TeamTournamentService, TournamentAdministrationService, TournamentExecutionService, TournamentDiscoveryService, TournamentBotPolicy, RankingService, TournamentService và ParticipantTournamentStateService.

Tests: mở rộng TeamTournamentTest, RankingServiceTest, TimelineStartApiTest, TournamentDiscoveryTest, TournamentTimelineApiTest; thêm TournamentRepairPostgresTest và TournamentDiscoveryPostgresTest.
