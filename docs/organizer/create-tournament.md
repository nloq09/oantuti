# Tạo giải đấu

[Mục lục](../README.md) · Tiếp theo: [Quản lý đăng ký](registration-management.md)

Chủ giải dùng tài khoản USER để tổ chức. Chủ giải không đồng thời đăng ký thi đấu trong giải của mình; dùng người chơi khác để thử toàn bộ luồng. Tài khoản ADMIN vào khu quản trị riêng và có quyền can thiệp, không phải tài khoản chơi game thông thường.

## Tạo một giải mới

Mở **Giải đấu → Tạo giải**. Wizard có sáu bước:

1. **Thể thức:** đặt tên, phạm vi hiển thị và chọn một [thể thức được hỗ trợ](../tournament/formats.md). Chọn BO1/BO3/BO5 theo thời gian có thể tổ chức; Swiss, vòng bảng và đội có cách xếp vòng riêng.
2. **Người tham gia:** chọn BOT_ONLY, HUMAN_ONLY hoặc MIXED nếu tổ hợp thể thức cho phép, sức chứa, chính sách duyệt đơn và check-in. Client và server đều kiểm tra tổ hợp; không ép giá trị bị vô hiệu hóa bằng DevTools.
3. **Bot:** với giải có bot, chọn ngôn ngữ, cách nộp và các giới hạn. Giải có workspace cần đủ thời gian build, test và kiểm thử chính thức. Đọc [Build/Test/Submit](../bot/build-test-submit.md) trước khi công bố policy.
4. **Timeline:** đặt giờ mở/đóng đăng ký, coding, validation, check-in và bắt đầu. Giao diện nhập giờ theo máy người dùng, server lưu thời điểm tuyệt đối; kiểm tra phần xác nhận để tránh nhầm múi giờ. Giờ đóng đăng ký có thể bằng giờ mở coding.
5. **Thi đấu:** kiểm tra concurrency, seeding, tie-break và tự chuyển vòng. Concurrency của giải vẫn chịu giới hạn thực tế của server/nhóm worker.
6. **Xác nhận:** đọc lại toàn bộ lịch và policy rồi tạo. Sửa bước trước có thể yêu cầu kiểm tra lại những bước sau.

Sau khi tạo, mở giải của mình sẽ vào **Quản lý giải**. Các phần nằm trong nhóm Tổng quan, Người chơi, Thi đấu và Quản trị; tab không áp dụng cho mode hiện tại có thể bị ẩn. Với timeline hợp lệ, server tự mở đăng ký khi đến giờ. Có thể mở sớm qua thao tác hiện trong UI; không cần chốt danh sách thủ công mới cho phép coding.

## Kiểm tra trước khi mời người chơi

- Dùng một tài khoản người chơi riêng để kiểm tra giải xuất hiện trong Khám phá và đăng ký được. PRIVATE không nằm trong danh sách khám phá công khai; việc có liên kết không cấp quyền đọc source hoặc quản lý.
- Đảm bảo người chơi hiểu đăng ký trước, nộp bot sau. Duyệt đơn không có nghĩa đã có bài READY hoặc đã vượt Final Validation.
- Kiểm tra Docker trên Central trước khi mở coding. Worker từ xa chỉ chạy trận BOT_ONLY với bài LOCKED; không thay Central build/test/validation.
- Nếu cần REMOTE GROUP, nhờ Admin gán nhóm và xem capacity **trước khi tạo lịch đấu**. Không có chế độ AUTO tự rơi về local.

Không sửa URL/ID để nhận quyền chủ giải. Server kiểm tra chủ sở hữu hoặc ADMIN cho từng mutation.

## Thay lịch sau khi công bố

Dùng hành động đúng giai đoạn trong Tổng quan/Cài đặt: gia hạn đăng ký, gia hạn coding, mở coding sớm, gia hạn validation, gia hạn check-in hoặc lùi giờ bắt đầu. Những thay đổi này yêu cầu lý do và revision hiện tại. Một số thao tác gia hạn dịch cả các mốc phía sau; đọc lịch mới trước khi xác nhận. Sau khi bắt đầu thi đấu, không còn quyền chỉnh timeline theo cách này.

Nếu nhận `STALE_TOURNAMENT_REVISION`, tải lại dữ liệu rồi xem lại tác động; không gửi lại nguyên thao tác cũ liên tục.

> Screenshot cần bổ sung: wizard sáu bước và màn hình xác nhận của một giải thử, không chứa thông tin riêng.

## Nguồn đối chiếu

- [Wizard](../../client/js/tournament/tournament-create-wizard.js), [kiểm tra cấu hình](../../client/js/tournament/tournament-create-validation.js), [timeline](../../client/js/tournament/create-timeline-model.js).
- [Controller tạo giải](../../server/src/main/java/com/rpsbattle/api/controller/TournamentController.java), [quyền sở hữu](../../server/src/main/java/com/rpsbattle/tournament/OwnedTournamentService.java).
- [Điều hướng quản lý](../../client/js/tournament/organizer-shell-model.js), [đổi timeline](../../server/src/main/java/com/rpsbattle/tournament/TimelineOverrideService.java).
