# Tournament UI review — 23/10/2026

## Thay đổi kiến trúc

- `client/js/tournament/presentation.js`: nhãn trạng thái, ngôn ngữ, tên hiển thị và danh sách luật được phép đưa lên UI người chơi. Giá trị chưa biết có nhãn dự phòng, không in enum tùy ý.
- `client/js/tournament/navigation.js`: điều hướng dùng chung theo giai đoạn, thể thức, vai trò và dữ liệu thực có. Menu người chơi và chủ giải dùng cùng resolver; chủ giải giữ quyền xem lịch sử, cấu hình và hạt giống trước thi đấu.
- `client/js/tournament/series-view.js`: một cặp BO3/BO5, tỷ số do server cấp, các ván con thu gọn và sắp theo số ván. Trang chưa có đủ dữ liệu không tự suy ra tỷ số cuối.
- `client/js/tournament/incident-dialog.js` và `client/js/ui/modal.js`: một hộp thoại báo cáo cho cặp đấu, chọn ván, giữ focus, hỗ trợ Escape và trả focus về nút mở.
- `client/js/replay/event-presentation.js`: diễn biến replay bằng tiếng Việt và tọa độ bàn cờ.
- API thêm `groupBySeries` tùy chọn cho trang trận đấu; caller cũ vẫn phân trang từng ván. DTO tổng quan và monitor cá nhân loại bớt hash, version và execution metadata; màn hình quản lý vẫn có dữ liệu kỹ thuật cần thiết.

## Lỗi và cách sửa

1. Tab tương lai và tab sai thể thức: lọc từ resolver; vòng tròn/Swiss không hiện nhánh đấu; replay chỉ hiện khi có dữ liệu.
2. Menu chủ giải trước thi đấu: giữ đăng ký, cấu hình, audit và chuẩn bị hạt giống; không hiện trận/kết quả/replay chưa có. Sau thi đấu không còn mục hạt giống vô nghĩa ở vòng tròn.
3. Enum, mã nội bộ và cấu hình thô ở UI người chơi: dùng mapper và allowlist luật; tài liệu giao thức bot/source và chi tiết kỹ thuật của quản trị vẫn giữ nguyên mục đích.
4. Nội dung trùng: bỏ các dashboard trạng thái/trận cá nhân lặp ngoài tab, header kết thúc gọn, tên chủ trùng tên người chơi ở bục kết quả chỉ xuất hiện một lần.
5. BO3/BO5 phẳng: card cặp đấu + tỷ số + danh sách ván con; phân trang không cắt cặp đấu thành nhiều trang.
6. Báo cáo sự cố: một nút cho mỗi cặp đấu, hộp thoại chọn ván theo thứ tự, sáu nhãn sự cố ánh xạ về hợp đồng API hiện có. Không tự tạo báo cáo khi chỉ kiểm tra UI.
7. Đồng đội: quản lý danh sách/đội hình tách khỏi bảng điểm và kết quả các bàn.
8. Workspace: lựa chọn viết code hoặc bot đã tải lên có mô tả riêng; kết quả build/test dùng nhãn dễ hiểu; log kỹ thuật thu gọn. Hết giờ khóa thao tác theo server, giữ bản nháp chưa lưu, gộp thông báo chỉ đọc và tránh hai đồng hồ đếm cùng một hạn.
9. Bài thiếu sau hạn: báo không có phiên bản sẵn sàng trước hạn, không nhận nhầm là đã khóa một bài hợp lệ.
10. Kiểm thử chính thức: kết quả đạt 8/8 không quay về các bước chờ khi xem source rồi Back.
11. Điều hướng nguồn/replay: mang ngữ cảnh giải, quay về đúng giải/trận; chống response cũ ghi đè trạng thái mới, refresh khi khôi phục trang.
12. Màn hình nhỏ: tab xuống hai cột thay vì khuất tab đang chọn; bộ lọc có khoảng cách đều; bảng có vùng cuộn riêng; modal nằm trong viewport.
13. Trang duyệt đăng ký admin: đồng bộ markup với controller hiện tại để không lỗi do thiếu phần tử; bỏ tiêu đề lặp.

