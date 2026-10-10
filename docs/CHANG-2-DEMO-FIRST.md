# PROMPT CHO AI AGENT — CHẶNG 2 DEMO FIRST

## 1. Bối cảnh

Project đã hoàn thành Chặng 1.

Đã có:

- Game Core.
- Rule Engine.
- ClassicRuleSet.
- GameEngine.
- LegalActionGenerator.
- Match.
- PlayerAgent.
- ConsoleHumanAgent.
- RandomBotAgent.
- BotAgent.
- GameEvent.
- Replay cơ bản.

Game đã chạy local.

Mục tiêu của Chặng 2 mới:

> Có một bản demo online càng sớm càng tốt.

Bản demo cần chứng minh được:

```text
HTML Client
    ↓
WebSocket
    ↓
Java Server
    ↓
GameEngine
    ↓
RuleEngine
    ↓
GameState mới
    ↓
WebSocket
    ↓
HTML Client
```

Chưa cần database, login, tournament, reconnect phức tạp.

---

# 2. Công nghệ

Backend:

```text
Java 21
Spring Boot
Spring Web
Spring WebSocket
Jackson
Gradle
JUnit 5
```

Client:

```text
HTML
CSS
JavaScript thuần
```

Không dùng React ở chặng này.

Không dùng PostgreSQL ở phần demo đầu.

---

# 3. Nguyên tắc bắt buộc

GameEngine không được biết:

```text
Spring
WebSocket
HTML
HTTP
Database
```

Client không được tự xử lý luật.

Client chỉ:

```text
render GameState
highlight legalActions
gửi Action
```

Server quyết định:

```text
Action hợp lệ hay không
combat
winner
turn
GameState
```

---

# PHẦN 2A — DEMO SỚM

---

# BƯỚC 1 — THÊM SPRING BOOT TỐI THIỂU

Chuyển project thành Spring Boot application.

Thêm dependency:

```text
Spring Boot Starter Web
Spring Boot Starter WebSocket
Jackson
Spring Boot Starter Test
```

Tạo:

```text
RpsBattleApplication
```

Server chạy:

```text
localhost:8080
```

Tạo:

```text
GET /api/health
```

Response:

```json
{
  "status": "UP"
}
```

Không thêm database.

Không thêm Spring Security.

Không sửa GameEngine.

Sau bước này phải chạy lại toàn bộ test Chặng 1.

---

# BƯỚC 2 — TẠO DEMO MATCH CỐ ĐỊNH

Để có demo nhanh, chưa cần Matchmaking.

Tạo một:

```text
DemoMatchService
```

Service này tạo sẵn:

```text
1 Match
2 Player
ClassicRuleSet
GameEngine
```

Match ID:

```text
demo-match
```

Player:

```text
P1
P2
```

Có thể khởi tạo bàn cờ bằng layout cố định.

Ví dụ:

```text
P2 ở phía trên
P1 ở phía dưới
```

Mục tiêu:

```text
Server startup
↓
demo-match sẵn sàng chơi
```

Chưa cần API tạo Match.

---

# BƯỚC 3 — DTO TỐI THIỂU

Tạo DTO tối thiểu:

```text
PositionDto
PieceDto
GameStateDto
ActionDto
GameEventDto
```

Không trả trực tiếp domain object ra WebSocket.

Tạo:

```text
GameStateMapper
```

Mapping:

```text
GameState
↓
GameStateDto
↓
JSON
```

GameStateDto tối thiểu chứa:

```text
matchId
boardWidth
boardHeight
pieces
currentPlayer
turn
sequence
phase
winner
legalActions
```

---

# BƯỚC 4 — WEBSOCKET TỐI THIỂU

Tạo endpoint:

```text
/ws/game
```

Tạo:

```text
GameWebSocketHandler
WebSocketConfig
```

Client connect:

```javascript
const socket =
    new WebSocket("ws://localhost:8080/ws/game");
```

Protocol Chặng 2A chỉ cần:

```text
JOIN
STATE
ACTION
ACTION_REJECTED
GAME_OVER
```

Không cần xây protocol quá lớn ở bước đầu.

---

# BƯỚC 5 — JOIN DEMO MATCH

Client gửi:

```json
{
  "type": "JOIN",
  "matchId": "demo-match",
  "playerId": "P1"
}
```

Client thứ hai:

```json
{
  "type": "JOIN",
  "matchId": "demo-match",
  "playerId": "P2"
}
```

Server lưu:

```text
P1 -> WebSocketSession A
P2 -> WebSocketSession B
```

Khi join thành công:

```json
{
  "type": "STATE",
  "payload": {
    "gameState": {}
  }
}
```

---

# BƯỚC 6 — GỬI ACTION QUA WEBSOCKET

Client gửi:

```json
{
  "type": "ACTION",
  "matchId": "demo-match",
  "playerId": "P1",
  "sequence": 5,
  "payload": {
    "type": "MOVE",
    "pieceId": "P1-R1",
    "from": {
      "row": 6,
      "col": 2
    },
    "to": {
      "row": 5,
      "col": 2
    }
  }
}
```

Server xử lý:

```text
WebSocketHandler
↓
DemoMatchService
↓
GameEngine.applyAction()
↓
GameTransition
↓
GameState mới
```

Sau đó broadcast:

```json
{
  "type": "STATE",
  "payload": {
    "gameState": {}
  }
}
```

cho cả P1 và P2.

Nếu Action sai:

```json
{
  "type": "ACTION_REJECTED",
  "payload": {
    "error": "INVALID_MOVE"
  }
}
```

---

# BƯỚC 7 — CLIENT HTML ĐƠN GIẢN

Tạo:

```text
client/
├── index.html
├── game.html
├── style.css
└── game.js
```

`index.html` chỉ cần:

```text
[ JOIN AS PLAYER 1 ]

[ JOIN AS PLAYER 2 ]
```

`game.html` cần:

```text
Board 9×9
Current Player
Turn
Sequence
Event / Status
```

Board render bằng:

```text
CSS Grid
```

Ví dụ:

```css
.game-board {
    display: grid;
    grid-template-columns: repeat(9, 60px);
    grid-template-rows: repeat(9, 60px);
}
```

Không cần icon đẹp ngay.

Có thể dùng:

```text
R
P
S
```

để đại diện quân.

---

# BƯỚC 8 — CLICK QUÂN VÀ CLICK Ô

Luồng UI:

```text
click quân
↓
selectedPiece
↓
highlight legalActions
↓
click destination
↓
ACTION
↓
WebSocket
```

Client không tự tính legal move.

Server gửi:

```text
legalActions
```

trong GameStateDto.

Ví dụ:

```json
{
  "pieceId": "P1-R1",
  "to": {
    "row": 5,
    "col": 2
  }
}
```

Client chỉ highlight.

---

# BƯỚC 9 — COMBAT DEMO

Tạo một scenario để demo nhanh:

```text
ROCK P1
↓
tấn công
SCISSORS P2
```

Kết quả:

```text
ROCK sống
SCISSORS bị xóa
```

Client phải cập nhật board ngay sau khi nhận STATE mới.

Không cần animation ở bước này.

---

# BƯỚC 10 — GAME OVER

Khi GameEngine trả winner:

Server gửi:

```json
{
  "type": "GAME_OVER",
  "payload": {
    "winner": "P1"
  }
}
```

Client hiển thị:

```text
PLAYER 1 WINS
```

---

# DEFINITION OF DONE — CHẶNG 2A

Chặng 2A hoàn thành khi demo được:

```text
1. Start Java server.

2. Mở 2 browser tab.

3. Tab 1 join P1.

4. Tab 2 join P2.

5. Hai tab cùng thấy board 9×9.

6. P1 click quân.

7. Client highlight legal action.

8. P1 di chuyển.

9. Cả hai tab cập nhật.

10. P2 di chuyển.

11. Có combat.

12. GameEngine quyết định kết quả.

13. Có thể kết thúc Match.
```

Nếu scenario này chạy được thì đã có:

```text
DEMO ONLINE ĐẦU TIÊN
```

Không làm thêm gì trước khi demo chạy ổn.

---

# PHẦN 2B — HOÀN THIỆN SAU DEMO

Sau khi demo 2A chạy được mới tiếp tục.

---

# BƯỚC 11 — CREATE MATCH REST API

Thay DemoMatchService cố định bằng:

```text
MatchRegistry
OnlineMatchService
```

Tạo:

```text
POST /api/v1/matches
```

Request:

```json
{
  "mode": "HUMAN_VS_HUMAN",
  "ruleSet": "classic-v1"
}
```

Response:

```json
{
  "matchId": "M123",
  "status": "WAITING"
}
```

---

# BƯỚC 12 — JOIN MATCH API

Tạo:

```text
POST /api/v1/matches/{id}/join
```

