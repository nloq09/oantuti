# RPS Battle — Oẳn Tù Tì Chiến Thuật

Game chiến thuật Oẳn Tù Tì trên bàn cờ 9×9. Dự án Java 21 + Gradle.

## Chạy nhanh

```bash
cd server
./gradlew test        # Chạy unit tests
./gradlew run --console=plain --args=human-vs-human  # Hai người chơi local
```

## Cấu trúc

```
server/
├── build.gradle
├── settings.gradle
└── src/
    ├── main/java/com/rpsbattle/
    │   ├── game/
    │   │   ├── board/     # Position, Board
    │   │   ├── piece/     # PieceType, PieceStatus, Piece
    │   │   ├── player/    # PlayerId, Player
    │   │   ├── action/    # Action, MoveAction, PlaceAction...
    │   │   ├── state/     # GamePhase, GameOutcome, GameResult, GameState
    │   │   └── engine/    # GameEngine, GameTransition, GameEvent
    │   ├── rule/          # Interfaces: MovementRule, CombatRule...
    │   ├── rules/classic/ # ClassicRuleSet implementation
    │   ├── match/         # Match, MatchManager
    │   ├── agent/         # PlayerAgent interface, PlayerView
    │   ├── bot/           # BotAgent, BotProcess, protocol
    │   └── app/           # LocalGameApplication
    └── test/java/com/rpsbattle/
```

## Bước hoàn thành

- [x] Bước 1 — Domain Model (Position, Board, Piece, Player, GameResult)
- [x] Bước 2 — Action & GameState
- [x] Bước 3 — Rule Engine
- [x] Bước 4 — Game Engine
- [x] Bước 5 — Legal Action Generator
- [x] Bước 6 — Match Engine
- [x] Bước 7 — Human vs Human local
- [x] Bước 8 — Bot Protocol
- [ ] Bước 9 — Bot vs Bot
- [ ] Bước 10 — Event Log & Replay
- [ ] Bước 11 — Cleanup & Kiểm tra kiến trúc

## Trạng thái sau Bước 3

`rule/` định nghĩa MovementRule, CombatRule, PlacementRule, VictoryRule,
TurnRule và RuleSet. `rules/classic/` hiện thực luật di chuyển ngang/dọc một ô,
combat RPS, vùng triển khai, thắng do hết quân hoặc giới hạn 200 lượt và đổi lượt.
RuleSet tập hợp các luật; các interface không phụ thuộc implementation Classic.
GameEngine được bổ sung ở Bước 4 bên dưới.

Kiểm tra: 128 tests pass, 0 failures, 0 errors. Chạy bằng JDK 25 hiện có trên máy,
biên dịch với `--release 21`; chưa xác nhận chạy trực tiếp trên JDK 21.
Do Windows khóa thư mục kết quả cũ, lượt kiểm tra dùng thư mục build riêng:

```powershell
# Tạo init script dùng một thư mục mới nếu build cũ đang bị khóa.
Set-Content .verify-step3.gradle "allprojects { layout.buildDirectory.set(file('build-step3-verification-final')) }"
.\gradlew.bat test --no-daemon --console=plain --init-script .verify-step3.gradle
```

## Bước 4 — Game Engine

`GameEngine(RuleSet).applyAction(GameState, PlayerId, Action)` trả về
`GameTransition(newState, events)`. Action sai bị từ chối bằng
`InvalidActionException` với nguyên nhân cụ thể và không sửa trạng thái đầu vào.
GameState sao chép Board cả khi nhận dữ liệu và khi trả qua `board()`;
để cập nhật board, sửa bản sao rồi gọi `withBoard()`.

```text
game/engine/
├── GameEngine.java
├── GameTransition.java
├── GameEvent.java
└── InvalidActionException.java
test/java/com/rpsbattle/game/engine/
└── GameEngineTest.java
```

