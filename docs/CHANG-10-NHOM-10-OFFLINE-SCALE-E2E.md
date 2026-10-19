# Bản nháp mất mạng, tải lớn và hoàn tất chặng 10

## Bản nháp và reconnect

Workspace giữ bản tạm trong sessionStorage theo tài khoản/giải/ngôn ngữ, bao gồm revision gốc, source server gốc và nội dung đang gõ. Bản tạm cũ dạng text vẫn đọc được; nội dung rỗng không bị bỏ qua. Dữ liệu chỉ tồn tại trong tab/session trình duyệt, không thay thế nút tải source. Nếu storage không khả dụng, nội dung trong bộ nhớ vẫn được giữ và giao diện báo để người chơi tải ra file.

Mất mạng vẫn có thể gõ khi cửa sổ coding chưa hết, nhưng save/build/test/import/submit không được gửi khi chưa đối chiếu lại server. Reconnect hoặc khôi phục từ BFCache đọc participant-flow trước rồi đọc workspace/revision. Nếu bản server thay đổi, giữ cả hai bản và yêu cầu chọn KEEP LOCAL, KEEP SERVER hoặc COPY BOTH. Không tự ghi đè source của tab khác. Nếu đã qua deadline, bản local chỉ còn để xem/tải; không gửi PUT mới. Monotonic countdown vẫn khóa thao tác khi mất mạng.

Autosave debounce phân tán theo khóa workspace trong khoảng 2–5 giây để giảm đợt ghi đồng loạt. Không gửi theo từng phím. Chỉ sau khi đối chiếu thành công và server còn cho phép ghi mới lên lịch autosave. Source đang gõ khi một save cũ hoàn tất vẫn được giữ.

## Quy mô và giới hạn

Migration V28 mở sức chứa 2–2000 cho SINGLE_ELIMINATION và SWISS; ROUND_ROBIN/GROUP_KNOCKOUT/TEAM_ROUND_ROBIN vẫn tối đa 32 để tránh lịch vòng tròn hàng triệu trận. API yêu cầu bot policy/enrollment cho giải lớn; form tạo giải đổi giới hạn theo thể thức. Không đổi GameEngine/RuleSet.

Build/test/final validation dùng chung queue PostgreSQL mặc định 200, tối đa hai lease RUNNING và hai worker coding. Khi đầy, trả 429 WORKSPACE_QUEUE_FULL; người dùng thử lại sau, không tự chạy hàng nghìn Docker container. Hàng đợi trận vẫn có giới hạn riêng và worker hiện có. Giải timeline chỉ reserve một đợt trận khởi đầu theo maxConcurrentMatches; runner nạp dần các trận còn lại thay vì reserve cả bracket 2000 người.

Freeze xử lý tối đa 25 người/giải và 50 người toàn cục mỗi lượt. Batch 25 người được commit cùng transaction; nếu batch lỗi thì thử lại từng người, rollback không làm mất lịch sử. Xoay thứ tự duyệt giải cho deadline, final validation, auto-start và notifications để giải lỗi không giữ mãi lượt của giải khác. Đóng check-in cũng giới hạn 50 registration mỗi lượt. Các quyết định đã lưu không được ghi trùng sau restart.

Đã kiểm thử fixture 2000 đơn APPROVED, 1000 workspace và 1000 autosave qua application service với 12 luồng, queue 200/lease 2 và 2000 quyết định freeze. Dữ liệu được seed trong DB test, không phải 2000 phiên đăng nhập HTTP đồng thời và không phải 2000 lần build Docker thật. Thời gian fixture khoảng 6,66 giây trên lần hồi quy này; đây không phải SLA hay bằng chứng đủ tài nguyên cho production. Với nhiều bài, cần bố trí cửa sổ validation đủ dài theo tốc độ worker thực tế.

## Điều chỉnh lịch có audit

Owner/admin có form “Điều chỉnh lịch giải” trong quản lý giải. API `POST /api/tournaments/{id}/timeline/override` nhận revision, action, value (ISO instant) và reason bắt buộc tối đa 400 ký tự. Các action:

- EXTEND_REGISTRATION: tăng hạn đăng ký trước giờ coding; dời các mốc sau tương ứng.
- EXTEND_CODING: tăng hạn coding; dời validation/check-in/start/end tương ứng.
- START_CODING_EARLY: mở coding sớm; nếu cần đóng registration tại mốc coding mới, vẫn kiểm tra thứ tự thời gian.
- EXTEND_CHECK_IN: tăng hạn check-in và dời giờ thi đấu; phục hồi người đã lỡ check-in nếu bot còn hợp lệ.
- DELAY_START: lùi giờ bắt đầu và các mốc sau.

Không áp dụng sau khi đã bắt đầu hoặc ở trạng thái kết thúc. Không mở lại coding khi đã có người check-in, roster đã READY hoặc policy allowResubmitBeforeDeadline không cho phép. Mỗi điều chỉnh ghi actor, reason, timeline cũ/mới và timestamp; revision cũ bị từ chối. Form giữ nội dung đang nhập qua live refresh.

