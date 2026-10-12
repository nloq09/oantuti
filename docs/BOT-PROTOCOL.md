# Hướng dẫn tạo bot và giao thức với server

Bot là chương trình đọc trạng thái trận đấu, chọn một nước đi hợp lệ rồi trả lựa chọn cho server. Bạn chỉ viết chiến thuật; server quản lý phòng, xếp quân, lượt chơi, giao chiến và kết quả.

## 1. Luồng hoạt động của một bot

1. Viết một file C++, Python hoặc Java; hoặc đóng gói Java thành JAR.
2. Tải file lên ở trang Vào trận, chọn bot rồi tạo/tham gia phòng.
3. Server tự xếp 6 ROCK, 6 PAPER, 6 SCISSORS cho bot và tự sẵn sàng.
4. Đến lượt bot, server gửi một dòng JSON `YOUR_TURN` vào **stdin** của chương trình.
5. Bot đọc JSON, tính toán và viết một dòng JSON `ACTION` ra **stdout**, rồi flush.
6. Server đọc phản hồi, kiểm tra sequence và nước đi, áp dụng luật, cập nhật bàn cờ rồi chuyển lượt.
7. Chương trình tiếp tục chờ stdin. Ở lượt tiếp theo của chính bot, server gửi trạng thái mới. Khi tiến trình bị đóng, stdin kết thúc; bot nên thoát.

Bot không cần mở HTTP/WebSocket, không cần biết port, không gửi token và không tự kết nối mạng. Trình duyệt dùng REST/WebSocket để người chơi xem và quản lý trận; chương trình bot dùng stdin/stdout với worker của server.

Mỗi bot giữ một tiến trình trong suốt trận. Có thể giữ biến giữa các lượt để ghi nhớ nước đi, tránh lặp hoặc lưu kết quả tìm kiếm. Một trận mới dùng tiến trình mới; không giả định bộ nhớ được giữ giữa các trận. Không có thông điệp kết quả/ACK dành riêng cho bot: sau khi trả một nước đi, chờ dòng YOUR_TURN tiếp theo hoặc EOF, không chờ ACTION_ACCEPTED.

## 2. Dữ liệu server gửi cho bot

Dưới đây là **ví dụ rút gọn số quân và số nước đi để dễ đọc**. Bàn cờ thật bắt đầu với 36 quân; server gửi tất cả quân đang trên bàn và tất cả nước đi hợp lệ của bot.

```json
{
  "type": "YOUR_TURN",
  "matchId": "A1B2C3D4",
  "turn": 12,
  "sequence": 104,
  "player": "P1",
  "state": {
    "phase": "PLAYING",
    "currentPlayer": "P1",
    "board": {
      "rows": 9,
      "cols": 9,
      "pieces": [
        {
          "id": "P1-ROCK-1",
          "ownerId": "P1",
          "type": "ROCK",
          "status": "ALIVE",
          "position": {"row": 4, "col": 4}
        },
        {
          "id": "P1-SCISSORS-1",
          "ownerId": "P1",
          "type": "SCISSORS",
          "status": "ALIVE",
          "position": {"row": 6, "col": 2}
        },
        {
          "id": "P2-SCISSORS-1",
          "ownerId": "P2",
          "type": "SCISSORS",
          "status": "ALIVE",
          "position": {"row": 3, "col": 4}
        },
        {
          "id": "P2-PAPER-1",
          "ownerId": "P2",
          "type": "PAPER",
          "status": "ALIVE",
          "position": {"row": 4, "col": 6}
        }
      ]
    },
    "players": [
      {"id": "P1", "name": "Player 1", "pieceIds": ["P1-ROCK-1", "P1-SCISSORS-1"]},
      {"id": "P2", "name": "Player 2", "pieceIds": ["P2-SCISSORS-1", "P2-PAPER-1"]}
    ]
  },
  "legalActions": [
    {"type": "MOVE", "pieceId": "P1-ROCK-1", "from": {"row": 4, "col": 4}, "to": {"row": 3, "col": 4}},
    {"type": "MOVE", "pieceId": "P1-ROCK-1", "from": {"row": 4, "col": 4}, "to": {"row": 4, "col": 5}}
  ]
}
```

Ví dụ được xuống dòng để đọc. Khi truyền thực tế, **toàn bộ object nằm trên một dòng**; ký tự newline đánh dấu kết thúc thông điệp. Parse JSON theo tên trường, không dựa vào thứ tự các trường hoặc khoảng trắng.

