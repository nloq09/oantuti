# Viết bot đầu tiên

Bot là chương trình thay bạn chọn nước đi. Server gửi trạng thái qua **stdin**, bot trả hành động qua **stdout**. Bạn không cần viết web server, gọi API hay tự kết nối WebSocket từ bot.

Đọc [Luật chơi](../player/game-rules.md), sau đó dùng [Bot Protocol](protocol.md) làm reference. Người chỉ chơi trên trình duyệt không cần cài worker; việc cài worker dành cho người vận hành máy chạy trận.

## Chọn ngôn ngữ và file

- **Python:** một file `.py`, runner đặt tên `main.py`, dùng Python 3.12; đọc từng dòng stdin và `print(..., flush=True)`.
- **C++:** một file `.cpp`, runner đặt tên `main.cpp`, có `main()`, biên dịch bằng `g++ -std=c++17 -O2`; flush stdout sau mỗi phản hồi.
- **Java:** một file `.java`, có `class Main` và `public static void main(String[] args)`, không khai báo `package`; runner dùng Java 21.
- **JAR:** file `.jar` chạy được bằng `java -jar`, có entry point trong manifest. Đây là lựa chọn tải bot có sẵn; editor trên web chỉ viết source Python/C++/Java. Giải phải cho phép dùng bot có sẵn và ngôn ngữ Java.

Không dựa vào thư viện cài riêng hay mạng bên ngoài: sandbox không có mạng và không cài dependency cho bot. Các bot mẫu của repo dùng thư viện chuẩn. Quyền cho phép từng ngôn ngữ còn tùy chính sách giải và image sẵn có trên server.

## Vòng đời một bot

1. Bạn viết/tải bot lên hoặc tạo bản trong workspace giải.
2. Server chuẩn bị chương trình/container. Placement và ready được phía hệ thống xử lý; protocol chương trình hiện chỉ chọn nước khi chơi.
3. Mỗi khi đến lượt, bot nhận một object JSON trên **đúng một dòng**: `YOUR_TURN`.
4. Bot đọc `player`, `state`, `legalActions`, chọn hành động và trả một dòng `ACTION` với cùng `sequence`.
5. Server kiểm tra phản hồi rồi áp dụng bằng GameEngine. Tiến trình tiếp tục đọc để xử lý lượt sau; không thoát ngay sau lượt đầu.
6. Hết trận server đóng tiến trình. Hiện không có message `INIT`, `GAME_OVER`, `READY` hay `PLACE` trong protocol stdin/stdout này.

Không tự tăng sequence hoặc giả định mỗi yêu cầu tăng đúng 1. Không giữ mặc định P1 cho mọi trận. Có thể giữ bộ nhớ giữa các lượt, nhưng nên dùng trạng thái mới nhận làm nguồn đúng khi tính toán.

## Bot Python tối thiểu chạy được

```python
import json
import sys

for line in sys.stdin:
    request = json.loads(line)
    if request["type"] != "YOUR_TURN":
        continue
    actions = request["legalActions"]
    if not actions:
        raise RuntimeError("No legal action supplied by runner")
    response = {
        "type": "ACTION",
        "sequence": request["sequence"],
        "action": actions[0],
    }
    print(json.dumps(response, separators=(",", ":")), flush=True)
```

Bot này luôn chọn nước hợp lệ đầu tiên, đủ để bắt đầu kiểm thử protocol. Nó chưa có chiến thuật tốt. **Không in** lời chào, dấu nhắc nhập liệu, Markdown hoặc log ra stdout. Nếu cần debug, dùng `print("debug", file=sys.stderr)` và tránh ghi quá nhiều.

Các file mẫu có sẵn để tải/copy:

- [Python tối thiểu](../../client/assets/bot-examples/main.py).
- [Python có chiến thuật](../../client/assets/bot-examples/tactical.py).
- [C++17](../../client/assets/bot-examples/main.cpp).
- [Java 21](../../client/assets/bot-examples/Main.java).

C++ và Java mẫu đã có phần đọc JSON/giữ nguyên action bằng thư viện chuẩn; không tự tách JSON bằng dấu phẩy vì ID/chuỗi có thể chứa ký tự escape.

## Đọc trạng thái để chọn nước

