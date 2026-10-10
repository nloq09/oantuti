# RPS Battle — Oẳn Tù Tì Chiến Thuật

Game chiến thuật Oẳn Tù Tì trên bàn cờ 9×9. Dự án Java 21 + Gradle.

Chặng 1 đã hoàn thành. Chặng 2 dùng lộ trình **Demo First**; đã hoàn thành
**Bước 1 — Spring Boot tối thiểu**, bước tiếp theo là **DemoMatchService cố định**.
Yêu cầu đầy đủ mới nằm trong [Chặng 2 Demo First](docs/CHANG-2-DEMO-FIRST.md),
thay thế thứ tự triển khai Chặng 2 trước đây.

## Server online — Chặng 2

Yêu cầu JDK 21 (hoặc JDK 24 nếu dùng môi trường kiểm tra hiện tại); Gradle
wrapper 8.14.3 và Spring Boot 3.5.16. Version Jackson/JUnit/AssertJ do
[Boot BOM](https://docs.spring.io/spring-boot/3.5/gradle-plugin/managing-dependencies.html)
quản lý. Wrapper chuyển từ 9.1.0 sang 8.14.3 theo
[phạm vi Gradle được Boot 3.5 hỗ trợ](https://docs.spring.io/spring-boot/3.5/system-requirements.html).
Không dùng JDK 25 để chạy wrapper 8.x; cấu hình biên dịch vẫn `--release 21`.

```powershell
cd server
# Đặt JAVA_HOME tới JDK phù hợp nếu PATH đang trỏ tới Java cũ.
.\gradlew.bat bootRun --console=plain
```

Server mặc định ở `http://localhost:8080`. Kiểm tra từ terminal khác:

```powershell
Invoke-RestMethod http://localhost:8080/api/health
```

`GET /api/health` trả HTTP 200, JSON `{"status":"UP"}` và không yêu cầu
đăng nhập. Chưa thêm Spring Security, JPA, Match API hay WebSocket endpoint.
Các starter Web, WebSocket, Test và Jackson đã được khai báo. Bỏ starter
Validation chưa dùng để giữ phần demo tối thiểu. `/api/v1/health` vẫn là alias
tương thích cho endpoint đã push trước khi đổi lộ trình.
GameEngine/RuleSet giữ nguyên. `application.yml` đặt port 8080; có thể override
bằng `--args="--server.port=8081"` khi chạy bootRun.

```powershell
.\gradlew.bat test
.\gradlew.bat bootJar
# Chạy executable jar của server online:
java -jar build/libs/rps-battle-1.0-SNAPSHOT.jar
```

`bootRun`/`bootJar` dùng RpsBattleApplication; `run` vẫn dùng LocalGameApplication
cho human/bot/replay local của Chặng 1. Đừng dùng plain jar để chạy server online.

Cây thư mục sau Bước 1 (phần Chặng 1 giữ nguyên, xem cây đầy đủ trong
[báo cáo Chặng 1](docs/CHANG-1-REVIEW.md)):

```text
server/
├── build.gradle
├── settings.gradle
├── gradlew, gradlew.bat
├── gradle/wrapper/
│   ├── gradle-wrapper.jar
│   └── gradle-wrapper.properties
└── src/
    ├── main/
    │   ├── java/com/rpsbattle/
    │   │   ├── RpsBattleApplication.java
    │   │   ├── api/controller/HealthController.java
    │   │   ├── game/{board,piece,player,action,state,engine}/
    │   │   ├── rule/
    │   │   ├── rules/classic/
    │   │   ├── agent/
    │   │   ├── match/
    │   │   ├── bot/
    │   │   ├── replay/
    │   │   └── app/
    │   └── resources/application.yml
    └── test/java/com/rpsbattle/
        ├── api/controller/HealthControllerTest.java
        ├── architecture/
        └── (các test Chặng 1 giữ nguyên)
```

### Tiến độ Chặng 2 — Demo First

- [x] 2A / Bước 1 — Spring Boot tối thiểu, `/api/health`, regression Chặng 1
- [ ] 2A / Bước 2 — DemoMatchService: `demo-match`, P1/P2, layout cố định
- [ ] 2A / Bước 3 — DTO tối thiểu và GameStateMapper
- [ ] 2A / Bước 4 — WebSocket `/ws/game`, protocol tối thiểu
- [ ] 2A / Bước 5 — JOIN P1/P2 và gửi STATE
- [ ] 2A / Bước 6 — ACTION qua engine, broadcast STATE/reject
- [ ] 2A / Bước 7 — Client HTML/CSS/JS thuần, board 9×9
- [ ] 2A / Bước 8 — Click quân/ô, highlight legalActions từ server
- [ ] 2A / Bước 9 — Combat demo
- [ ] 2A / Bước 10 — GAME_OVER; xác nhận demo hai browser tab

**Mốc bắt buộc trước 2B:** hai tab join P1/P2, cùng thấy board, MOVE được đồng
bộ, combat và kết quả do GameEngine quyết định. Client chỉ render, highlight
legalActions và gửi Action. GameEngine/RuleSet không biết HTTP/WebSocket/Spring.

- [ ] 2B / Bước 11 — Create Match REST API, OnlineMatchService
- [ ] 2B / Bước 12 — Join Match REST API
- [ ] 2B / Bước 13 — MatchRegistry thread-safe
- [ ] 2B / Bước 14 — Chuẩn hóa WebSocket protocol/version/requestId/sequence
- [ ] 2B / Bước 15 — Human vs Bot online
- [ ] 2B / Bước 16 — Timer server side
- [ ] 2B / Bước 17 — Reconnect full snapshot
- [ ] 2B / Bước 18 — History trong memory
- [ ] 2B / Bước 19 — Database metadata/result/events sau khi online ổn
- [ ] 2B / Bước 20 — Cleanup và kiểm tra ranh giới kiến trúc

Phần demo đầu chưa cần matchmaking, database, login hay reconnect phức tạp.
Chưa triển khai Bước 2 hoặc các bước sau trong lần điều chỉnh lộ trình này.

Kết quả Bước 1 trước khi đổi lộ trình: **250 tests pass, 0 failures, 0 errors, 0 skipped**,
gồm toàn bộ 249 tests Chặng 1 và 1 integration test health qua HTTP với full Spring context
và embedded Tomcat trên port ngẫu nhiên. Đã chạy bootRun trên cổng 8080,
gọi GET health thực tế nhận HTTP 200, `Content-Type: application/json`,
`{"status":"UP"}`. bootJar build thành công, manifest Start-Class là
`com.rpsbattle.RpsBattleApplication`. Task run vẫn dùng CLI Chặng 1.

Lần kiểm tra dùng JDK 24.0.2 với `--release 21` và thư mục build riêng
`build-step3-verification-stage2-step1-a` do thư mục kết quả cũ bị Windows khóa.
Chưa xác nhận runtime trực tiếp JDK 21. Server smoke test đã được dừng sau kiểm tra.

Sau điều chỉnh Demo First: **251 tests pass, 0 failures, 0 errors, 0 skipped**
(249 tests Chặng 1 và 2 HTTP health cases cho đường dẫn mới/alias cũ).
`test bootJar` thành công trên JDK 24.0.2 với `--release 21`, build riêng
`build-step3-verification-stage2-demo-first-step1-a`. Nội dung GameEngine/RuleSet
không thay đổi.

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
    │   ├── match/         # Match, MatchStatus
    │   ├── agent/         # PlayerAgent interface, PlayerView
    │   ├── bot/           # BotAgent, BotProcess, protocol
    │   ├── replay/        # MatchLog, ReplayService, ReplayJson
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
- [x] Bước 9 — Bot vs Bot
- [x] Bước 10 — Event Log & Replay
- [x] Bước 11 — Cleanup & Kiểm tra kiến trúc

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
DockerSandboxRunner mà không đổi BotAgent. CLI Bot vs Bot được bổ sung ở Bước 9.

Dependency: Jackson databind 2.18.6 ([release notes chính thức](https://github.com/FasterXML/jackson/wiki/Jackson-Release-2.18.6)).

Kết quả Bước 8: **220 tests pass, 0 failures, 0 errors**, gồm 25 test protocol/process.
Các test dùng JVM bot riêng để kiểm tra nhiều lượt, stderr lớn, timeout/terminate,
EOF giữa dòng, response quá dài, JSON lỗi, sequence sai và nước đi sai luật được
GameEngine reject. Test chạy trên JDK 25 với `--release 21`, thư mục build riêng
`build-step3-verification-step8-b`; chưa kiểm tra runtime trực tiếp trên JDK 21.

## Bước 9 — Bot vs Bot local

Trong thư mục `server`, chạy hai bot mẫu ở hai JVM riêng:

```powershell
.\gradlew.bat run --console=plain --args="bot-vs-bot demo:1 demo:2 --timeout-ms 5000"
```

Linux/macOS dùng `./gradlew` thay `gradlew.bat`. `demo:1` và `demo:2` truyền seed
cho DemoBotApplication; bot chỉ chọn từ legalActions nhận qua JSON Lines và
không import GameEngine. Không có randomness ẩn trong runner.

CLI tổng quát: `bot-vs-bot <botA> <botB> [--timeout-ms 2000]`.
Mỗi bot có thể là executable không cần argument (ví dụ `botA.exe`), hoặc file
`.json` chứa mảng command/arguments để giữ nguyên đường dẫn có dấu cách.
Ví dụ nội dung `botA.json`:

```json
["python", "bots/bot A.py", "--seed", "42"]
```

Chạy `--args="bot-vs-bot botA.json botB.json"`. Command và đường dẫn tương đối
được tính từ thư mục `server`; chương trình không chạy command qua shell.
Mỗi bot phải thực hiện protocol của Bước 8, flush response và giữ stdout chỉ
cho JSON Lines. Timeout mặc định là 2.000 ms mỗi lượt ghi/đọc.

LocalMatchRunner sở hữu hai BotProcess và đóng cả hai khi trận kết thúc hoặc
có lỗi. Luồng là LocalMatchRunner → Match → BotAgent A/B → LocalProcessRunner A/B.
Match vẫn dùng GameEngine giống Human vs Human. Khi thành công, in Match ID,
Winner (hoặc DRAW), Total turns, Remaining pieces và Execution time (gồm khởi
động, thi đấu, cleanup). Khi bot lỗi hoặc trả action sai luật, CLI báo lỗi và
thoát mã 1, không tự gán kết quả thắng/thua; cú pháp CLI sai trả mã 2.

Kết quả Bước 9: **228 tests pass, 0 failures, 0 errors**, gồm 8 test mới cho
runner/CLI/command files. Kiểm tra bot process thật, cùng seed cho cùng trạng
thái cuối, cleanup khi lỗi và đường dẫn có dấu cách. CLI demo:1 vs demo:2 chạy
thành công: P1 thắng ở lượt 200, còn P1=15 và P2=13 quân.
Test chạy trên JDK 25 với `--release 21`, thư mục build riêng
`build-step3-verification-step9-a`; chưa kiểm tra runtime trực tiếp trên JDK 21.
Event Log và Replay được bổ sung ở Bước 10 bên dưới.

## Bước 10 — Event Log và Replay

Match giữ initialState và danh sách GameEvent không thể sửa từ bên ngoài.
Các event lifecycle mới gồm MATCH_STARTED, TURN_STARTED, TURN_ENDED,
MATCH_FINISHED; cùng với PIECE_MOVED, COMBAT_OCCURRED, PIECE_REMOVED và các
event GameEngine hiện có. Mỗi event JSON có `sequence`, `type`, `payload`.
Các event trong cùng action chia sẻ sequence; TURN_ENDED hoàn tất sequence đó.
Action bị reject không thêm event. Match kết thúc ngay lúc start cũng có log.

ReplayService nằm trong `replay/`, cùng MatchLog và codec ReplayJson.
`write(path, match)` xuất trận đã FINISHED; `read(path)` trả MatchLog;
`replay(path)` hoặc `replay(initialState, events)` dựng lại trạng thái cuối.
Replay áp dụng các fact đã ghi, không gọi agent, randomness hay tính lại luật
combat. Kiểm tra lifecycle, thứ tự sequence, piece payload và kết quả lưu trong
file; log thiếu MATCH_FINISHED hoặc sai thứ tự bị từ chối.

File chứa matchId, ruleSet, seed, initialState, events và result. Snapshot chứa
kích thước board, mọi thuộc tính piece/player, currentPlayer, phase, turnNumber,
sequence và result. Truyền seed qua constructor Match khi caller có seed gốc;
nếu không được cung cấp thì `seed: null` (ví dụ CLI dùng hai seed bot độc lập).
Replay chỉ cần initialState + events nên không cần chạy lại các bot.

Xuất log Human vs Human:

```powershell
.\gradlew.bat run --console=plain --args="human-vs-human --export match-human.json"
```

Xuất log Bot vs Bot và đọc replay:

```powershell
.\gradlew.bat run --console=plain --args="bot-vs-bot demo:1 demo:2 --timeout-ms 5000 --export match-demo.json"
.\gradlew.bat run --console=plain --args="replay match-demo.json"
```

Log chỉ được xuất khi có `--export`; đường dẫn tương đối tính từ `server`.
Các file `match-*.json` được ignore trong Git. Core vẫn không phụ thuộc JSON.

Kết quả Bước 10: **239 tests pass, 0 failures, 0 errors**, gồm 11 replay tests.
So sánh cấu trúc đầy đủ original final state với replay final state, kiểm tra
JSON round-trip, cả ba outcome combat, pass/surrender, kết thúc ngay lúc start,
log action sai, log thiếu/sai sequence, metadata result và CLI human export/replay.
CLI bot export/replay cũng chạy thành công: lượt 200, WIN(P1), còn quân 15–13.
Test chạy trên JDK 25 với `--release 21`, thư mục build riêng
`build-step3-verification-step10-b`; chưa kiểm tra runtime trực tiếp JDK 21.
## Bước 11 — Cleanup và kiểm tra kiến trúc

Chặng 1 hoàn thành. **249 tests pass, 0 failures, 0 errors, 0 skipped**.
Sửa invariant/index của Board, bảo đảm allPieces trả snapshot độc lập,
báo lỗi replay thiếu snapshot rõ ràng và từ chối command JSON có dữ liệu thừa.
Thêm 6 kiểm tra kiến trúc để giữ core độc lập với adapter/infrastructure,
engine độc lập luật Classic, bot không gọi engine và randomness có seed/injection.

[Báo cáo cuối Chặng 1](docs/CHANG-1-REVIEW.md) ghi cây thư mục đầy đủ,
dependency giữa các package, circular dependency và kết quả từng nhóm test.
Kiểm tra CLI human, bot và replay thành công trên JDK 25 với `--release 21`;
chưa kiểm tra runtime trực tiếp trên JDK 21.
