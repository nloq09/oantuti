# PROMPT AI AGENT — CHẶNG 8
# PHÁT TRIỂN CHẾ ĐỘ GIẢI ĐẤU CHO NGƯỜI CHƠI

## 0. Bối cảnh hiện tại

Project: `RPS Battle — Oẳn Tù Tì Chiến Thuật 9×9`

Đến hết Chặng 7, hệ thống dự kiến đã có:
- Java 21, Spring Boot, PostgreSQL, JPA, Flyway, Spring Security.
- Account, role USER/ADMIN.
- Bot ownership, upload bot, Docker sandbox.
- Match Engine, GameEngine, RuleSet, Replay.
- Tournament core: registration, approval, check-in, seeding, Round Robin, Single Elimination, Multi-stage, Group Stage, Swiss, BO1/BO3/BO5, Team Tournament, School vs School, Scheduling, Bracket, Standings, Spectator, Replay, Referee, Dispute, Audit, Recovery.
- Admin UI hoàn chỉnh.

Chặng 8 tập trung vào:

> Biến Tournament từ một module quản trị/kỹ thuật thành một **chế độ chơi hoàn chỉnh dành cho người dùng**, với toàn bộ trải nghiệm từ tìm giải → đăng ký → check-in → chờ trận → vào trận tự động → theo dõi vòng đấu → xem bảng xếp hạng → nhận kết quả → tiếp tục vòng sau.

Không thêm gameplay mới.
Không viết lại GameEngine, RuleEngine, Bot Protocol hay Tournament Scheduler hiện có.

---

# 1. MỤC TIÊU CUỐI CHẶNG 8

User phải có flow:

```text
HOME
 ↓
GIẢI ĐẤU
 ↓
KHÁM PHÁ GIẢI
 ↓
XEM CHI TIẾT
 ↓
ĐĂNG KÝ
 ↓
CHỌN TÔI TỰ CHƠI / BOT
 ↓
ĐƯỢC DUYỆT
 ↓
CHECK-IN
 ↓
TOURNAMENT LOBBY
 ↓
ĐẾN LƯỢT THI ĐẤU
 ↓
READY CHECK
 ↓
VÀO MATCH TỰ ĐỘNG
 ↓
THI ĐẤU
 ↓
KẾT QUẢ
 ↓
QUAY LẠI GIẢI
 ↓
BRACKET / STANDINGS
 ↓
VÒNG TIẾP THEO
 ↓
CHAMPION / BỊ LOẠI / KẾT THÚC
```

Không bắt user nhập match code trong tournament.

---

# 2. NGUYÊN TẮC UX

Tournament UI phải luôn trả lời rõ:

1. Tôi đang tham gia giải nào?
2. Trạng thái hiện tại của tôi là gì?
3. Trận tiếp theo là khi nào?
4. Tôi cần làm gì ngay bây giờ?
5. Tôi đang đứng ở đâu trong giải?

Mỗi màn hình phải có CTA rõ như:
- Đăng ký
- Check-in
- Sẵn sàng
- Vào trận
- Xem trận
- Xem replay
- Xem bracket
- Xem bảng xếp hạng

---

# PHẦN A — TOURNAMENT HUB

## BƯỚC 1

Tạo `tournaments.html`.

Các tab:
```text
Đang diễn ra
Đang mở đăng ký
Sắp diễn ra
Đã tham gia
Đã kết thúc
```

## BƯỚC 2 — Tournament Card

Card hiển thị:
```text
Tên giải
Format
Số participant
Status
Registration deadline
Start time
Organizer
Visibility
CTA
```

CTA theo status:
```text
OPEN_REGISTRATION → ĐĂNG KÝ
REGISTERED        → XEM ĐĂNG KÝ
CHECK_IN          → CHECK-IN
RUNNING           → VÀO GIẢI
FINISHED          → XEM KẾT QUẢ
```

---

# PHẦN B — TOURNAMENT DETAIL

## BƯỚC 3

Tạo `tournament-detail.html`.

Tabs:
```text
TỔNG QUAN
NGƯỜI THAM GIA
LỊCH THI ĐẤU
BRACKET
BẢNG XẾP HẠNG
TRẬN ĐẤU
REPLAY
```

Nếu user là participant, thêm vùng nổi bật:
```text
TRẠNG THÁI CỦA TÔI
```

