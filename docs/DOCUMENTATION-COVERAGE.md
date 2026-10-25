# Documentation coverage

Bộ Markdown cho GitHub, đối chiếu source ngày **24/10/2026**. Giai đoạn 1–2 đã có trước; lần này hoàn thành nội dung giai đoạn 3–5 theo yêu cầu làm hết phần còn lại của người dùng. Không dừng chờ review giữa ba giai đoạn này.

Bổ sung ngày **25/10/2026**: cập nhật tài liệu theo hàng đợi worker tự động và worker riêng của chủ giải, đối chiếu controller/service, lệnh chạy và giao diện hiện tại. Các kết quả kiểm tra của lần hoàn thiện ban đầu bên dưới được giữ nguyên theo thời điểm chạy.

## Created

- **Giai đoạn 1 — nền tảng:** 5 hướng dẫn [Player](player/getting-started.md), Bot Guide và Protocol.
- **Giai đoạn 2 — Bot/Tournament:** tổng 6 hướng dẫn [Bot](bot/bot-guide.md), 6 hướng dẫn [Tournament](tournament/formats.md), JSONL request/response và ví dụ Python/C++/Java chạy được.
- **Giai đoạn 3 — Organizer:** 7 hướng dẫn [tạo giải](organizer/create-tournament.md), [đăng ký](organizer/registration-management.md), [bài nộp](organizer/submission-management.md), [validation](organizer/validation-management.md), [điều hành](organizer/tournament-operation.md), [monitor](organizer/match-monitor.md), [audit](organizer/audit.md).
- **Giai đoạn 4 — Admin:** 5 hướng dẫn [users](admin/users.md), [bots](admin/bots.md), [workers](admin/workers.md), [security](admin/security.md), [system operations](admin/system-operations.md).
- **Giai đoạn 5 — Technical:** 8 hướng dẫn [architecture](technical/architecture.md), [API](technical/api.md), [WebSocket](technical/websocket.md), [worker protocol](technical/worker-protocol.md), [database](technical/database.md), [deployment](technical/deployment.md), [monitoring](technical/monitoring.md), [backup/recovery](technical/backup-recovery.md).
- [Home](README.md), [Glossary](glossary.md), [ảnh lobby desktop thật](player/images/lobby-desktop.png), [ảnh Khám phá desktop toàn trang](tournament/images/discover-desktop.png), checker và BotDocumentationTest.

Tổng: **37 hướng dẫn theo vai trò**, cộng mục lục, glossary và coverage. Không dựng website tài liệu.

## Updated / Removed

- README repo và Home dẫn đủ sáu nhóm, không còn link tới tài liệu đã xóa.
- Xóa 62 Markdown theo tên CHANG-* ở lượt trước. Lượt này bỏ 37 Markdown cũ còn lại ở gốc docs; hướng dẫn deploy/worker/backup/auth hữu ích được viết lại vào tài liệu chuẩn, các báo cáo tiến độ/sửa UI/kiểm thử lịch sử không giữ làm hướng dẫn hiện tại.
- Giữ source/migrations/data/replay/benchmark và các thay đổi onboarding worker đã có. Không xóa chức năng game để dọn tài liệu.
- Route inventory trong API đọc từ mapping controller; checker phát hiện thiếu route hoặc nguồn/line thay đổi. Công cụ bot-guide cũ trỏ về Protocol mới, không tự tái tạo giao diện game trong lần viết tài liệu này.

## Coverage theo giai đoạn

