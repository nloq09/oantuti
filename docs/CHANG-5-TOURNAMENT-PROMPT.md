# PROMPT AI AGENT — CHẶNG 5
# PHÁT TRIỂN HỆ THỐNG GIẢI ĐẤU HOÀN CHỈNH

## 0. Bối cảnh hiện tại

Project:

```text
RPS Battle — Oẳn Tù Tì Chiến Thuật 9×9
```

Đến hết Chặng 4, hệ thống dự kiến đã có:

```text
Java 21
Spring Boot
Gradle

HTML/CSS/JavaScript ES Modules
REST
WebSocket
Jackson

PostgreSQL
Spring Data JPA
Flyway
Spring Security

Register / Login / Logout
User Account
USER / ADMIN

Bot ownership
Bot upload:
- Python
- C++
- Java source
- Java JAR

Docker sandbox

Human vs Human
Human vs built-in bot
Human vs uploaded bot
Uploaded bot điều khiển từng seat

Match persistence
Match history
Replay persistence

Tournament cơ bản
Round Robin
Ranking
Spectator
Web Replay

Production config
Monitoring
Worker / Queue
Rate limiting
Backup / Recovery
```

Mục tiêu Chặng 5:

> Phát triển hệ thống giải đấu từ mức “có tournament cơ bản” thành một module thi đấu hoàn chỉnh, có đăng ký, duyệt người tham gia, check-in, seed, nhiều thể thức, vòng đấu, bracket, lịch thi đấu, bảng xếp hạng, xử lý lỗi trận, tạm dừng/tiếp tục giải, quản trị giải và trải nghiệm spectator hoàn chỉnh.

Không tập trung thêm gameplay mới.

Không viết lại GameEngine.

## 1. Mục tiêu cuối Chặng 5

Sau chặng này hệ thống phải hỗ trợ:

```text
Create Tournament
Tournament Registration
Participant Approval
Check-in
Bot Lock / Version Lock
Seeding
Round Robin
Single Elimination
Double Elimination hoặc Swiss
(ít nhất implement thêm 1 thể thức ngoài Round Robin)
Multi-round Scheduling
Bracket
Tournament Stages
Match Queue
Live Tournament Dashboard
Live Spectator
Standings
Tie-break
Forfeit / Disqualification
Pause / Resume Tournament
Cancel Tournament
Tournament Admin
Tournament Rules
Tournament Result
Champion / Podium
Replay từng trận
Export Tournament
Tournament History
```

Ưu tiên:

```text
1. Single Elimination
2. Swiss
3. Double Elimination
```

Nếu thời gian hạn chế, bắt buộc hoàn thiện:

```text
Round Robin
+
Single Elimination
```

## 2. Nguyên tắc kiến trúc

Giữ nguyên:

```text
Tournament
    ↓
Tournament Scheduler
    ↓
Tournament Match
    ↓
Match Service
    ↓
Match
    ↓
GameEngine
    ↓
RuleSet
```

Tournament chỉ điều phối.

Không được:

```text
Tournament
→ tự xử lý combat

Tournament
→ tự validate move

Tournament
→ sửa GameState trực tiếp
```

Không tạo:

```text
TournamentGameEngine
TournamentRuleEngine
```

## 3. Cấu trúc package đề xuất

```text
com.rpsbattle/
└── tournament/
    ├── domain/
    │   ├── Tournament.java
    │   ├── TournamentStage.java
    │   ├── TournamentRound.java
    │   ├── TournamentMatch.java
    │   ├── TournamentParticipant.java
    │   ├── TournamentRegistration.java
    │   ├── TournamentFormat.java
    │   ├── TournamentStatus.java
    │   ├── ParticipantStatus.java
    │   └── MatchStatus.java
    │
    ├── registration/
    │   ├── RegistrationService.java
    │   ├── CheckInService.java
    │   └── ParticipantApprovalService.java
    │
    ├── seeding/
    │   ├── SeedingService.java
    │   ├── RandomSeedingStrategy.java
    │   └── RankingSeedingStrategy.java
    │
    ├── scheduler/
    │   ├── TournamentScheduler.java
    │   ├── RoundRobinScheduler.java
    │   ├── SingleEliminationScheduler.java
    │   └── SwissScheduler.java
    │
    ├── bracket/
    │   ├── BracketService.java
    │   ├── BracketNode.java
    │   └── BracketMapper.java
    │
    ├── execution/
    │   ├── TournamentExecutionService.java
    │   ├── TournamentRoundExecutor.java
    │   └── TournamentMatchExecutor.java
    │
    ├── ranking/
    │   ├── RankingService.java
    │   ├── RankingEntry.java
    │   ├── TieBreakService.java
    │   └── ScoringRule.java
    │
    ├── admin/
    │   ├── TournamentAdminService.java
    │   ├── TournamentControlService.java
    │   └── DisqualificationService.java
    │
    └── persistence/
```