---

# PHẦN C — MY TOURNAMENT STATUS

## BƯỚC 4

Tạo component `MyTournamentStatus`.

Các trạng thái:
```text
CHƯA ĐĂNG KÝ
CHỜ DUYỆT
ĐÃ DUYỆT
CẦN CHECK-IN
ĐÃ CHECK-IN
CHỜ XẾP TRẬN
SẮP THI ĐẤU
READY CHECK
ĐANG THI ĐẤU
CHỜ VÒNG TIẾP
ĐÃ QUALIFY
BỊ LOẠI
DISQUALIFIED
CHAMPION
```

Card phải có:
```text
status
next action
next match
deadline
```

---

# PHẦN D — REGISTRATION

## BƯỚC 5

Khi user bấm `ĐĂNG KÝ`, mở form đăng ký.

Cho phép chọn:
```text
Tôi tự chơi
Bot của tôi
```

Nhưng phải tuân theo tournament policy:
```text
HUMAN_ONLY
BOT_ONLY
HUMAN_OR_BOT
```

## BƯỚC 6 — Bot Selection

Hiển thị bot user sở hữu:
```text
AlphaBot v1.2     READY
GreedyBot v3.0    READY
TestBot v1.0      ERROR
```

Bot không READY phải disabled.

## BƯỚC 7 — Rule confirmation

Trước submit hiển thị:
```text
Format
RuleSet
Turn Time
Best-of
Check-in requirement
Start Time
Forfeit policy
```

Checkbox:
```text
Tôi đã đọc và đồng ý với luật giải.
```

---

# PHẦN E — REGISTRATION STATUS

## BƯỚC 8

Sau submit:
```text
ĐÃ GỬI ĐĂNG KÝ
Trạng thái: CHỜ DUYỆT
```

Cho phép hủy nếu policy cho phép.

Nếu bị từ chối:
```text
ĐĂNG KÝ BỊ TỪ CHỐI
Reason: ...
```

---

# PHẦN F — CHECK-IN

## BƯỚC 9

Khi check-in mở:
```text
CHECK-IN ĐÃ MỞ
Còn: 14:52
[ CHECK-IN NGAY ]
```

Timer chỉ để hiển thị. Server time là source of truth.

## BƯỚC 10

Sau check-in:
```text
✓ ĐÃ CHECK-IN
Bạn đã sẵn sàng tham gia giải.
```

Nếu bỏ lỡ:
```text
BẠN ĐÃ BỎ LỠ CHECK-IN
```

Server xử lý no-show theo policy.

---

# PHẦN G — TOURNAMENT LOBBY

## BƯỚC 11

Tạo `tournament-lobby.html`.

Layout:
```text
HUST RPS CHAMPIONSHIP
Group Stage — Round 2

MY STATUS
✓ CHECKED IN
Rank #4
6 pts

NEXT MATCH
vs GreedyBot
10:30
starts in 08:21
[ READY ]

LIVE NOW
FastBot vs TestBot [WATCH]

STANDINGS / BRACKET
```

Tournament Lobby là màn hình chính khi giải đang diễn ra.

---

# PHẦN H — READY CHECK

## BƯỚC 12

Trước trận, server phát trạng thái:
```text
MATCH_READY_REQUIRED
```

Client hiển thị:
```text
TRẬN ĐẤU SẮP BẮT ĐẦU

AlphaBot
vs
GreedyBot

Bạn có 30 giây để xác nhận.

[ SẴN SÀNG ]
```

Server quyết định hết giờ/no-show, không dùng client timer làm nguồn thật.

---

# PHẦN I — AUTO MATCH ENTRY

## BƯỚC 13

Khi hai phía ready:
```text
MATCH_ASSIGNED
```

Client hiển thị countdown ngắn rồi chuyển:
```text
game.html?matchId=...
```

Không yêu cầu nhập mã phòng.

Tournament phải tự tạo/assign Match qua application/service layer hiện có.

---

# PHẦN J — TOURNAMENT MATCH CONTEXT

## BƯỚC 14

Trong game screen, nếu là tournament match phải hiển thị:
```text
Tên giải
Stage
Round
BO3 / BO5
Game hiện tại
Series score
```