| Topic | Source verified | Documented | Screenshot | Example | Status |
| --- | --- | --- | --- | --- | --- |
| 1: Luật và chơi | ClassicRuleSet, OnlineMatch, UI lobby/game | 5 Player guides | Lobby desktop thật | Đặt quân/8 hướng/combat/cứ điểm | Nội dung hoàn thành |
| 1: Bot lifecycle/protocol | BotProtocol, runners và DTO | Guide + Protocol | Không bắt buộc | JSONL với codec/engine thật | Reference hoàn thành |
| 2: Examples/limits/build | Mẫu repo, sandbox, workspace/policy | 6 Bot guides | Chưa có ảnh workspace bổ sung | Python/C++/Java, limits | Nội dung hoàn thành |
| 2: Tournament flow | Registration/timeline/freeze/validation/schedulers | 6 Tournament guides | Khám phá desktop thật; còn thiếu từng phase | Trạng thái/policy và tie-break | Nội dung hoàn thành |
| 3: Organizer | Wizard/nav, service/controller, dashboards | 7 guides | Còn thiếu ảnh wizard/monitor | Action/guard/revision/review | Nội dung hoàn thành |
| 4: Admin | Directory, security, telemetry/control/routing, operations | 5 guides | Còn thiếu ảnh mới theo vai trò | PowerShell/Bash, token, drain/revoke | Nội dung hoàn thành |
| 5: API/WebSocket/worker | Controller mappings, gateways, queue/lease/result | 3 technical references | Không bắt buộc | Envelope/records/inventory | Nội dung và source contracts |
| 5: Architecture/DB | Build/modules, SQL + Java Flyway, persistence | 2 technical guides | Mermaid thay ảnh UI | Data/model/constraints | Nội dung hoàn thành |
| 5: Deploy/monitor/recovery | Compose/Nginx/Caddy/profiles, metrics, ops.py, agent | 3 technical runbooks | Không bắt buộc | Local/VPS/update/backup/restore | Cần diễn tập trên hạ tầng đích |

## Missing

Không còn thiếu file của cấu trúc năm giai đoạn trong prompt. Ảnh thật của từng phase/role giải, màn hình worker sau onboarding, và thử deploy/restore trên hạ tầng đích chưa hoàn thành trong lượt tài liệu này. Các vị trí cần ảnh được đánh dấu thay vì chèn ảnh giả hoặc ảnh dữ liệu nhạy cảm.

## Unsupported

Không document thành tính năng có sẵn: JWT/Swagger tự cấu hình, bot stdin INIT/PLACE/READY/GAME_OVER, double elimination, stream bàn cờ remote từng nước, nhiều Central replica, distributed session/rate limiter, resume engine RAM giữa trận, tự scale capacity, UI reset password/đổi role tùy ý, tự backup ra cloud hoặc purge toàn bộ artifact/replay theo ngày.

Swiss, Team, emergency submission và remote recovery **có** trong source; được mô tả với đúng điều kiện, không đánh dấu thiếu. Không hứa mọi giải có mọi tính năng hoặc tự bắt đầu bất chấp hạ tầng/policy.

## Needs verification

- **Proxy body limit:** mẫu Nginx/Caddy là 6 MiB, thấp hơn request complete worker 22 MiB/replay 15 MiB. Runbook nêu rõ cần cấu hình riêng và thử upload lớn; không sửa proxy production trong lượt này.
- **VPS/restore:** lệnh đối chiếu file/config/công cụ repo và tài liệu primary Docker/Certbot/Caddy; chưa deploy hoặc restore dữ liệu thật. Restore Nginx cùng host cần xử lý cổng loopback cố định của overlay.
- **API:** danh mục annotation không suy luận security. Hợp đồng auth/body/response/guard chính được viết theo controller/service; những family còn lại link record/method cụ thể. Không có OpenAPI schema toàn bộ DTO hoặc compatibility promise cho client bên ngoài.
- **Sức tải:** limits/capacity là guard/config, không benchmark production. Synthetic remote load không chứng minh throughput Docker.
- **Bot mẫu:** C++/Java starter trích JSON cần thiết của server bằng parser tối thiểu; không phải thư viện JSON tổng quát. Cần parser phù hợp nếu viết chiến thuật đọc board sâu.

## Kiểm chứng

Tại root repo:

