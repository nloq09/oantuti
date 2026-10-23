# Bot Protocol — stdin/stdout

Reference này mô tả JSON Lines đang được `BotProtocol` encode/decode, không phải REST API hay WebSocket của trình duyệt. Xem [Hướng dẫn bot](bot-guide.md) để viết chương trình đầu tiên và [Luật chơi](../player/game-rules.md) để hiểu chiến thuật.

## Đóng khung message

- UTF-8; **một object JSON đầy đủ trên một dòng**, kết thúc bằng newline. Ví dụ trình bày nhiều dòng bên dưới chỉ để đọc; dùng [turn.jsonl](examples/turn.jsonl) khi gửi thật.
- Server gửi `YOUR_TURN` vào stdin. Bot trả đúng một `ACTION` xuống stdout rồi flush.
- Giữ vòng lặp cho nhiều yêu cầu trong cùng tiến trình. Không tự xuất thêm message trước yêu cầu hoặc gửi hai phản hồi cho một lượt.
- Debug chỉ xuống stderr; không in prompt, banner hay code fence xuống stdout.
- Không có message khởi tạo, đặt quân, ready hoặc kết thúc riêng trong protocol này. Kết thúc trận được thể hiện bằng việc runner đóng tiến trình.

## YOUR_TURN: server → bot

Ví dụ trạng thái minh họa còn hai quân, không phải đội hình bắt đầu:

```json
{
  "type": "YOUR_TURN",
  "matchId": "DOCS-DEMO",
  "turn": 24,
  "sequence": 70,
  "player": "P1",
  "state": {
    "phase": "PLAYING",
    "currentPlayer": "P1",
    "board": {
      "rows": 9,
      "cols": 9,
      "pieces": [
        {"id":"P1-ROCK-1","ownerId":"P1","type":"ROCK","status":"ALIVE","position":{"row":6,"col":0}},
        {"id":"P2-SCISSORS-1","ownerId":"P2","type":"SCISSORS","status":"ALIVE","position":{"row":1,"col":8}}
      ]
    },
    "players": [
      {"id":"P1","name":"Player 1","pieceIds":["P1-ROCK-1"]},
      {"id":"P2","name":"Player 2","pieceIds":["P2-SCISSORS-1"]}
    ]
  },
  "legalActions": [
    {"type":"MOVE","pieceId":"P1-ROCK-1","from":{"row":6,"col":0},"to":{"row":5,"col":0}},
    {"type":"MOVE","pieceId":"P1-ROCK-1","from":{"row":6,"col":0},"to":{"row":5,"col":1}},
    {"type":"MOVE","pieceId":"P1-ROCK-1","from":{"row":6,"col":0},"to":{"row":6,"col":1}},
    {"type":"MOVE","pieceId":"P1-ROCK-1","from":{"row":6,"col":0},"to":{"row":7,"col":0}},
    {"type":"MOVE","pieceId":"P1-ROCK-1","from":{"row":6,"col":0},"to":{"row":7,"col":1}}
  ]
}
```

| Field | Type | Required | Meaning | Example |
| --- | --- | --- | --- | --- |
| `type` | string | Có | Loại yêu cầu, chính xác `YOUR_TURN` | `"YOUR_TURN"` |
| `matchId` | string | Có | ID ván, coi là chuỗi opaque | `"DOCS-DEMO"` |
| `turn` | integer | Có | Số lượt hiện tại trong ván | `24` |
| `sequence` | integer 64-bit | Có | Phiên bản trạng thái; trả nguyên số này trong response | `70` |
| `player` | string | Có | Bên bot điều khiển, hiện `P1` hoặc `P2` | `"P1"` |
| `state` | object | Có | Trạng thái công khai hiện tại | object trong ví dụ |
| `legalActions` | array of objects | Có | Hành động runner cung cấp cho lượt này | array MOVE hoặc PASS |

Tên trường là **`player`**, không phải `playerId`. Không chuyển sequence sang chuỗi, số thập phân hoặc số lượt. Với ngôn ngữ có giới hạn chính xác số nguyên JSON, cần giữ giá trị nguyên 64-bit chính xác khi parse/serialize.

### State

