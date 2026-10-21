# Rà soát luồng giải đấu — 21/10/2026

## Kết quả xử lý

Cả sáu vấn đề bên dưới đã được sửa. Phần mô tả lỗi và số liệu audit cũ được giữ làm bằng chứng trước sửa.

- DRAFT chỉ chủ giải/admin được thêm nhanh bot, vẫn phải có quyền với bot đó. Người khác chỉ đăng ký khi mở nhận đơn, trạng thái PENDING cho đến khi chủ giải duyệt.
- Với giải thủ công, hạn đăng ký chặn đơn mới nhưng không chặn chủ giải duyệt/từ chối đơn đã nhận. Sau khi chốt roster thì khóa xét duyệt. Route legacy không can thiệp quy trình duyệt riêng của timeline. Server trả `acceptingRegistrations` theo giờ server để UI ẩn form hết hạn và hiển thị giai đoạn xét duyệt.
- Swiss và loại trực tiếp hỗ trợ tối đa 4000 người/bot, không phụ thuộc việc bật coding; các format còn lại tối đa 32. Giới hạn tối thiểu theo format được giữ nguyên.
- Client/server cùng yêu cầu hạn đăng ký < hạn check-in, hạn check-in <= giờ bắt đầu. Khi bỏ qua check-in, giờ bắt đầu không được trước hạn đăng ký; ngày giờ vẫn có thể để trống.
- Có thể lưu lịch cũ trong bản nháp, nhưng không thể mở đăng ký khi hạn đóng đăng ký đã đến hoặc đã qua. Áp dụng cả timeline và hạn thủ công, dùng Clock của server. Lịch đang mở còn thời gian nhận đơn vẫn được công bố.
- Final PASS vẫn là kết quả kiểm thử lịch sử; hướng dẫn tiếp theo xét trạng thái giải, đã/lỡ check-in, bị loại, đang chạy, tạm dừng, khôi phục và kết thúc.

Đã kiểm tra API thật bằng hai tài khoản: Swiss HUMAN_ONLY 64 suất; hai deadline bằng nhau bị chặn; đăng ký đúng hạn → duyệt sau hạn → hai người check-in → READY; lịch hết hạn bị từ chối, chỉnh lịch rồi công bố thành công; người ngoài không thể quick-add hoặc tự duyệt bot. Hai lượt kiểm tra tạo tổng cộng tám giải thử, đều đã được hủy; không hủy giải của người dùng. Lượt cuối cũng xác nhận giải thủ công hết hạn không còn xuất hiện trong danh sách đang nhận đăng ký. Dấu vết: `server/build/local-run/tournament-flow-fixed-results.json` (không commit).

Kiểm thử component xác nhận Final PASS không còn chỉ dẫn chờ check-in ở giải đã hủy/kết thúc/đang chạy/tạm dừng/chờ khôi phục. Browser local đang bị tắt trong Work settings nên chưa kiểm tra trực quan bằng thao tác trình duyệt trong lượt sửa này.

### Kiểm chứng bản sửa

- Client: **283/283 PASS**, không bỏ qua ca nào; kiểm tra cú pháp hai module điều khiển trang cũng đạt.
- Backend: chạy toàn bộ **453 ca giải đấu**, gồm sandbox Docker Python/C++/Java, giải thật trên Docker và persistence PostgreSQL. Lượt đầu có 5 ca lỗi: 4 fixture công bố lịch cũ không còn hợp lệ theo quy tắc mới và 1 lỗi mock đồng hồ bị scheduler xen vào. Đã sửa fixture để công bố trước hạn rồi mới tiến đồng hồ, giữ nguyên kiểm tra nghiệp vụ.
- Chạy lại **225 ca thuộc các nhóm bị ảnh hưởng**, bổ sung hai ca discovery cho H2/PostgreSQL. Các ca nghiệp vụ đều qua; còn một lỗi cài mock. Đã dùng stubbing phù hợp và tách scheduler deadline nền khỏi fixture mô phỏng giờ; các ca deadline vẫn chủ động gọi xử lý đóng hạn. Chạy lại **26 ca đồng hồ/deadline**: PASS toàn bộ. Đây chỉ là cấu hình test, không tắt scheduler production.
- Ghép kết quả mới nhất theo ca (giữ riêng các lần chạy parameterized): **455/455 PASS, không còn ca lỗi hoặc bị bỏ qua**. Con số này là kết quả bộ đầy đủ cộng các lượt chạy lại, không phải một lượt toàn bộ đều xanh ngay lần đầu. Dấu vết tổng hợp: `server/build/local-run/flow-fix-combined-results.json`.
- `:bootJar` thành công; bản cuối được khởi động lại tại localhost:8080 và `/api/health` trả UP. Hai lượt API thật nêu trên đều đạt, kể cả từ chối quick-add trái quyền và xét duyệt sau hạn.

