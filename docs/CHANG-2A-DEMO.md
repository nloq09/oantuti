# Chặng 2A — Demo hai người chơi online

Bước 1–10 hoàn thành. Một match cố định `demo-match`, P1 đi trước, P2 ở phía
trên. Board 9×9 có bốn quân để demo combat nhanh: ROCK P1 tại C7, PAPER P1
tại G8, SCISSORS P2 tại C3 và ROCK P2 tại G2. Đây là snapshot pre-deployed
cho demo, không phải màn triển khai đủ 18 quân/player của CLI local.

## Chạy

```powershell
cd server
# Trên máy hiện tại chưa có JDK 21; dùng JDK 24 để chạy Gradle 8.14.3.
$env:JAVA_HOME = 'C:\Users\hi\.jdks\openjdk-24.0.2+12-54'
.\gradlew.bat bootRun --console=plain
```

Nếu có JDK 21, đặt JAVA_HOME tới JDK 21. Mọi source được biên dịch với
`--release 21`. Server và client cùng ở `http://localhost:8080`; không cần
Live Server, Node, React hoặc CORS wildcard. `processResources` đóng gói thư
mục `client/` vào `static/` trong server jar. Có thể build bằng `bootJar` rồi
chạy executable jar trong `libs/` của thư mục build. In thư mục bằng
`.\gradlew.bat -q printBuildDir`; trên Windows build được lưu ngoài repo tại
`GRADLE_USER_HOME/rps-battle-builds/<checkout-key>` để tránh OneDrive khóa class.
Linux/macOS vẫn dùng `build/libs/rps-battle-1.0-SNAPSHOT.jar`.

Mở hai tab `http://localhost:8080/demo/`. Tab A chọn JOIN AS PLAYER 1,
tab B chọn JOIN AS PLAYER 2. Trang `/` hiện là client Chặng 2B.
Mỗi ghế chỉ có một session đang mở. Action cần cả hai người đã JOIN; server
trả WAITING_FOR_PLAYER nếu đối thủ chưa kết nối. Ghế được nhường khi đóng tab.
Match ở memory không reset khi reload; khởi động lại server để có trận mới.

## Demo combat và game over

Lần lượt click quân nguồn rồi ô đích được highlight:

1. P1: C7 → C6.
2. P2: C3 → C4.
3. P1: C6 → C5.
4. P2: G2 → G3.
5. P1: C5 → C4. Engine xử lý ROCK tấn công SCISSORS; SCISSORS bị xóa.

Hai tab cùng nhận STATE sequence 5 và thấy ROCK P1 tại C4. Event log có
COMBAT_OCCURRED, PIECE_REMOVED, PIECE_MOVED. Có thể chọn Surrender ở lượt P2
để kết thúc ngay, hoặc kiểm tra thắng do loại hết quân:

6. P2 Pass, P1 G8 → G7.
7. P2 Pass, P1 G7 → G6.
8. P2 Pass, P1 G6 → G5.
9. P2 Pass, P1 G5 → G4.
10. P2 Pass, P1 G4 → G3. Engine xử lý PAPER tấn công ROCK cuối cùng của P2.

Sequence cuối 15; cả hai tab nhận STATE FINISHED rồi GAME_OVER, hiển thị
PLAYER 1 WINS; board và các nút action bị disable. Hòa được hiển thị DRAW.

## Luồng và protocol

```text
client/game.js -> WebSocket /ws/game -> GameWebSocketHandler
 -> DemoMatchService -> Match.playTurn -> GameEngine -> ClassicRuleSet
 -> GameTransition -> GameStateMapper -> STATE (+ GAME_OVER)
```

SubmittedActionAgent chỉ đưa action nhận được vào Match hiện có. Không sửa
GameEngine hay RuleSet. Service không tính movement/combat/winner; handler
chỉ parse message, kiểm tra ghế, gọi service và gửi DTO. GameStateMapper tạo
DTO thuần JSON cho piece/action/event; domain không có Spring/Jackson annotation.

Service serialize các action của một match; handler serialize xử lý/broadcast
và gửi WebSocket để giữ đúng thứ tự sequence. Client chỉ dùng legalActions
do LegalActionGenerator/TurnRule cung cấp, không tự tính nước đi hoặc combat.
Chỉ player đang đến lượt nhận legalActions; người chờ nhận danh sách rỗng.

JOIN:

```json
{"type":"JOIN","matchId":"demo-match","playerId":"P1"}
```

ACTION:

```json
{"type":"ACTION","matchId":"demo-match","playerId":"P1","sequence":0,"payload":{"type":"MOVE","pieceId":"P1-R1","from":{"row":6,"col":2},"to":{"row":5,"col":2}}}
```

Các command hỗ trợ: MOVE, PASS, SURRENDER. Server kiểm tra playerId trùng với
ghế của session; không cho session P1 thực hiện action dưới tên P2. Sequence
cũ bị reject và gửi lại full STATE mới nhất. Action sai không tăng sequence,
không đổi board. JSON hỏng, trailing tokens, unknown fields và số nguyên dạng
thập phân bị từ chối. Không gửi stack trace hoặc class exception cho client.

Message server: STATE với `payload.gameState`, ACTION_REJECTED với
`payload.error`, GAME_OVER với winner/outcome/sequence. STATE có matchId,
boardWidth/boardHeight, pieces, currentPlayer, turn, sequence, phase,
winner/outcome, legalActions và events của transition mới nhất.

Các lỗi demo: INVALID_MESSAGE, UNSUPPORTED_MESSAGE, MATCH_NOT_FOUND,
PLAYER_NOT_FOUND, SEAT_TAKEN, ALREADY_JOINED, JOIN_REQUIRED, PLAYER_MISMATCH,
WAITING_FOR_PLAYER, NOT_YOUR_TURN, INVALID_MOVE, INVALID_ACTION,
INVALID_SEQUENCE, MATCH_FINISHED.

## File chính

```text
client/
  index.html, game.html, style.css, game.js
server/src/main/java/com/rpsbattle/
  online/DemoMatchService.java, DemoRequestException.java
  api/dto/PositionDto.java, PieceDto.java, ActionDto.java,
          GameStateDto.java, GameEventDto.java
  api/mapper/GameStateMapper.java
  websocket/config/WebSocketConfig.java
  websocket/handler/GameWebSocketHandler.java
  websocket/message/ClientMessage.java, ServerMessage.java
server/src/test/java/com/rpsbattle/
  online/DemoMatchServiceTest.java
  api/mapper/GameStateMapperTest.java
  websocket/DemoWebSocketIntegrationTest.java
```

## Kiểm tra tổng hợp cuối 2A

**260 tests PASS, 0 failures, 0 errors, 0 skipped.** Chạy toàn bộ một lần cuối
2A, gồm 249 test Chặng 1, 2 health cases, 4 service tests, 2 mapping tests và
3 integration tests dùng JDK HttpClient/WebSocket với embedded Tomcat thật.

- Hai client join, luân phiên MOVE, broadcast board giống nhau, combat,
  surrender, STATE FINISHED + GAME_OVER, reject action sau kết thúc.
- Invalid JSON/null/trailing tokens, identity giả, sai lượt, trùng ghế, match/
  player không tồn tại, action sai luật, sequence cũ/trùng và sequence thập phân.
- Client HTML/JS/CSS trả HTTP 200; ngắt kết nối nhường ghế; người mới nhận snapshot.
- Service race cùng sequence chỉ chấp nhận một action; snapshot không đổi khi
  reject; engine giải quyết combat và thắng do hết quân.
- DTO mapping/JSON và danh sách immutable; kiểm tra import core/rule không
  phụ thuộc Spring/WebSocket/online/API/persistence.

Build `bootJar` thành công. Chạy executable jar trên cổng 8080 và mở hai browser
tab thực tế: click/select/highlight, 5 lượt combat, rồi 10 lượt pass/move đến
thắng do hết quân ở sequence 15; cả hai tab hiển thị PLAYER 1 WINS.

Môi trường: JDK 24.0.2 với `--release 21`; chưa xác nhận runtime trực tiếp
JDK 21. Thư mục build riêng tránh Windows/OneDrive khóa kết quả cũ:
`build-step3-verification-stage2a-final-a` (ignored). Checkout mới dùng bình
thường `.\gradlew.bat test bootJar`.

## Phạm vi tiếp theo

Demo dùng ghế public P1/P2, không có authentication, database hoặc reset API.
Chỉ dùng để trình diễn local. Create/join Match REST API, bot online, timer,
reconnect protocol, history endpoint và persistence thuộc 2B. Chưa triển khai 2B.
