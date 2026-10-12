# PROMPT CHO AI AGENT — CHẶNG 2B: HOÀN THIỆN CLIENT

## 1. Bối cảnh hiện tại

Project đã hoàn thành Chặng 1 và Chặng 2A.

Hiện tại đã có:

```text
Java 21
Spring Boot
GameEngine
RuleEngine
ClassicRuleSet
Match
LegalActionGenerator
GameEvent
WebSocket
HTML/CSS/JavaScript Client cơ bản
```

Demo hiện tại đã chạy được:

```text
2 browser
↓
join P1 / P2
↓
board 9×9
↓
MOVE
↓
COMBAT
↓
GAME OVER
```

Backend hiện đang giữ Match trong memory.

KHÔNG cần triển khai:

```text
Database
Login
Register
Spring Security
Tournament
Ranking
Upload bot
Distributed Worker
```

Mục tiêu của chặng này là:

> Hoàn thiện client thành một game có giao diện và trải nghiệm đủ tốt để demo thực tế.

---

# 2. Mục tiêu cuối chặng

Sau chặng này người dùng phải có thể:

```text
Mở trang chủ
↓
Chọn chế độ chơi
↓
Tạo / vào trận
↓
Đặt quân
↓
Chơi trên bàn cờ 9×9
↓
Xem animation combat
↓
Xem lượt và timer
↓
Surrender nếu muốn
↓
Kết thúc trận
↓
Xem kết quả
↓
Rematch hoặc về Home
```

Phải hỗ trợ tối thiểu:

```text
Human vs Human

Human vs Bot
```

Client sử dụng:

```text
HTML
CSS
JavaScript thuần
```

Không dùng React trong chặng này.

---

# 3. Kiến trúc client

Tổ chức lại client:

```text
client/
│
├── index.html
├── lobby.html
├── game.html
├── result.html
│
├── css/
│   ├── global.css
│   ├── home.css
│   ├── lobby.css
│   ├── game.css
│   └── animations.css
│
├── js/
│   ├── config.js
│   │
│   ├── api/
│   │   └── match-api.js
│   │
│   ├── socket/
│   │   └── game-socket.js
│   │
│   ├── game/
│   │   ├── game-controller.js
│   │   ├── board-renderer.js
│   │   ├── input-controller.js
│   │   ├── animation-controller.js
│   │   ├── placement-controller.js
│   │   └── game-state.js
│   │
│   ├── ui/
│   │   ├── modal.js
│   │   ├── toast.js
│   │   ├── timer.js
│   │   └── sound.js
│   │
│   └── pages/
│       ├── home.js
│       ├── lobby.js
│       ├── game.js
│       └── result.js
│
└── assets/
    ├── pieces/
    │   ├── rock-blue.svg
    │   ├── rock-red.svg
    │   ├── paper-blue.svg
    │   ├── paper-red.svg
    │   ├── scissors-blue.svg
    │   └── scissors-red.svg
    │
    ├── effects/
    ├── ui/
    └── sounds/
```

---

# 4. Nguyên tắc client

Client không được chứa game rule.

Không được viết:

```javascript
if (rock && scissors) {
    rockWins();
}
```

Client chỉ:

```text
render state
render legalActions
send Action
play animation từ GameEvent
```

Server vẫn là authoritative.

Luồng đúng:

```text
Client click
↓
ACTION
↓
Server
↓
GameEngine
↓
GameEvent + GameState
↓
Client render
```

---

# PHẦN A — HOME SCREEN

# 5. Trang Home

Tạo `index.html`.

Giao diện:

```text
┌───────────────────────────────────┐
│            RPS BATTLE             │
│                                   │
│         OẲN TÙ TÌ 9×9             │
│                                   │
│      [     PLAY GAME     ]        │
│                                   │
│      [   HOW TO PLAY    ]         │
│                                   │
│      [     SETTINGS     ]         │
└───────────────────────────────────┘
```

Không cần account.

Home phải có:

```text
Play Game
How To Play
Settings
```

---

# 6. How To Play

Tạo modal hoặc panel giải thích:

```text
ROCK > SCISSORS
SCISSORS > PAPER
PAPER > ROCK
```

Luật:

```text
- Board 9×9
- Mỗi lượt đi một quân
- Đi 1 ô theo 8 hướng, kể cả chéo
- Đi vào quân địch để combat
- Hết quân thì thua
```

Dùng icon trực quan.