| Field | Type | Required | Meaning | Example |
| --- | --- | --- | --- | --- |
| `state.phase` | string | Có | Giai đoạn trạng thái; yêu cầu chơi hiện dùng `PLAYING` | `"PLAYING"` |
| `state.currentPlayer` | string | Có | Bên đang có lượt, thông thường trùng `player` | `"P1"` |
| `state.board.rows` | integer | Có | Số hàng | `9` |
| `state.board.cols` | integer | Có | Số cột | `9` |
| `state.board.pieces` | array | Có | Quân đang nằm trên bàn; không có object riêng cho ô trống | xem ví dụ |
| `state.players` | array | Có | Người chơi theo thứ tự P1, P2 | xem ví dụ |
| `state.players[].id` | string | Có | ID bên | `"P2"` |
| `state.players[].name` | string | Có | Tên hiển thị, không phải khóa nhận diện | `"Player 2"` |
| `state.players[].pieceIds` | array of strings | Có | Danh sách ID gắn với bên; không dùng độ dài để đếm quân sống | `["P2-SCISSORS-1"]` |

### Piece

| Field | Type | Required | Meaning | Example |
| --- | --- | --- | --- | --- |
| `id` | string | Có | ID quân; dùng nguyên giá trị cho `pieceId` | `"P1-ROCK-1"` |
| `ownerId` | string | Có | Bên sở hữu; so với request `player` | `"P1"` |
| `type` | string | Có | `ROCK`, `PAPER`, `SCISSORS` | `"ROCK"` |
| `status` | string | Có | Trạng thái quân; quân sống trên bàn hiện là `ALIVE` | `"ALIVE"` |
| `position` | object | Có | Vị trí server | `{"row":6,"col":0}` |
| `position.row` | integer | Có | Hàng từ 0 đến 8, tăng từ trên xuống | `6` |
| `position.col` | integer | Có | Cột từ 0 đến 8, tăng từ trái sang phải | `0` |

Quân bị loại được gỡ khỏi `board.pieces`; không chờ một phần tử `DEAD` trong mảng. Dùng `ownerId`, `type` và `position` thay vì tách ID quân. Chuỗi có thể chứa ký tự escape; luôn dùng JSON parser đúng chuẩn. Thứ tự mảng `pieces` được sort theo ID, không phải thứ tự nước nên chọn.

Bot thấy cả loại và vị trí quân đối thủ. Không có field lịch sử nước đi, deadline, thời gian còn lại, token hay seed xếp quân trong request. Không xoay tọa độ khi bot là P2.

## ACTION: bot → server

Ví dụ phản hồi hợp lệ cho request trên:

```json
{"type":"ACTION","sequence":70,"action":{"type":"MOVE","pieceId":"P1-ROCK-1","from":{"row":6,"col":0},"to":{"row":5,"col":0}}}
```

| Field | Type | Required | Meaning | Example |
| --- | --- | --- | --- | --- |
| `type` | string | Có | Chính xác `ACTION` | `"ACTION"` |
| `sequence` | integer 64-bit | Có | Bằng sequence của YOUR_TURN vừa nhận | `70` |
| `action` | object | Có | Hành động được chọn | MOVE ở trên |
| `action.type` | string | Có | Decoder hiểu `MOVE`, `PASS`, `SURRENDER`; runner quyết định quyền dùng | `"MOVE"` |
| `action.playerId` | string | Không | Nếu gửi phải khớp bên được server cấp; nên bỏ field này | `"P1"` |

### MOVE

| Field | Type | Required | Meaning | Example |
| --- | --- | --- | --- | --- |
| `pieceId` | string | Có | ID quân của bot sẽ đi | `"P1-ROCK-1"` |
| `from` | object `{row,col}` | Có | Vị trí hiện tại; mỗi tọa độ phải là integer | `{"row":6,"col":0}` |
| `to` | object `{row,col}` | Có | Ô đích; mỗi tọa độ phải là integer | `{"row":5,"col":0}` |

Không có action `ATTACK`: MOVE vào ô đối thủ là tấn công. Engine kiểm tra quyền sở hữu, vị trí gốc, bước di chuyển và ô đích. Một MOVE hợp lệ vẫn có thể thua giao chiến.

### PASS và SURRENDER

Các ví dụ sau mô tả cú pháp decoder, không phải cam kết runner cho phép trả ở mọi lượt:

```json
{"type":"ACTION","sequence":70,"action":{"type":"PASS"}}
```

```json
{"type":"ACTION","sequence":70,"action":{"type":"SURRENDER"}}
```

Trong phòng online dùng bot upload, `pendingBotTurn` lấy các MOVE từ engine; **chỉ khi không có MOVE mới thêm PASS**. Không cung cấp SURRENDER trong danh sách bot; chủ ghế có thể đầu hàng trên giao diện. `completeBot` yêu cầu action thuộc danh sách đã cấp. Vì vậy không tự PASS để tránh nước khó khi vẫn có MOVE.

Một số harness kiểm thử dùng decoder rồi đưa action thẳng vào engine; việc decoder đọc được PASS/SURRENDER không đảm bảo action đó được trận thật chấp nhận. Bot có tính tương thích nên luôn chọn đúng object trong `legalActions`, không dựa vào việc một harness chấp nhận action ngoài danh sách.

## Thời gian và phản hồi lỗi

Thời gian phản hồi thông thường của bot riêng là **2 giây/nước**. Runner Docker của bot upload cho lượt trao đổi đầu tiên tối đa **25 giây** để gồm biên dịch/khởi động; workspace có build và bước khởi động sandbox riêng rồi áp hạn trao đổi. Không dùng mốc 25 giây làm ngân sách suy nghĩ cho mọi lượt. Đồng hồ 30 giây của người chơi tay là cơ chế khác.

Parser từ chối JSON không phải object, message type sai, sequence thiếu/sai kiểu/không khớp, action thiếu, tọa độ không phải integer, `playerId` giả mạo và JSON thừa sau object. Các lỗi vận chuyển còn gồm tiến trình thoát, không có newline/flush, timeout, dòng phản hồi quá dài hoặc log stdout gây dư message.

Trong phòng online, bot lỗi hoặc trả action không được chấp nhận sẽ bị ghi nhận `BOT_FAILED` và xử đầu hàng ván. Lỗi hạ tầng được xử lý riêng ở hệ thống giải; không tự biến mọi lỗi worker thành thắng/thua. Với bài nộp, lỗi build/test trước trận được hiển thị trong kết quả kiểm thử thay vì một kết quả thi đấu chính thức.

## Kiểm chứng ví dụ

[turn.jsonl](examples/turn.jsonl) và [response.jsonl](examples/response.jsonl) là bản một dòng của ví dụ. Kiểm thử `BotDocumentationTest` dựng trạng thái và danh sách nước đi bằng engine, so với `encodeTurn`, parse response bằng decoder thật và áp nước bằng engine. Bot Python trong [Bot Guide](bot-guide.md) đọc request này và chọn MOVE đầu tiên.

Từ thư mục `server`, với JDK phù hợp đã được cấu hình:

```sh
./gradlew :test --tests com.rpsbattle.bot.BotDocumentationTest --console=plain
```

Trên Windows thay `./gradlew` bằng `./gradlew.bat`. Test này không cần database/Docker, không đăng ký worker và không tạo trận giải thật.

## Nguồn đối chiếu

Đối chiếu ngày 23/10/2026:

- [BotTurnRequest](../../server/src/main/java/com/rpsbattle/bot/BotTurnRequest.java), [BotActionResponse](../../server/src/main/java/com/rpsbattle/bot/BotActionResponse.java), [BotProtocol](../../server/src/main/java/com/rpsbattle/bot/BotProtocol.java), [BotAgent](../../server/src/main/java/com/rpsbattle/bot/BotAgent.java).
- [LegalActionGenerator](../../server/src/main/java/com/rpsbattle/game/engine/LegalActionGenerator.java), [OnlineMatch](../../server/src/main/java/com/rpsbattle/online/OnlineMatch.java), [DockerBotProcess](../../server/src/main/java/com/rpsbattle/bot/upload/DockerBotProcess.java).
- [WorkspaceSandbox](../../server/src/main/java/com/rpsbattle/tournament/coding/WorkspaceSandbox.java), [BotTestService](../../server/src/main/java/com/rpsbattle/codevalidation/BotTestService.java), [BotDocumentationTest](../../server/src/test/java/com/rpsbattle/bot/BotDocumentationTest.java).