```sh
node tools/check-documentation.mjs
python tools/verify-bot-examples.py
git diff --check
```

Checker không gọi production, không tạo token/worker/giải, không test endpoint ngoài mạng hoặc khẳng định ảnh mọi giao diện đã được review. Kiểm thử source/protocol không thay benchmark Docker, browser walkthrough hoặc diễn tập khôi phục thực.

Giai đoạn 1–2 đã chạy 142 kiểm tra backend chọn lọc về codec, policy, timeline/deadline, workspace, validation, Swiss/team/bracket: 142 đạt, không fail/skip.

Lượt hoàn thiện giai đoạn 3–5:

- Checker: đủ 37 hướng dẫn, 41 Markdown tính cả README repo, 745 liên kết/ảnh nội bộ, 174 API mappings khớp source, ba ví dụ bot nhúng và JSONL đạt kiểm tra. Syntax Node/Python của công cụ được kiểm tra; `git diff --check` đạt.
- Bot Python, Python chiến thuật, C++17, Java 21 và executable JAR chạy nhiều request liên tiếp: đạt. Lần đầu compiler bị chặn ghi executable trong temp sandbox; chạy lại với temp nằm trong workspace đạt, không sửa source bot.
- Kiểm tra trước phát hành: toàn bộ 470 kiểm thử client đạt, không thất bại hoặc bỏ qua; bao gồm luồng đăng ký worker, che/xóa token và lệnh theo hệ điều hành.
- Chạy chung 104 kiểm tra backend chọn lọc về protocol tài liệu, Admin, WebSocket, điều hành giải và worker: **103 đạt, 1 thất bại, 0 bỏ qua**. Ca `WorkerControlTest.fifoDoesNotStarveCacheMissAndCompletedMatchesAreNotClaimable` (kế thừa từ RemoteJobClaimTest) chọn job khác kỳ vọng. Chạy riêng ca này lần tiếp theo **đạt**; chưa xác định nguyên nhân phụ thuộc lượt chạy/thời gian, không gọi toàn bộ suite là xanh ổn định và không sửa scheduler/test để che kết quả.
- Ảnh Khám phá giải chụp thật full page ở viewport 1440×900; không tạo giải/token/worker hoặc nhập credential để có ảnh. Các ảnh flow đăng nhập còn thiếu nêu ở Missing.

Backend dùng môi trường test local, một số runner được mock. Việc kiểm chứng không deploy hay thay dữ liệu production; không lấy các kết quả này làm benchmark Docker/khả năng phục vụ thực tế.

## Cập nhật tài liệu worker — 25/10/2026

Đã viết lại hướng dẫn worker dùng chung và worker riêng: nơi cấp token theo vai trò, các bước tải JAR/runtime, lệnh PowerShell/Bash, chạy lại bằng đúng state-dir, thu hồi token so với credential, giới hạn 20 máy/5 token và phạm vi một giải. Bỏ chỉ dẫn vận hành theo nhóm; mô tả AUTO/LOCAL/REMOTE_REQUIRED cùng thời điểm quyết định chạy local. API, kiến trúc, schema V43/V44, cập nhật server và recovery được đồng bộ với source.

Kiểm chứng trong lượt sửa tài liệu:

- `node tools/check-documentation.mjs`: đạt; 37 hướng dẫn, 41 Markdown, 781 liên kết/ảnh nội bộ và 177 API mappings khớp source. Ví dụ bot nhúng và JSON Lines giữ nguyên, checker đạt.
- Tám đối chiếu lệnh lần đầu/chạy lại cho PowerShell/Bash, ở hướng dẫn Admin và chủ giải, khớp hàm tạo lệnh hiện tại.
- 12 liên kết tới mục trong Markdown được đối chiếu với heading đích; đạt.
- `git diff --check`: đạt. Lượt này chỉ chỉnh Markdown, không thay đổi hoặc triển khai ứng dụng lên VPS.