Khi gia hạn sau freeze, các version/freeze cũ giữ nguyên; chỉ mở workspace DRAFT cho cửa sổ mới, hủy job cũ và đánh dấu final cũ đã được supersede trong history. Worker cũ bị fence và không thể ghi đè bài/kết quả mới. Bài cũ không tự trở lại roster; người chơi phải build/kiểm thử/nộp hoặc có READY phù hợp trước hạn mới. Mỗi lần gia hạn coding có thêm tối đa ba lượt build, phần bổ sung từ gia hạn chung giới hạn 300; quota từ các lượt emergency cá nhân đã cấp được tính riêng và giữ lại. Không cho retry hoặc cấp emergency từ final đã supersede. Nguồn vẫn thuộc riêng người chơi.

`participant-flow` bổ sung participantStatus chuẩn hóa cho đăng ký, chờ coding, coding/submission, validation, check-in, chờ thi đấu và kết quả. `emergencyCoding` phân biệt cửa sổ khẩn cấp cá nhân với gia hạn chung. `/me` tiếp tục cung cấp chi tiết match, ready, nhóm/đội và thứ hạng hiện có.

## Vận hành và khôi phục

Schema hiện tại V28. Sao lưu native trước nâng cấp tại `backups/before-stage10-final-v28` (gitignored), gồm pg_dump, bot/replay archive và SHA256 manifest. Không sửa migration cũ; V28 thay constraint sức chứa theo thể thức và thêm index truy vấn ứng viên freeze. Dữ liệu cũ được giữ nguyên. Các nhóm 1–9 tiếp tục dùng dữ liệu/queue/audit cũ, không có runner mới.

## File thêm/sửa

- Thêm draft-recovery.js, timeline-overrides.js và hai client test tương ứng.
- Thêm FairTournamentScan, TimelineOverrideService, migration V28; FairTournamentScanTest, LargeBracketAdmissionTest, LargeTournamentDeadlineTest, TimelineOverrideApiTest.
- Sửa workspace controller, deadline-policy-editor, notifications, tournament-list, toast; OwnedTournamentService, TimelineStartService, TimelineNotificationService, CodingDeadlineService, FinalValidationService/Store, SubmissionStore, TimelineAccessGuard, TimelineRegistrationService, TournamentTimelineController.
- Cập nhật FlywayMigrationTest, TimelineStartApiTest, README và hướng dẫn deployment. Không sửa GameEngine/RuleSet hoặc thuật toán chiến đấu/ghép đối thủ.

## Kết quả kiểm thử

- Hồi quy backend: **196 PASS, 0 FAIL, 0 SKIPPED**, gồm **23 Flyway PostgreSQL tests**, timeline/đăng ký/xét duyệt/coding/freeze/final/check-in/start, override, queue scale, worker và volume fixture. Các ca gia hạn/check-in/start được chạy lại trên các bản chốt; lượt cuối kiểm tra thêm quota emergency khi gia hạn chung, gia hạn sau freeze và khởi động giải.
- Client: **122 PASS, 0 FAIL, 0 SKIPPED**; syntax check workspace/override và script E2E đạt. Kiểm thử bản tạm, revision conflict, giữ local/server/cả hai, reconnect sau deadline, phân tán debounce, giữ form khi live refresh, stepper và routing hiện có. bootJar cuối thành công.
- Native PostgreSQL V28: migration áp dụng trên DB đã sao lưu. API chấp nhận Swiss 2000, từ chối vòng tròn 2000 và loại trực tiếp 2001; giải fixture sức chứa đã hủy, không chạy hàng nghìn bot thật.
- E2E hai tài khoản/Python Docker thật dùng lịch rút gọn theo cùng thứ tự của prompt: UPCOMING chặn signup/editor → đăng ký/duyệt → chờ coding → save/build/public-hidden-sample/submit → freeze và từ chối source gửi trễ → manager gia hạn có audit → giữ phiên bản cũ, mở lại coding → hai bản thay thế READY/submit/freeze/final PASS → check-in/phòng chờ → tự RUNNING → một trận loại trực tiếp → FINISHED, bảng điểm và replay. Bot tự ready theo logic hiện có; entry người dùng bot trả SPECTATOR, không có player token.
- Sau restart JAR cuối: một schedule/match ID, một TOURNAMENT_STARTED, đúng selected version/hash mới, version cũ LOCKED/source hash giữ nguyên, participantStatus kết quả và replay events/finalState còn nguyên. Quyền sửa source vẫn đóng.
- **Chưa chạy E2E trực quan trong browser**: công cụ trả `Local browser use is disabled in Work settings`. Không có screenshot, không coi mô phỏng model client hoặc native API là bằng chứng thao tác mất mạng thật của trình duyệt. Đây là phần cần kiểm tra thủ công khi browser được bật; không ảnh hưởng các kết quả backend/client/native đã chạy.