Response:

```json
{
  "playerId": "P1",
  "seat": 1
}
```

Không còn hard-code P1/P2.

---

# BƯỚC 13 — MATCH REGISTRY

Tạo:

```text
MatchRegistry
```

Dùng:

```java
ConcurrentHashMap
```

Quản lý:

```text
matchId
↓
Match
```

API:

```text
register()
findById()
remove()
getActiveMatches()
```

---

# BƯỚC 14 — WEBSOCKET PROTOCOL CHUẨN HÓA

Sau khi demo chạy, mới chuẩn hóa message thành:

```json
{
  "protocolVersion": 1,
  "type": "ACTION",
  "requestId": "REQ1",
  "matchId": "M123",
  "sequence": 20,
  "payload": {}
}
```

Các type:

```text
JOIN_MATCH
GAME_STATE
YOUR_TURN
ACTION
ACTION_ACCEPTED
ACTION_REJECTED
GAME_OVER
PING
PONG
```

---

# BƯỚC 15 — HUMAN VS BOT ONLINE

Cho phép:

```text
Human
vs
RandomBotAgent
```

API:

```json
{
  "mode": "HUMAN_VS_BOT",
  "ruleSet": "classic-v1"
}
```

Khi tới lượt bot:

```text
OnlineMatchService
↓
BotAgent
↓
chooseAction()
↓
GameEngine
```

Client Human nhận GameState mới.

---

# BƯỚC 16 — TIMER SERVER SIDE

Thêm:

```text
TurnClock
```

Server giữ thời gian thật.

Client chỉ hiển thị.

Không để client quyết định timeout.

---

# BƯỚC 17 — RECONNECT CƠ BẢN

Client reconnect:

```json
{
  "type": "RESUME_MATCH",
  "matchId": "M123",
  "playerId": "P1"
}
```

Server gửi full GameState mới nhất.

Chưa cần event delta.

---

# BƯỚC 18 — HISTORY TRONG MEMORY

Trước database, có thể lưu:

```text
List<GameEvent>
```

trong Match.

Tạo:

```text
GET /api/v1/matches/{id}/events
```

để debug/demo replay.

---

# BƯỚC 19 — DATABASE SAU CÙNG

Chỉ thêm database sau khi:

```text
Human vs Human online
+
Human vs Bot online
```

đã ổn.

Thêm:

```text
Spring Data JPA
H2 hoặc PostgreSQL
```

Lưu:

```text
Match metadata
Match result
Match events
```

Không persist GameState theo kiểu JPA entity.

---

# BƯỚC 20 — CLEANUP

Sau khi online flow ổn, review:

```text
GameEngine
```

không import:

```text
Spring
WebSocket
JPA
```

Client không có:

```text
ROCK beats SCISSORS
```

WebSocketHandler không có:

```text
movement rule
combat rule
victory rule
```

Luật vẫn phải là:

```text
GameEngine
↓
RuleSet
```

---

# TIẾN ĐỘ ĐỀ XUẤT

Thay vì:

```text
Backend đầy đủ
↓
Database
↓
WebSocket
↓
Client
↓
Demo
```

hãy làm:

```text
Spring Boot
↓
WebSocket
↓
Demo Match
↓
HTML Board
↓
Human vs Human
↓
DEMO
```

sau đó mới:

```text
Create Match API
↓
Human vs Bot
↓
Reconnect
↓
Persistence
```

---

# MỐC DEMO 1

Phải đạt sớm nhất có thể:

```text
2 browser
↓
1 match
↓
board 9×9
↓
MOVE
↓
COMBAT
↓
GAME OVER
```

Đây là demo quan trọng nhất của Chặng 2.

---

# MỐC DEMO 2

Sau đó bổ sung:

```text
Create Match
Join Match
Human vs Bot
Timer
```

---

# MỐC DEMO 3

Cuối Chặng 2:

```text
Match History
Reconnect
Persistence
```

---

# KẾT QUẢ CUỐI CHẶNG 2

Khi hoàn thành toàn bộ Chặng 2 phải có:

```text
Human vs Human online
Human vs Bot online
HTML client
WebSocket realtime
REST create/join match
Server authoritative
LegalActions từ server
Reconnect cơ bản
Match history
Persistence cơ bản
```

Nhưng bản demo đầu tiên phải xuất hiện từ khoảng:

```text
BƯỚC 1 → BƯỚC 10
```

chứ không đợi hoàn thành cả Chặng 2.