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
- [ ] Bước 4 — Game Engine
- [ ] Bước 5 — Legal Action Generator
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
GameEngine là công việc của Bước 4, chưa triển khai trong bước này.

Kiểm tra: 128 tests pass, 0 failures, 0 errors. Chạy bằng JDK 25 hiện có trên máy,
biên dịch với `--release 21`; chưa xác nhận chạy trực tiếp trên JDK 21.
Do Windows khóa thư mục kết quả cũ, lượt kiểm tra dùng thư mục build riêng:

```powershell
# Tạo init script dùng một thư mục mới nếu build cũ đang bị khóa.
Set-Content .verify-step3.gradle "allprojects { layout.buildDirectory.set(file('build-step3-verification-final')) }"
.\gradlew.bat test --no-daemon --console=plain --init-script .verify-step3.gradle
```
