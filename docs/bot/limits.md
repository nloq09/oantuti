# Giới hạn bot và công việc kiểm thử

Đối chiếu source ngày 23/10/2026. Cần phân biệt **bot upload chạy trận thường**, **workspace build/test của giải** và **remote worker**. Không lấy một con số của runner này áp cho runner khác. Luật/giới hạn hiển thị của giải và cấu hình server đang chạy là điều kiện cần xem thêm.

## Source, artifact và số phiên bản

| Nội dung | Giá trị trong source | Phạm vi |
| --- | --- | --- |
| File source upload `.py/.cpp/.java` | Tối đa 512 KiB (524.288 byte) | `BotUploadService` |
| JAR upload hoặc nhập | Tối đa 5 MiB | Upload và nhập JAR vào workspace |
| Source workspace | Mặc định 524.288 byte UTF-8; policy chỉ cho đặt từ 1 đến mức này | Source không rỗng; Java phải có Main, không package |
| Artifact workspace sau build | Tối đa 8 MiB | Artifact hợp lệ được hash trước sử dụng |
| Số version | Mặc định 10, policy cho 1–100 | Tổng version của cùng user trong giải và trần từng workspace |
| Job đồng thời của một user | 1 | Gồm workspace khác ngôn ngữ/giải |
| Tổng lượt test trọn đời | Không có bộ đếm trần riêng trong phần workspace được đọc | Vẫn bị giới hạn cửa sổ, trạng thái, rate limit, user-busy và queue |
| Queue build/test/final | Mặc định 200 job QUEUED/RUNNING; 2 job RUNNING dùng chung | `rps.coding.queue-capacity`; không tăng theo số người đăng ký |

Mỗi build đã tạo version đều tính vào giới hạn, kể cả lỗi. Chạy test trên version đang có không tạo một version build mới. Cửa sổ coding được gia hạn có audit và lượt nộp khẩn cấp có thể cấp thêm version; người chơi không tự tăng hạn mức bằng cách đổi ngôn ngữ.

## Sandbox Docker

Các runner dưới đây cùng dùng cấu hình cô lập tương ứng; không phải mức tài nguyên dành cho cả VPS:

| Tài nguyên | Giới hạn |
| --- | --- |
| CPU | 0,5 CPU cho mỗi container |
| RAM và tổng RAM+swap | 384 MiB / 384 MiB; không cấp thêm swap vượt RAM này |
| PID | 64 |
| File descriptor | 64 soft/hard |
| Mạng | `none` |
| Root filesystem | Chỉ đọc; file bot/artifact mount chỉ đọc |
| User | UID/GID 65534 |
| `/tmp` | tmpfs 64 MiB; C++ cho exec để chạy binary; Python/Java/JAR noexec |
| Java heap | `-Xmx128m`; nằm trong trần container 384 MiB |

Image cố định: `python:3.12-slim`, `gcc:14`, `eclipse-temurin:21-jdk`. Runner dùng `--pull=never`; không tự tải image hoặc cài thư viện cho bot. Máy vận hành cần chuẩn bị image trước khi nhận việc.

## Thời gian

| Công việc | Giới hạn được xác minh |
| --- | --- |
| Một nước bot upload | Thông thường 2 giây; exchange đầu 25 giây gồm compile/start |
| Một build workspace | 25 giây chờ process build |
| Boot workspace test | Chờ marker sandbox tối đa 25 giây, sau đó áp hạn trao đổi |
| Một response trong public test | 2 giây |
| Public test / đấu mẫu | Có ngân sách tổng 60 giây trong bộ kiểm thử |
| Hidden tests hiện tại | Có ngân sách kiểm tra 55 giây và 2 giây/response |
| Coding job lease | 240 giây; reclaim tối đa 3 attempts trước báo lỗi |

Các mốc này không phải SLA hoàn thành cả hàng đợi: thời gian chờ queue, chuẩn bị sandbox và số bước kiểm thử là những phần riêng. Đồng hồ mặc định 30 giây của người chơi tay không phải ngân sách của bot.

## Stdout và stderr

| Runner | Stdout | Stderr |
| --- | --- | --- |
| Bot upload / `LocalProcessRunner` | Một dòng tối đa 65.536 ký tự, phải có newline | Bị discard, không có log lưu cho người chơi |
| Workspace test | Ngưỡng 65.536 ký tự mỗi dòng; tổng 4×1024×1024 ký tự mỗi phiên; queue output 4 dòng | Tổng 32 KiB; vượt ngưỡng làm phiên lỗi |
| Remote `SandboxBot` | Ngưỡng 65.536 ký tự/dòng, tổng 4×1024×1024 ký tự/phiên | Tổng 64 KiB |

Transport dùng `Reader` để đếm stdout, nên không xem con số ký tự là một bảo đảm byte UTF-8 bằng nhau. Hãy giữ response ngắn hơn ngưỡng và đúng **một dòng trên mỗi request**; nhiều dòng xuất sớm vẫn sai protocol dù chưa vượt dung lượng. Build workspace có bộ đọc artifact riêng và log stderr giới hạn 32 KiB.

## Khi chạm giới hạn

Không giảm timeout bằng cách bỏ newline/flush, không tự PASS ngoài `legalActions`, không dùng network để chuyển tính toán ra ngoài. Giảm thuật toán, bộ nhớ và debug log; kiểm thử lại trước hạn. Queue đầy hoặc Docker/image chưa sẵn sàng cần người vận hành xử lý; xem [Troubleshooting](troubleshooting.md).

## Nguồn đối chiếu

- [TournamentBotPolicy](../../server/src/main/java/com/rpsbattle/tournament/domain/TournamentBotPolicy.java), [WorkspaceStore](../../server/src/main/java/com/rpsbattle/tournament/coding/WorkspaceStore.java), [TimelineAccessGuard](../../server/src/main/java/com/rpsbattle/tournament/timeline/TimelineAccessGuard.java).
- [BotUploadService](../../server/src/main/java/com/rpsbattle/bot/upload/BotUploadService.java), [DockerBotProcess](../../server/src/main/java/com/rpsbattle/bot/upload/DockerBotProcess.java), [LocalProcessRunner](../../server/src/main/java/com/rpsbattle/bot/LocalProcessRunner.java).
- [WorkspaceSandbox](../../server/src/main/java/com/rpsbattle/tournament/coding/WorkspaceSandbox.java), [BotTestService](../../server/src/main/java/com/rpsbattle/codevalidation/BotTestService.java), [CodingQueueStore](../../server/src/main/java/com/rpsbattle/tournament/coding/CodingQueueStore.java), [CodingWorker](../../server/src/main/java/com/rpsbattle/tournament/coding/CodingWorker.java), [SandboxBot](../../worker/src/main/java/com/rpsbattle/remote/SandboxBot.java).