Ví dụ:
```text
HUST RPS CHAMPIONSHIP
Quarterfinal
BO3 — Game 2/3

AlphaBot 1 — 0 GreedyBot
```

## BƯỚC 15

Không hiện nút `Rematch` của game thường.

Sau game:
```text
QUAY LẠI GIẢI
```

---

# PHẦN K — SERIES FLOW

## BƯỚC 16

BO3:
```text
Game 1
↓
Series score
↓
Game 2
↓
nếu 1-1 → Game 3
↓
Series winner
```

BO5 tương tự.

Không tạo SeriesGameEngine; mỗi game vẫn là Match hiện tại.

## BƯỚC 17

Giữa các game:
```text
GAME 1 FINISHED
AlphaBot wins
Series: 1–0

NEXT GAME
```

Có thể auto-start sau delay hoặc ready check theo policy.

---

# PHẦN L — ROUND PROGRESSION

## BƯỚC 18

Nếu thắng knockout:
```text
✓ QUALIFIED
Bạn đã vào SEMIFINAL
```

Nếu thua:
```text
BẠN ĐÃ BỊ LOẠI
Final placement: Top 8
```

CTA:
```text
XEM BRACKET
XEM LIVE MATCHES
```

---

# PHẦN M — GROUP STAGE UX

## BƯỚC 19

Hiển thị bảng group:
```text
GROUP B

1 AlphaBot     7 pts
2 GreedyBot    6 pts
3 FastBot      3 pts
4 RandomBot    1 pt
```

Highlight qualification zone:
```text
Top 2 advance
```

---

# PHẦN N — SWISS UX

## BƯỚC 20

Swiss view:
```text
ROUND 3 / 5

Score: 2-0
Current Rank: #4
Buchholz: 8
```

History:
```text
Round 1 — FastBot — WIN
Round 2 — AlphaBot — WIN
Round 3 — GreedyBot — NEXT
```

---

# PHẦN O — BRACKET UI

## BƯỚC 21

Bracket hỗ trợ:
```text
zoom
horizontal scroll
focus current participant
```

Nút:
```text
[ TÌM TÔI TRONG BRACKET ]
```

Match states:
```text
PENDING
READY
LIVE
FINISHED
FORFEIT
ERROR
```

---

# PHẦN P — SCHEDULE

## BƯỚC 22

Tab `LỊCH THI ĐẤU`.

Views:
```text
Tất cả
Trận của tôi
Theo vòng
Theo thời gian
```

Next match card:
```text
NEXT MATCH
vs TacticalBot
10:30
starts in 08:21
```

---

# PHẦN Q — LIVE MATCH CENTER

## BƯỚC 23

Tab `LIVE`.

Card:
```text
AlphaBot
vs
GreedyBot

Turn 73
BO3
1–1

[ WATCH ]
```

Spectator tái sử dụng board/game renderer hiện có.

---

# PHẦN R — NOTIFICATIONS

## BƯỚC 24

User cần nhận:
```text
Registration approved
Registration rejected
Check-in opened
Check-in ending soon
Opponent assigned
Ready check opened
Match starting
Match completed
Qualified
Eliminated
Tournament finished
```

Ưu tiên:
```text
WebSocket
+
in-app toast
+
notification center
```

---

# PHẦN S — NOTIFICATION CENTER

## BƯỚC 25

Navbar có:
```text
🔔
```

Dropdown:
```text
GreedyBot là đối thủ tiếp theo.
Ready check bắt đầu.
Bạn đã vào Semifinal.
```

State:
```text
READ
UNREAD
```

Có `Mark all as read`.

---

# PHẦN T — HOME INTEGRATION

## BƯỚC 26

Nếu user đang tham gia giải LIVE, Home chỉ thêm compact banner:
```text
GIẢI ĐẤU ĐANG DIỄN RA
HUST Championship

Next match: 10:30

[ VÀO GIẢI ]
```

Không biến Home thành dashboard lớn.

---

# PHẦN U — MY TOURNAMENTS

## BƯỚC 27

Account page thêm:
```text
GIẢI ĐẤU CỦA TÔI
```

Sections:
```text
Đang tham gia
Sắp tới
Đã kết thúc
```

Mỗi item:
```text
name
status
placement
controller/bot
date
```

---

# PHẦN V — TOURNAMENT RESULT

## BƯỚC 28