Mốc source: `3e15dad`. Đây là báo cáo kiểm tra; chưa sửa hành vi nghiệp vụ. Các script thăm dò và JSON kết quả nằm ở thư mục build local bị Git bỏ qua. Không lưu cookie, mật khẩu hoặc source riêng vào báo cáo.

## Phạm vi và cách chạy

- Chạy toàn bộ bộ kiểm thử `com.rpsbattle.tournament.*`, gồm các lớp con coding, scheduler, persistence: 5 thể thức, BO1/3/5, seeding, Swiss/BYE, vòng bảng/qualification, đội/đội hình, lịch tự động, ba cách nộp bot, freeze, kiểm thử chính thức, check-in, ready check, điều phối trận, spectator/replay, audit, tranh chấp, pause/resume, retry, override, rút lui, recovery và cleanup.
- Chạy toàn bộ 277 kiểm thử client.
- API trên server local/PostgreSQL thật: khách và tài khoản khác bị chặn thao tác quản lý; tạo/cấu hình/mở đăng ký; hai tài khoản đăng ký người chơi, chủ giải duyệt, check-in hai bên và chốt READY; hủy và từ chối start sau hủy.
- Docker thật: hai bot mẫu Python, giải loại trực tiếp BO3; ván đầu kết thúc DRAW không có lỗi, series tự tạo và chạy ván hai. Dừng lượt quan sát sau 150 giây và hủy giải thử. Không coi đây là bằng chứng đã hoàn tất cả BO3 hoặc xuất replay của cả series.
- Mọi giải mới của lượt rà có tiền tố `Flow audit`, được hủy sau khi chạy để không chiếm quota. Hai bot mẫu thử được giữ theo tài khoản kiểm thử để không xóa artifact đang được lịch sử trận tham chiếu.
- Browser hiện bị tắt trong Work settings nên lượt này **không bấm toàn bộ luồng UI trong trình duyệt**. Kiểm tra UI bằng module hiện có, dữ liệu API thật và DOM helper; không dùng kết quả của lượt rà giao diện trước thay cho bằng chứng lượt này.

## Kết quả chạy

- Backend: **448 ca, 73 lớp kiểm thử, 0 lỗi, 0 bỏ qua**. Lệnh `:test --tests 'com.rpsbattle.tournament.*'` kết thúc BUILD SUCCESSFUL sau 12 phút 57 giây, có các ca Docker và PostgreSQL thật.
- Client: **277 ca, 0 lỗi, 0 bỏ qua**.
- Native/API: sáu phát hiện bên dưới được tái hiện bằng validator/component và phản hồi server thật; không chỉ suy đoán qua đọc code. Luồng người chơi thủ công hai tài khoản đã đi tới READY; luồng BO3 Docker đi qua ván đầu và bắt đầu ván hai.
- Toàn bộ 5 giải tạo bởi các script audit đã được xác nhận CANCELLED/FINISHED sau xử lý. Không đổi role tài khoản, không sửa hoặc hủy giải cũ của người dùng, không thay source nghiệp vụ.
- Các log đóng test có scheduler thử kết nối container PostgreSQL đã dừng trong quá trình shutdown; không có ca thất bại và không dùng log shutdown đó làm bằng chứng về lỗi production.

## Các vấn đề đã tái hiện trước bản sửa

### 1. Ưu tiên cao — Tự thêm bot vào bản nháp được duyệt ngay