## Kiểm thử

- 453 kiểm thử client đạt, không bỏ qua kiểm thử.
- 41 kiểm thử server mục tiêu đạt: monitor metadata, phân trang cặp đấu, tranh chấp, final validation API và tournament service.
- Sáu kiểm tra API trên server local đạt: DTO tổng quan không có metadata thừa; BO5 có đúng 4 ván và tỷ số 1–3; trang theo cặp; phân trang cũ; metadata cá nhân; người không quản lý nhận 403 với monitor quản lý.
- `:bootJar` build thành công; `git diff --check` không có lỗi khoảng trắng.
- Kiểm tra trực tiếp giữ focus bằng Tab/Shift+Tab, đóng bằng Escape và trả focus của hộp thoại.
- Kiểm tra source chỉ đọc → browser Back → workspace vẫn 8/8; replay tua tới cuối và quay lại đúng tab trận đấu.
- Tạo một giải thử local để quan sát đăng ký → lập trình → hết hạn → kiểm thử. Hai tài khoản được duyệt trước giờ coding; server tự chuyển giai đoạn. Không build/nộp bot trong fixture này để kiểm tra tình huống thiếu bài. Giải thử đã được hủy sau kiểm tra để không chờ chạy thi đấu.

## Trang đã kiểm tra trực tiếp

- `/tournaments.html`: khám phá, empty state và các bước tạo giải.
- `/tournament-detail.html`: vòng tròn, loại trực tiếp BO3/BO5, vòng bảng–loại trực tiếp, Swiss và đồng đội, dùng các giải thật đã hoàn tất có trận/replay.
- `/tournament-detail.html`: đăng ký, lập trình và kiểm thử của giải thử theo thời gian server; không giả lập state trong trình duyệt.
- `/tournament-code.html`: hai lựa chọn nộp, editor đang mở, hết giờ, thiếu bài, kết quả 8/8, source snapshot và Back.
- `/tournament.html`: tổng quan và menu quản lý giải đã kết thúc/đăng ký.
- `/tournament-submissions.html`: danh sách bài và trạng thái khóa.
- `/tournament-final-validation.html`: 2 bài đạt, mỗi bài đủ 8/8 bước.
- `/replay.html`: bàn cờ, tua tới cuối, diễn biến tiếng Việt và quay về cặp đấu.
- `/admin/tournament-detail.html`, `/admin/applications.html`: đăng nhập admin, trang giải, danh sách đơn đã khóa; không có lỗi console ở trang đơn đã kiểm tra.

Các kích thước được đo trên workspace và BO5: 1366×768, 1440×900, 1920×1080, 768×1024 và 390×844. Dashboard kiểm thử và đơn đăng ký admin được kiểm tra thêm ở màn hình nhỏ. Những lần đo này không có tràn ngang toàn trang. Không coi đây là kiểm chứng mọi tổ hợp route × kích thước × trạng thái.

## Giới hạn

- Không chạy lại toàn bộ giải cho mọi tổ hợp thể thức, loại người tham gia, chính sách bot và BO trong lượt này; sử dụng bộ giải thực chiến đã chạy trước đó cùng một fixture mới kiểm tra chuyển giai đoạn.
- Mất mạng, response đảo thứ tự và revision cũ được bao phủ bằng kiểm thử tự động; không giả lập tất cả sự cố mạng/hạ tầng trong trình duyệt.
- Nội dung tên giải do người tạo nhập được giữ nguyên. Các tên fixture cũ có chứa tên enum thể thức là dữ liệu tên giải thử, không phải nhãn trạng thái lấy từ backend.
- Ảnh và dữ liệu đo cục bộ nằm trong `server/build/local-run/ui-review-fixed-2026-10-23/`; đây là thư mục build không đưa vào Git. Log kiểm thử và script API nằm trong `server/build/local-run/`.

## Files changed

Danh sách tệp của thay đổi được lưu kèm ở `TOURNAMENT-UI-REVIEW-FILES.txt`. Các tệp test cập nhật nhãn/đường dẫn theo hành vi mới và vẫn kiểm tra quyền, state server, khả năng thao tác và không tự bật mutation từ discovery.
