# RPS Battle — Oẳn Tù Tì Chiến Thuật

Game chiến thuật Oẳn Tù Tì trên bàn cờ 9×9. Dự án Java 21 + Gradle.

## Chạy nhanh

```bash
cd server
./gradlew test        # Chạy unit tests
./gradlew run         # Chạy ứng dụng (Bước 7+)
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
- [ ] Bước 6 — Match Engine
- [ ] Bước 7 — Human vs Human local
- [ ] Bước 8 — Bot Protocol
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
Bước 6 — Match Engine chưa triển khai.

Kết quả Bước 5: **172 tests pass, 0 failures, 0 errors**, gồm 17 test cho
LegalActionGenerator. Test chạy trên JDK 25 với `--release 21`, dùng thư mục
build riêng `build-step3-verification-step5-a`; chưa kiểm tra runtime JDK 21.
