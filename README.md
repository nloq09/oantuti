# RPS Battle — Oẳn Tù Tì Chiến Thuật

Game chiến thuật trên bàn cờ **9 × 9**, kết hợp luật Búa–Bao–Kéo với di chuyển theo lượt. Server Java xử lý luật và trạng thái trận; client chạy trên trình duyệt, đồng bộ thời gian thực qua WebSocket.

Repository: [nloq09/oantuti](https://github.com/nloq09/oantuti).

## Chức năng hiện tại

### Tài khoản và lịch sử

- Đăng ký/đăng nhập/đăng xuất bằng session cookie, thanh điều hướng theo phiên.
- Hồ sơ: xem thông tin và thống kê, sửa tên hiển thị, đổi mật khẩu rồi đăng nhập lại.
- Lịch sử trận theo tài khoản, filter/phân trang 20 trận và mở replay bằng archive UUID.
- Khách vẫn chơi tay/đấu với bot của game và xem giải công khai; bot riêng, hồ sơ, lịch sử và quản lý giải cần đăng nhập.
- [Hướng dẫn giao diện tài khoản](docs/ACCOUNT-UI.md).


### Chế độ và phòng chơi

- **Hai người**: tạo phòng, sao chép/chia sẻ mã và tham gia phòng từ trình duyệt khác.
- **Đấu với bot**: chơi với bot chiến thuật có sẵn của game.
- Trong **cả hai chế độ**, mỗi người được chọn **Tôi tự chơi** hoặc bot riêng đã tải lên để điều khiển quân. Lựa chọn áp dụng khi tạo hoặc tham gia phòng.
- Giao diện chỉ có hai chế độ trên, không có nút chế độ Bot đấu bot. Hai người vẫn có thể cùng chọn bot riêng trong một phòng Hai người.
- Phòng quản lý vị trí Xanh/P1 và Đỏ/P2, kết nối, xếp quân và trạng thái sẵn sàng.
- Server xác thực token vị trí, kiểm tra lượt/sequence và từ chối thao tác không hợp lệ.

### Bàn cờ và trận đấu

- Đặt quân thủ công, di chuyển quân đang xếp, xếp ngẫu nhiên, đặt lại, sẵn sàng và hủy sẵn sàng.
- Bàn cờ xoay theo người chơi: **quân của bạn luôn ở dưới**, kể cả P2; tọa độ gửi tới server giữ nguyên.
- Đánh dấu quân đã chọn, ô di chuyển hợp lệ và ô tấn công; hiệu ứng di chuyển/giao chiến.
- Đồng hồ lượt, số quân từng loại của hai bên và diễn biến trận; nút Xóa chỉ xóa nhật ký đang hiển thị.
- Bỏ lượt, đầu hàng và rời phòng. Rời khi trận đang chơi được tính là đầu hàng.
- Tự kết nối lại và nhận trạng thái đầy đủ khi WebSocket bị mất kết nối.
- Màn hình kết quả hiển thị thắng/thua/hòa, thời gian, số lượt và quân còn lại; tạo ván mới hoặc về trang chủ.
- Nhật ký ở màn hình kết quả chỉ chứa sự kiện trong trạng thái cuối trận, **không phải replay toàn trận**.
- Nút **Replay đầy đủ** mở toàn bộ diễn biến từ trạng thái đầu trận, hỗ trợ tua, bước trước/sau, phát/dừng và ×1/×2/×4.

### Giải đấu, xếp hạng và khán giả — Chặng 3

- Home → **Giải đấu**: tạo giải Vòng tròn hoặc Loại trực tiếp ngay cả khi chưa có bot; chia sẻ mã/link để người khác tham gia và đăng ký bot đã lưu hoặc tải bot mới ngay trong giải. Giải có enrollment hỗ trợ tối đa 4.000 người với Loại trực tiếp/Swiss; các thể thức còn lại tối đa 32. Số trận Docker chạy đồng thời vẫn có giới hạn. Vòng tròn chia vòng/BYE, tổng `n × (n − 1) / 2` trận; loại trực tiếp có bracket/BYE và `n − 1` trận thật. Chủ giải đặt giới hạn chạy đồng thời và thứ tự tie-break trước READY.
- Bản nháp cho phép thêm/gỡ bot; Bắt đầu kiểm tra artifact nội bộ và Docker rồi khóa danh sách.
- Server tự cấu hình hai seat trong chế độ `HUMAN_VS_HUMAN`, xếp quân, sẵn sàng và chạy qua Match/GameEngine hiện có. Không tạo game mode hoặc engine mới.
- Hàng đợi giới hạn mặc định 4 trận đồng thời trên toàn bộ giải đấu trong một server, có thêm giới hạn riêng mỗi giải. Vòng tròn tiếp tục khi một trận lỗi hạ tầng; loại trực tiếp chuyển RECOVERY_REQUIRED để không tự gán người thắng. Có thể hủy giải.
- Cập nhật trực tiếp tiến độ, LIVE, kết quả và bảng xếp hạng qua WebSocket.
- Thắng 3, hòa 1, thua 0 điểm; tie-break tùy chỉnh gồm số thắng, đối đầu, quân còn và thời gian tính nước trung bình. Loại trực tiếp ưu tiên kết quả bracket; hòa chọn người đi tiếp theo quân còn rồi hạt giống. Trận lỗi hạ tầng `FAILED` và BYE không tính điểm.
- Xem LIVE trong `game.html` ở chế độ khán giả chỉ đọc, không chiếm seat; server từ chối thao tác chơi từ spectator.
- Giải hoàn tất hiển thị vô địch; replay đầy đủ và xuất JSON metadata/participants/matches/ranking. Token bot không xuất trong JSON công khai.
- Giải đấu lưu PostgreSQL; replay trận kết thúc lưu JSON + metadata PostgreSQL, đọc được sau restart. Chặng 3 là nền tảng ban đầu: [CHANG-3.md](docs/CHANG-3.md); hành vi hiện tại: [lưu giải đấu](docs/TOURNAMENT-OWNERSHIP.md), [lưu replay](docs/REPLAY-PERSISTENCE.md).

### Remote Bot Worker — Chặng 11

Đã triển khai **nhóm 1–10**: ứng dụng `rps-worker.jar` riêng, token đăng ký một lần/giới hạn lượt, credential và state riêng tư; agent báo capabilities, heartbeat mỗi 5 giây, Central đánh dấu OFFLINE sau 30 giây. Admin có danh sách máy, tạo/thu hồi token, duyệt TRUSTED/COMMUNITY hoặc từ chối có reason/revision/audit, và tải JAR qua trang setup. Schema V29/V30 chỉ lưu hash secret; worker dùng xác thực stateless và chủ động kết nối ra Central. Nhóm 3 đã có API claim/assigned/decline, queue PostgreSQL và reservation slot; SDK claim nằm trong worker 0.10.0. Nhóm 4 bổ sung tải artifact theo execution còn lease, SHA-256 và cache riêng tư 20 GiB với LRU/pin; có --prefetch để tải các assignment đã nhận. Nhóm 5 có --run-once để claim/start và chạy một batch headless trong Docker, giữ bot suốt trận, lưu kết quả/replay riêng tư và chặn chạy trùng. Nhóm 6 bổ sung upload kết quả + replay, V32 lưu biên nhận chống trùng, ACK trả slot và bộ áp dụng vào history/replay/ranking/bracket; --upload-pending gửi lại kết quả đã hoàn thành. Nhóm 7 bổ sung lease renewal, grace 30 giây, requeue có execution mới, tối đa 3 lần thử và --recover để đối chiếu state/cleanup orphan/upload khi reconnect. Schema V33 giữ lịch sử recovery; lỗi hạ tầng không tạo winner giả. Nhóm 8 bổ sung Drain/DRAINED, disable hủy execution, bật lại với epoch mới, revoke credential vĩnh viễn và lịch sử quản trị; các thao tác có reason/revision/CSRF, đồng bộ với claim/artifact/result qua database guard. Nhóm 9 nối scheduler giải vào remote queue theo nhóm worker và policy REMOTE_REQUIRED, mặc định TRUSTED; V34 giữ nhóm/membership/quota/routing. Có trần CPU/RAM/admin, giảm nhận việc khi tải cao, ưu tiên cache kèm FIFO cho job chờ từ 30 giây. Worker 0.10.0 có --serve để tự nhận batch liên tục. Giải chưa cấu hình vẫn chạy local; remote hiện dành cho BOT_ONLY dùng submission LOCKED. Agent không có --serve vẫn chỉ heartbeat/recovery. Xem [nền tảng danh tính](docs/CHANG-11-NHOM-1-WORKER-IDENTITY.md) và [heartbeat, giao diện admin, hướng dẫn chạy](docs/CHANG-11-NHOM-2-HEARTBEAT-ADMIN.md) và [claim job, slot và giới hạn tích hợp](docs/CHANG-11-NHOM-3-JOB-CLAIM-SLOTS.md) và [artifact, SHA-256, cache](docs/CHANG-11-NHOM-4-ARTIFACT-CACHE.md) và [chạy headless, Docker và outbox](docs/CHANG-11-NHOM-5-HEADLESS-EXECUTION.md) và [upload kết quả/replay, idempotency](docs/CHANG-11-NHOM-6-RESULT-REPLAY-UPLOAD.md) và [lease/crash/network recovery](docs/CHANG-11-NHOM-7-LEASE-RECOVERY.md) và [drain/disable/revoke, quyền và chính sách vận hành](docs/CHANG-11-NHOM-8-WORKER-CONTROL-SECURITY.md) và [capacity, worker groups, cấu hình giải và --serve](docs/CHANG-11-NHOM-9-CAPACITY-GROUPS.md).

Nhóm 10 bổ sung capacity 4.000 người cho Loại trực tiếp/Swiss, metrics hàng đợi/receipt/cache trên trang Worker, observer CLI và baseline Docker một trận. Benchmark PostgreSQL đã kiểm tra 3.999 receipt với 10 identity mô phỏng và đỉnh 80 reservation; đây không phải 10 máy Docker vật lý. Xem [cách chạy, kết quả và giới hạn kiểm chứng](docs/CHANG-11-NHOM-10-SCALE-BENCHMARK.md).

### Chặng 12 — Luồng giao diện giải đấu

Đã triển khai nhóm 1: header và stepper thống nhất trên detail/lobby/workspace, model Next Action và khối “Việc của bạn” theo quyền server. Các nhóm Organizer/timeline create/remote status tiếp tục theo lộ trình riêng. [Phạm vi, file thay đổi và kiểm chứng](docs/CHANG-12-NHOM-1-PLAYER-SHELL.md).

Nhóm 2 bổ sung giao diện trước giải: lịch/luật trước đăng ký, trạng thái đơn và chờ coding, bài đã khóa, các bước kiểm thử chính thức, check-in và phòng chờ. Liên kết xem source mở snapshot đúng phiên bản ở chế độ chỉ đọc, có kiểm tra tài khoản/giải; không mở nhầm draft đang sửa. [Phạm vi, API và kiểm chứng](docs/CHANG-12-NHOM-2-PRE-TOURNAMENT.md).

Nhóm 3 bổ sung form tạo cuộc thi bot với timeline đầy đủ, xem trước lịch theo múi giờ thiết bị, kiểm tra thứ tự các mốc ngay dưới field và đồng bộ giờ nhận/nộp bot với coding. Tạo giải giữ trạng thái nháp; chủ giải mở đăng ký sau khi kiểm tra cấu hình. Giải có người tự chơi giữ lịch thủ công. [Hướng dẫn và kiểm chứng](docs/CHANG-12-NHOM-3-CREATE-TIMELINE.md).

Nhóm 4 bổ sung khung quản lý cho chủ giải: header với giai đoạn/hạn giờ từ server, sidebar trên desktop và menu thu gọn trên màn hình nhỏ. Các trang duyệt đơn, bài nộp và kiểm thử dùng chung điều hướng; audit có mục riêng và các mục chính hỗ trợ link trực tiếp. Chưa có dữ liệu sức khỏe hạ tầng tổng hợp thì hiển thị rõ chưa xác minh. [Phạm vi và kiểm chứng](docs/CHANG-12-NHOM-4-ORGANIZER-SHELL.md).

Nhóm 5 bổ sung thống kê theo giai đoạn từ API riêng có kiểm tra quyền: đăng ký, coding, validation, check-in và thi đấu. Đếm toàn giải thay vì một trang danh sách, tránh trùng người có nhiều workspace và bỏ validation đã bị thay thế. Bộ lọc validation chạy trước phân trang; thông tin worker chỉ giới hạn máy đang giữ assignment của giải. [Định nghĩa số liệu, API và kiểm chứng](docs/CHANG-12-NHOM-5-PHASE-METRICS.md).

Nhóm 6 bổ sung bảng “Cần xử lý” theo phase với ba mức Thông tin/Cần chú ý/Khẩn cấp, liên kết tới đúng đơn, bài kiểm thử hoặc bộ lọc trận/check-in. Chỉ nhận snapshot cùng giải và revision; lỗi cập nhật được báo dữ liệu cũ. Không suy đoán trận treo hay thiếu capacity. [Quy tắc và kiểm chứng](docs/CHANG-12-NHOM-6-NEEDS-ATTENTION.md).

Nhóm 7 bổ sung API read-only trạng thái remote theo giải, lọc/phân trang, thông tin attempt/worker/lease và phân biệt kết quả đang chờ áp dụng. Schema V36 và worker 0.11.0 thêm tín hiệu bắt đầu gameplay sau chuẩn bị sandbox, tách STARTING khỏi RUNNING_REMOTE. Nâng Central trước rồi cập nhật worker; worker cũ vẫn chạy nhưng chưa báo được gameplay. Màn hình My Match/Match Monitor dùng dữ liệu này ở nhóm 8. [API, chuyển trạng thái và kiểm chứng](docs/CHANG-12-NHOM-7-REMOTE-READ-MODEL.md).

Nhóm 8 nối trạng thái execution vào thẻ **Trận của tôi** trên detail/lobby và tab phân trang riêng. Chủ giải có **Match Monitor** với bộ lọc, worker/lease/heartbeat, mốc gameplay/receipt và lịch sử các lần nhận việc. API chỉ cho người chơi đọc trận của mình; monitor/history dành cho chủ giải/admin. Trận remote không có stream sẽ không hiện nút xem trực tiếp; kết quả pending được phân biệt với kết quả đã áp dụng. [API, file thay đổi và kiểm chứng](docs/CHANG-12-NHOM-8-MY-MATCH-MONITOR.md).

Nhóm 9 thống nhất model CTA/route giữa hub, progression và player; card discovery và thông báo lịch sử mở trang giải để lấy quyền hiện tại, không đoán quyền coding/check-in. Các trang tournament dùng refresh queue chung có coalescing, abort/generation guard, dừng khi ẩn tab và phục hồi BFCache; WebSocket compact chỉ invalidation rồi tải HTTP. Giữ bộ lọc, form đang nhập và các kiểm soát mutation hiện có. [Luồng, file và kết quả kiểm chứng](docs/CHANG-12-NHOM-9-NAVIGATION-REFRESH.md).

Nhóm 10 bỏ ô hẹn công bố chưa có tác dụng; kết quả hiển thị khi giải kết thúc theo read model server. Cải thiện bố cục màn hình nhỏ, vùng bảng cuộn và focus khi refresh; regression PASS 218 client tests, 152 ca backend và smoke HTTP/WebSocket trên PostgreSQL. Đã triển khai code đủ 10 nhóm; kiểm tra trực quan và browser click E2E còn chờ bật quyền trình duyệt. [Quyết định công bố, file và kết quả kiểm chứng](docs/CHANG-12-NHOM-10-RESULT-RESPONSIVE-REGRESSION.md).

Đã sửa các điểm bất hợp lý sau rà soát: xếp đội trước giờ thi, hạn nộp đội hình 5 phút có fallback, giữ giải khi validation bị lỗi hạ tầng, phân nhóm discovery theo timeline, phục hồi/sửa lịch bản nháp, kết quả đang review và tối ưu refresh/xếp hạng. [Hành vi, API và kiểm thử](docs/TOURNAMENT-FLOW-REPAIRS.md).

### Bot của người chơi

- Trang **Bot của tôi** quản lý bot theo tài khoản trong PostgreSQL, chọn bot và tải file lên server.
- Hỗ trợ **Python `.py`, C++ `.cpp`, Java `.java` và `.jar`**.
- Chọn ngôn ngữ, xem trạng thái Docker, nhật ký tải lên và dùng bot ở trang Vào trận.
- **Đấu thử với bot** tạo một trận thật với bot chiến thuật của game.
- Bot được tự xếp 18 quân, tự sẵn sàng và tự chọn nước đi; chủ bot theo dõi trận.
- Bot sai giao thức, trả nước đi không hợp lệ, lỗi tiến trình hoặc quá thời gian sẽ bị xử thua.
- Bot riêng chạy trong container Docker; chơi tay và bot chiến thuật có sẵn **không cần Docker**.
- ZIP, tải manifest riêng, sửa mã nguồn và build độc lập chưa được hỗ trợ.

### Giao diện

- Trang chủ có Bắt đầu chơi, Bot của tôi, Cách chơi và Cài đặt.
- Trang Vào trận có chọn chế độ, chọn người/bot điều khiển, tạo phòng; phần nhập mã phòng nằm dưới khung Kết nối với đối thủ ở cột phải.
- Phong cách đấu trường với nền lâu đài, khung vàng, màu Xanh/Đỏ; bố cục thích ứng điện thoại.
- Cài đặt âm thanh, nhạc nền và hiệu ứng lưu trên trình duyệt.
- Hướng dẫn bot và giao thức có cả bản Markdown và trang web.

### Lập trình bot trong giải đấu — Chặng 9

Đã triển khai nhóm 1–10: chính sách nộp bot, [workspace và build Python/C++/Java](docs/CHANG-9-NHOM-2-5-WORKSPACE.md), public/hidden tests, đấu mẫu, lịch sử version, submit/lock theo deadline và dashboard bài nộp cho chủ giải/admin. Bot có sẵn và bot code trên hệ thống dùng chung pipeline validation; trận chính thức chạy đúng artifact đã khóa, không compile lại. Queue PostgreSQL có giới hạn, lease và recovery sau restart. Xem [luồng sử dụng, API, vận hành và giới hạn nhóm 6–10](docs/CHANG-9-NHOM-6-10-SUBMISSIONS.md). Schema bài nộp dùng V23; V26/V27 lưu quyết định khóa bài và kết quả kiểm thử chính thức; V28 bổ sung sức chứa theo thể thức và index cho batch deadline; V29 thêm danh tính remote worker; V30 bổ sung capabilities, heartbeat và audit duyệt máy; schema hiện tại V31 thêm hàng đợi claim/execution và reservation remote.

Chặng 10 đã triển khai đủ 10 nhóm chức năng. [Nhóm 1 — Timeline và Phase Resolver](docs/CHANG-10-NHOM-1-TIMELINE.md) bổ sung lưu lịch, API cấu hình có quyền/revision/audit và phase theo giờ server. [Nhóm 2 — Đăng ký người chơi](docs/CHANG-10-NHOM-2-REGISTRATION.md) đã tách đơn đăng ký khỏi chọn bot, mở/đóng form theo phase server và chặn truy cập editor sớm. [Nhóm 3 — Xét duyệt và chờ coding](docs/CHANG-10-NHOM-3-REVIEW-WAITING.md) đã có trang manager/admin duyệt, từ chối hoặc waitlist với lý do/revision/audit; người được duyệt thấy countdown, luật và protocol trước giờ code. [Nhóm 4 — Workspace theo timeline](docs/CHANG-10-NHOM-4-CODING-WINDOW.md) đã đồng bộ cửa sổ coding, countdown/cảnh báo, ba cách nộp và quyền đọc bài sau giờ đóng. [Nhóm 5 — Khóa bài và tự nộp](docs/CHANG-10-NHOM-5-DEADLINE-FREEZE.md) đã có hai chính sách deadline, snapshot bài khóa bền vững, xử lý idempotent sau restart và màn hình xem bài đã nộp/thiếu bài. [Nhóm 6 — Kiểm thử chính thức](docs/CHANG-10-NHOM-6-FINAL-VALIDATION.md) đã có pipeline trên frozen artifact, hàng đợi chung có lease/recovery, dashboard owner/admin, ba chính sách bài lỗi và lượt nộp khẩn cấp có audit. Bot chỉ được duyệt roster sau final PASS; [Nhóm 7 — Check-in và phòng chờ](docs/CHANG-10-NHOM-7-CHECK-IN.md) đã mở check-in theo giờ server, yêu cầu final PASS/applied và đơn APPROVED, lưu thời điểm xác nhận, loại người bỏ lỡ hạn khỏi roster và hiển thị phòng chờ có version, thông báo, luật, lịch/countdown. [Nhóm 8 — Tự bắt đầu và lobby](docs/CHANG-10-NHOM-8-START-LOBBY.md) đã chốt roster đúng giờ server, xác minh frozen artifact, khởi tạo lịch/queue trong transaction có rollback và chuyển trang chi tiết sang lobby khi giải thực sự RUNNING. Giải thiếu người được hủy có lý do; lỗi chuẩn bị được ghi audit và thử lại. [Nhóm 9 — Thông báo và tiến trình](docs/CHANG-10-NHOM-9-NOTIFICATIONS-PROGRESS.md) đã bổ sung nhắc theo lịch có chống trùng bền vững, thông báo riêng cho đơn đăng ký/kết quả final, thanh sáu bước và CTA theo phase server. [Nhóm 10 — Offline, quy mô và E2E](docs/CHANG-10-NHOM-10-OFFLINE-SCALE-E2E.md) bổ sung đối chiếu revision khi reconnect, bản nháp tạm, autosave 2–5 giây, batch/cursor công bằng, V28 cho giải Loại trực tiếp/Swiss tới 2.000 người và điều chỉnh lịch có audit. Giải không có timeline giữ cách hoạt động cũ.

### Chế độ giải đấu cho người chơi — Chặng 8

- Hub khám phá giải theo 5 nhóm, tìm kiếm và phân trang; các tab chi tiết theo thể thức và trạng thái riêng của từng đăng ký.
- Tạo giải BOT_ONLY / HUMAN_ONLY / HUMAN_OR_BOT, đăng ký Tôi tự chơi hoặc bot READY, duyệt và check-in theo giờ server.
- Lobby có đối thủ tiếp theo, rank và trận đang chạy. Người thật xác nhận sẵn sàng trong cửa sổ 30 giây, sau đó tự vào đúng ghế; bot dùng executor và sandbox hiện có.
- Ready/entry có quyền theo tài khoản, CSRF, idempotency và no-show. V17 giữ dữ liệu bot cũ và thêm danh tính human; replay và gameplay dùng engine hiện tại.
- [Hướng dẫn, API, migration và kết quả kiểm thử](docs/CHANG-8-NHOM-1-5-TOURNAMENT-MODE.md). Đã bổ sung context trận BO1, bracket focus/zoom, lịch lọc/phân trang, luật snapshot, kết quả/podium, thông báo cá nhân lưu trạng thái đã đọc, reconnect và báo cáo sự cố. [Tiến độ và giới hạn nhóm 6–10](docs/CHANG-8-NHOM-6-10.md). Đã hỗ trợ [BO1/BO3/BO5 thật](docs/TOURNAMENT-SERIES.md): phòng/replay riêng từng ván, ready check mới, tỷ số series và điểm tính một lần cho cả cặp đấu. Đã bổ sung [hai bảng → bán kết chéo → chung kết](docs/TOURNAMENT-GROUP-STAGE.md), bảng điểm riêng, qualification snapshot và phục hồi PostgreSQL. [Swiss và Đồng đội / trường](docs/TOURNAMENT-SWISS-TEAM.md) có ghép vòng không lặp, BYE/Buchholz, đội trưởng, roster, lineup riêng tư trước khóa và fixture ba bàn. Đã bổ sung [vòng bảng nhiều giai đoạn, rút giữa giải và phân trang server](docs/TOURNAMENT-COMPLETION.md): cấu hình số bảng/top N mỗi giai đoạn rồi loại trực tiếp, rút từng suất người/bot và tải lịch/trận/replay theo trang.

### Quản trị

**Chặng 7 — Nhóm 6–10:** quản trị giải/participants/lifecycle, tranh chấp dựa trên review có audit, kiểm tra replay, audit chỉ đọc và System Health. Thêm cảnh báo polling, responsive/accessibility và hồ sơ ADMIN trong theme game; migration V16 giữ dữ liệu cũ. Kiểm thử cuối: 493 backend và 38 client qua, không bỏ qua; bootJar thành công. [Chức năng, API, giới hạn vận hành và kiểm thử](docs/CHANG-7-NHOM-6-10-ADMIN.md).

## Luật chơi

1. Mỗi bên có **6 Búa, 6 Bao, 6 Kéo**; đặt trong nửa bàn cờ của mình, chia bởi đường chéo A1–I9 (P1: row > col; P2: row < col); không đặt trên đường chéo hoặc cứ điểm. Trận bắt đầu khi cả hai bên đủ quân và sẵn sàng.
2. Mỗi lượt di chuyển một quân **một ô theo cả 8 hướng**, gồm ngang, dọc và chéo; không ra ngoài bàn hoặc vào ô có quân cùng bên.
3. Đi vào quân đối thủ để giao chiến: **Búa thắng Kéo, Kéo thắng Bao, Bao thắng Búa**. Cùng loại thì hòa: cả hai sống và giữ nguyên vị trí, vẫn tính một lượt.
4. Cứ điểm của mỗi đội nằm ở **góc dưới bên trái theo góc nhìn của đội đó**: P1 ở **A9 `(8,0)`**, P2 ở **I1 `(0,8)`**. Chiếm cứ điểm đối thủ (quân tấn công phải sống và đứng trên ô đó sau giao chiến) hoặc loại hết quân đối thủ thì **thắng ngay**. Không được đặt quân vào cứ điểm khi xếp đội hình ban đầu; chỉ được di chuyển quân vào để phòng thủ sau khi trận bắt đầu. Cả hai hết quân thì hòa. Đầu hàng thì đối thủ thắng.
5. Giới hạn mặc định **200 lượt**: bên còn nhiều quân hơn thắng, bằng nhau thì hòa.
6. Thời gian lượt online mặc định **30 giây**; hết giờ server tự bỏ lượt. Đây là cấu hình server, không phải lựa chọn trên client.

## Công nghệ và yêu cầu

- **Server**: Java 21, Spring Boot 3.5.16, Gradle Wrapper 8.14.3, REST và Spring WebSocket; Jackson xử lý JSON.
- **Client**: HTML/CSS và JavaScript ES modules, không cần framework hay npm để chơi.
- **Kiểm thử**: JUnit/Spring Boot Test và Node.js test runner.
- **Bot riêng**: Docker Engine Linux hoặc Docker Desktop ở chế độ Linux containers.
- Dùng **JDK 21 hoặc JDK 24** để chạy Gradle hiện tại; không dùng JDK 25 với wrapper 8.x. Mã được biên dịch cho Java 21.
- Node.js dùng để chạy test client; script kiểm thử upload cần bản có `fetch`, `File` và `WebSocket` toàn cục, ví dụ Node.js 24.

## Chạy trong VS Code / Windows

**Chặng 4 — Nhóm 1:** server hiện yêu cầu PostgreSQL trước khi khởi động. Tạo DB và nạp `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` theo [hướng dẫn database](docs/CHANG-4-NHOM-1-DATABASE.md). Database schema đã có nền Flyway/JPA; đăng nhập và lưu các tính năng vào DB sẽ triển khai ở các nhóm tiếp theo.

**Nhóm 2:** đã có API tài khoản `/api/auth/register`, `/login`, `/logout`, `/me`, `/csrf`; session HttpOnly và BCrypt, tài khoản lưu PostgreSQL. [Cách gọi API và chính sách auth](docs/AUTH.md), [báo cáo Nhóm 2](docs/CHANG-4-NHOM-2-AUTH.md). Chưa thêm trang login/profile; game và quyền bot/giải đấu hiện vẫn giữ flow cũ, sẽ migrate theo account ở các nhóm sau.

**Nhóm 3:** thêm API profile, đổi mật khẩu, quản trị users và session revalidation theo DB. Đổi password/disable account làm phiên cũ mất hiệu lực; profile chỉ sửa displayName; admin API cần role ADMIN. [Báo cáo Nhóm 3](docs/CHANG-4-NHOM-3-ACCOUNT.md). Giải đấu chuyển ownership ở Nhóm 6; UI profile/login ở Nhóm 8.

**Nhóm 4:** metadata bot lưu PostgreSQL theo chủ tài khoản; upload/dùng bot riêng cần đăng nhập và CSRF. Có My Bots API, đổi tên, xóa mềm, nhập bot cũ bằng token chứng minh. Client lấy danh sách từ server; token trong trình duyệt không còn là quyền dùng bot. [Báo cáo Nhóm 4](docs/CHANG-4-NHOM-4-BOTS.md), [hướng dẫn tài khoản và nhập bot](docs/BOT-OWNERSHIP.md). Chưa có trang login: đăng nhập qua API/helper theo hướng dẫn cho tới Nhóm 8.

**Nhóm 5:** metadata trận/ghế lưu PostgreSQL với archive UUID riêng; thêm `GET /api/account/matches`, detail và `GET /api/account/stats`, filter/phân trang và thống kê theo chủ ghế kể cả bot chơi thay. Không cộng lại khi nhận thông báo kết thúc lặp. [Hướng dẫn lịch sử](docs/MATCH-HISTORY.md), [báo cáo Nhóm 5](docs/CHANG-4-NHOM-5-HISTORY.md). Bàn cờ đang chơi vẫn ở RAM; UI History đã có ở Nhóm 8.

**Nhóm 6:** giải/roster/lịch/kết quả lưu PostgreSQL; quyền quản lý dùng owner account/ADMIN, bỏ capability client. Người khác vẫn join bằng bot của họ khi DRAFT. Restart giữ ranking/kết quả và đánh dấu RECOVERY_REQUIRED cho giải đang chạy, không tự chạy lại. [Hướng dẫn giải đấu](docs/TOURNAMENT-OWNERSHIP.md), [báo cáo Nhóm 6](docs/CHANG-4-NHOM-6-TOURNAMENT.md). Bản đầu chỉ hỗ trợ PUBLIC.

**Nhóm 7:** replay trận thường/giải đấu lưu JSON trên ổ bền vững và metadata PostgreSQL; kiểm tra SHA-256 và kết quả dựng từ events khi đọc, xử lý file thiếu/hỏng, dùng archive UUID cho link giải đấu. [Hướng dẫn replay](docs/REPLAY-PERSISTENCE.md), [báo cáo Nhóm 7](docs/CHANG-4-NHOM-7-REPLAY.md). Cấu hình thư mục bằng REPLAY_DIRECTORY; backup cả DB và thư mục này.

**Nhóm 8:** thêm login.html, register.html, profile.html, history.html; navbar theo session, guest policy rõ, profile/password/history nối API có sẵn. [Hướng dẫn](docs/ACCOUNT-UI.md), [báo cáo](docs/CHANG-4-NHOM-8-ACCOUNT-UI.md).

**Nhóm 9:** thêm MatchWorker/LocalMatchWorker, FIFO có giới hạn và giữ chỗ toàn lịch giải; mặc định 4 trận giải chạy/256 trận chờ. Quá tải trả SERVER_BUSY, rate limit HTTP/WS trả RATE_LIMITED; chống đăng nhập sai liên tục, Actuator metrics chỉ ADMIN xem và checkpoint trước khi đóng server. [Cấu hình, monitoring và giới hạn](docs/WORKER-OPERATIONS.md), [báo cáo](docs/CHANG-4-NHOM-9-WORKER.md).

**Nhóm 10:** Dockerfile Java 21, Compose PostgreSQL/server/Caddy HTTPS với volume bền vững và role DB ứng dụng riêng; runner bot opt-in socket, source mount một file và label cleanup. Công cụ backup/restore DB + source + replay có checksum, restore chỉ vào target trống. [Triển khai](docs/DEPLOYMENT.md), [vận hành](docs/OPERATIONS.md), [tài khoản/quyền](docs/AUTH.md), [báo cáo](docs/CHANG-4-NHOM-10-DEPLOYMENT.md).

**Chặng 5 — Nhóm 1:** chuẩn hóa vòng đời giải bằng state machine, migration V10 giữ dữ liệu cũ và lưu thời điểm đổi trạng thái. Chủ giải có thể mở đăng ký, khóa roster rồi bắt đầu; READY chưa có lịch đấu giữ nguyên qua restart. [Phạm vi, API và kết quả kiểm thử](docs/CHANG-5-NHOM-1-LIFECYCLE.md), [kế hoạch chặng 5](docs/CHANG-5-TOURNAMENT-PROMPT.md). Các nhóm tiếp theo và tổng kết Chặng 5 nằm bên dưới.

**Chặng 5 — Nhóm 2:** đơn đăng ký PENDING, duyệt/từ chối/rút, cấu hình giới hạn và check-in; bot vắng mặt bị loại khỏi roster READY. Source được kiểm tra biên dịch trong Docker trước khi nhận vào giải; V11 giữ roster cũ và lưu quyết định/timestamps qua restart. [Cách dùng, API và kiểm thử](docs/CHANG-5-NHOM-2-REGISTRATION.md). Tiếp theo: khóa phiên bản/hash và seeding.

**Chặng 5 — Nhóm 3:** khóa bot vào bản sao source/JAR theo SHA-256, kiểm tra integrity khi start/launch; thêm RANDOM/MANUAL/RANKING, seed 64-bit và lưu thứ tự qua restart bằng V12. [Cách dùng, giới hạn và kiểm thử](docs/CHANG-5-NHOM-3-BOT-LOCK-SEEDING.md). Nhóm 4–6 bổ sung bên dưới.

**Chặng 5 — Nhóm 4–6:** Round Robin theo vòng/BYE, loại trực tiếp 2–32 bot với bracket và đi tiếp do server quyết định, standings/tie-break tùy chỉnh, thời gian tính nước và lưu qua restart bằng V13. Đã kiểm thử chung: 462 backend, 27 client, trong đó 11 migration tests; Docker nhiều vòng và native restart đều qua. [Cách dùng, API, giới hạn và danh sách file](docs/CHANG-5-NHOM-4-6-ROUNDS-BRACKET-STANDINGS.md). Nhóm 7–10 đã hoàn thiện bên dưới.

**Chặng 5 — Nhóm 7–10 hoàn tất:** pause/resume, chuyển vòng thủ công, forfeit/DQ, review/override/retry có audit, dashboard/realtime/replay, lịch sử tài khoản và recovery. V14 lưu audit và version lock. Đã kiểm thử 480 backend, 31 client, gồm 12 migration tests; demo 8 tài khoản và Docker RR8/SE16/SE32 đều qua. [Hướng dẫn, chính sách kết quả và tổng kết](docs/CHANG-5-FINAL-TOURNAMENT.md).

**Chặng 7 — Nhóm 1–5:** giao diện ADMIN riêng tại `/admin/index.html`: shell/sidebar, dashboard runtime, tìm/lọc/phân trang và chi tiết user/bot/match. Khóa/mở user/bot và lưu trữ bot yêu cầu lý do, CSRF và audit V15; review/retry dùng workflow giải đấu. ADMIN có liên kết **Quản trị** trên menu tài khoản; USER không truy cập được dữ liệu admin. Đã kiểm thử 486 backend, 34 client, build bootJar và thao tác UI trên PostgreSQL thật. [Hướng dẫn, API, chính sách và giới hạn](docs/CHANG-7-NHOM-1-5-ADMIN.md), [yêu cầu gốc](docs/CHANG-7-ADMIN-UI-PROMPT.md).

ADMIN đăng nhập vào thẳng khu quản trị, dùng giao diện lâu đài/viền vàng giống game và không có menu vào chơi. Replay và xem trực tiếp chỉ đọc cũng nằm trong shell ADMIN. USER tiếp tục sử dụng giao diện game; bản điều chỉnh này đã qua 35 test client và 11 test hồi quy login/account/admin.

Để chạy bộ production, copy `infra/.env.production.example` sang `.env.production`, điền domain và hai mật khẩu DB riêng, tải image bot nếu cần, rồi:

```bash
docker compose --env-file .env.production -f infra/docker-compose.yml -f infra/docker-compose.bots.yml up -d --build --wait --wait-timeout 180
```

Client/API/WebSocket cùng `https://<RPS_DOMAIN>/`. Chạy không cần bot upload thì bỏ override `docker-compose.bots.yml`; bot chiến thuật có sẵn vẫn hoạt động. Hướng dẫn HTTPS/domain, quyền Docker socket, backup/restore và giới hạn nằm trong DEPLOYMENT.md.




Mở thư mục repository trong VS Code, mở terminal PowerShell tại thư mục gốc:

```powershell
cd server
# Nếu Java mặc định chưa phù hợp, đặt đường dẫn JDK thực tế trên máy:
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21'
.\gradlew.bat bootRun --console=plain
```

Nếu đang dùng JDK 24 đã cài trên máy phát triển hiện tại, có thể thay dòng JAVA_HOME bằng:

```powershell
$env:JAVA_HOME = 'C:\Users\hi\.jdks\openjdk-24.0.2+12-54'
```

Mở [http://localhost:8080](http://localhost:8080). Server phục vụ cả client và API, không cần Live Server hoặc chạy frontend riêng. Giữ terminal server mở; **Ctrl+C** để dừng.

Sau khi sửa code: dừng server, chạy lại `bootRun`, rồi **Ctrl+F5** trên trình duyệt. Tài nguyên client được copy vào server khi build/chạy.

Đổi cổng nếu 8080 đang được sử dụng:

```powershell
.\gradlew.bat bootRun --console=plain --args="--server.port=8081"
```

Kiểm tra sức khỏe server từ terminal khác:

```powershell
Invoke-RestMethod http://localhost:8080/api/health
```

Kết quả mong đợi: `status` là `UP`.

### Linux / macOS

Từ thư mục gốc repository, dùng JDK 21:

```bash
cd server
chmod +x gradlew
./gradlew bootRun --console=plain
```

### Build file JAR

Trong thư mục `server`:

```powershell
.\gradlew.bat bootJar --no-daemon --console=plain
.\gradlew.bat -q printBuildDir
```

Lệnh cuối in thư mục output. File chạy server nằm tại `<build-dir>/libs/rps-battle-1.0-SNAPSHOT.jar`:

```powershell
java -jar "<build-dir>/libs/rps-battle-1.0-SNAPSHOT.jar"
```

Trên Windows, build output nằm dưới `%USERPROFILE%\.gradle\rps-battle-builds\<checkout-key>` để tránh OneDrive khóa file class. Linux/macOS mặc định dùng `server/build`. Dùng `bootJar` cho server online; task `run` là chương trình console riêng.

## Bật Docker để dùng bot riêng

1. Mở Docker Desktop, chờ Engine sẵn sàng và giữ Docker chạy.
2. Kiểm tra `docker info --format '{{.OSType}}'` trả về `linux`.
3. Chuẩn bị image một lần:

```powershell
docker pull python:3.12-slim
docker pull gcc:14
docker pull eclipse-temurin:21-jdk
```

Server cần tìm được lệnh `docker` trong PATH. Nếu vừa cài Docker, mở lại VS Code/terminal rồi khởi động lại server. Không cần bấm Run từng image: server tự tạo và dọn container cho bot.

Đăng nhập tài khoản theo [AUTH.md](docs/AUTH.md), vào **Bot của tôi**, tải file, chọn bot rồi bấm **Dùng trong trận**. Ở Vào trận, chọn chế độ và kiểm tra mục Người chơi của bạn trước khi tạo/vào phòng. Chọn Tôi tự chơi nếu muốn điều khiển bằng tay.

- Source tối đa **512 KiB**, JAR tối đa **5 MiB**; kho server tối đa 100 bot.
- Python: Python 3.12, thư viện chuẩn.
- C++: GCC 14, C++17; một file `.cpp`.
- Java source: `public class Main`, không `package`; Java 21.
- JAR: có `Main-Class`, tương thích Java 21.
- Container không mạng, root filesystem chỉ đọc, user không phải root, CPU 0.5, RAM 384 MB, giới hạn tiến trình và thư mục tạm. C++/Java được biên dịch khi trận bắt đầu.
- Metadata và quyền sở hữu bot lưu PostgreSQL; danh sách phục hồi khi đăng nhập cùng tài khoản. Source và metadata nội bộ nằm tại `server/data/bots`; cần sao lưu cả DB và thư mục này. Bot cũ chưa có owner được nhập bằng token chứng minh.

Bot nhận một dòng JSON `YOUR_TURN` qua stdin, tính toán từ trạng thái và `legalActions`, rồi trả một dòng JSON `ACTION` qua stdout với đúng `sequence`; phải flush và chỉ trả nước đi hợp lệ. Đọc chi tiết trong [BOT-PROTOCOL.md](docs/BOT-PROTOCOL.md), [BOT-UPLOAD.md](docs/BOT-UPLOAD.md) hoặc trang `/bot-protocol.html` và `/bot-guide.html`.

Mẫu tải trực tiếp từ trang hướng dẫn, source ở `client/assets/bot-examples/`: `main.py`, `tactical.py`, `main.cpp`, `Main.java`.

## Cấu hình server

Cấu hình mặc định nằm trong `server/src/main/resources/application.yml`. Có thể ghi đè bằng tham số Spring Boot:

- `server.port`: mặc định 8080.
- `rps.turn-seconds`: mặc định 30 giây.
- `rps.bots.enabled`: mặc định `true`; đặt `false` để tắt runner bot riêng.
- `rps.bots.directory`: mặc định `data/bots`, tính từ thư mục làm việc server.
- `rps.tournament.max-concurrent-matches`: mặc định 4, cho phép 1–4.
- `rps.tournament.match-timeout-seconds`: mặc định 900 giây, timeout hạ tầng để không giữ slot mãi.
- `rps.tournament.bot-delay-millis`: mặc định 600 ms để dễ xem LIVE; dùng 0 trên server stress test.
- `rps.bot-tick-millis`: mặc định 200 ms; dùng 10 trên server stress test.

Ví dụ chạy JAR với thời gian 60 giây:

```powershell
java -jar "<build-dir>/libs/rps-battle-1.0-SNAPSHOT.jar" --server.port=8080 --rps.turn-seconds=60
```

## API và WebSocket

REST hiện có:

- `GET /api/health` (alias `/api/v1/health`): sức khỏe server.
- `POST /api/matches`: tạo phòng với `mode` là `HUMAN_VS_HUMAN` hoặc `HUMAN_VS_BOT`; tùy chọn `seed`, `botId` (bot riêng cần session/CSRF; token bot client không cấp quyền).
- `GET /api/matches/{id}`: thông tin phòng.
- `POST /api/matches/{id}/join`: tham gia, tùy chọn `botId` theo account.
- `POST /api/bots`: tải multipart với `name` và `file`, trả metadata/quyền sử dụng bot.
- `GET /api/bots/capabilities`: trạng thái Docker/image từng ngôn ngữ và giới hạn kích thước file.
- `POST /api/tournaments`, `GET /api/tournaments`, `GET /api/tournaments/{id}`: tạo/list/detail giải.
- `POST /api/tournaments/{id}/participants`: account đăng ký bot của mình ở bản nháp bằng `botId` và session/CSRF.
- `DELETE /api/tournaments/{id}/participants/{participantId}`: owner giải/ADMIN gỡ participant, hoặc owner bot rút bot của mình khi DRAFT; dùng session/CSRF.
- `POST /api/tournaments/{id}/start`, `POST /api/tournaments/{id}/cancel`: owner giải/ADMIN được bắt đầu/hủy qua session/CSRF; không dùng token trong trình duyệt.
- `GET /api/tournaments/{id}/ranking`, `GET /api/tournaments/{id}/export`: xếp hạng và xuất JSON.
- `GET /api/matches/{id}/replay`: nhận archive UUID hoặc mã phòng; initialState, full events, result, finalState và tên hai bên.

WebSocket `/ws/game` nhận JSON với thông tin vị trí/token. Các thông điệp chính: `JOIN_MATCH`, `RESUME_MATCH`, `PLACE`, `RANDOM_PLACEMENT`, `RESET_PLACEMENT`, `READY`, `UNREADY`, `ACTION`, `STATE_REQUEST`, `LEAVE`, `PING`. Server trả `GAME_STATE`, `ACTION_REJECTED`, `LEFT_ROOM`, `PONG`. Snapshot server là nguồn trạng thái chính; client không tự quyết định kết quả nước đi.

Client tự dùng `ws://` với HTTP và `wss://` với HTTPS, cùng host với trang web.

Khán giả gửi `SPECTATE_MATCH` với `matchId`, nhận snapshot `GAME_STATE`/`GAME_OVER`. Theo dõi giải gửi `WATCH_TOURNAMENT` với `tournamentId`, nhận `TOURNAMENT_STATE`. Hai subscription không gửi token người chơi/bot; không có quyền PLACE/READY/ACTION/LEAVE-as-player.

## Cấu trúc project

```text
oantuti/
├── README.md
├── Dockerfile / .dockerignore      # Build Java 21 image, không đưa secrets/data vào image
├── infra/                         # Compose prod/DB dev, Caddy HTTPS, DB init và env mẫu
├── client/                         # Client web, được server phục vụ cùng host
│   ├── admin/                      # ADMIN: dashboard, users, bots, matches, tournaments, disputes, replay, audit, system, profile
│   ├── index.html                  # Trang chủ
│   ├── lobby.html                  # Chế độ, chọn bot, tạo/vào phòng
│   ├── bots.html                   # Quản lý, tải và đấu thử bot
│   ├── game.html                   # Xếp quân và chơi online
│   ├── result.html                 # Kết quả và sự kiện cuối trận
│   ├── tournaments.html            # Danh sách, tạo giải và chọn bot
│   ├── tournament.html             # Tiến độ, các trận, ranking, champion
│   ├── replay.html                 # Phát lại toàn trận, tua và tốc độ
│   ├── bot-guide.html              # Hướng dẫn upload, bot mẫu
│   ├── bot-protocol.html           # Giao thức stdin/stdout
│   ├── css/                        # Theme, layout từng trang, hiệu ứng
│   ├── assets/
│   │   ├── pieces/                 # SVG Búa/Bao/Kéo cho hai bên
│   │   ├── bot-examples/           # Bot mẫu Python/C++/Java
│   │   └── castle-courtyard.png    # Nền đấu trường
│   ├── js/
│   │   ├── admin/                  # Shell, guard, API, bảng, dialog và các view quản trị
│   │   ├── pages/                  # Khởi tạo và thao tác từng màn hình
│   │   ├── api/                    # REST phòng và upload bot
│   │   ├── socket/                 # Kết nối, reconnect WebSocket
│   │   ├── game/                   # Client state, board, input, animation
│   │   ├── tournament/             # REST/WS giải đấu, ranking và các trang
│   │   ├── spectator/              # GameController chỉ đọc
│   │   ├── replay/                 # Dựng board từ event facts, không gọi engine
│   │   └── ui/                     # Dialog, thông báo, timer, âm thanh
│   ├── tests/                      # Node test runner
│   ├── demo/                       # Demo cố định của chặng 2A
│   └── package.json                # Script test; không cần npm để chạy game
├── worker/             # Agent Java 21: danh tính, capabilities và heartbeat
├── server/
│   ├── build.gradle                # Java, Spring Boot, test, copy client
│   ├── settings.gradle
│   ├── gradlew / gradlew.bat        # Gradle Wrapper
│   ├── gradle/wrapper/
│   └── src/
│       ├── main/
│       │   ├── resources/application.yml
│       │   └── java/com/rpsbattle/
│       │       ├── RpsBattleApplication.java  # Entry point server online
│       │       ├── game/            # Action, board, piece, player, state, engine
│       │       ├── rule/            # Các interface luật độc lập
│       │       ├── rules/           # Luật classic và biến thể online
│       │       ├── match/           # Điều phối trận ở tầng game
│       │       ├── agent/           # Human, random bot, tactical bot
│       │       ├── bot/             # Giao thức bot và process runner
│       │       │   └── upload/      # Lưu file, quyền bot, Docker sandbox
│       │       ├── online/          # Registry phòng, placement, ready, timeout
│       │       ├── api/             # Controller, DTO, mapper, lỗi HTTP
│       │       ├── websocket/       # Gateway, handler, message, cấu hình
│       │       ├── config/          # Cấu hình online
│       │       ├── replay/          # MatchLog, CLI/web replay, lưu file + metadata DB
│       │       ├── tournament/      # Domain, registry, scheduler, execution, ranking
│       │       ├── worker/          # FIFO admission, capacity và local match worker
│       │       ├── operations/      # Rate limit, metrics, request log, graceful checkpoint
│       │       ├── spectator/       # Subscription chỉ đọc và snapshot live
│       │       └── app/             # Entry point console và CLI bot
│       └── test/                    # Unit, architecture, API/WS, Docker tests
├── tools/                          # Kiểm tra bot mẫu/upload, sinh hướng dẫn
└── docs/                           # Đặc tả, hướng dẫn, báo cáo các chặng
```

Các file client cũ `client/game.js`, `client/style.css` được giữ từ demo trước. Client chính hiện tải module trong `client/js/`; demo cố định được giữ riêng trong `client/demo/`.

Luồng chính: **trình duyệt → REST/WebSocket → dịch vụ phòng → GameEngine/RuleSet → snapshot → trình duyệt**. Với bot riêng, server gửi trạng thái qua BotProtocol cho Docker runner, kiểm tra action rồi áp dụng qua engine như nước đi của người chơi.

## Kiểm thử

Từ thư mục `server`:

```powershell
.\gradlew.bat test --no-daemon --console=plain
```

Từ thư mục gốc repository:

```powershell
node --test client/tests/*.test.js
```

Kiểm tra mẫu tin cậy bằng compiler/interpreter local, nếu Python/C++/Java đã cài:

```powershell
python tools/verify-bot-examples.py
```

Kiểm tra upload và chạy bot thật qua REST/WebSocket: bật Docker, tải image, khởi động server kiểm thử cổng 8081 rồi chạy từ thư mục gốc:

```powershell
node tools/verify-uploaded-bots.mjs
```

Script tạo bot và phòng thử trên server đích; mặc định `http://127.0.0.1:8081`, đổi bằng biến `RPS_TEST_URL`. Chỉ dùng với server kiểm thử. Test Docker trong Gradle có thể **SKIPPED** nếu thiếu Engine/image; skipped không có nghĩa đã kiểm chứng sandbox.

Stress test Chặng 3: dùng server riêng cổng 8081 với `--rps.bot-tick-millis=10 --rps.tournament.bot-delay-millis=0`, sau đó chạy `node tools/verify-tournaments.mjs`. Script tạo 4/10 bot, kiểm tra 6/45 trận, lỗi bot, spectator, replay và dọn container/phòng. Không chạy lên server đang dùng cho người chơi.

## Chương trình console và replay

Chặng 1 được giữ độc lập với server web. Trong thư mục `server`:

```powershell
.\gradlew.bat run --args="human-vs-human"
.\gradlew.bat run --args="human-vs-human --export match-local.json"
.\gradlew.bat run --args="bot-vs-bot demo:1 demo:2 --export match-bots.json"
.\gradlew.bat run --args="replay match-bots.json"
```

CLI có thể đấu hai bot và export/replay log JSON. Đây là chức năng console, không phải một chế độ bổ sung trên trang Vào trận. Bot CLI có thể chỉ định executable hoặc file JSON mảng lệnh; chỉ chạy local code bạn tin cậy.

## Giới hạn hiện tại

- Bàn cờ/phòng đang chơi lưu trong RAM: restart không tiếp tục ván cũ. Metadata trận và lịch sử đã commit được giữ trong PostgreSQL theo account. Metadata bot theo tài khoản lưu bền vững trong PostgreSQL; source cần ổ lưu trữ bền vững và bản sao lưu.
- Đã có tài khoản/session/PostgreSQL, history/stats và ownership bot/giải. UI Login/Register/Profile/History đã có ở Nhóm 8; chưa có ranking người chơi xuyên các giải hoặc matchmaking tự động. Ranking hiện tại tính riêng cho từng giải.
- Chạy nhiều instance chưa có cơ chế chia sẻ trạng thái phòng. Bộ Compose là một deployment/host; account cần đăng nhập lại sau restart vì servlet session vẫn ở RAM.
- Hosting chỉ chạy một container ứng dụng không tự cung cấp Docker Engine cho runner bot riêng. Muốn giữ tính năng upload bot, cần môi trường phù hợp hoặc worker Docker riêng.
- Đã có ownership, CSRF/session, rate limit một instance và giới hạn lịch giải. Public với người dùng không tin cậy vẫn cần quota tài nguyên theo account, reverse proxy đáng tin, backup, cleanup container sau crash và cách ly worker vận hành; queue/phòng chưa chia sẻ giữa nhiều instance.

## Tài liệu liên quan

- [Giao diện và các màn hình](docs/CLIENT-BATTLE-UI.md).
- [Production deployment](docs/DEPLOYMENT.md), [vận hành và backup/restore](docs/OPERATIONS.md), [tài khoản và phân quyền](docs/AUTH.md).
- [Chặng 3: Tournament, Ranking, Spectator và Replay](docs/CHANG-3.md).
- [Upload bot và Docker](docs/BOT-UPLOAD.md).
- [Giao thức bot chi tiết](docs/BOT-PROTOCOL.md).
- [Hướng dẫn chặng 2B](docs/CHANG-2B-CLIENT.md), [đặc tả client](docs/CHANG-2B-CLIENT-SPEC.md).
- [Demo chặng 2A](docs/CHANG-2A-DEMO.md), [lộ trình Demo First](docs/CHANG-2-DEMO-FIRST.md).
- [Báo cáo kiến trúc chặng 1](docs/CHANG-1-REVIEW.md).

Tài liệu các chặng mô tả mốc phát triển và có thể chứa giao diện cũ; README và hướng dẫn giao diện hiện tại phản ánh cách sử dụng mới nhất.
