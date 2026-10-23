# Check-in, phòng chờ và sẵn sàng thi đấu

Check-in xác nhận có mặt trong giải. Nó khác với đơn được duyệt, version READY và nút **Sẵn sàng** của từng ván. Xem [thuật ngữ](../glossary.md) để tránh nhầm ba bước này.

## Điều kiện và thao tác

1. Mở giải, theo dõi **Việc của bạn** và giờ check-in của server.
2. Với giải bot theo timeline, chờ bài khóa qua Final Validation và kết quả được áp dụng. APPROVED hoặc READY trước nộp chưa thay thế điều kiện này.
3. Khi server cấp `canCheckIn`, bấm **Check-in** hoặc **Check-in ngay** một lần.
4. Chờ xác nhận thành công, xem trạng thái **Đã check-in** và thời điểm được ghi nhận.

Nếu giải không yêu cầu check-in, không cần tự tìm nút để gửi xác nhận. Vẫn phải đáp ứng các điều kiện tham gia còn lại; không có check-in không đồng nghĩa mọi bot đều được thi.

## Hết hạn

Thời gian do server quyết định. Chưa gửi thành công trước hạn có thể bị đánh dấu missed/not checked in và không có trong roster thi đấu. Đóng tab, mất mạng hoặc đồng hồ máy sai không gia hạn cửa sổ. Nếu đã bấm nhưng chưa rõ kết quả, đồng bộ lại trạng thái trước khi kết luận thất bại/thành công.

## Phòng chờ

Sau check-in, flow có thể cấp `OPEN_WAITING_ROOM`. Màn hình hiển thị xác nhận, giờ dự kiến bắt đầu, thông báo và lịch khi đã được tạo. Không có lịch trước start không phải lúc nào cũng là lỗi: server chưa chuẩn bị fixtures hoặc còn điều kiện cần xử lý.

Đến `tournamentStartAt`, server thử bắt đầu khi đủ điều kiện; thiếu validation, số người, đội hình hoặc hạ tầng có thể chặn. Người chơi chờ thông báo thay vì tự tạo một phòng thường.

## Check-in và ready check không giống nhau

Người tự chơi có thể phải **Sẵn sàng thi đấu** cho từng ván sau khi đã check-in giải. Cửa sổ ready mặc định hiện là 30 giây (server có thể cấu hình). Bấm đúng trận/window đang được cấp, rồi dùng **Vào trận** khi có ghế. No-show có thể xử thua cả cặp BO.

Bot thi đấu tự động không cần người chơi bấm thay nước đi. Với giải đồng đội, check-in cá nhân chưa đủ để có đội/đội hình hợp lệ; xem [thể thức đồng đội](formats.md).

> Screenshot: Check-in đang mở, xác nhận Đã check-in và Phòng chờ trên desktop. Chưa bổ sung ảnh của phiên giải thật.

## Nguồn đối chiếu

- [TimelineRegistrationService](../../server/src/main/java/com/rpsbattle/tournament/timeline/TimelineRegistrationService.java), [OwnedTournamentService](../../server/src/main/java/com/rpsbattle/tournament/OwnedTournamentService.java), [TimelineStartService](../../server/src/main/java/com/rpsbattle/tournament/TimelineStartService.java).
- [TournamentReadyService](../../server/src/main/java/com/rpsbattle/tournament/TournamentReadyService.java), [check-in UI](../../client/js/tournament/timeline-check-in.js), [next action](../../client/js/tournament/next-action.js), [match entry](../../client/js/tournament/match-entry.js).