Tài khoản B gửi `POST /api/tournaments/{id}/participants` với bot thuộc B vào giải thủ công DRAFT của A. Server trả 200, participant có trong roster và registration là APPROVED. Không cần A mở đăng ký hoặc duyệt.

Đã tái hiện trên giải thử `83c2254c-ac7e-4c46-9f6c-d710c72ea69e`. Đây là luồng legacy đang được test ownership cho phép, nhưng không khớp luồng mới “chủ giải cấu hình → mở đăng ký → duyệt”. Người biết ID có thể chiếm suất trước khi chủ giải công bố. Không phải lấy được bot của người khác.

Nguồn: `OwnedTournamentService.add` dòng 160–179. Phần add xác thực sở hữu bot, nhưng không yêu cầu quyền quản lý cho nhánh DRAFT, rồi ghi APPROVED.

Đề xuất: trong DRAFT chỉ chủ giải/admin được dùng quick-add; người ngoài đăng ký qua route registrations khi REGISTRATION mở, luôn PENDING. Giữ kiểm tra sở hữu bot và capacity. Cập nhật test legacy theo quy tắc này.

### 2. Ưu tiên cao — Đơn nộp đúng hạn không thể duyệt sau khi đóng giờ đăng ký thủ công

Tạo giải HUMAN_ONLY thủ công có hạn đăng ký 5 giây, mở đăng ký và hai tài khoản gửi đơn đúng hạn. Sau hạn, chủ giải duyệt một đơn nhận 400 `REGISTRATION_CLOSED`; đóng đăng ký nhận 400 `NOT_ENOUGH_BOTS`, vì chưa có participant nào được duyệt.

Không phải mất dữ liệu, nhưng chủ giải bị kẹt ở luồng bình thường và phải hủy giải hoặc tìm cơ chế can thiệp khác. Luồng timeline đã có review sau đóng đăng ký; luồng thủ công chưa tương đương.

Nguồn: `OwnedTournamentService.review` gọi `registrationPhase`, hàm này áp deadline cho cả thao tác nộp đơn lẫn xét duyệt. `closeRegistration` đếm participant đã được duyệt.

Đề xuất: deadline chặn đơn mới, không chặn xét duyệt đơn đã nhận đúng hạn. Thêm trạng thái/chỉ dẫn “Đã đóng nhận đơn · Chờ xét duyệt”, rồi cho chủ giải chốt roster khi đủ điều kiện. Lỗi thiếu người nên dùng ngôn ngữ chung thay vì NOT_ENOUGH_BOTS cho giải người.

### 3. Ưu tiên vừa — Sức chứa giải chỉ người không khớp client/server

Chọn Swiss, HUMAN_ONLY, 64 suất: `validateCreateStep(1)` trả true. Payload thực tế của wizard có `botPolicy:null`; backend trả 400 `INVALID_PARTICIPANT_LIMIT` vì capacity >32 chỉ được phép khi `enroll=true`, mà enroll lấy từ botPolicy có hay không.

Nguồn: `tournament-create-validation.js:8`, `capacity.js:1`, `tournament-create-form.js:35`, `OwnedTournamentService.java:105`.

Đề xuất: tách giới hạn sức chứa khỏi việc bật coding. Chọn một quy tắc thống nhất theo format + participant policy và trả về capabilities; ít nhất UI phải giới hạn đúng trước khi đi hết sáu bước.

### 4. Ưu tiên vừa — Hai deadline bằng nhau qua client nhưng hỏng sau khi đã tạo nháp

Hạn đăng ký và hạn check-in cùng `2026-11-01T10:00`: client chấp nhận vì chỉ chặn mốc sau nhỏ hơn mốc trước. API timing trả 400 `INVALID_TOURNAMENT_TIMING` vì backend yêu cầu registrationDeadline < checkInDeadline.

Wizard tạo bản nháp trước rồi mới gửi timing nên người dùng gặp thông báo cấu hình dở dang và bị chuyển sang xử lý bản nháp, dù có thể ngăn ngay ở bước Timeline. Cơ chế chặn tạo trùng vẫn hoạt động.