### Các trường ở cấp ngoài

- `type`: chuỗi `YOUR_TURN`, nghĩa là server đang yêu cầu bot chọn nước đi.
- `matchId`: mã trận. Có thể dùng để xác định bộ nhớ chiến thuật thuộc trận nào.
- `turn`: số lượt hành động đã được tính trong trận, bắt đầu từ 0 khi bắt đầu chơi. Đây là số lượt chung của cả hai bên, không phải số lượt riêng của bot và không phải một vòng gồm hai người đi. PASS cũng tiêu thụ lượt.
- `sequence`: số nguyên đánh dấu phiên bản trạng thái server. Phản hồi phải sao chép **đúng giá trị nhận được**. Nó đã tính cả các thay đổi trước khi chơi, nên không bắt đầu từ 0 và không nhất thiết bằng turn. Không tự tăng, không lấy turn thay sequence, không giả định mỗi lần bot nhận dữ liệu chỉ tăng 1.
- `player`: định danh bên mà bot điều khiển, `P1` hoặc `P2`. **Tên trường là player**, không phải playerId. Không viết cố định P1 vì bot tham gia phòng có thể là P2.
- `state`: trạng thái hiện tại để đánh giá nước đi.
- `legalActions`: danh sách những hành động bot được phép chọn ở chính trạng thái này.

### state.phase và state.currentPlayer

`phase` là giai đoạn trận, hiện worker gửi yêu cầu bot trong giai đoạn `PLAYING`. `currentPlayer` là người đang có lượt; trong yêu cầu YOUR_TURN bình thường, nó trùng với `player`.

Bot không cần xử lý đặt quân/READY. Không gửi PLACE, READY hay RANDOM_PLACEMENT qua giao thức bot.

### state.board

- `rows`, `cols`: kích thước bàn; hiện đều là 9. Khi viết thuật toán, đọc từ dữ liệu thay vì luôn gắn cứng 9.
- `pieces`: danh sách object quân đang trên bàn. Đây không phải mảng 9×9; ô không có quân không có object riêng. Bot có thể dựng một ma trận hoặc map `(row,col) → quân` để tra cứu nhanh.

Trong mỗi object quân:

- `id`: ID duy nhất của quân. Khi đi phải dùng đúng ID này làm pieceId. Coi ID là chuỗi opaque: không suy ra loại/chủ quân bằng cách tách tên ID; đọc type và ownerId.
- `ownerId`: bên sở hữu. `ownerId == request.player` là quân của bot; khác player là quân đối thủ.
- `type`: `ROCK` (Búa), `PAPER` (Bao), `SCISSORS` (Kéo).
- `status`: hiện quân trên bàn có trạng thái `ALIVE`. Khi bị loại, quân bị gỡ khỏi board.pieces; không chờ một object DEAD xuất hiện trong danh sách này. Lọc ALIVE khi tính toán để giữ logic rõ ràng.
- `position.row`, `position.col`: vị trí hiện tại, đều là số nguyên.

Bot thấy vị trí **và loại quân của cả hai bên**; hiện không có quân ẩn. Dùng board.pieces làm nguồn chính để đếm quân sống và dựng bàn cờ. Mảng được serialize theo ID, không phải theo ô; thứ tự phần tử không thể hiện độ ưu tiên chiến thuật.

### Tọa độ bàn cờ

Góc trên trái là `(0,0)`, góc dưới phải là `(8,8)`. Row tăng từ trên xuống; col tăng từ trái sang phải:

- Đi lên: `(row-1,col)`.
- Đi xuống: `(row+1,col)`.
- Đi trái: `(row,col-1)`.
- Đi phải: `(row,col+1)`.
- Đi chéo: `(row-1,col-1)`, `(row-1,col+1)`, `(row+1,col-1)`, `(row+1,col+1)`.

P1 xuất phát ở hàng 6–8, P2 ở hàng 0–2. P1 thường tiến lên, P2 thường tiến xuống, nhưng **cả hai được đi đủ 8 hướng, kể cả chéo**. Giao diện xoay góc nhìn để quân của người xem ở dưới; dữ liệu bot luôn giữ tọa độ server. Không xoay from/to khi bot là P2.

### state.players