## 4. Tournament lifecycle

Chuẩn hóa lifecycle:

```text
DRAFT
↓
REGISTRATION
↓
CHECK_IN
↓
READY
↓
RUNNING
↓
FINISHED
```

Có nhánh:

```text
PAUSED
CANCELLED
```

Không cho phép transition tùy ý.

Tạo:

```java
TournamentStateMachine
```

hoặc service tương đương.

Ví dụ:

```text
DRAFT → REGISTRATION     hợp lệ
REGISTRATION → CHECK_IN  hợp lệ
RUNNING → DRAFT          không hợp lệ
FINISHED → RUNNING       không hợp lệ
```

Viết test đầy đủ transition.

# PHẦN A — TOURNAMENT CREATION

## BƯỚC 1 — Create Tournament nâng cao

Form:

```text
Tournament Name
Description
Format
Max Participants
Registration Open At
Registration Close At
Check-in Required
Start Time
RuleSet
Turn Time Limit
Visibility
Allow Spectators
Max Concurrent Matches
```

## BƯỚC 2 — Tournament visibility

Enum:

```text
PUBLIC
PRIVATE
UNLISTED
```

PUBLIC:

```text
mọi user có thể xem
```

PRIVATE:

```text
chỉ owner/admin/invited participants
```

UNLISTED:

```text
cần link/code
```

# PHẦN B — REGISTRATION

## BƯỚC 3 — TournamentRegistration

Tạo:

```text
TournamentRegistration
```

Chứa:

```text
id
tournamentId
userId
botId
botVersionId
status
registeredAt
approvedAt
checkedInAt
```

## BƯỚC 4 — Registration Status

```text
PENDING
APPROVED
REJECTED
CANCELLED
CHECKED_IN
```

## BƯỚC 5 — Register API

```http
POST /api/tournaments/{id}/registrations
```

Request:

```json
{
  "botId": "BOT-123"
}
```

Server phải snapshot/lock:

```text
botVersionId
```

Không tự động lấy version mới sau khi tournament bắt đầu.

## BƯỚC 6 — Registration rules

Validate:

```text
Tournament đang REGISTRATION
User authenticated
Bot thuộc user
Bot READY
Không đăng ký trùng
Chưa vượt maxParticipants
```

# PHẦN C — APPROVAL

## BƯỚC 7

Tournament owner/admin có:

```text
Approve
Reject
```

API:

```http
POST /api/tournaments/{id}/registrations/{registrationId}/approve
POST /api/tournaments/{id}/registrations/{registrationId}/reject
```

# PHẦN D — CHECK-IN

## BƯỚC 8

Nếu:

```text
checkInRequired = true
```

trước giờ start phải check-in.

API:

```http
POST /api/tournaments/{id}/check-in
```

## BƯỚC 9

Participant không check-in:

```text
NOT_CHECKED_IN
```

và không được đưa vào bracket/schedule.

# PHẦN E — BOT VERSION LOCK

## BƯỚC 10

Khi:

```text
registration approved
hoặc
tournament READY
```

khóa:

```text
botVersionId
source hash
artifact hash
```

Nếu user upload version mới:

```text
Tournament vẫn chạy version cũ
```

## BƯỚC 11 — Hash

Lưu:

```text
SHA-256 artifact hash
```

để đảm bảo bot không bị thay đổi giữa giải.

Không cần dùng hash cho cryptographic signing.

Chỉ dùng kiểm tra integrity/version lock.

# PHẦN F — SEEDING

## BƯỚC 12 — SeedingService

Tạo:

```java
SeedingService
```

Strategies:

```text
RANDOM
MANUAL
RANKING
```

## BƯỚC 13 — Random seeding

Phải deterministic nếu cung cấp:

```text
seed
```

Ví dụ:

```text
same participants
+
same seed
=
same bracket
```

## BƯỚC 14 — Manual seeding

