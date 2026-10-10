# Bước 11 — Kiểm tra cuối Chặng 1

Ngày kiểm tra: 10/10/2026. Chặng 1 hoàn thành: Human vs Human và Bot vs Bot
local cùng chạy qua Match và GameEngine; xuất JSON và replay trạng thái cuối.
Không thêm Spring Boot, database, UI hay network vào Chặng 1.

## Cleanup và invariant

- Board từ chối cùng pieceId ở hai ô. updatePiece kiểm tra bounds/ô đích trước
  khi sửa cả hai index; update bị reject không làm mất quân cũ hay ghi đè quân khác.
- Board.allPieces trả snapshot độc lập, không còn là view thay đổi theo Board.
- Replay thiếu initialState, trường snapshot, player/owner không hợp lệ hoặc
  payload bắt buộc báo lỗi dữ liệu; CLI trả mã 1 thay vì NPE không được xử lý.
- File JSON command bot từ chối trailing tokens.
- Thêm regression tests và 6 ArchitectureTest, không thêm dependency kiểm tra kiến trúc.

## Ranh giới kiến trúc

GameEngine nhận GameState + Action + RuleSet, trả GameTransition + GameEvent.
Core `game/` và các interface `rule/` không import console, JSON, database,
network, process runner, agent hay adapter. Engine không import ClassicRuleSet
và không hardcode ROCK/PAPER/SCISSORS. ClassicGameSetup nằm bên ngoài engine.
Rule interface và implementation không phụ thuộc UI/network.

Match chọn agent, tạo PlayerView và legal actions, rồi đưa action vào engine.
Match không phụ thuộc ConsoleHumanAgent, RandomBotAgent hay BotAgent cụ thể.
BotAgent chỉ trao đổi protocol qua BotProcess; bot không gọi GameEngine.
RandomBotAgent nhận RandomGenerator; DemoBotApplication tạo Random với seed
được cung cấp. Không có Math.random, ThreadLocalRandom hoặc Random không seed.
Caller cung cấp generator chịu trách nhiệm seed; reproducibility đã có test.

ArchitectureTest kiểm tra source/import và các pattern trên. Đây là guard cho
cấu trúc hiện tại, không phải bộ phân tích đầy đủ mọi truy cập gián tiếp/reflection.

## Dependency giữa các package

Danh sách dưới đây là import nội bộ trực tiếp, bỏ prefix `com.rpsbattle`.

```text
game.player   -> (không có)
game.board    -> game.piece
game.piece    -> game.board, game.player
game.action   -> game.board, game.piece, game.player
game.state    -> game.board, game.piece, game.player
rule          -> game.action, game.board, game.piece, game.player, game.state
game.engine   -> game.action, game.board, game.piece, game.player, game.state, rule
rules.classic -> game.action, game.board, game.engine, game.piece, game.player, game.state, rule
agent         -> game.action, game.board, game.engine, game.player, game.state
match         -> agent, game.action, game.engine, game.player, game.state, rule
bot           -> agent, game.action, game.board, game.piece, game.player
replay        -> game.board, game.engine, game.piece, game.player, game.state, match, rule
app           -> agent, bot, game.board, game.engine, game.player, game.state, match, replay, rules.classic
```

Có một vòng ở cấp package: `game.board ↔ game.piece`. Board chứa Piece;
Piece dùng value object Position thuộc game.board. Chuỗi class liên quan là
`Board -> Piece -> Position`; Position không phụ thuộc Board/Piece. Vì vậy
không có vòng class tương ứng hay vòng gọi giữa các tầng. Giữ package Position
theo cấu trúc domain đã yêu cầu; chuyển nó chỉ để bỏ vòng package sẽ đổi API
mà không cải thiện ranh giới engine/adapter. Không có vòng package khác trong
đồ thị import nội bộ trên.

Dependency ngoài: JDK API; Jackson databind 2.18.6 cho bot/replay/command JSON;
JUnit Jupiter 5.11.0 và AssertJ 3.26.3 cho tests. Gradle wrapper 9.1.0.
Core không import Jackson dù dependency runtime nằm chung Gradle module.

## Cây thư mục cuối cùng

Các tên cùng dòng bên dưới là các file `.java` riêng trong cùng folder.
Build/cache và JSON của lần smoke test không đưa vào Git.

