# Theo dõi trận, series và replay

[Mục lục](../README.md) · [Vận hành giải](tournament-operation.md) · [Audit](audit.md)

Trong quản lý giải chọn **Trận đấu**. Người chơi dùng **Trận của tôi**; hai màn hình đọc phạm vi khác nhau. Bảng có bộ lọc trạng thái, phân trang và lựa chọn gom theo series. BO3/BO5 là một cặp đấu gồm nhiều ván; một ván kết thúc chưa chắc quyết định người đi tiếp.

## Theo dõi một cặp đấu

1. Tìm đúng vòng và hai người tham gia. Kiểm tra series/game number nếu giải BO3/BO5.
2. Phân biệt chờ ghép/chờ tài nguyên, chờ ready, đang thực thi và hoàn tất. Không gán thắng/thua khi execution chỉ mất kết nối.
3. Với trận remote, mở thông tin attempt được phép đọc. Một match có thể có nhiều lần thực thi; chỉ kết quả hợp lệ được server tiếp nhận và áp dụng mới quyết định giải.
4. Khi có lỗi, đối chiếu mã an toàn và [tình trạng worker](../admin/workers.md). Các ID/lease/epoch nằm trong phần kỹ thuật, không phải điều kiện để người chơi tự chạy lại bot.
5. Với kết quả hoàn tất, mở lịch sử hoặc replay khi liên kết đã có. Dữ liệu upload/áp dụng có thể đến sau lúc worker báo xong.

## Bàn cờ trực tiếp và replay

Trận online còn trong Central có thể cung cấp spectator qua `/ws/game`. Remote worker hiện gửi kết quả và replay, **chưa hỗ trợ stream bàn cờ từng nước trực tiếp**. Vì vậy không hứa nút “xem trực tiếp” cho một execution remote chỉ đang chạy.

Replay là bản ghi của ván thực tế; thao tác override không sửa sự kiện trong file. Khi report quản trị khác winner gốc trong replay, xem Audit để hiểu quyết định. Replay thiếu/không hợp lệ cần kiểm tra metadata, checksum và volume, không sửa `valid=true` thủ công.

Trang monitor không trả source bot, credential worker hoặc full job spec cho người xem công khai. Không chụp thông tin nội bộ/định danh vào ảnh chia sẻ rộng nếu không cần.

## Khi bảng đứng lâu

Cập nhật lại và xem dữ liệu có bị stale không. Nếu nhiều trận chờ, kiểm tra capacity/routing và vòng đã release. Nếu một execution hết lease, chờ cơ chế recovery có giới hạn; đừng tạo thêm giải để “kích chạy”. Sau khi xử lý nguyên nhân, dùng retry/review đúng [quy trình](tournament-operation.md).

> Screenshot cần bổ sung: monitor desktop có một series hoàn tất và chi tiết attempt đã ẩn thông tin riêng.

## Nguồn đối chiếu

- [API monitor](../../server/src/main/java/com/rpsbattle/api/controller/MatchMonitorController.java), [read model](../../server/src/main/java/com/rpsbattle/tournament/MatchMonitorService.java), [attempt history](../../server/src/main/java/com/rpsbattle/tournament/RemoteExecutionReadService.java).
- [Replay API](../../server/src/main/java/com/rpsbattle/api/controller/ReplayWebController.java), [spectator](../../server/src/main/java/com/rpsbattle/spectator/SpectatorService.java).