Tournament admin có thể đặt:

```text
Seed 1
Seed 2
...
```

Validate duplicate seed.

# PHẦN G — ROUND ROBIN NÂNG CAO

## BƯỚC 15

Giữ scheduler hiện tại nhưng bổ sung:

```text
Round Number
```

Ví dụ:

```text
Round 1
A-B
C-D

Round 2
A-C
B-D

Round 3
A-D
B-C
```

Không chỉ trả danh sách match phẳng.

## BƯỚC 16

Với số participant lẻ:

```text
BYE
```

không tạo fake Match.

# PHẦN H — SINGLE ELIMINATION

## BƯỚC 17 — SingleEliminationScheduler

Implement:

```text
2
4
8
16
32
```

participant trực tiếp.

## BƯỚC 18 — Non power-of-two

Ví dụ:

```text
6 participants
```

Phải sinh BYE hợp lý để đưa bracket về power-of-two.

Không loại participant ngẫu nhiên.

## BƯỚC 19 — Bracket

Ví dụ 8 bot:

```text
Quarterfinal
   ↓
Semifinal
   ↓
Final
```

Bracket nodes phải chứa:

```text
matchId
round
slotA
slotB
winnerTo
```

## BƯỚC 20 — Advancement

Khi Match FINISHED:

```text
Winner
↓
next bracket slot
```

Không để UI tự quyết định advancement.

Server là nguồn sự thật.

# PHẦN I — OPTIONAL SWISS

Nếu đủ thời gian, implement Swiss.

## BƯỚC 21

Swiss pairing phải cố gắng:

```text
đấu với người cùng điểm
tránh rematch
cân bằng pairing
```

Nếu không implement Swiss trong Chặng 5, phải để architecture mở rộng được.

# PHẦN J — TOURNAMENT STAGES

## BƯỚC 22

Cho phép tournament sau này có:

```text
Group Stage
↓
Knockout Stage
```

Chặng này có thể chưa cần UI tạo multi-stage tùy ý.

Nhưng domain nên có:

```text
TournamentStage
```

## BƯỚC 23

Ví dụ future-compatible:

```text
Stage 1
ROUND_ROBIN

Top 4 qualify

Stage 2
SINGLE_ELIMINATION
```

Không bắt buộc activate feature nếu chưa đủ thời gian.

# PHẦN K — MATCH SCHEDULING

## BƯỚC 24

Tournament match có:

```text
scheduledAt
startedAt
finishedAt
```

## BƯỚC 25

Scheduler phải hỗ trợ:

```text
maxConcurrentMatches
```

Ví dụ:

```text
16 tournament matches
maxConcurrent = 4
```

Chỉ 4 trận cùng chạy.

# PHẦN L — PAUSE / RESUME

## BƯỚC 26

Tournament owner/admin có:

```text
PAUSE
RESUME
```

Pause:

```text
không start match mới
```

Không bắt buộc kill match đang chạy.

## BƯỚC 27

Running Match có thể:

```text
finish normally
```

trong khi Tournament PAUSED.

# PHẦN M — CANCEL

## BƯỚC 28

Cancel Tournament:

```text
DRAFT
REGISTRATION
CHECK_IN
READY
PAUSED
```

được phép.

RUNNING:

cần policy rõ:

```text
stop scheduling new matches
running matches finish hoặc cancel theo config
```

# PHẦN N — FORFEIT

## BƯỚC 29

Cho phép admin đánh dấu:

```text
Participant forfeited
```

Tất cả future Match xử lý theo format.

Round Robin:

```text
remaining matches = FORFEIT LOSS
```

Single Elimination:

```text
opponent advances
```

# PHẦN O — DISQUALIFICATION

## BƯỚC 30

ParticipantStatus:

```text
ACTIVE
WITHDRAWN
DISQUALIFIED
```

## BƯỚC 31

Không xóa participant khỏi tournament history.

Disqualified participant phải còn trong audit/history.

# PHẦN P — MATCH RESULT OVERRIDE

## BƯỚC 32

ADMIN hoặc Tournament Owner có thể:

```text
mark match for review
```

Không cho sửa result âm thầm.

Nếu override:

```text
old result
new result
reason
actor
timestamp
```

phải được audit.

# PHẦN Q — AUDIT LOG

## BƯỚC 33

Tạo:

```text
TournamentAuditEvent
```

Ví dụ:

```text
TOURNAMENT_CREATED
REGISTRATION_OPENED
PARTICIPANT_APPROVED
CHECK_IN
TOURNAMENT_STARTED
MATCH_STARTED
MATCH_FINISHED
TOURNAMENT_PAUSED
PARTICIPANT_DISQUALIFIED
RESULT_OVERRIDDEN
TOURNAMENT_FINISHED
```

# PHẦN R — STANDINGS

## BƯỚC 34

Round Robin:

```text
Played
Wins
Draws
Losses
Points
```

## BƯỚC 35 — Tie breaks

Thứ tự configurable:

```text
Points
Wins
Head-to-head
Buchholz nếu Swiss
Remaining Pieces
Average Move Time
```

Không hard-code toàn bộ trong controller.

# PHẦN S — QUALIFICATION

## BƯỚC 36

Nếu future multi-stage:

```text
Top N
```

có thể qualify.

Ví dụ:

```text
Top 4
```

# PHẦN T — TOURNAMENT DASHBOARD

## BƯỚC 37 — tournament.html

Tabs:

```text
OVERVIEW
PARTICIPANTS
MATCHES
BRACKET
STANDINGS
REPLAYS
ADMIN
```

ADMIN tab chỉ owner/admin.

## BƯỚC 38 — Overview

Hiển thị:

```text
Tournament Name
Format
Status
Participants
Current Round
Completed Matches
Running Matches
Start Time
Champion nếu finished
```

# PHẦN U — PARTICIPANT UI

## BƯỚC 39

Danh sách:

```text
Seed
Bot
Owner
Status
Check-in
```

Ví dụ:

```text
#1 AlphaBot   dat123   CHECKED IN
#2 GreedyBot  user02   CHECKED IN
#3 FastBot    user03   PENDING
```

# PHẦN V — BRACKET UI

## BƯỚC 40

Single Elimination phải có bracket trực quan:

```text
Quarterfinal
Semifinal
Final
Champion
```

Không render bracket bằng ảnh tĩnh.

Dùng HTML/CSS/JS.

## BƯỚC 41

Bracket phải cập nhật realtime khi Match kết thúc.

# PHẦN W — LIVE MATCH CENTER

## BƯỚC 42

Tournament dashboard có:

```text
LIVE NOW
```

Cards:

```text
AlphaBot
vs
GreedyBot

Turn 45

● LIVE

[ WATCH ]
```

# PHẦN X — SPECTATOR

## BƯỚC 43

Tái sử dụng spectator hiện tại.

Không tạo GameBoard mới.

Spectator từ tournament phải:

```text
WATCH
↓
game.html?mode=spectator...
```

hoặc route phù hợp hiện tại.

# PHẦN Y — REPLAY CENTER

## BƯỚC 44

Tournament có tab:

```text
REPLAYS
```

Danh sách:

```text
Round 1
AlphaBot vs GreedyBot
WIN AlphaBot

[ WATCH REPLAY ]
```

# PHẦN Z — TOURNAMENT HISTORY

## BƯỚC 45

Account page:

```text
MY TOURNAMENTS
```

Chia:

```text
Created
Participated
Won
```

## BƯỚC 46

API:

```http
GET /api/account/tournaments
```

Filters:

```text
created
participated
finished
won
```

# PHẦN AA — GLOBAL TOURNAMENT LIST

## BƯỚC 47

Trang:

```text
tournaments.html
```

Sections:

```text
UPCOMING
REGISTRATION OPEN
LIVE
FINISHED
```

## BƯỚC 48 — Search / filter

Filter:

```text
format
status
owner
```

Không cần full-text search engine.

# PHẦN AB — NOTIFICATION

## BƯỚC 49

Trong UI hiển thị:

```text
Registration approved
Check-in open
Tournament starting
Match scheduled
Tournament finished
```

Có thể dùng:

```text
WebSocket event
+
toast
```

Không cần email ở chặng này.

# PHẦN AC — TOURNAMENT WEBSOCKET

## BƯỚC 50

Chuẩn hóa:

```text
WATCH_TOURNAMENT
```

Server gửi:

```text
TOURNAMENT_UPDATED
REGISTRATION_UPDATED
ROUND_STARTED
MATCH_STARTED
MATCH_FINISHED
STANDINGS_UPDATED
BRACKET_UPDATED
TOURNAMENT_FINISHED
```

# PHẦN AD — ROUND CONTROL

## BƯỚC 51