Khi giải kết thúc:
```text
TOURNAMENT COMPLETE
```

Nếu champion:
```text
🏆 CHAMPION
Bạn đã vô địch!
```

Nếu không:
```text
Final Placement: #5
```

Summary:
```text
Matches Played
Wins
Draws
Losses
Series Wins
Average Turns
Bot Errors
```

---

# PHẦN W — PODIUM

## BƯỚC 29

Hiển thị:
```text
          🥇
       AlphaBot

🥈 GreedyBot    🥉 FastBot
```

Có owner/team nếu áp dụng.

---

# PHẦN X — TEAM / SCHOOL TOURNAMENT UX

## BƯỚC 30 — Captain Dashboard

Hiển thị:
```text
TEAM: HUST A
Roster
Lineup
Next Fixture
Team Score
Standings
```

Captain có:
```text
Submit Lineup
Change Lineup trước lock
View Team Matches
```

## BƯỚC 31 — Member View

```text
Your Slot: A2
Opponent: B2
Match Time: 10:00
```

## BƯỚC 32 — Fixture View

```text
HUST A          2
       VS
HUST B          1

A1  WIN   B1
A2  LOSS  B2
A3  WIN   B3
```

---

# PHẦN Y — DISPUTE UX

## BƯỚC 33

Trong dispute window, Match/Series result có:
```text
[ BÁO CÁO SỰ CỐ ]
```

Form:
```text
Reason
Description
Related Match
[ SUBMIT ]
```

Status:
```text
OPEN
UNDER_REVIEW
RESOLVED
REJECTED
```

User không được sửa result trực tiếp.

---

# PHẦN Z — WITHDRAW / FORFEIT

## BƯỚC 34

Participant có:
```text
RÚT KHỎI GIẢI
```

Phải confirm mạnh và hiển thị consequence:
```text
Bạn có thể bị xử thua các trận còn lại.
```

---

# PHẦN AA — RULES PAGE

## BƯỚC 35

Mỗi giải có tab `LUẬT`.

Hiển thị snapshot:
```text
Board
Piece quota
Turn time
Max turns
Format
Best-of
Scoring
Tie-break
Forfeit policy
Check-in policy
```

Sau tournament start không lấy config động mới thay cho snapshot.

---

# PHẦN AB — API BỔ SUNG

Chỉ thêm nếu chưa có endpoint tương đương:

```text
GET    /api/tournaments/discover
GET    /api/tournaments/{id}/me
POST   /api/tournaments/{id}/registrations
DELETE /api/tournaments/{id}/registrations/me
POST   /api/tournaments/{id}/check-in
POST   /api/tournaments/{id}/ready
GET    /api/tournaments/{id}/schedule/me
GET    /api/tournaments/{id}/matches/live
GET    /api/tournaments/{id}/notifications
POST   /api/tournaments/{id}/notifications/read
GET    /api/account/tournaments
```

Không duplicate API hiện tại nếu đã đủ.

---

# PHẦN AC — WEBSOCKET EVENTS

Chuẩn hóa participant events:

```text
TOURNAMENT_STATE
REGISTRATION_UPDATED
CHECK_IN_OPENED
CHECK_IN_CONFIRMED
ROUND_STARTED
OPPONENT_ASSIGNED
MATCH_READY_REQUIRED
MATCH_ASSIGNED
MATCH_STARTED
MATCH_FINISHED
SERIES_UPDATED
STANDINGS_UPDATED
BRACKET_UPDATED
QUALIFIED
ELIMINATED
TOURNAMENT_FINISHED
TOURNAMENT_NOTIFICATION
```

---

# PHẦN AD — CLIENT STATE

## BƯỚC 36

Tạo `TournamentClientState`.

Fields:
```text
tournamentId
status
participantStatus
registration
checkIn
currentStage
currentRound
nextMatch
currentMatch
series
standings
bracket
notifications
```

DOM không phải source of truth.

---

# PHẦN AE — SERVER TIME SYNC

## BƯỚC 37

Vì có:
```text
registration deadline
check-in deadline
ready deadline
scheduled match
```

client phải dùng server timestamp/offset.

Không dùng local clock làm nguồn quyết định.

---

# PHẦN AF — NO-SHOW POLICY

## BƯỚC 38

Server xử lý:
```text
check-in timeout
ready timeout
match no-show
```

