# Khám phá và tạo giải đấu

Trang `/tournaments.html` có hai tab toàn chiều rộng: **Khám phá giải đấu** (mặc định) và **Tạo giải đấu**. Query `tab=discover|create` giữ tab khi tải lại; điều hướng Back/Forward và phím mũi tên/Home/End được hỗ trợ. Chuyển tab không tải lại trang hoặc xóa dữ liệu đang nhập.

## Khám phá

Các chip trạng thái, tìm theo tên, lọc thể thức và phân trang dùng API discovery hiện tại. Bộ lọc chỉ áp dụng khi bấm Tìm giải; query giữ bộ lọc/trang sau reload. Danh sách có 3 cột desktop, 2 cột tablet và 1 cột mobile. Trạng thái rỗng có nút chuyển sang Create; lỗi mạng có nút thử lại. Không polling discovery khi đang mở Create, bỏ qua phản hồi cũ và không thay danh sách đang có focus trong lần cập nhật nền.

## Wizard tạo giải

1. **Thể thức:** tên, 5 thể thức hiện có, BO1/BO3/BO5, cấu hình các giai đoạn vòng bảng.
2. **Người tham gia:** sức chứa theo thể thức, chỉ bot/chỉ người/hỗn hợp, hướng dẫn đồng đội.
3. **Bot:** chính sách nộp bài, ngôn ngữ, giới hạn source/version, deadline, bot có sẵn khi phù hợp. Giải chỉ người ẩn cấu hình bot.
4. **Timeline:** editor timeline hiện có hoặc ba mốc thời gian thủ công. Full timeline chỉ dành cho BOT_ONLY và buộc check-in như luồng cũ.
5. **Thi đấu:** số trận đồng thời 1–4 và check-in. Timeout, số vòng và tài nguyên worker do server quản lý; giao diện không tạo trường giả.
6. **Xác nhận:** tổng hợp cấu hình, sau đó mới hiện nút Tạo giải đấu.

Validate từng bước, có thể quay lại sửa. Chỉnh dữ liệu khóa lại các bước sau; trước submit kiểm tra lại toàn bộ. Không có API tạo giải trước bước cuối. API/payload và `createConfiguredTournament` được tái sử dụng; thành công chuyển sang `/tournament.html?id=...`. Nếu đã tạo bản nháp nhưng cấu hình tiếp thất bại, giữ link bản nháp và chặn tạo trùng. Khách phải đăng nhập để tạo giải.

Dữ liệu form chỉ được giữ trong DOM khi chuyển bước/tab, không lưu source hay nội dung form vào localStorage. Reload chỉ khôi phục tab và bộ lọc. `resultPublishAt` vẫn null theo editor hiện có; không bổ sung tính năng backend hoặc thay đổi Organizer/Detail.

## File thay đổi

- `client/tournaments.html`: hai panel và markup wizard.
- `client/css/tournament-mode.css`: CSS được giới hạn trong trang tournament home.
- `client/js/tournament/tournament-list.js`: kết nối auth, discovery, tab và form.
- `tournament-hub.js`: tìm kiếm, thẻ giải, phân trang và trạng thái rỗng/lỗi.
- `tournament-tab-state.js`, `tournament-tabs.js`: URL và tab có hỗ trợ bàn phím.
- `tournament-create-form.js`: kết nối editor cũ và field/payload.
- `tournament-create-wizard.js`, `tournament-create-validation.js`: bước, điều hướng và validation.
- `tournament-create-summary.js`, `tournament-create-submit.js`: xác nhận, submit và xử lý bản nháp dở dang.

Các file JS trên nằm trong `client/js/tournament/`. Không thay đổi Java, migration hoặc API.

## Kiểm chứng

- 275 kiểm thử client qua, gồm 16 ca mới trong 6 file `tournament-tabs`, `tournament-tab-state`, `tournament-create-wizard`, `tournament-create-validation`, `tournament-discover`, `tournament-create-submit` và helper DOM.
- Kiểm thử coordinator xác nhận payload, thứ tự gọi API, redirect thành công, chặn submit trùng và khôi phục bản nháp.
- Trình duyệt thật: Discover mặc định; CTA rỗng đổi tab không reload; khách bị chặn tạo; đăng nhập quay lại Create; validation tên/timeline; đi đủ sáu bước; giữ dữ liệu khi chuyển tab; summary BO3/BOTH/full timeline/concurrency; BO5/HUMAN_ONLY ẩn bot; reload giữ tab.
- Desktop 1440 px có 3 cột; mobile 390 px có 1 cột, wizard chỉ hiện một bước, không tràn ngang trang.
- Tạo thật trên dữ liệu local bị backend từ chối `TOURNAMENT_LIMIT`: registry đã có đủ 100 giải. Không xóa dữ liệu hoặc đổi quota để vượt qua giới hạn này. Do đó redirect sau tạo mới được xác nhận bằng kiểm thử coordinator, chưa xác nhận thành công end-to-end trên database local hiện tại.

- Build `bootJar` thành công; 82 module tournament/account qua kiểm tra cú pháp. Server đã khởi động lại ở cổng 8080; 81 HTML/CSS/module tournament được kiểm tra qua HTTP và khớp nguyên văn với source, API discovery trả đúng schema phân trang.