Mỗi player có `id`, `name`, `pieceIds`. Name là tên hiển thị, không phải khóa nhận diện. PieceIds là danh sách ID gắn với player trong trạng thái; để xác định quân còn sống/vị trí/loại, tra board.pieces và ownerId thay vì chỉ đếm pieceIds.

### Những dữ liệu chưa gửi

Giao thức này không gửi event log, lịch sử các nước đi, token upload/phòng, tên bot, seed xếp quân, deadline, thời gian còn lại hay bảng luật. Nếu cần lịch sử, lưu trạng thái giữa các lần YOUR_TURN và so sánh; giữa hai lần nhận của bot có thể có cả nước đi của bot và đối thủ. Không truy cập những trường này như thể chúng có trong JSON. Luật và giới hạn hiện tại được mô tả bên dưới.

## 3. Cách dùng legalActions để tính toán

Server đã sinh các nước MOVE hợp lệ, bao gồm đi vào ô trống và tấn công. Trong phòng online hiện tại, nếu có MOVE thì danh sách chứa các MOVE; nếu không có MOVE thì server gửi một PASS để bot vẫn trả được hành động:

```json
[{"type":"PASS"}]
```

Bot **không được tự PASS để tránh một nước khó** khi PASS không có trong danh sách. Danh sách online không cung cấp SURRENDER cho chương trình bot; chủ bot có thể đầu hàng bằng giao diện. Bộ decode cấp thấp hỗ trợ nhiều loại hơn, nhưng hành động của bot online vẫn phải có trong legalActions.

Một MOVE có:

- `pieceId`: quân sẽ đi.
- `from`: ô hiện tại của quân.
- `to`: ô đích.

Không có loại ATTACK riêng. Nếu to đang có quân đối thủ, MOVE đó là tấn công. Server chỉ đảm bảo nước đi hợp lệ theo luật; **hợp lệ không có nghĩa là có lợi**. Đi ROCK vào PAPER vẫn có thể nằm trong danh sách và khiến ROCK bị loại.

Quy trình dễ triển khai:

1. Dựng map quân theo ID và map quân theo ô từ board.pieces.
2. Với từng MOVE, tra quân tấn công theo pieceId và quân ở ô to.
3. Tính điểm: ăn quân thắng thì cộng; tự mất quân thì trừ; cân nhắc đổi quân cùng loại.
4. Mô phỏng bàn cờ sau giao chiến rồi kiểm tra quân sống ở ô đích có bị đối thủ ăn ngay ở lượt sau không.
5. Với ô trống, cân nhắc tiến gần quân có thể thắng, né loại khắc chế và tránh lặp đi lặp lại.
6. Chọn action có điểm tốt nhất và trả **nguyên object đã chọn**. Không tự tạo ID mới, đổi tọa độ hay gửi toàn bộ bàn cờ.

Luật đang dùng: mỗi nước di chuyển một ô theo 8 hướng, kể cả chéo, không ra ngoài bàn, không vào ô có quân cùng bên. ROCK thắng SCISSORS; SCISSORS thắng PAPER; PAPER thắng ROCK. **Cùng loại thì cả hai bị loại**. Khi bên tấn công thắng, quân đó sống ở ô đích; khi thua, quân phòng thủ vẫn ở ô đích. Nếu cả hai cùng loại, ô đích trống sau trận đánh.

Trong ví dụ trên, ROCK ở `(4,4)` đi lên `(3,4)` ăn SCISSORS. Đi sang `(4,5)` tuy hợp lệ nhưng đứng cạnh PAPER ở `(4,6)`, có nguy cơ bị ăn. Nếu mô phỏng, phải gỡ quân ở ô from và gỡ quân thua trước khi đánh giá bàn sau nước đi; không chỉ thay tọa độ trên bàn cũ.

Trận kết thúc khi một bên hết quân. Giới hạn mặc định là 200 lượt; khi chạm giới hạn, bên có nhiều quân sống hơn thắng, bằng nhau thì hòa. Vì vậy đổi quân cùng loại có thể hợp lý tùy chênh lệch quân và số lượt còn lại. Các điểm số/thuật toán gợi ý ở đây là chiến thuật mẫu, không phải điều kiện thắng do server áp đặt.

## 4. Bot trả về gì cho server?

Với request có sequence 104, bot chọn nước đầu tiên thì trả:

```json
{"type":"ACTION","sequence":104,"action":{"type":"MOVE","pieceId":"P1-ROCK-1","from":{"row":4,"col":4},"to":{"row":3,"col":4}}}
```

Ba trường cần thiết:

- `type`: đúng chuỗi `ACTION`, phân biệt chữ hoa/chữ thường.
- `sequence`: đúng số nguyên từ request vừa nhận, giữ kiểu số JSON, không chuyển thành chuỗi.
- `action`: một object chọn từ legalActions. Với MOVE phải giữ đủ type, pieceId, from, to; row/col phải là số nguyên. Với PASS chỉ cần type PASS.

Ví dụ nếu request mới có sequence 105 và legalActions chỉ có PASS:

```json
{"type":"ACTION","sequence":105,"action":{"type":"PASS"}}
```

Không gửi player/token/matchId trong response; server đã gắn tiến trình với bên điều khiển. Không thêm object board đã chỉnh sửa, điểm đánh giá hay danh sách các nước dự kiến. Mỗi YOUR_TURN chỉ trả một ACTION cho lượt hiện tại. Nếu tính trước nhiều lượt, giữ kế hoạch trong bộ nhớ bot, đến lượt sau phải kiểm tra lại theo snapshot/legalActions mới.

Server kiểm tra phản hồi theo thứ tự: đọc một dòng hoàn chỉnh → parse JSON → kiểm tra type/sequence/các trường action → đối chiếu action trong danh sách đã gửi → engine kiểm tra và áp dụng luật → phát trạng thái mới cho người xem. Bot không quyết định ai thắng giao chiến hay tự cộng/trừ số quân trên server.

## 5. Vòng lặp đọc/ghi theo từng ngôn ngữ

Mẫu C++, Python, Java tải tại `/bot-guide.html`. Thay hàm chọn action, giữ phần giao tiếp. Ví dụ Python tối thiểu:

```python
import json
import sys

def choose_action(request):
    return request["legalActions"][0]

for line in sys.stdin:
    request = json.loads(line)
    action = choose_action(request)
    response = {
        "type": "ACTION",
        "sequence": request["sequence"],
        "action": action,
    }
    print(json.dumps(response, separators=(",", ":")), flush=True)
```

- Python: json.loads đọc request; json.dumps tạo response; print(..., flush=True). Không dùng input("Nhập nước đi:") vì prompt in ra stdout làm hỏng giao thức. Debug bằng print(..., file=sys.stderr).
- C++: std::getline(std::cin, line) đọc một request; in JSON với `std::endl` hoặc `std::flush`. Không pretty-print response thành nhiều dòng. Mẫu có sẵn chỉ trích action đầu tiên bằng thư viện chuẩn; để viết chiến thuật đầy đủ cần parser JSON đúng chuẩn, tự chứa trong file source. Môi trường không cài sẵn nlohmann/json nên không chỉ thêm include rồi kỳ vọng biên dịch được.
- Java: BufferedReader.readLine đọc request; System.out.println(response) rồi System.out.flush(). Source dùng public class Main, không package. Java không kèm sẵn Jackson/Gson trong container; nếu dùng thư viện ngoài, đóng gói JAR có dependency và Main-Class. Mẫu source có sẵn dùng thư viện chuẩn để trả action đầu tiên; regex/trích chuỗi trong mẫu không thay thế một parser JSON đầy đủ để xử lý mọi trường.

Bot nên xử lý nhiều dòng liên tiếp trong cùng tiến trình. Nếu chỉ đọc một lượt rồi thoát, lượt tiếp theo sẽ lỗi. Bot không cần viết file trên máy chủ; nếu dùng bộ nhớ ngoài trong container, chỉ /tmp là vùng ghi tạm và không tồn tại qua trận.

## 6. Ví dụ thuật toán tính điểm bằng Python

File mẫu chiến thuật tải tại `/assets/bot-examples/tactical.py`. Nó đọc đủ board.pieces, tra quân theo ID/ô, chấm điểm giao chiến, mô phỏng nước đi, phạt nguy cơ bị ăn ngay và ưu tiên tiến gần quân có thể thắng. Vẫn trả một action nguyên bản từ legalActions. Đây là baseline một lớp, chưa tìm kiếm minimax nhiều lượt và không bảo đảm thắng bot chiến thuật của server.

Các phần quan trọng trong file:

- `BEATS`: quan hệ thắng giữa ba loại quân.
- `evaluate(action, request)`: đánh giá một ứng viên từ trạng thái nhận được.
- `choose_action(request)`: chọn ứng viên có điểm cao nhất.
- Vòng stdin/stdout: giữ đúng sequence, một JSON trên một dòng, flush sau mỗi phản hồi.

Bạn có thể nâng cấp bằng cách thêm minimax/alpha-beta, quiescence khi có giao chiến, bảng trạng thái đã gặp hoặc tìm kiếm có giới hạn thời gian. Khi mô phỏng nước đi của đối thủ, bot phải tự sinh nước theo luật trên bàn đã mô phỏng; legalActions nhận được chỉ là nước của bên hiện tại ở snapshot hiện tại, không phải mọi nước của đối thủ hay của các trạng thái tương lai.

## 7. Thời gian và lỗi thường gặp

Lượt đầu có 25 giây gồm khởi động/biên dịch; những lượt sau có 2 giây để trả trọn dòng. JSON request không kèm deadline. Nếu tìm kiếm nặng, đặt ngân sách thấp hơn 2 giây, giữ một action hợp lệ làm phương án dự phòng. Tiến trình có 0,5 CPU và 384 MB RAM, không dựa vào tốc độ chạy local để dùng hết sát 2 giây.

Phản hồi tối đa 65.536 ký tự. Không gửi log, lời chào, markdown/code fence, hai action hoặc thêm JSON thứ hai trên stdout. Chỉ gửi dòng ACTION, có newline và flush. Stderr được tách khỏi giao thức nhưng hiện bị bỏ, không hiển thị lỗi biên dịch/debug lên giao diện.

Các lỗi khiến bot bị xử thua: không biên dịch/khởi động được; tiến trình thoát; không trả dòng đầy đủ; timeout; JSON sai; type sai; sequence cũ; row/col kiểu sai; ID/from/to không khớp; action ngoài legalActions; vượt tài nguyên hoặc độ dài phản hồi. UI hiện báo BOT_FAILED chung, nên hãy tự kiểm tra cú pháp và giao tiếp trước khi tải lên.

Nếu chủ bot rời/đầu hàng trong lúc bot tính toán, trận kết thúc và phản hồi đến muộn bị bỏ; bot không thể mở lại trận bằng một ACTION. Sau khi trận kết thúc, server đóng tiến trình/container, không gửi yêu cầu nước đi mới.

## 8. Tự kiểm tra trước khi tải lên

1. Chuẩn bị một file input gồm hai hoặc nhiều request JSON, mỗi object trên đúng một dòng. Để kiểm tra parse/stdout, có thể dùng fixture rút gọn, nhưng tất cả pieceId/from/to của action phải khớp các quân trong fixture.
2. Chạy bot với stdin từ file, lưu stdout riêng; với mỗi request phải có đúng một dòng ACTION.
3. Parse từng response bằng JSON parser; kiểm tra type ACTION, sequence giống request, action thuộc legalActions.
4. Thử bot ở cả P1 và P2; thêm trường hợp chỉ có PASS, ô đích trống, tấn công thắng/thua/cùng loại, nhiều lượt liên tiếp.
5. Kiểm tra thời gian và bộ nhớ trong runner Docker sau khi image sẵn sàng, rồi tải lên thử với bot có sẵn và bạn bè.

Lệnh `python tools/verify-bot-examples.py` kiểm tra các bot mẫu tin cậy trong repository bằng compiler/interpreter local. Nó không kiểm tra mọi chiến thuật và không thay thế kiểm thử Docker. Không dùng cách chạy local này cho source không tin cậy của người khác.

Định dạng upload, cách chuẩn bị Docker, giới hạn file và quyền dùng bot xem trong [BOT-UPLOAD.md](BOT-UPLOAD.md).


## Cứ điểm

Cứ điểm P1 ở A9 `(8,0)`, P2 ở I1 `(0,8)`, đều là góc dưới bên trái theo góc nhìn của đội mình. Chiếm cứ điểm đối phương sau giao chiến hoặc loại hết quân đối phương thì thắng ngay. Nếu cùng loại, cả hai bị loại nên quân tấn công không chiếm được cứ điểm. Không được đặt quân vào cứ điểm khi xếp đội hình ban đầu; có thể di chuyển quân vào để phòng thủ khi đang chơi. Tọa độ trong giao thức vẫn là tọa độ server, không xoay theo người chơi.