Client chỉ render kết quả.

---

# PHẦN AG — MATCH IDEMPOTENCY

## BƯỚC 39

Không được tạo duplicate tournament match khi:
```text
scheduler chạy lại
retry HTTP
WebSocket reconnect
server recovery
```

Dùng tournamentMatchId/seriesGameId ổn định và unique constraint phù hợp.

---

# PHẦN AH — RECONNECT

## BƯỚC 40

Reload ở:
```text
Tournament Lobby
Ready Check
Tournament Match
Series transition
```

phải quay về đúng trạng thái.

Nếu match đang chạy:
```text
resume game
```

Nếu match đã xong:
```text
return tournament flow
```

---

# PHẦN AI — PERFORMANCE

## BƯỚC 41

Tournament lớn không tải tất cả dữ liệu một lần.

Dùng:
```text
pagination
lazy load
stage filter
round filter
```

Replay chỉ tải khi user mở.

---

# PHẦN AJ — MOBILE

## BƯỚC 42

Mobile ưu tiên:
```text
My Status
Next Match
Ready
Standings
Schedule
Notifications
```

Bracket cho horizontal scroll.

---

# PHẦN AK — VISUAL DESIGN

Tournament mode dùng fantasy theme hiện tại nhưng giảm trang trí để dễ đọc dữ liệu.

Màu:
```text
Blue   = primary
Gold   = tournament/prestige
Green  = ready/qualified
Orange = waiting/check-in
Red    = eliminated/error
```

Status luôn có text/icon, không chỉ màu.

---

# PHẦN AL — TOURNAMENT HUB UI GỢI Ý

```text
┌──────────────────────────────────────────────────────────┐
│ GIẢI ĐẤU                                      🔔 Profile │
├──────────────────────────────────────────────────────────┤
│ [Đang diễn ra] [Mở đăng ký] [Sắp tới] [Đã tham gia]     │
│                                                          │
│ HUST RPS CHAMPIONSHIP                                    │
│ Group Stage → Knockout                                   │
│ 24 / 32 Participants                                     │
│ Registration closes in 02:13:42                          │
│                                             [ĐĂNG KÝ]     │
└──────────────────────────────────────────────────────────┘
```

---

# PHẦN AM — TOURNAMENT LOBBY UI GỢI Ý

```text
┌──────────────────────────────────────────────────────────┐
│ HUST RPS CHAMPIONSHIP                         ● LIVE      │
│ Group Stage — Round 2                                    │
├──────────────────────┬───────────────────────────────────┤
│ MY STATUS            │ NEXT MATCH                        │
│ ✓ CHECKED IN         │ AlphaBot vs GreedyBot             │
│ Rank #4              │ 10:30                             │
│ 6 pts                │ starts in 08:21                   │
│                      │ [ READY ]                         │
├──────────────────────┼───────────────────────────────────┤
│ STANDINGS            │ LIVE NOW                          │
│ 1 AlphaBot  7        │ FastBot vs TestBot [WATCH]       │
│ 2 GreedyBot 6        │ BotX vs BotY       [WATCH]       │
└──────────────────────┴───────────────────────────────────┘
```

---

# PHẦN AN — BACKEND TESTS

Phải thêm tối thiểu:

```text
TournamentDiscoveryTest
ParticipantTournamentStateTest
RegistrationFlowTest
CheckInFlowTest
ReadyCheckTest
AutoMatchAssignmentTest
TournamentMatchIdempotencyTest
TournamentReconnectTest
SeriesProgressionTest
GroupQualificationTest
SwissParticipantViewTest
TournamentNotificationTest
NoShowPolicyTest
TournamentResultSummaryTest
TeamParticipantViewTest
```

---

# PHẦN AO — CLIENT TESTS

```text
tournament-hub.test.js
tournament-detail.test.js
registration-flow.test.js
check-in.test.js
ready-check.test.js
tournament-lobby.test.js
next-match.test.js
series-progress.test.js
bracket-focus.test.js
standings.test.js
notifications.test.js
tournament-reconnect.test.js
```

---

# PHẦN AP — END-TO-END 1: CÁ NHÂN