- `matchId`: nhận diện ván. Không coi đây là token hay ID giải.
- `turn`: số lượt của ván; `sequence`: phiên bản trạng thái phải trả lại chính xác.
- `player`: bên bot đang điều khiển. `state.currentPlayer` là bên đang có lượt.
- `state.board`: có `rows`, `cols`, `pieces`; không phải mảng 9×9.
- Mỗi quân có `id`, `ownerId`, `type`, `status`, `position`. Lấy chủ quân từ `ownerId`, loại từ `type`; không suy luận bằng tên ID.
- `legalActions`: các lựa chọn runner đang cung cấp. Chọn một object có sẵn rồi gửi lại là cách ít sai nhất.

Bot thấy vị trí và loại quân của cả hai bên; hiện không có quân ẩn. Tọa độ vẫn là tọa độ server dù bot là P2.

Muốn cải thiện chiến thuật: tra quân ở ô `to`, ưu tiên giao chiến thắng, tránh bị ăn lượt sau và bảo vệ cứ điểm. MOVE hợp lệ có thể khiến quân của bạn thua khi giao chiến; `legalActions` không lọc hộ những nước bất lợi.

## Chạy thử giao tiếp trên máy của bạn

Từ thư mục gốc repository, với Python đã cài:

```sh
python client/assets/bot-examples/main.py < docs/bot/examples/turn.jsonl
```

Lệnh chuyển hướng trên dùng Bash hoặc CMD. Trong PowerShell:

```powershell
Get-Content -LiteralPath docs/bot/examples/turn.jsonl | python client/assets/bot-examples/main.py
```

Bot phải in đúng nội dung tương ứng trong [response.jsonl](examples/response.jsonl): một dòng `ACTION`, sequence 70 và MOVE đầu tiên của request. [turn.jsonl](examples/turn.jsonl) là trạng thái minh họa còn hai quân; không phải đội hình bắt đầu ván.

Nếu thay file bằng bot của bạn, chỉ chạy code bạn tin cậy trên máy cá nhân. Việc chạy được ví dụ stdin/stdout chưa thay thế kiểm thử trong sandbox của game.

## Dùng bot trong game và trong giải

**Trận thường:** đăng nhập → **Bot của tôi** → tải bot → chọn ở **Người chơi của bạn** trong lobby → tạo/tham gia phòng. Bot upload hợp lệ chưa chứng minh chiến thuật/protocol đúng trong mọi lượt; thử với bot dựng sẵn trước.

**Giải:** làm theo [Luồng tham gia giải](../player/tournament-flow.md). Một bản build thành công chưa có nghĩa READY hoặc đã nộp. Hãy kiểm thử công khai, đấu mẫu, hoàn tất điều kiện giải, chọn phiên bản READY rồi **Nộp bot** trước hạn; xem chính sách tự nộp/khóa bài của giải.

Đọc tiếp: [Python/C++/Java mẫu](examples.md) · [Build/Test/Submit](build-test-submit.md) · [Giới hạn thực tế](limits.md) · [Chẩn đoán lỗi](troubleshooting.md). Định dạng từng field: [Bot Protocol](protocol.md).

## Nguồn đối chiếu

Đối chiếu ngày 23/10/2026:

- [BotProtocol](../../server/src/main/java/com/rpsbattle/bot/BotProtocol.java), [BotAgent](../../server/src/main/java/com/rpsbattle/bot/BotAgent.java), [BotLanguage](../../server/src/main/java/com/rpsbattle/bot/upload/BotLanguage.java).
- [CodeWorkspaceService](../../server/src/main/java/com/rpsbattle/tournament/coding/CodeWorkspaceService.java), [WorkspaceStore](../../server/src/main/java/com/rpsbattle/tournament/coding/WorkspaceStore.java), [WorkspaceSandbox](../../server/src/main/java/com/rpsbattle/tournament/coding/WorkspaceSandbox.java), [BotTestService](../../server/src/main/java/com/rpsbattle/codevalidation/BotTestService.java).
- [OnlineMatch](../../server/src/main/java/com/rpsbattle/online/OnlineMatch.java), [bot samples](../../tools/verify-bot-examples.py), [trang bot](../../client/bots.html), [workspace](../../client/tournament-code.html).
