# Hoàn thiện chế độ giải đấu

## Vòng bảng nhiều giai đoạn

Khi tạo giải, chọn **Vòng bảng nhiều giai đoạn → Loại trực tiếp**. Mỗi dòng cấu hình xác định số bảng và top N đi tiếp của một giai đoạn. Có thể thêm/bỏ dòng; thứ tự các dòng chính là thứ tự thi đấu. Giai đoạn cuối luôn là loại trực tiếp. Ví dụ:

- 6 người: 2 bảng, top 2 → 1 bảng, top 2 → chung kết.
- 16 người: 4 bảng, top 2 → 2 bảng, top 2 → bán kết/chung kết.
- 9 người: 3 bảng, top 1 → nhánh loại trực tiếp 3 người có BYE.

Giới hạn vận hành hiện có: 4–32 người, 1–8 giai đoạn vòng bảng, 1–16 bảng và top 1–16 mỗi bảng. Mỗi bảng phải đủ ít nhất hai người và đủ top N; mỗi giai đoạn phải cho ít nhất hai người đi tiếp. Server kiểm tra cả sức chứa khi cấu hình và roster thực tế khi khóa/check-in. Nếu số người có mặt không đủ cấu hình thì không bắt đầu với một lịch không hợp lệ.

Mỗi giai đoạn chia bảng theo seeding serpentine và đấu vòng tròn. Điểm được tính riêng trong phạm vi vòng của giai đoạn, không mang điểm giai đoạn cũ sang bảng mới. Server chờ toàn bộ series hoàn tất và hết review trước khi chốt qualification, tạo lịch tiếp và commit trước admission worker. Suất đi tiếp, thành viên bảng, khoảng vòng, stage number và ID trận được lưu. Kết quả vòng đã cấp suất cho giai đoạn sau không được override. Trọng tài vẫn có thể giữ/từ chối báo cáo hoặc loại participant theo quyền hiện có.

Giai đoạn sau chỉ nhận người còn hợp lệ. Nếu người đã rút/bị loại khiến một bảng trống hoặc thiếu người, lịch vẫn kết thúc có kiểm soát; không dựng trận giả. Nhánh cuối dùng BYE và cơ chế champion hợp lệ hiện có. BO1/BO3/BO5 vẫn dùng từng phòng/replay thật.

Đây là pipeline cấu hình **các vòng bảng → loại trực tiếp**. Swiss và Đồng đội vẫn là các thể thức độc lập; không có trình ghép tùy ý mọi loại scheduler hoặc đồ thị phân nhánh. Giới hạn 32 người và queue vận hành vẫn áp dụng; lịch đầu quá lớn có thể cần tăng `MAX_PENDING_MATCHES` như hướng dẫn deployment.

API: `POST /api/tournaments/{id}/stage-plan` với `{revision, stages:[{groups:2,qualifyTopN:2},{groups:1,qualifyTopN:2}]}`. Chỉ chủ giải/admin, chỉ DRAFT trước khi có đăng ký/trận. Client tạo giải gửi cấu hình trước khi thêm các bot. Snapshot luật khóa lại toàn bộ kế hoạch.

## Rút giữa giải

Trong **Trạng thái của tôi**, người chơi có thể rút từng suất người/bot mình sở hữu khi giải READY, RUNNING, PAUSED hoặc RECOVERY_REQUIRED. Hộp xác nhận nêu rõ hậu quả và yêu cầu đánh dấu xác nhận. Không thể rút hộ đối thủ, hoàn tác rút hoặc rút một giải đã kết thúc.

API: `POST /api/tournaments/{id}/withdraw` với `{revision,participantId,confirmed:true}`; session + CSRF, revision và ownership được kiểm tra ở server. Server lưu trạng thái WITHDRAWN/audit trước, rồi vòng execution xử lý ván đang chơi bằng đầu hàng hiện có và phát trạng thái kết thúc cho hai phía. Ván đang xếp quân chưa bắt đầu hoặc các trận chưa chơi được xử forfeit, không tạo replay giả. Hai phía cùng không hợp lệ dùng double-forfeit. Kết quả/replay đã hoàn tất được giữ nguyên; lỗi commit rollback trạng thái, audit và các trận đang bị thay đổi.

Rút một thành viên đồng đội không tự giải tán đội hoặc thay đội trưởng. Đội trưởng vẫn chọn các suất còn lại theo chính sách lineup hiện có; slot không hợp lệ bị xử thua. Không thay quyền đội trưởng bằng quyền chủ giải.

## Dữ liệu tải theo trang