GameEngine điều phối action; GameTransition giữ snapshot mới và danh sách event
không thể sửa. GameEvent chứa PiecePlaced, PieceMoved, CombatOccurred,
PieceRemoved, TurnChanged và GameFinished. Các event được sắp theo thứ tự xử lý,
cùng sequence của action và chứa payload phục vụ replay sau này.

MovementRule và CombatRule quyết định di chuyển/combat. ClassicTurnRule kiểm tra
phase, lượt và việc action có tính vào giới hạn lượt không. ClassicVictoryRule
xử lý surrender và chỉ kiểm tra hết quân khi PLAYING. Placement tăng sequence
và đổi người nhưng không tăng số lượt thi đấu; Move/Pass tăng lượt trước khi
kiểm tra victory để kết thúc đúng lượt 200. Trận kết thúc giữ người vừa hành động.
Việc thiết lập READY/PLAYING sẽ do tầng điều khiển match ở các bước sau.

Test tích hợp bao gồm cả 7 scenario yêu cầu, action sai, placement/pass/surrender,
giới hạn lượt, cô lập snapshot và một RuleSet khác để xác nhận engine không chứa
luật Classic. Legal Action Generator được bổ sung ở Bước 5 bên dưới.

Kết quả kiểm tra Bước 4: **155 tests pass, 0 failures, 0 errors**, trong đó 27
test tích hợp GameEngine. Chạy trên JDK 25 với `--release 21`; thư mục build
kiểm tra riêng là `build-step3-verification-step4-b` để tránh khóa file kết quả
trong thư mục OneDrive. Chưa kiểm tra runtime trực tiếp trên JDK 21.

## Bước 5 — Legal Action Generator

`new LegalActionGenerator(ruleSet).getLegalActions(state, player)` trả về
`List<Action>` không thể sửa, gồm các MoveAction hợp lệ và nước tấn công.
Generator nằm trong `game/engine/`; test ở
`src/test/java/com/rpsbattle/game/engine/LegalActionGeneratorTest.java`.

Generator xét mỗi ô trong kích thước Board rồi dùng MovementRule và TurnRule
để lọc. Không hard-code bốn hướng Classic, do đó RuleSet khác có thể cho phép
đi chéo hoặc đi xa. Chi phí là O(số quân × số ô), phù hợp bàn 9×9 hiện tại.
Quân được duyệt theo hàng, cột, id; đích theo hàng, cột để kết quả deterministic,
không phụ thuộc thứ tự chèn quân vào Board.

Không có action trùng, đi ra ngoài bàn hay vào quân cùng đội. Quân chết và quân
của đối phương không sinh nước đi. Người không còn nước đi nhận danh sách rỗng;
với Classic, người sai lượt hoặc game ngoài PLAYING cũng nhận danh sách rỗng.
API này sinh nước di chuyển, không sinh Place/Pass/Surrender. GameEngine vẫn
validate cuối cùng khi nhận action. Snapshot không bị thay đổi khi sinh nước đi.

Các test bao gồm giữa bàn, hai góc, đồng đội chặn, tấn công (kể cả trận combat
sẽ thua), hết nước đi, board chữ nhật, thứ tự deterministic và RuleSet khác.
Một test đối chiếu danh sách sinh ra với mọi MoveAction GameEngine chấp nhận.
Match Engine được bổ sung ở Bước 6 bên dưới.

Kết quả Bước 5: **172 tests pass, 0 failures, 0 errors**, gồm 17 test cho
LegalActionGenerator. Test chạy trên JDK 25 với `--release 21`, dùng thư mục
build riêng `build-step3-verification-step5-a`; chưa kiểm tra runtime JDK 21.

## Bước 6 — Match Engine

```text
main/java/com/rpsbattle/
├── match/
│   ├── Match.java
│   └── MatchStatus.java
└── agent/
    ├── PlayerAgent.java
    ├── PlayerView.java
    ├── ConsoleHumanAgent.java
    └── RandomBotAgent.java
```