Tournament có thể cấu hình:

```text
autoAdvanceRounds = true
```

Nếu false:

```text
Admin phải bấm Start Next Round
```

# PHẦN AE — MATCH REVIEW

## BƯỚC 52

Tournament admin có trang:

```text
MATCH REVIEW
```

Hiển thị:

```text
result
bot errors
timeouts
replay
execution logs
```

Không expose Docker internals cho participant bình thường.

# PHẦN AF — RULE CONFIG SNAPSHOT

## BƯỚC 53

Khi Tournament chuyển READY:

snapshot:

```text
ruleSet
turnSeconds
maxTurns
pieceQuota
boardSize
scoring
tieBreakRules
```

Sau start không đổi giữa giải.

# PHẦN AG — DATABASE

## BƯỚC 54

Mở rộng schema:

```text
tournament_registrations
tournament_stages
tournament_rounds
tournament_matches
tournament_participants
tournament_audit_events
tournament_standings
```

Dùng Flyway migration mới.

## BƯỚC 55 — Constraints

DB constraints:

```text
unique tournament + bot registration
unique participant seed
foreign keys
valid status mapping
```

# PHẦN AH — TRANSACTIONS

## BƯỚC 56

Các operation cần transaction:

```text
approve registration
start tournament
finish match + advance bracket
update standings
finish tournament
```

Không để:

```text
match FINISHED
nhưng bracket chưa advance
```

# PHẦN AI — CONCURRENCY

## BƯỚC 57

Tránh double-finishing một Match.

Nếu two events cùng đến:

```text
finishMatch()
```

chỉ một update thành công.

Có thể dùng:

```text
optimistic locking
```

với:

```java
@Version
```

trên entity phù hợp.

# PHẦN AJ — ADMIN TOOLS

## BƯỚC 58

Admin có:

```text
Create Tournament
Open Registration
Close Registration
Approve Participant
Start Check-In
Start Tournament
Pause
Resume
Cancel
Disqualify Participant
Review Match
Finish Tournament
```

# PHẦN AK — TOURNAMENT OWNER PERMISSIONS

## BƯỚC 59

Tournament owner được quản lý giải của mình.

Không được quản lý giải người khác.

ADMIN có override.

# PHẦN AL — FAILURE HANDLING

## BƯỚC 60

Bot failure:

```text
TIMEOUT
CRASH
INVALID_OUTPUT
ILLEGAL_ACTION
```

phải được quy đổi theo tournament policy.

## BƯỚC 61

Infrastructure failure:

```text
Docker unavailable
worker failure
server restart
```

không tự coi participant thua nếu lỗi không phải do bot.

Match chuyển:

```text
ERROR
```

và cho phép:

```text
RETRY
```

# PHẦN AM — RETRY MATCH

## BƯỚC 62

Admin có:

```text
Retry Match
```

Chỉ khi:

```text
ERROR
```

Không retry Match đã có valid result nếu không qua override workflow.

# PHẦN AN — RECOVERY

## BƯỚC 63

Server restart giữa tournament:

```text
Tournament RUNNING
```

phải recover thành:

```text
PAUSED
hoặc RECOVERY_REQUIRED
```

Sau review:

```text
RESUME
```

# PHẦN AO — EXPORT

## BƯỚC 64

Export tournament:

```text
JSON
```

Chứa:

```text
metadata
rules snapshot
participants
seeds
rounds
matches
results
standings
audit events
champion
```

# PHẦN AP — REPORT

## BƯỚC 65

Tạo summary endpoint:

```http
GET /api/tournaments/{id}/report
```

Dữ liệu:

```text
Champion
Runner-up
Total Matches
Average Match Duration
Total Bot Errors
Longest Match
Shortest Match
Final Standings
```

# PHẦN AQ — TESTING

Phải thêm:

```text
TournamentLifecycleTest
TournamentRegistrationTest
TournamentApprovalTest
TournamentCheckInTest
BotVersionLockTest
SeedingServiceTest
RoundRobinRoundSchedulerTest
SingleEliminationSchedulerTest
BracketAdvancementTest
ByeHandlingTest
TournamentPauseResumeTest
TournamentCancelTest
ForfeitTest
DisqualificationTest
ResultOverrideAuditTest
StandingsTest
TieBreakTest
TournamentAuthorizationTest
TournamentRecoveryTest
TournamentConcurrencyTest
```