Trang người chơi dùng `GET /api/tournaments/{id}/summary` để lấy metadata, xếp hạng, trạng thái bảng/đội và các trận đang chạy (tối đa 20); không tải toàn bộ trận/series/audit đã hoàn tất. Audit tổng quan giới hạn 20 mục gần nhất. Lịch sử Swiss cá nhân nằm trong `/me`, chỉ có dữ liệu của người dùng đó.

- `GET /api/tournaments/{id}/matches?page=0&size=20&round=1&stage=1&status=FINISHED&mine=true&order=time&replay=true`: lọc/phân trang trên server; size 1–50; mine yêu cầu đăng nhập và lấy ownership từ session. Các bộ lọc tùy chọn độc lập. Trả items, series liên quan, total, page, size, revision. ID là khóa sắp xếp phụ ổn định.
- `GET /api/tournaments/{id}/bracket`: khi mở tab Bracket mới tải series và ván mới nhất của các node, không tải cả lịch sử vòng bảng.
- `GET /api/tournaments/{id}/fixtures/{fixtureId}/matches`: khi bấm xem các bàn mới tải ba series/ván của đúng fixture đã khóa. Replay chỉ tải khi mở link.
- WebSocket `WATCH_TOURNAMENT` với `updatesOnly:true` gửi ID/revision và sự kiện thay đổi, không phát lại toàn bộ snapshot mỗi lượt. Trang người chơi tải tổng quan mới; client cũ/admin vẫn dùng giao thức full snapshot tương thích.

Bộ lọc lịch/trận/replay gồm giai đoạn, vòng, của tôi và thứ tự thời gian. Kết quả request cũ không được ghi đè một bộ lọc mới, dữ liệu của tab đã tháo khỏi DOM không được render lại.

## Tương thích và vận hành

Không thêm enum format hoặc SQL migration; vẫn schema V20. JSON cấu trúc bổ sung `groupPlans`, `groupHistory`, `stageNumber`, `startRound`; dữ liệu cũ mặc định một giai đoạn hai bảng/top 2 và stage 1 bắt đầu từ vòng 0. Không tạo lại ID hoặc chốt lại qualification của giải đã lưu. Sao lưu DB/bot/replay trước nâng phiên bản; deploy đồng bộ các server vì binary cũ không hiểu chuỗi nhiều giai đoạn. Giải đang dở sau restart vẫn cần review/resume theo RECOVERY_REQUIRED.

GameEngine, RuleSet và bot protocol không đổi. Thay đổi scheduler chỉ mở rộng vòng bảng; withdrawal tái sử dụng đầu hàng, lưu replay và executor hiện có.

## Kiểm thử và nghiệm thu

- Mã backend cuối: **557 tests, 0 failure/error/skipped**; client **69 tests pass**, kiểm tra cú pháp **76 module**; `bootJar` thành công.
- Kiểm tra riêng: ownership/revision/xác nhận rút, rollback khi commit lỗi, forfeit không tạo replay, phân trang ổn định/lọc mine, snapshot nhẹ, WebSocket chỉ ID/revision vẫn tương thích watcher cũ.
- Scheduler: 3 bảng/top 1 với BYE; 32 người qua ba vòng bảng BO3 rồi loại trực tiếp; snapshot/khôi phục giữ độc lập bảng điểm và qualification; toàn bộ người bị loại không tạo vòng lặp hoặc champion giả.
- Native PostgreSQL/HTTP/WebSocket: 6 người qua 2 bảng/top 2 → 1 bảng/top 2 → chung kết, đủ 13 ván/replay thật. Giải 3 người thử rút khi đang chơi giữ 2 replay thật và xử một trận chưa chơi bằng forfeit, không tạo replay giả. Kiểm tra ngăn rút đối thủ/thiếu xác nhận và lọc lịch theo giai đoạn, phân trang size 3 không lặp ID.
- UI thật: tạo giải với hai cấu hình bảng, server lưu đúng kế hoạch; lọc giai đoạn thứ hai ra 6 trận, bracket tải replay chung kết; rút qua checkbox/hộp xác nhận đổi trạng thái sang Đã rút. Các bản nháp/READY dùng thử được cancel qua API, giữ audit.
- Mobile 390×844: page width 375 px, không tràn ngang; các bảng cuộn trong khung.
- Sau restart tiến trình Windows: kế hoạch, lịch sử qualification, trạng thái rút, champion và 15 replay của hai giải mới giữ nguyên. Đối chiếu tập ID trận thay vì thứ tự hàng PostgreSQL. Hồi quy các fixture Swiss/đồng đội (42 ván), vòng bảng cũ (18 ván), BO3/BO5 đều qua; không tạo lại trận hoặc thay kết quả cũ.
