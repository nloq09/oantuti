# Chặng 9 — Hidden tests, submission, khóa bài và hàng đợi

## Luồng sử dụng

Chủ giải tạo giải với chính sách bot, mở đăng ký. Người tham gia chọn bot có sẵn hoặc mở workspace theo policy. Cả hai nguồn đều tạo snapshot, build artifact riêng và chạy **Kiểm tra toàn bộ điều kiện giải**. Nút này kiểm tra giao thức, tài nguyên, nước đi, trận mẫu và hidden tests nếu được yêu cầu, kể cả khi chủ giải tắt nút chạy test công khai/đấu thử riêng lẻ.

Chỉ version READY mới được nộp. Người chơi chọn version và xác nhận luật; server tạo bài nộp cùng đơn đăng ký PENDING trong một transaction. Chủ giải vẫn phải duyệt đăng ký. SUBMITTED có thể thay bằng một version mới nếu còn hạn và policy cho phép. Lịch sử source/version cũ không bị ghi đè. Một user có một bài nộp active cho mỗi giải và một execution identity ổn định qua các lần nộp lại.

Các thời điểm khóa:

- `lockOnSubmit`: khóa ngay khi nộp.
- `lockAtDeadline`: khóa các bài SUBMITTED khi hết hạn.
- Chốt danh sách hoặc bắt đầu giải: khóa bài đã nộp trước khi kiểm tra điều kiện roster. Check-in và executor từ chối bot thiếu bài LOCKED.
- `AUTO_SUBMIT_LATEST_READY`: khi hết hạn, chọn version READY mới nhất, chỉ thay bài đang nộp nếu version đó mới hơn bài hiện tại. Bài bị từ chối/loại không tự được nộp lại. Xử lý deadline có marker PostgreSQL để không chạy lặp vô hạn; trường hợp không đủ điều kiện/capacity được ghi audit.

Khóa áp dụng cho các workspace của user trong giải, chặn sửa source/build và tạo workspace mới để lách khóa. Các artifact vẫn được xác minh SHA-256 trước chạy. Từ chối một bài đã khóa là loại bài (`DISQUALIFIED`), không mở khóa để thay artifact. Retest bài khóa có thể xác nhận lại hoặc loại bài nếu thất bại; không đổi source/artifact.

## Hidden tests và quyền riêng tư

Hidden tests nằm trong Java server, không trong JS/static assets. Bộ test tiến trạng thái thật với reference bot có seed ngẫu nhiên phía server, kiểm tra nhiều sequence và cả hai phía qua BotProtocol/GameEngine hiện có.

Kết quả hidden chỉ có `passed` và `{id,status}` cho từng case. Không lưu/trả input, state, expected action, stderr, exception text hoặc timing riêng của hidden case. Public tests vẫn có input mẫu công khai. TestResult JSON được giữ trên version; organizer chỉ thấy summary và không được đọc source hay debug của participant qua dashboard.

Workspace và API version/source dùng identity từ session, không nhận userId của người khác làm quyền. Mọi mutation dùng CSRF. Submission/dashboard có no-store và Vary Cookie; các public tournament DTO/export không chứa source hoặc đường dẫn artifact.

## Bot có sẵn và runtime chính thức

`POST submission/preuploaded` chụp source/JAR thuộc tài khoản, kiểm tra họ ngôn ngữ và mode, tạo version build riêng. Bot từng READY trong game thường không tự đủ điều kiện cho giải. Python/C++/Java source đi qua compiler/test như bot code mới. JAR được chụp nguyên byte, vẫn phải chạy validation đầy đủ trong sandbox Java 21. Workspace hiển thị rõ JAR không có source để soạn.

Một bot identity nội bộ được lưu cho lịch sử/ownership hiện có; snapshot source identity giữ hash riêng. Artifact thực thi đã compile có SHA-256 trong submission. Tournament executor tra đúng version LOCKED theo tournament/registration, mở artifact này qua WorkspaceSandbox và BotAgent, **không compile lại source trong trận**. Danh tính và replay vẫn đi qua luồng Match/OnlineMatch hiện có.

Để giữ tương thích dữ liệu cũ, giải legacy không đăng ký pipeline mới tiếp tục dùng runner cũ. Giải tạo với `botPolicy` rõ ràng từ UI được enrollment ngay; các giải CODE/BOTH cũ được backfill enrollment, và thao tác import/submit cũng enrollment. API cũ có thể giữ đơn đăng ký legacy, nhưng bot trong giải đã enrollment không thể check-in/start/execution khi thiếu bài LOCKED. Người dùng nên đăng ký qua luồng snapshot → validation → submit mới.

## Dashboard và API

Trang **Bài nộp bot và hàng đợi** có liên kết từ quản lý giải; admin dùng `/admin/submissions.html`. Dữ liệu bài nộp phân trang 25 dòng, queue hiển thị tối đa 100 job gần nhất. Owner/admin xem nguồn bài, ngôn ngữ, version, trạng thái build/tests/submission và thời điểm; thao tác khóa, kiểm thử lại, từ chối, cho nộp lại đòi lý do và revision giải. Không chỉnh source participant. Retest chỉ chạy trước khi giải thi đấu và trên version active của workspace.

Các endpoint dưới `/api/tournaments/{id}`:

- `GET submission/me`, `GET submission/me/versions`: bài và lịch sử version của chính user.
- `POST submission/preuploaded` với `{botId}`: snapshot bot có sẵn, xếp build.
- `POST workspace/me/validate` với `{language,revision}`: xếp toàn bộ required tests.
- `POST workspace/me/submit` với `{versionId,revision,rulesAccepted}`: nộp version thuộc mình, dùng revision giải.
- `GET submissions?page=0`: dashboard owner/admin.
- `POST submissions/{submissionId}/action` với `{action,reason,revision}`; action gồm lock/retest/reject/allow-resubmit.
- `GET coding-jobs`: queue owner/admin, không trả source/job input.

Client lấy trạng thái công việc bằng polling có giới hạn khi đang BUILDING/TESTING. Không cần giữ WebSocket để job tiếp tục; không công khai job event của tài khoản khác. Nút submit chỉ là hỗ trợ UX: server luôn tự kiểm tra trạng thái, quyền, mode, ngôn ngữ, revision, deadline, source/artifact hash và policy.

## Queue, transaction và recovery

V23 thêm bảng submission, queue/job lease, audit, enrollment và marker deadline; bổ sung verdict flags và runtime metadata cho version. Primary/foreign keys, unique bài active theo user/giải và constraint artifact hash/locked timestamp giữ identity.

Queue PostgreSQL có guard row khóa transaction để admission không vượt capacity. Mặc định tối đa **200 job QUEUED/RUNNING**, cấu hình `rps.coding.queue-capacity`; tối đa hai RUNNING trên queue chung. Mỗi user có một workspace job active. Worker lease 240 giây, retry tối đa ba lần nếu lease hết hạn. Completion đối chiếu lease token nên worker cũ không ghi đè lần chạy mới. Job được tạo cùng transaction claim version; commit thành công rồi worker mới đọc được.

Restart không xóa queue. Job QUEUED được lấy tiếp; job RUNNING hết lease được reclaim hoặc đánh FAILED nếu hết số lần thử. Không cần chạy 200 Docker containers cùng lúc. Submission promotion, registration và audit dùng transaction chung; nếu commit lỗi, snapshot RAM được phục hồi và chỉ thông báo sau commit. Job completion/flags/status có transaction; không suy READY bằng tìm chuỗi trong JSON.

Queue chịu phối hợp nhiều worker process bằng DB; phần tournament registry của ứng dụng vẫn là RAM, vì vậy production vẫn nên chạy **một replica server** cho đến khi có coordination đầy đủ cho tournament/online rooms. Chưa có retention tự động cho version/job/source cũ; cần tính quota ổ đĩa và backup. Số đo CPU time/memory peak thực tế chưa được thu; UI báo giới hạn sandbox và elapsed time, không bịa số đo.

## Triển khai và giới hạn

Sao lưu PostgreSQL cùng toàn bộ thư mục bot (bao gồm `coding/`, versions/) và replay trước nâng. Local đã lưu `backups/before-stage9-submissions-v23` với checksum manifest; không đưa backup/fixture/credential lên Git. V23 chỉ thêm bảng/cột, không sửa migration cũ. Deploy client/server đồng bộ, dùng Docker Linux và ba image cố định như nhóm 2–5.

Giới hạn roster/format hiện có (tối đa 32 participant chính thức) được giữ. Kiểm thử tải workspace/queue là mô phỏng storage/admission độc lập, không có nghĩa scheduler đã hỗ trợ giải 2.000 người. Không thêm gameplay, không sửa GameEngine/RuleSet/Match Engine/BotProtocol. Thay đổi executor là chọn artifact đã khóa; BotUploadService chỉ thêm phương thức tạo identity snapshot nội bộ từ version đã kiểm thử.

## Kiểm thử cuối đợt

- Backend chạy tổng: **591 test PASS, 0 fail, 0 skipped**; bao gồm Docker thật. Sau đó bổ sung chặn gửi lại bài REJECTED khi chưa được chủ giải cho phép và chạy lại toàn bộ `TournamentSubmissionApiTest`: **7/7 PASS**. Không cộng hai lượt chạy thành số test độc lập.
- Client: **80/80 PASS**. Build JAR thành công.
- Native E2E cả CODE_IN_PLATFORM_ONLY, PRE_UPLOADED_ONLY và BOTH: build, public/hidden/sample, submit/lock, duyệt roster, thi đấu từ compiled artifact và replay đều PASS; sử dụng Python, C++ và Java.
- Restart bản cuối: ba giải giữ nguyên LOCKED/version/artifact hash, dashboard và replay. Các fixture cũ vòng bảng, BO3/BO5, Swiss/đồng đội và rút giữa giải vẫn giữ lịch sử, kết quả và replay.
- Kiểm tra trình duyệt local: dashboard hiển thị bài đã khóa và job DONE; workspace có Hidden tests PASS, submission LOCKED, source và nút nộp bị vô hiệu hóa đúng trạng thái.

Bộ mới bao gồm hidden-result security, ownership/forged version, resubmit/history/permission, deadline auto-submit, dashboard revision/reason, JAR sandbox, 1.000 workspace/500 save đồng thời, 200 build job rồi 200 test job, admission overflow và lease fencing. Đây là kiểm tra đồng thời tầng storage/queue; chưa đo CPU peak hay mô phỏng 1.000 người bằng trình duyệt.
