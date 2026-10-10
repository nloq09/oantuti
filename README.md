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
- [ ] Bước 2 — Action & GameState
- [ ] Bước 3 — Rule Engine
- [ ] Bước 4 — Game Engine
- [ ] Bước 5 — Legal Action Generator
- [ ] Bước 6 — Match Engine
- [ ] Bước 7 — Human vs Human local
- [ ] Bước 8 — Bot Protocol
- [ ] Bước 9 — Bot vs Bot
- [ ] Bước 10 — Event Log & Replay
- [ ] Bước 11 — Cleanup & Kiểm tra kiến trúc