`Match(initialState, ruleSet, player1, player2)` nhận trạng thái READY hoặc
PLAYING đã triển khai quân, với đúng hai player khác nhau. Hai agent tương ứng
thứ tự `players` trong GameState. `start()` chuyển sang PLAYING;
`playTurn()` gọi agent hiện tại với PlayerView và legalActions, rồi chuyển action
qua GameEngine. `runToCompletion()` tự start và lặp đến FINISHED.
MatchStatus gồm CREATED, RUNNING, FINISHED. Match không chứa luật combat hoặc
di chuyển, không phụ thuộc console và không tự tạo randomness.

Nếu hết nước di chuyển, Match cung cấp PassAction khi TurnRule cho phép; nếu
không được pass thì báo lỗi rõ ràng. Action sai/null hoặc lỗi của agent được
trả về caller và không cập nhật GameState, caller có thể gọi lại `playTurn()`.
Trạng thái đã thắng/hòa ngay từ đầu được kết thúc trước khi hỏi agent.

PlayerView cung cấp snapshot và playerId; agent không thể sửa Board của match.
RandomBotAgent nhận `RandomGenerator` hoặc seed qua constructor, chọn một phần
tử của legalActions và không gọi GameEngine. Cùng seed và cùng thứ tự action
sẽ cho cùng lịch sử trận đấu.

ConsoleHumanAgent nhận Reader/Writer từ bên ngoài, chọn số thứ tự nước đi hoặc
nhập `pass`/`surrender`. Khi hai người dùng chung console, truyền cùng một
BufferedReader cho hai agent. Input sai định dạng được hỏi lại, EOF báo lỗi;
luật vẫn do GameEngine validate. Chưa làm giao diện bàn cờ, nhập tọa độ hay
entry point Human vs Human của Bước 7.

Kết quả Bước 6: **189 tests pass, 0 failures, 0 errors**, gồm 10 Match tests
và 7 PlayerAgent tests. Kiểm tra hai seeded bot cho cùng lịch sử action, kết thúc
trận, đổi lượt, pass khi bị chặn, surrender, action sai không sửa state,
snapshot của agent, Human/Bot cùng Match và input console dùng chung.
Test chạy trên JDK 25 với `--release 21`, thư mục build riêng
`build-step3-verification-step6-b`; chưa kiểm tra runtime trực tiếp trên JDK 21.

## Bước 7 — Human vs Human local

Chạy trong thư mục `server` (cần JDK 21 trở lên):

```powershell
.\gradlew.bat run --console=plain --args=human-vs-human
```

Linux/macOS: `./gradlew run --console=plain --args=human-vs-human`.
Không truyền args cũng chạy Human vs Human. Gradle run chuyển stdin đến ứng dụng.

LocalGameApplication thiết lập hai ConsoleHumanAgent trên cùng Match và dùng
ConsoleBoardRenderer hiển thị bàn 9×9, cột A–I, hàng 1–9; `R1/P1/S1` là quân
Player 1, `R2/P2/S2` là quân Player 2. ClassicGameSetup triển khai cố định mỗi
người 6 ROCK, 6 PAPER, 6 SCISSORS trong vùng hợp lệ, qua PlaceAction và GameEngine.

Nhập `move A7 A6` hoặc `A7 A6` (không phân biệt hoa/thường), số thứ tự action
trong danh sách, `pass` hoặc `surrender`. Player 1 đi trước; ví dụ hai lượt đầu:
`A7 A6`, rồi `A2 A3`. Console chỉ đọc tọa độ, tìm pieceId và tạo MoveAction;
luật di chuyển/combat vẫn do GameEngine kiểm tra. Nước sai báo lỗi và cho nhập
lại mà không mất lượt. EOF dừng ứng dụng với thông báo rõ ràng.

Khi kết thúc, in bàn cuối, kết quả thắng/hòa và số quân còn lại. Phần console
nằm ở `app/` và ConsoleHumanAgent; GameEngine không phụ thuộc console.