# PHẦN AR — CLIENT TESTS

Thêm:

```text
tournament-list.test.js
registration.test.js
check-in.test.js
bracket-renderer.test.js
standings.test.js
tournament-admin.test.js
tournament-live-update.test.js
```

# PHẦN AS — LOAD TEST

## BƯỚC 66

Test:

```text
8 participants
Round Robin
28 matches
```

## BƯỚC 67

Test:

```text
16 participants
Single Elimination
15 matches
```

## BƯỚC 68

Test:

```text
32 participants
Single Elimination
31 matches
```

Kiểm tra:

```text
bracket correctness
no duplicate advancement
no stuck matches
```

# PHẦN AT — END-TO-END DEMO

Chặng 5 hoàn thành khi demo được:

```text
1. Admin/User tạo Tournament.

2. Chọn Single Elimination.

3. Mở Registration.

4. 8 users đăng ký bot.

5. Admin approve.

6. Check-in.

7. Bot versions được lock.

8. Seed participants.

9. Start Tournament.

10. Quarterfinal được sinh đúng.

11. Matches tự chạy theo capacity.

12. Spectator xem trận LIVE.

13. Winner tự advance bracket.

14. Semifinal được sinh đúng.

15. Final được sinh đúng.

16. Champion xuất hiện.

17. Bracket hoàn chỉnh.

18. Replay mọi trận xem được.

19. Tournament History lưu trong DB.

20. Export tournament hoạt động.
```

Với Round Robin phải demo:

```text
Standings update sau từng trận
Tie-break đúng
Final ranking đúng
```

# PHẦN AU — ARCHITECTURE CHECK

Bắt buộc giữ:

```text
game/
rule/
```

không import:

```text
tournament
JPA
Spring Security
WebSocket
Docker
```

Tournament có thể phụ thuộc:

```text
match application layer
```

Match không phụ thuộc Tournament.

# DEFINITION OF DONE — CHẶNG 5

Chặng 5 được coi là hoàn thành khi:

```text
Tournament lifecycle hoàn chỉnh

Registration hoạt động
Approval hoạt động
Check-in hoạt động

Bot version lock hoạt động

Round Robin hoàn chỉnh
Single Elimination hoàn chỉnh

Seeding hoạt động
Bracket realtime hoạt động
Winner advancement đúng

Standings đúng
Tie-break đúng

Pause / Resume hoạt động
Forfeit / Disqualification hoạt động

Spectator hoạt động
Replay hoạt động

Tournament History persist

Recovery sau restart có policy rõ

Authorization owner/admin đúng

Không phá GameEngine hiện tại
```

# CÁCH GIAO CHO AI AGENT

Không implement toàn Chặng 5 trong một lượt.

Chia nhóm:

```text
Nhóm 1
Tournament lifecycle + DB migration

Nhóm 2
Registration + Approval + Check-in

Nhóm 3
Bot version lock + Seeding

Nhóm 4
Round Robin nâng cao

Nhóm 5
Single Elimination + Bracket

Nhóm 6
Standings + Tie-break

Nhóm 7
Pause / Resume / Cancel / Forfeit / DQ

Nhóm 8
Tournament Dashboard + Bracket UI

Nhóm 9
Realtime + Spectator + Replay integration

Nhóm 10
Recovery + Audit + Load Test + Final Regression
```

Sau MỖI nhóm:

```text
1. Chạy toàn bộ backend tests.

2. Chạy toàn bộ client tests.

3. Chạy migration test.

4. Build bootJar.

5. Báo pass/fail/skipped.

6. Liệt kê file thêm/sửa.

7. Kiểm tra GameEngine/RuleSet có bị sửa không.

8. Nếu phải sửa core, giải thích.

9. Không tự chuyển nhóm tiếp theo.

10. Chờ tôi review.
```

# ƯU TIÊN TRIỂN KHAI

Thứ tự:

```text
Lifecycle
   ↓
Registration
   ↓
Check-in
   ↓
Version Lock
   ↓
Seeding
   ↓
Round Robin
   ↓
Single Elimination
   ↓
Bracket
   ↓
Standings
   ↓
Admin Controls
   ↓
Spectator / Replay
   ↓
Recovery / Audit
```

Mục tiêu Chặng 5:

> RPS Battle trở thành một hệ thống tổ chức giải đấu thực sự, thay vì chỉ là một game có chức năng tạo tournament cơ bản.
