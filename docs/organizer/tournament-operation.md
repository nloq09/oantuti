# Vận hành giải từ check-in đến kết quả

[Mục lục](../README.md) · [Kiểm thử chính thức](validation-management.md) · [Theo dõi trận](match-monitor.md)

Trước khi bắt đầu: đăng ký đã đóng, danh sách hợp lệ, bài cần khóa đã LOCKED/qua validation, check-in đáp ứng policy và hạ tầng có sức chứa. Chủ giải theo dõi qua **Tổng quan**, **Check-in**, **Trận đấu**, **Nhánh đấu**, **Kết quả**, **Audit**; tab nào hiện phụ thuộc mode và phase.

## Check-in và bắt đầu

Đọc [hướng dẫn check-in](../tournament/check-in.md). Check-in theo giải khác với READY của workspace và sẵn sàng trước từng trận người chơi. Theo dõi người đủ điều kiện và người chưa check-in trước hạn. Server tự xử lý mốc timeline; không coi đồng hồ client là quyền vào trận.

Khi giải đủ điều kiện, dùng thao tác bắt đầu nếu UI cho phép; lịch tự động vẫn chịu cùng điều kiện server. Nếu bị chặn, xem validation chưa áp dụng, review chưa xử lý, thiếu người, thiếu artifact hoặc thiếu worker đúng nhóm. Không giảm policy để che lỗi hạ tầng.

Seeding/lịch đã tạo là dữ liệu thi đấu. Điều chỉnh nhóm worker, policy hay roster có thể bị khóa sau thời điểm này. Xem [thể thức](../tournament/formats.md) để hiểu bye, thứ tự vòng, series và đội.

## Tạm dừng, tiếp tục và chuyển vòng

- **Pause:** ngăn tiến trình giải nhận lịch mới theo điều khiển giải; không coi đây là snapshot có thể đóng băng giữa một nước đi. Các cửa sổ ready được xóa để tránh giữ xác nhận cũ.
- **Resume:** chỉ từ PAUSED hoặc RECOVERY_REQUIRED; cần hết review/trận FAILED chưa xử lý và artifact còn hợp lệ. Cửa sổ ready/lineup được chuẩn bị lại.
- **Next round:** chỉ dùng khi đã tắt tự chuyển vòng, vòng trước đã hoàn tất và không còn review. Không ép chạy vòng mới trong lúc cặp đấu trước còn pending/running.
- **Finish:** chỉ khi các trận, bracket và series đã có quyết định, không còn review. Cần lý do. Nút không hợp lệ ở phase hiện tại sẽ bị ẩn/từ chối.
- **Cancel:** kết thúc giải theo hướng hủy, có audit; không xóa lịch sử để tạo lại giải cùng ID.

Giải đội cần lineup hợp lệ; thời gian chọn đội hình và fallback thực hiện theo scheduler. Không ghép tay bằng cách sửa bảng tournament_matches.

## Trận lỗi và quyết định quản trị

Khi cần review, dùng trang trận/chi tiết và nhập lý do. Yêu cầu review khi giải RUNNING có thể chuyển giải sang PAUSED.

Retry trận yêu cầu PAUSED/RECOVERY_REQUIRED và trận FAILED còn có thể thử lại; không chạy lại một trận đã FINISHED chỉ vì không thích kết quả. Loại người tham gia yêu cầu giải đã dừng, không có trận RUNNING chứa người đó; trận chưa đấu được xử theo forfeit, lịch sử đã hoàn tất được giữ.

Override kết quả yêu cầu review và trạng thái phù hợp. Server chặn nếu vòng Swiss phía sau đã tạo, ván sau trong series đã có, qualification vòng bảng đã khóa hoặc nhánh phía sau đã thi đấu. Quyết định quản trị được ghi riêng; replay vẫn giữ sự kiện gốc.

## Sau restart hoặc sự cố

RECOVERY_REQUIRED nghĩa là cần người kiểm tra, không phải trận thua. Xem [khôi phục](../technical/backup-recovery.md): xử lý review/trận lỗi, kiểm tra artifact/remote attempts rồi resume nếu server cho phép; có thể hủy và tạo giải mới. Trạng thái bàn cờ trong RAM không tự phục hồi giữa trận.

## Nguồn đối chiếu

- [Điều khiển giải](../../server/src/main/java/com/rpsbattle/tournament/TournamentAdministrationService.java), [state machine](../../server/src/main/java/com/rpsbattle/tournament/domain/TournamentStateMachine.java).
- [Quyền và bắt đầu](../../server/src/main/java/com/rpsbattle/tournament/OwnedTournamentService.java), [UI controls](../../client/js/tournament/organizer-controls.js).
- [Điều hướng](../../client/js/tournament/organizer-shell-model.js), [controller](../../server/src/main/java/com/rpsbattle/api/controller/TournamentAdministrationController.java).