---

# 7. Settings

Settings chỉ cần:

```text
Sound ON/OFF

Music ON/OFF

Animation ON/OFF
```

Lưu bằng:

```javascript
localStorage
```

Không cần server.

---

# PHẦN B — LOBBY

# 8. Lobby Screen

Tạo:

```text
lobby.html
```

Giao diện:

```text
SELECT GAME MODE

[ Human vs Human ]

[ Human vs Bot ]

[ Back ]
```

---

# 9. Human vs Human

Cho hai lựa chọn:

```text
CREATE MATCH
JOIN MATCH
```

Create:

```text
server tạo match
↓
trả matchId
```

Hiển thị:

```text
MATCH CODE

ABC123
```

Có button:

```text
COPY CODE
```

---

# 10. Join Match

Input:

```text
Enter Match Code
[ ______ ]

[ JOIN ]
```

Nếu sai:

```text
Match not found
```

Nếu full:

```text
Match is full
```

---

# 11. Human vs Bot

Không cần matchmaking.

Khi chọn:

```text
Human vs Bot
```

server tạo:

```text
HumanAgent
vs
RandomBotAgent
```

Có thể cho chọn bot level:

```text
Easy
Normal
```

Ở chặng này:

```text
Easy = RandomBot
Normal = GreedyBot nếu đã có
```

Nếu chưa có GreedyBot thì chỉ để Easy.

---

# PHẦN C — MATCH WAITING ROOM

# 12. Waiting Room

Human vs Human cần màn hình:

```text
MATCH ABC123

Player 1
READY

Player 2
WAITING...

[ COPY MATCH CODE ]

[ LEAVE ]
```

Khi Player 2 join:

```text
Player 2
CONNECTED
```

Sau đó:

```text
START GAME
```

hoặc server tự start.

---

# PHẦN D — PLACEMENT

# 13. Placement Phase

Trước PLAYING phải có phase:

```text
PLACEMENT
```

Giao diện:

```text
PLACE YOUR PIECES

ROCK      6
PAPER     6
SCISSORS  6

        BOARD 9×9

[ RANDOMIZE ]

[ RESET ]

[ READY ]
```

---

# 14. Placement Interaction

Người chơi:

```text
click ROCK
↓
click ô hợp lệ
↓
piece được đặt
```

Hoặc:

```text
click piece đã đặt
↓
click ô khác
```

để reposition.

Không cần drag & drop.

---

# 15. Placement Zone

Player 1:

```text
row 6
row 7
row 8
```

Player 2:

```text
row 0
row 1
row 2
```

Server vẫn phải validate.

Client chỉ highlight placement zone.

---

# 16. Random Placement

Button:

```text
RANDOMIZE
```

Client có thể tạo layout ngẫu nhiên hợp lệ rồi gửi placement action từng quân.

Hoặc nếu backend hỗ trợ:

```text
RANDOM_PLACEMENT
```

thì server thực hiện.

Ưu tiên đơn giản nhất.

---

# 17. Ready

Khi đủ 18 quân:

```text
READY
```

mới enable.

Khi cả hai Ready:

```text
PLAYING
```

---

# PHẦN E — GAME SCREEN

# 18. Layout chính

Game screen bố cục:

```text
┌─────────────────────────────────────────────────────────────┐
│ RPS BATTLE          MATCH ABC123          TURN 12           │
├──────────────┬────────────────────────────┬─────────────────┤
│              │                            │                 │
│ Player 2     │                            │ Match Info      │
│              │                            │                 │
│ Rock: 5      │        BOARD 9×9           │ Turn: P1        │
│ Paper: 4     │                            │ Time: 00:05     │
│ Scissors: 6  │                            │                 │
│              │                            │ Event Log       │
│──────────────│                            │                 │
│              │                            │                 │
│ Player 1     │                            │                 │
│              │                            │                 │
│ Rock: 6      │                            │                 │
│ Paper: 5     │                            │                 │
│ Scissors: 4  │                            │                 │
└──────────────┴────────────────────────────┴─────────────────┘
```

---

# 19. Board

Board dùng:

```css
display: grid;
grid-template-columns: repeat(9, 1fr);
```

Mỗi cell:

```text
60×60 hoặc responsive
```

Phải có:

```text
A B C D E F G H I
1 2 3 4 5 6 7 8 9
```

---

# 20. Board Zones

Top 3 rows:

```text
Player 2 zone
red accent
```