```text
1. Login.
2. Home → Giải đấu.
3. Xem giải mở đăng ký.
4. Mở detail.
5. Chọn AlphaBot hoặc Tôi tự chơi.
6. Đăng ký.
7. Admin approve.
8. Nhận notification.
9. Check-in.
10. Tournament bắt đầu.
11. Vào Tournament Lobby.
12. Xem next opponent.
13. Ready Check.
14. Bấm Sẵn sàng.
15. Tự động vào Match.
16. Match kết thúc.
17. Quay lại Tournament.
18. Standings cập nhật.
19. Qualify hoặc bị loại.
20. Tiếp tục vòng sau.
21. Tournament kết thúc.
22. Xem placement/result/replay.
```

---

# PHẦN AQ — END-TO-END 2: BO3 KNOCKOUT

```text
1. Quarterfinal assigned.
2. Ready.
3. Game 1.
4. Series 1-0.
5. Game 2.
6. Series 1-1.
7. Game 3.
8. Series 2-1.
9. QUALIFIED.
10. Bracket cập nhật.
11. Next Match = Semifinal.
```

---

# PHẦN AR — END-TO-END 3: TEAM / SCHOOL

```text
1. Captain login.
2. Vào Tournament.
3. Xem roster.
4. Submit lineup.
5. Lineup lock.
6. Fixture assigned.
7. A1 nhận notification.
8. A1 ready.
9. A1 vs B1.
10. A2 vs B2.
11. A3 vs B3.
12. Team score cập nhật.
13. Standings cập nhật.
14. Captain xem kết quả fixture.
```

---

# DEFINITION OF DONE — CHẶNG 8

Chặng 8 hoàn thành khi:

```text
Tournament là user-facing mode hoàn chỉnh

Tournament Hub hoạt động
Discovery hoạt động
Tournament Detail hoạt động

Registration hoạt động
Human/Bot selection đúng policy
Registration status rõ

Check-in hoạt động
Tournament Lobby hoạt động

My Tournament Status hoạt động
Next Match hoạt động

Ready Check hoạt động
Auto Match Entry hoạt động
Không cần Match Code

Tournament match hiển thị stage/round/series
BO3/BO5 flow đúng

Sau Match quay lại Tournament đúng
Standings cập nhật
Bracket cập nhật

Qualification / Elimination rõ ràng
Notifications hoạt động

Reconnect đúng trạng thái

Team Tournament participant UX hoạt động
Dispute UX hoạt động

Tournament Result hoạt động

Mobile usable

Không sửa GameEngine
```

---

# CÁCH GIAO CHO AI AGENT

Không implement toàn Chặng 8 một lượt.

Chia:

```text
Nhóm 1
Tournament Hub + Discovery

Nhóm 2
Tournament Detail + My Status

Nhóm 3
Registration + Human/Bot Selection

Nhóm 4
Check-in + Tournament Lobby

Nhóm 5
Ready Check + Auto Match Entry

Nhóm 6
Tournament Match Context + Series Flow

Nhóm 7
Bracket + Standings + Schedule

Nhóm 8
Notifications + Reconnect

Nhóm 9
Team Tournament UX + Dispute UX

Nhóm 10
Responsive + E2E + Regression
```

Sau MỖI nhóm:

```text
1. Chạy toàn bộ backend tests.
2. Chạy toàn bộ client tests.
3. Chạy migration tests nếu có DB change.
4. Build bootJar.
5. Chạy integration test liên quan.
6. Báo pass / fail / skipped.
7. Liệt kê file thêm/sửa.
8. Kiểm tra GameEngine / RuleSet có bị sửa không.
9. Nếu sửa core phải giải thích.
10. Không tự chuyển nhóm tiếp theo.
11. Chờ review.
```

---

# ƯU TIÊN TRIỂN KHAI

```text
Tournament Hub
    ↓
Registration
    ↓
Check-in
    ↓
Tournament Lobby
    ↓
Ready Check
    ↓
Auto Match Entry
    ↓
Series Flow
    ↓
Standings / Bracket
    ↓
Notifications
    ↓
Team UX
```

Mục tiêu Chặng 8:

> Biến “Giải đấu” thành một chế độ chơi hoàn chỉnh cho người dùng, nơi participant có thể đi xuyên suốt toàn bộ giải mà không cần thao tác kỹ thuật, nhập mã trận thủ công hoặc tự theo dõi trạng thái bằng nhiều màn hình rời rạc.
