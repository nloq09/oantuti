# RPS Battle Documentation

Hướng dẫn chính thức cho RPS Battle — Oẳn Tù Tì chiến thuật 9×9. Bộ **Markdown đọc trực tiếp trên GitHub**, không cần chạy game hoặc build website. Nội dung theo source hiện tại, cập nhật ngày **24/10/2026**.

## Bạn là ai?

### Người chơi

[Bắt đầu](player/getting-started.md) → [Luật chơi](player/game-rules.md) → [Cách chơi một ván](player/how-to-play.md) → [Luồng tham gia giải](player/tournament-flow.md) → [FAQ](player/faq.md).

### Viết Bot

[Bot Guide](bot/bot-guide.md) → [Protocol](bot/protocol.md) → [Python/C++/Java mẫu](bot/examples.md) → [Build/Test/Submit](bot/build-test-submit.md). Tra [giới hạn](bot/limits.md) và [chẩn đoán lỗi](bot/troubleshooting.md).

### Tham gia giải

[Thể thức](tournament/formats.md) → [Đăng ký](tournament/registration.md) → [Coding](tournament/coding-phase.md) → [Final Validation](tournament/validation.md) → [Check-in](tournament/check-in.md) → [Bracket/Ranking](tournament/bracket-ranking.md).

### Organizer — Chủ giải

[Tạo giải](organizer/create-tournament.md) → [Quản lý đăng ký](organizer/registration-management.md) → [Bài nộp](organizer/submission-management.md) → [Kiểm thử chính thức](organizer/validation-management.md) → [Điều hành giải](organizer/tournament-operation.md). Khi thi đấu dùng [Match Monitor/Replay](organizer/match-monitor.md) và [Audit](organizer/audit.md).

### Admin

[Người dùng](admin/users.md) · [Bot](admin/bots.md) · [Cài đặt và vận hành worker](admin/workers.md) · [Bảo mật](admin/security.md) · [System Operations](admin/system-operations.md).

### Developer

[Kiến trúc](technical/architecture.md) · [REST API](technical/api.md) · [WebSocket](technical/websocket.md) · [Worker Protocol](technical/worker-protocol.md) · [Database](technical/database.md) · [Setup/Deploy Ubuntu](technical/deployment.md) · [Monitoring](technical/monitoring.md) · [Backup/Recovery](technical/backup-recovery.md).

## Tra cứu và phạm vi

[Glossary](glossary.md) thống nhất thuật ngữ. [Coverage report](DOCUMENTATION-COVERAGE.md) ghi đủ năm giai đoạn, source đối chiếu, phần chưa hỗ trợ, ảnh còn thiếu và giới hạn kiểm chứng.

37 hướng dẫn nằm trong sáu thư mục trên. Các báo cáo chặng, prompt triển khai và ghi chú sửa lỗi cũ đã gỡ; nội dung vận hành còn cần được đưa vào đúng hướng dẫn, không giữ nhiều bản chỉ dẫn mâu thuẫn. Git history vẫn lưu phiên bản cũ.

Player/Bot/Tournament là tài liệu cho người dùng; Organizer/Admin/Technical dành cho vận hành và phát triển. Đây là cách chia độc giả, **không phải** cơ chế cấp quyền. Endpoint/source/secret vẫn được backend kiểm tra theo session, ownership và assignment.

## Bảo trì tài liệu

1. Sửa Markdown cùng thay đổi rule/protocol/config/UI. Dùng liên kết tương đối và đúng chữ hoa/thường để GitHub/Linux mở được.
2. Thêm ảnh thật cạnh tài liệu; ảnh thiếu ghi rõ, không dùng mockup làm bằng chứng giao diện. Không đưa password/token/cookie/source riêng vào ảnh hoặc ví dụ.
3. Khi đổi API mapping, chạy `node tools/documentation-routes.mjs --write`, rồi cập nhật auth/request/response/guard trong API reference; danh mục tự động không suy ra quyền.
4. Chạy `node tools/check-documentation.mjs` để kiểm tra cấu trúc, liên kết/ảnh, route inventory, bot mẫu nhúng và JSON Lines. Dùng `python tools/verify-bot-examples.py` khi sửa ví dụ bot.
5. Commit/push để GitHub hiển thị phiên bản mới. Tài liệu không cần deploy lại game; thay hướng dẫn không có nghĩa đã áp dụng cấu hình lên VPS.

Mỗi hướng dẫn có nguồn đối chiếu để người bảo trì kiểm tra. Các con số mặc định là cấu hình/source, không phải benchmark hoặc SLA production.