Middle 3:

```text
neutral
```

Bottom 3:

```text
Player 1 zone
blue accent
```

Không dùng màu quá đậm làm khó nhìn quân.

---

# 21. Piece Rendering

Dùng SVG/icon.

Piece phải phân biệt:

```text
ROCK
PAPER
SCISSORS
```

và:

```text
Player 1
Player 2
```

Ví dụ:

```text
Blue border = P1
Red border = P2
```

---

# 22. Selected Piece

Khi click quân:

```text
selected
```

hiển thị:

```text
blue glowing ring
```

hoặc:

```text
pulse animation
```

---

# 23. Legal Action Highlight

Server gửi:

```text
legalActions
```

Client lọc theo selected piece.

Ô đi hợp lệ:

```text
green highlight
```

Ô có quân địch có thể attack:

```text
red target highlight
```

Không tự tính legalActions.

---

# 24. Hover

Hover cell hợp lệ:

```text
slight scale
glow
cursor pointer
```

Cell không hợp lệ:

```text
default cursor
```

---

# 25. Move Animation

Khi nhận:

```text
PIECE_MOVED
```

animation:

```text
piece
↓
translate
↓
destination
```

Duration:

```text
200–300 ms
```

Không quá lâu.

---

# 26. Combat Animation

Khi nhận:

```text
COMBAT_OCCURRED
```

animation tùy matchup.

## ROCK vs SCISSORS

```text
rock impact
↓
scissors break
```

## PAPER vs ROCK

```text
paper wrap
↓
rock covered
```

## SCISSORS vs PAPER

```text
slash
↓
paper cut
```

Same type:

```text
impact
↓
both disappear
```

Không tính winner ở client.

---

# 27. Piece Removed Animation

Khi nhận:

```text
PIECE_REMOVED
```

animation:

```text
fade
+
scale down
```

Khoảng:

```text
150–250 ms
```

---

# 28. Animation Queue

Không chạy event animation chồng nhau.

Tạo:

```javascript
AnimationController
```

Có queue:

```text
PIECE_MOVED
↓
COMBAT
↓
REMOVE
↓
STATE UPDATE
```

Mỗi animation hoàn thành mới chạy animation tiếp.

---

# 29. Event Log

Panel bên phải:

```text
Turn 12

P1:
C7 → C6

Combat:
ROCK beats SCISSORS

P2 turn
```

Giới hạn:

```text
10–20 event gần nhất
```

Không để panel tăng vô hạn.

---

# PHẦN F — TURN SYSTEM

# 30. Current Turn

Hiển thị rõ:

```text
YOUR TURN
```

hoặc:

```text
OPPONENT TURN
```

Khi không phải lượt:

```text
disable board interaction
```

---

# 31. Timer

Hiển thị:

```text
00:05
```

Client timer chỉ để render.

Server gửi deadline hoặc remaining time.

Client không quyết định timeout.

---

# 32. Timer Warning

Khi còn:

```text
<= 3 seconds
```

timer pulse.

Không sử dụng màu sắc gây khó đọc.

---

# PHẦN G — CONTROLS

# 33. Surrender

Button:

```text
SURRENDER
```

Khi click:

```text
Are you sure?
```

Nếu confirm:

```text
SURRENDER action
```

server xử lý.

---

# 34. Leave Match

Nếu đang PLAYING:

```text
Leave = surrender
```

Nếu WAITING:

```text
Leave = leave room
```

---

# 35. Sound

Thêm các sound effect:

```text
select
move
combat
victory
defeat
```

Sound có thể OFF trong Settings.

---

# PHẦN H — RESULT SCREEN

# 36. Result Modal / Page

Khi GAME_OVER:

```text
VICTORY
```

hoặc:

```text
DEFEAT
```

hoặc:

```text
DRAW
```

Hiển thị:

```text
Winner
Total turns
Remaining pieces
Match duration
```

---

# 37. Result Actions

Buttons:

```text
REMATCH

PLAY AGAIN

HOME
```

---

# 38. Rematch

Human vs Bot:

```text
REMATCH
```

tạo match mới ngay.

Human vs Human:

```text
P1 requests rematch
↓
P2 accepts
↓
new Match
```

Nếu backend chưa hỗ trợ rematch protocol:

Có thể đơn giản:

```text
PLAY AGAIN
↓
return lobby
```

ưu tiên demo chạy ổn trước.

---