Kết quả Bước 7: **195 tests pass, 0 failures, 0 errors**, gồm 6 test ứng dụng:
quota/vùng triển khai, hai dạng tọa độ, retry action sai, lỗi định dạng,
trận hòa sau 200 lượt và EOF. Đã chạy thử Gradle CLI với `human-vs-human`,
stdin `surrender`, nhận `Result: WIN(P2)` và BUILD SUCCESSFUL.
Test chạy trên JDK 25 với `--release 21`, thư mục build riêng
`build-step3-verification-step7-a`; chưa kiểm tra runtime trực tiếp JDK 21.
Bot Protocol Local được bổ sung ở Bước 8 bên dưới.

## Bước 8 — Bot Protocol Local

```text
bot/
├── BotAgent.java
├── BotProcess.java
├── LocalProcessRunner.java
├── BotProtocol.java
├── BotProtocolMessage.java
├── BotTurnRequest.java
├── BotActionResponse.java
└── BotProtocolException.java
```

BotAgent implement PlayerAgent và AutoCloseable, nhận BotProcess và Duration
timeout. LocalProcessRunner nhận danh sách executable/arguments (không chạy
qua shell), khởi động một process dùng lại qua nhiều lượt. Ví dụ cấu hình:

```java
try (BotAgent bot = new BotAgent(
        new LocalProcessRunner(List.of("python", "bot.py")), Duration.ofSeconds(2))) {
    // Truyền bot vào Match như mọi PlayerAgent khác.
}
```

Runner giao tiếp UTF-8 stdin/stdout theo JSON Lines: ghi request rồi flush,
đọc đến newline. Timeout bao gồm ghi và đọc; response tối đa 65.536 ký tự.
EOF giữa dòng, process thoát, lỗi I/O hoặc timeout báo BotProtocolException và
dừng process. Stderr được discard riêng để log không lẫn JSON hoặc chặn stdout.
`close()` dừng process và các descendant hiện có, đóng pipe, dừng executor;
caller sở hữu vòng đời agent và nên dùng try-with-resources. Chưa có sandbox.

Request chứa `type=YOUR_TURN`, matchId, turn, sequence, player (`P1`/`P2`),
state và legalActions. State gồm phase, currentPlayer, players và board
(rows, cols, pieces); mỗi piece có id, ownerId, type, position, status.
legalActions có type, pieceId, from, to cho MOVE hoặc type=PASS khi hết nước đi.
Tọa độ JSON là 0-indexed, khác tọa độ hiển thị console.

Bot phải flush một response trên một dòng, ví dụ:

```json
{"type":"ACTION","sequence":36,"action":{"type":"MOVE","pieceId":"P1-1","from":{"row":6,"col":0},"to":{"row":5,"col":0}}}
```

BotProtocol dùng Jackson, xác nhận type/sequence, trường bắt buộc và số nguyên
tọa độ, hỗ trợ MOVE/PASS/SURRENDER. Server gán playerId từ PlayerView; playerId
khác nếu được gửi trong action sẽ bị reject. JSON lỗi hoặc sequence cũ đóng bot.
Action đúng định dạng vẫn được Match đưa qua GameEngine kiểm tra luật.
Game Core không import Jackson; BotAgent không gọi GameEngine.

BotProcess là interface transport; sau này có thể thay LocalProcessRunner bằng
DockerSandboxRunner mà không đổi BotAgent. Bước 9 CLI Bot vs Bot chưa triển khai.

Dependency: Jackson databind 2.18.6 ([release notes chính thức](https://github.com/FasterXML/jackson/wiki/Jackson-Release-2.18.6)).

Kết quả Bước 8: **220 tests pass, 0 failures, 0 errors**, gồm 25 test protocol/process.
Các test dùng JVM bot riêng để kiểm tra nhiều lượt, stderr lớn, timeout/terminate,
EOF giữa dòng, response quá dài, JSON lỗi, sequence sai và nước đi sai luật được
GameEngine reject. Test chạy trên JDK 25 với `--release 21`, thư mục build riêng
`build-step3-verification-step8-b`; chưa kiểm tra runtime trực tiếp trên JDK 21.