Nguồn: `tournament-create-validation.js:15`, `TournamentTiming.java:5`, `create-configured-tournament.js`.

Đề xuất: đồng bộ strict/non-strict cho từng cặp mốc với backend; test cả trường hợp bằng nhau. Thông báo gắn đúng trường trước nút Tiếp tục.

### 5. Ưu tiên vừa — Công bố được lịch đã qua toàn bộ

Tạo giải bot với toàn bộ 10 mốc timeline đã qua nhiều ngày. PUT timeline trả 200, mở đăng ký trả 200; ngay sau đó phase là READY_TO_START trong khi lifecycle vẫn REGISTRATION. Scheduler có thể xử lý tiếp/hủy do thiếu check-in sau đó; kết quả tức thời không có cửa sổ để ai tham gia hợp lệ.

Đề xuất: lúc công bố phải xác minh ít nhất thời gian đóng đăng ký/coding/start chưa qua, hoặc yêu cầu chủ giải đặt lại lịch. Không cấm đọc lịch cũ hoặc restore lịch sử. Cho lưu bản nháp cũ và cho mở đăng ký là hai việc khác nhau.

Nguồn: `TournamentTimeline` kiểm tra thứ tự nhưng không kiểm tra lịch công bố so với clock; `OwnedTournamentService.openRegistration` chỉ chuyển lifecycle; `TournamentPhaseResolver` chỉ chiếu thời gian.

### 6. Ưu tiên vừa — Giải đã hủy vẫn bảo đang chờ check-in

Dữ liệu thật của giải `89ae0041-87b9-4409-b811-37f8310f1e16`: tournamentStatus=CANCELLED, phase=CANCELLED, finalValidation.status=PASSED, applied=true. Gọi `renderFinalValidation` với chính dữ liệu này vẫn tạo câu “TOURNAMENT READY · Chờ giai đoạn check-in”.

Kết quả kiểm thử bot là sự kiện lịch sử hợp lệ, nhưng chỉ dẫn bước tiếp theo sai. Cùng nhánh ready không xét lifecycle nên cũng có thể gây hiểu nhầm sau check-in/đang chạy/đã kết thúc.

Nguồn: `final-validation-view.js:11` chỉ xét PASSED && applied.

Đề xuất: giữ badge đã đạt, nhưng chỉ dẫn theo phase/lifecycle/check-in hiện tại: đã hủy, chờ check-in, đã check-in, đang thi đấu hoặc xem kết quả.

## Thứ tự nên sửa

Chặn quick-add trái luồng ở DRAFT → tách deadline nộp đơn khỏi quyền xét duyệt → đồng bộ validation capacity/timeline trước tạo → xác minh lịch trước công bố → chuẩn hóa thông điệp bước tiếp theo. Các thay đổi cần test hồi quy cả timeline và legacy manual; không gộp tất cả trạng thái thành một nhánh bot-only.

## Giới hạn kết luận

Các kiểm thử tự động có thể qua trong khi UX và quy tắc giữa các lớp vẫn mâu thuẫn. Không kết luận “mọi luồng đều ổn” từ số test. Bộ kiểm thử có một số lớp kế thừa chạy lại các trường hợp chung, nên số ca không phải số hành trình người dùng độc lập. Remote worker production, mất mạng thật và toàn bộ tổ hợp trạng thái UI không được chạy thủ công trong lượt này.

## Dấu vết thăm dò local

Các file dưới `server/build/local-run/` không được đưa lên Git: `tournament-flow-audit-results.json` (capacity, ngày giờ, đăng ký/check-in), `tournament-bot-audit-results.json` (DRAFT admission và Docker BO3), `tournament-deadline-audit-results.json` (đơn đúng hạn nhưng không thể duyệt), `tournament-validation-copy-audit.json` (CANCELLED nhưng component vẫn chờ check-in), `tournament-flow-audit-cleanup.json` (xác nhận đóng các giải thử), `flow-audit-backend-results.json` và `flow-audit-client-tests.txt` (kết quả test). Các script cùng tiền tố giữ cách tái hiện bằng các tài khoản thử sẵn có trên máy.