```text
BT3/
├── .gitignore
├── README.md
├── docs/
│   └── CHANG-1-REVIEW.md
└── server/
    ├── build.gradle
    ├── settings.gradle
    ├── gradlew
    ├── gradlew.bat
    ├── gradle/wrapper/
    │   ├── gradle-wrapper.jar
    │   └── gradle-wrapper.properties
    └── src/
        ├── main/java/com/rpsbattle/
        │   ├── game/
        │   │   ├── board/  Board, Position
        │   │   ├── piece/  Piece, PieceStatus, PieceType
        │   │   ├── player/ Player, PlayerId
        │   │   ├── action/ Action, MoveAction, PlaceAction, PassAction, SurrenderAction
        │   │   ├── state/  GameState, GamePhase, GameOutcome, GameResult
        │   │   └── engine/ GameEngine, GameEvent, GameTransition,
        │   │              InvalidActionException, LegalActionGenerator
        │   ├── rule/ RuleSet, MovementRule, CombatRule, CombatResult,
        │   │         PlacementRule, VictoryRule, TurnRule
        │   ├── rules/classic/ ClassicRuleSet, ClassicMovementRule, ClassicCombatRule,
        │   │                  ClassicPlacementRule, ClassicVictoryRule,
        │   │                  ClassicTurnRule, ClassicGameSetup
        │   ├── agent/ PlayerAgent, PlayerView, ConsoleHumanAgent, RandomBotAgent
        │   ├── match/ Match, MatchStatus
        │   ├── bot/ BotAgent, BotProcess, LocalProcessRunner, BotProtocol,
        │   │        BotProtocolMessage, BotTurnRequest, BotActionResponse,
        │   │        BotProtocolException, DemoBotApplication
        │   ├── replay/ MatchLog, ReplayJson, ReplayService
        │   └── app/ LocalGameApplication, ConsoleBoardRenderer,
        │            LocalMatchRunner, BotCommands
        └── test/java/com/rpsbattle/
            ├── game/action/ ActionTest
            ├── game/board/ BoardTest
            ├── game/state/ GameStateTest
            ├── game/engine/ GameEngineTest, LegalActionGeneratorTest
            ├── rules/classic/ ClassicCombatRuleTest, ClassicMovementRuleTest,
            │                  ClassicPlacementRuleTest, ClassicVictoryRuleTest,
            │                  ClassicTurnRuleTest, ClassicRuleSetTest
            ├── agent/ PlayerAgentTest
            ├── match/ MatchTest
            ├── bot/ BotProtocolTest, BotProcessTest, TestBotProcessMain (fixture)
            ├── replay/ ReplayServiceTest
            ├── app/ LocalGameApplicationTest, LocalMatchRunnerTest, BotCommandsTest
            └── architecture/ ArchitectureTest
```

## Kết quả test

Toàn bộ **249 tests PASS; 0 failures, 0 errors, 0 skipped**. Bao gồm các test
nested/parameterized, tổng hợp từ XML của Gradle:

- ActionTest: 13 PASS.
- BoardTest (bao gồm Position): 26 PASS.
- GameStateTest: 30 PASS.
- GameEngineTest: 27 PASS; LegalActionGeneratorTest: 17 PASS.
- ClassicCombatRuleTest: 18 PASS (đủ ma trận RPS).
- ClassicMovementRuleTest: 13 PASS; ClassicPlacementRuleTest: 13 PASS.
- ClassicVictoryRuleTest: 9 PASS; ClassicTurnRuleTest: 7 PASS; ClassicRuleSetTest: 2 PASS.
- PlayerAgentTest: 7 PASS; MatchTest: 10 PASS.
- BotProtocolTest: 15 PASS; BotProcessTest: 10 PASS.
- ReplayServiceTest: 12 PASS.
- LocalGameApplicationTest: 6 PASS; LocalMatchRunnerTest: 5 PASS; BotCommandsTest: 3 PASS.
- ArchitectureTest: 6 PASS.

Chạy bằng JDK 25 hiện có trên máy, biên dịch `--release 21`; chưa xác nhận
runtime trực tiếp JDK 21. Dùng thư mục build mới để tránh Windows/OneDrive khóa
kết quả cũ: `build-step3-verification-step11-a` (init script được ignore).

```powershell
cd server
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-25'
.\gradlew.bat test --no-daemon --console=plain --init-script .verify-step3.gradle
```

Khi không bị khóa build, chạy bình thường bằng `./gradlew test` hoặc
`.\gradlew.bat test`. Không cần init script trong CI/checkout mới.

Smoke test CLI: human surrender kết thúc đúng WIN(P2); bot `demo:1` vs `demo:2`
WIN(P1), lượt 200, còn P1=15/P2=13. Xuất log rồi replay cho cùng trạng thái cuối.
Tests cũng xác nhận đầy đủ snapshot replay bằng JSON cấu trúc, cleanup bot khi
lỗi/timeout và cùng seed cho cùng kết quả.

Bot process local có timeout, giới hạn line và cleanup; chưa có sandbox OS/container.
Đây là giới hạn triển khai local của Chặng 1, chưa phù hợp chạy bot không tin cậy
trên server public. Networking, hosting và Spring Boot thuộc chặng sau.