# PHẦN I — HUMAN VS BOT

# 39. Bot Turn

Khi tới lượt Bot:

```text
BOT THINKING...
```

Hiển thị loading nhỏ.

Không khóa toàn bộ UI.

---

# 40. Delay bot animation

Nếu bot trả action ngay lập tức:

Không render ngay lập tức.

Có thể client giữ tối thiểu:

```text
300–500 ms
```

trước animation để người xem hiểu diễn biến.

Không làm chậm logic server.

---

# PHẦN J — CONNECTION

# 41. Connection Status

Hiển thị nhỏ:

```text
● Connected
```

Nếu mất WebSocket:

```text
○ Disconnected
```

và modal:

```text
Connection lost.

Reconnecting...
```

---

# 42. Reconnect đơn giản

Client tự retry:

```text
1s
2s
3s
5s
```

không retry quá nhanh.

Sau reconnect:

```text
JOIN / RESUME
↓
server gửi full STATE
```

---

# 43. Không cho thao tác khi disconnected

Trong trạng thái:

```text
DISCONNECTED
```

board phải disable.

---

# PHẦN K — CLIENT STATE

# 44. GameState phía client

Tạo object:

```javascript
const gameState = {
    matchId: null,
    playerId: null,
    phase: null,
    currentPlayer: null,
    turn: 0,
    sequence: 0,

    pieces: [],
    legalActions: [],

    selectedPieceId: null,

    connected: false,
    animationRunning: false
};
```

---

# 45. Không lưu DOM làm state

Không làm kiểu:

```javascript
if (cell.classList.contains("rock")) ...
```

để xác định game logic.

DOM chỉ để render.

State thật lấy từ:

```text
server
```

---

# 46. Full Render

Sau mỗi STATE từ server:

```text
update client state
↓
render board
↓
render player panels
↓
render turn
↓
render legal actions
```

---

# PHẦN L — ERROR HANDLING

# 47. Client Error

Hiển thị toast cho:

```text
INVALID_MOVE

NOT_YOUR_TURN

INVALID_SEQUENCE

MATCH_NOT_FOUND

MATCH_FULL
```

Ví dụ:

```text
Invalid move
```

Không hiện raw exception.

---

# 48. Sequence mismatch

Nếu server trả:

```text
INVALID_SEQUENCE
```

client:

```text
request latest state
```

hoặc server gửi STATE mới.

Không thử gửi lại action cũ tự động.

---

# PHẦN M — RESPONSIVE

# 49. Desktop

Ưu tiên:

```text
1366×768
1920×1080
```

Board phải luôn nhìn đủ.

---

# 50. Tablet

Nếu width nhỏ:

```text
Player panels
↓
collapse
```

Event log có thể nằm dưới board.

---

# 51. Mobile

Không cần tối ưu hoàn hảo.

Chỉ đảm bảo:

```text
không vỡ layout
board scroll/scale được
```

---

# PHẦN N — POLISH UI

# 52. Style

Phong cách:

```text
cartoon strategy
clean
blue vs red
fantasy board
```

Không dùng quá nhiều gradient.

---

# 53. Color System

Định nghĩa CSS variables:

```css
:root {
    --player-1: #2f80ed;
    --player-2: #e74c3c;

    --move-cell: #27ae60;
    --attack-cell: #eb5757;

    --background: #f4f4f4;
    --surface: #ffffff;
    --text: #222222;
}
```

Sau này dễ đổi theme.

---

# 54. Asset naming

Assets phải đặt tên rõ:

```text
rock-blue.svg
rock-red.svg

paper-blue.svg
paper-red.svg

scissors-blue.svg
scissors-red.svg
```

Không dùng:

```text
image1.png
image2.png
```

---

# PHẦN O — BACKEND TỐI THIỂU CẦN BỔ SUNG

Không làm database.

Chỉ bổ sung backend nếu client cần.

Tạo:

```text
MatchRegistry
```

in-memory:

```java
ConcurrentHashMap<String, Match>
```

---

# 55. Create Match

API:

```text
POST /api/matches
```

Request:

```json
{
  "mode": "HUMAN_VS_HUMAN"
}
```

hoặc:

```json
{
  "mode": "HUMAN_VS_BOT"
}
```

Response:

```json
{
  "matchId": "ABC123"
}
```

---

# 56. Join Match

API:

```text
POST /api/matches/{id}/join
```

Response:

```json
{
  "playerId": "P2"
}
```

---

# 57. Match Info

API:

```text
GET /api/matches/{id}
```

Response:

```json
{
  "matchId": "ABC123",
  "mode": "HUMAN_VS_HUMAN",
  "status": "WAITING",
  "players": 1
}
```

---

# 58. WebSocket messages

Chặng này dùng:

```text
JOIN_MATCH
GAME_STATE
YOUR_TURN
ACTION
ACTION_ACCEPTED
ACTION_REJECTED
GAME_EVENT
GAME_OVER
RESUME_MATCH
PING
PONG
```

---

# PHẦN P — TEST

# 59. Manual Test 1

Human vs Human:

```text
Browser A
vs
Browser B
```

Test:

```text
create
join
placement
play
combat
game over
```

---

# 60. Manual Test 2

Human vs Bot:

```text
Human
vs
RandomBot
```

Test:

```text
human move
bot move
combat
game over
```

---

# 61. Manual Test 3

Disconnect:

```text
disconnect P1
↓
P2 vẫn còn match
↓
P1 reconnect
↓
state khôi phục
```

---

# 62. Manual Test 4

Invalid Action:

Cố gửi bằng console:

```text
move đối phương
move sai lượt
move ngoài board
```

Server phải reject.

Client không crash.

---

# 63. UI Test

Kiểm tra:

```text
selected piece
legal cell
attack cell
timer
event log
victory modal
defeat modal
```

---

# DEFINITION OF DONE — CHẶNG 2B

Chặng này hoàn thành khi demo được luồng:

```text
HOME
↓
PLAY
↓
HUMAN VS HUMAN
↓
CREATE MATCH
↓
SECOND PLAYER JOIN
↓
PLACEMENT
↓
READY
↓
GAME
↓
MOVE
↓
COMBAT
↓
GAME OVER
↓
RESULT
↓
HOME
```

và:

```text
HOME
↓
PLAY
↓
HUMAN VS BOT
↓
PLACEMENT
↓
GAME
↓
BOT TURN
↓
COMBAT
↓
RESULT
```

---

# UI HOÀN THIỆN PHẢI CÓ

Bắt buộc:

```text
Home

Lobby

Waiting Room

Placement

Game Board 9×9

Piece icons

Selected highlight

Legal move highlight

Attack highlight

Player panels

Turn indicator

Timer

Event log

Combat animation

Game over modal

Human vs Bot

Surrender

Connection status
```

Nên có:

```text
How To Play

Settings

Sounds

Random Placement

Rematch
```

---

# KHÔNG LÀM TRONG CHẶNG NÀY

Không triển khai:

```text
Database

Login

Account

Ranking

Tournament

Bot Upload

Admin

Redis

Docker Sandbox

Distributed Server
```

Mục tiêu là:

> Có một game online hoàn chỉnh về trải nghiệm trước khi mở rộng nền tảng.

---

# KIẾN TRÚC CUỐI CHẶNG

```text
                HTML CLIENT
                    │
              JavaScript UI
                    │
           REST + WebSocket
                    │
                    ▼
              Spring Boot
                    │
             MatchRegistry
                    │
             Online Match
                    │
                    ▼
               GameEngine
                    │
                    ▼
                RuleSet
```

GameEngine vẫn không biết:

```text
HTML
CSS
JavaScript
Spring
WebSocket
```

Client vẫn không biết:

```text
ROCK thắng SCISSORS
PAPER thắng ROCK
SCISSORS thắng PAPER
```

Client chỉ biết:

```text
piece
position
legalAction
GameEvent
GameState
```

---

# THỨ TỰ AI AGENT NÊN LÀM

Không yêu cầu agent làm toàn bộ cùng lúc.

Thực hiện theo thứ tự:

```text
1. Refactor client folder.

2. Home.

3. Lobby.

4. Create / Join Match.

5. Waiting Room.

6. Placement.

7. Board UI.

8. Piece rendering.

9. Selection + legal actions.

10. Player panels.

11. Turn + timer.

12. Combat animation.

13. Event log.

14. Game over.

15. Human vs Bot.

16. Surrender.

17. Connection status.

18. Reconnect.

19. Responsive.

20. Polish.
```

Sau mỗi bước:

```text
chạy server
mở browser
test chức năng mới
đảm bảo chức năng cũ không hỏng
```

Không refactor GameEngine trừ khi có lỗi kiến trúc thực sự.