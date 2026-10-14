# PROMPT AI AGENT — CHẶNG 7
# GIAO DIỆN ADMIN: DASHBOARD, QUẢN TRỊ USER, BOT, GIẢI ĐẤU, HỆ THỐNG & AUDIT

## 0. Bối cảnh hiện tại

Project:

```text
RPS Battle — Oẳn Tù Tì Chiến Thuật 9×9
```

Đến hết Chặng 6, hệ thống dự kiến đã có:

```text
Java 21
Spring Boot
PostgreSQL
JPA
Flyway
Spring Security

User Account
Role USER / ADMIN

Bot ownership
Bot upload
Docker sandbox

Match history
Replay persistence

Tournament:
- Registration
- Approval
- Check-in
- Seeding
- Round Robin
- Single Elimination
- Multi-stage
- Group Stage
- Swiss
- BO1 / BO3 / BO5
- Team Tournament
- School vs School
- Scheduling
- Referee
- Dispute
- Result Confirmation
- Bracket
- Standings
- Spectator
- Replay
- Audit
- Recovery
- Tournament History
```

Chặng 7 tập trung vào:

> Xây dựng giao diện quản trị riêng cho user có vai trò ADMIN, giúp vận hành toàn hệ thống mà không phải thao tác bằng API thủ công.

Không thêm gameplay mới.

Không viết lại:

```text
GameEngine
RuleEngine
Match Engine
Bot Protocol
Tournament Core
```

---

# 1. Mục tiêu cuối Chặng 7

Sau chặng này, ADMIN phải có một giao diện riêng để:

```text
Xem dashboard hệ thống

Quản lý user

Khóa / mở khóa account

Xem match đang chạy

Xem tournament đang chạy

Quản lý tournament

Duyệt / xử lý tournament sự cố

Quản lý bot

Xem bot lỗi

Xem Docker / worker trạng thái

Xem audit log

Xem dispute

Xem report

Theo dõi queue

Xem system health

Xem metrics cơ bản

Truy cập replay

Điều hướng tới match live / tournament live

Thực hiện các admin action có xác nhận
```

---

# 2. Nguyên tắc UX cho Admin

Admin UI không được giống Home/Game UI dành cho người chơi.

Phải là:

```text
Dashboard
+
Sidebar
+
Topbar
+
Content Area
```

Ưu tiên:

```text
rõ ràng
nhiều dữ liệu
dễ quét
ít animation
thao tác nhanh
```

Không ưu tiên:

```text
trang trí game quá nhiều
hero banner lớn
button cỡ lớn không cần thiết
```

Theme vẫn giữ nhận diện RPS Battle:

```text
navy / blue
gold accent
red warning
green success
```

nhưng giao diện admin phải gọn, chuyên nghiệp hơn.

---

# 3. Admin Layout chuẩn

Tạo layout:

```text
┌──────────────────────────────────────────────────────────────┐
│ LOGO | RPS BATTLE ADMIN                      [Admin Avatar]   │
├───────────────┬──────────────────────────────────────────────┤
│ Dashboard     │                                              │
│ Users         │                                              │
│ Bots          │                CONTENT                       │
│ Matches       │                                              │
│ Tournaments   │                                              │
│ Disputes      │                                              │
│ Replays       │                                              │
│ Audit Logs    │                                              │
│ System        │                                              │
│               │                                              │
│ Back to Game  │                                              │
└───────────────┴──────────────────────────────────────────────┘
```

---

# 4. Route / Page structure

Tạo:

```text
admin/
├── index.html
├── users.html
├── user-detail.html
├── bots.html
├── bot-detail.html
├── matches.html
├── match-detail.html
├── tournaments.html
├── tournament-detail.html
├── disputes.html
├── dispute-detail.html
├── replays.html
├── audit.html
└── system.html
```

Hoặc nếu frontend hiện tại dùng page switching:

```text
admin.html
```

với module-based views.

Không bắt buộc SPA framework.

Tiếp tục dùng:

```text
HTML
CSS
JavaScript ES Modules
```

---

# 5. Cấu trúc client đề xuất

```text
client/
├── admin/
│   ├── index.html
│   ├── users.html
│   ├── bots.html
│   ├── matches.html
│   ├── tournaments.html
│   ├── disputes.html
│   ├── audit.html
│   └── system.html
│
├── css/
│   ├── admin.css
│   ├── admin-table.css
│   ├── admin-form.css
│   └── admin-responsive.css
│
└── js/
    └── admin/
        ├── admin-shell.js
        ├── admin-api.js
        ├── admin-auth.js
        ├── dashboard.js
        ├── users.js
        ├── bots.js
        ├── matches.js
        ├── tournaments.js
        ├── disputes.js
        ├── audit.js
        ├── system.js
        └── components/
            ├── data-table.js
            ├── stat-card.js
            ├── status-badge.js
            ├── confirm-dialog.js
            ├── pagination.js
            └── filters.js
```

---

# 6. Admin access guard

## BƯỚC 1

Mọi admin page phải kiểm tra:

```text
authenticated
role == ADMIN
```

Nếu không:

```text
redirect / login
```

hoặc:

```text
403 Forbidden
```

Không chỉ ẩn button ở client.

Server vẫn phải kiểm tra ADMIN role.

---

# 7. Admin Topbar

Topbar có:

```text
RPS BATTLE ADMIN

Search optional

System Status

Notifications optional

Admin Avatar
```

Dropdown:

```text
Profile
Back to Game
Logout
```

Không cần:

```text
Play Game
How To Play
```

trong admin shell.

---

# 8. Sidebar

Sidebar:

```text
Dashboard

Users

Bots

Matches

Tournaments

Disputes

Replays

Audit Logs

System
```

Highlight route hiện tại.

Cho phép collapse trên desktop.

Mobile:

```text
hamburger
```

---

# PHẦN A — ADMIN DASHBOARD

## BƯỚC 2 — Dashboard summary

Trang:

```text
/admin/index.html
```

Hiển thị stat cards:

```text
Total Users

Active Users

Uploaded Bots

Active Matches

Running Tournaments

Open Disputes

Queued Matches

System Errors
```

---

## BƯỚC 3 — Realtime cards

Các card realtime:

```text
Active Matches

Running Tournaments

Worker Load

Docker Bot Containers

WebSocket Sessions
```

---

## BƯỚC 4 — Recent Activity

Panel:

```text
Recent Admin / System Activity
```

Ví dụ:

```text
User dat123 uploaded AlphaBot

Tournament HUST Cup started

Match M123 finished

Bot BOT22 timeout

Admin disabled user U145
```

---

## BƯỚC 5 — Quick Actions

Cho phép:

```text
View Running Tournaments

View Open Disputes

View Failed Bots

Open System Health
```

Không thêm destructive quick action trên Dashboard.

---

# PHẦN B — USER MANAGEMENT

## BƯỚC 6 — Users List

Trang:

```text
/admin/users.html
```

Columns:

```text
User

Username

Email

Role

Status

Matches

Bots

Created At

Last Login

Actions
```

---

## BƯỚC 7 — Search & Filter

Search:

```text
username
email
displayName
```

Filter:

```text
Role
Status
Created date
```

---

## BƯỚC 8 — User status

Badge:

```text
ACTIVE

DISABLED
```

---

## BƯỚC 9 — User Detail

Trang:

```text
/admin/user-detail.html?id=...
```

Hiển thị:

```text
Account Info

Stats

Recent Matches

Bots

Tournaments

Security / Status

Audit History
```

---

## BƯỚC 10 — Disable User

Button:

```text
Disable Account
```

Phải có confirm:

```text
Bạn có chắc muốn vô hiệu hóa tài khoản này?
```

Yêu cầu nhập reason.

Không disable silent.

---

## BƯỚC 11 — Enable User

Cho user DISABLED:

```text
Enable Account
```

---

## BƯỚC 12 — Role change

Nếu hệ thống cho phép:

```text
USER → ADMIN
ADMIN → USER
```

thì phải:

```text
confirm mạnh
audit
```

Không cho admin tự hạ role của chính mình nếu đó là admin cuối cùng, nếu có logic tương ứng.

Nếu chưa có backend support thì không làm UI giả.

---

# PHẦN C — BOT MANAGEMENT

## BƯỚC 13 — Bots List

Columns:

```text
Bot Name

Owner

Language

Status

Version

Created At

Last Used

Failures

Actions
```

---

## BƯỚC 14 — Bot Filters

Filter:

```text
Language

Status

Owner

Build State

Has Errors
```

---

## BƯỚC 15 — Bot Detail

Hiển thị:

```text
Metadata

Owner

Current Version

Artifact Hash

Language

Build/Test Status

Recent Matches

Recent Failures

Execution Metrics

Storage Info
```

---

## BƯỚC 16 — Admin bot actions

Cho phép:

```text
Disable Bot

Enable Bot

View Logs

View Match History

Delete/Archive nếu policy cho phép
```

Không trực tiếp sửa source code user.

---

## BƯỚC 17 — Failed Bot View

Có filter nhanh:

```text
TIMEOUT

CRASH

INVALID_OUTPUT

ILLEGAL_ACTION

BUILD_ERROR
```

---

# PHẦN D — MATCH MANAGEMENT

## BƯỚC 18 — Matches List

Columns:

```text
Match ID

Mode

P1

P2

Status

Turn

Started At

Duration

Tournament

Actions
```

---

## BƯỚC 19 — Match Filters

```text
RUNNING
FINISHED
FAILED
ERROR

Tournament
User
Bot
Date
```

---

## BƯỚC 20 — Match Detail

Hiển thị:

```text
Match Metadata

Players / Bots

Current State

Timeline

Result

Replay

Bot Errors

Execution Info
```

---

## BƯỚC 21 — Live Match

Nếu RUNNING:

```text
[ WATCH LIVE ]
```

Không cho admin điều khiển board.

---

## BƯỚC 22 — Replay

Nếu FINISHED:

```text
[ OPEN REPLAY ]
```

---

## BƯỚC 23 — Match Intervention

Không cho admin tùy ý sửa trạng thái game.

Chỉ các action thuộc tournament/admin workflow:

```text
Mark for Review

Retry if ERROR

Cancel if infrastructure failure policy allows
```

---

# PHẦN E — TOURNAMENT MANAGEMENT

## BƯỚC 24 — Tournament List

Columns:

```text
Tournament

Owner

Format

Status

Participants

Stage

Progress

Start Time

Actions
```

---

## BƯỚC 25 — Tournament Filters

```text
DRAFT

REGISTRATION

CHECK_IN

READY

RUNNING

PAUSED

FINISHED

CANCELLED
```

---

## BƯỚC 26 — Tournament Detail Admin

Trang detail có tabs:

```text
Overview

Participants

Stages

Matches

Bracket

Standings

Schedule

Disputes

Audit

Admin Controls
```

---

## BƯỚC 27 — Admin Controls

Theo status cho phép:

```text
Open Registration

Close Registration

Start Check-in

Start Tournament

Pause

Resume

Cancel

Force Finish nếu backend support

Archive
```

Không hiện action không hợp lệ theo state machine.

---

## BƯỚC 28 — Tournament health

Hiển thị:

```text
Running Matches

Queued Matches

Failed Matches

Open Disputes

Worker Allocation

Bot Failures
```

---

# PHẦN F — PARTICIPANT MANAGEMENT

## BƯỚC 29

Admin tournament view phải quản lý:

```text
Registrations

Approved

Rejected

Checked-in

No-show

Disqualified
```

---

## BƯỚC 30

Actions:

```text
Approve

Reject

Disqualify

Restore nếu policy cho phép

Inspect Bot

Inspect User
```

---

# PHẦN G — DISPUTE MANAGEMENT

## BƯỚC 31 — Dispute List

Columns:

```text
ID

Tournament

Match / Series

Created By

Reason

Status

Created At

Assigned Referee

Actions
```

---

## BƯỚC 32 — Dispute Filters

```text
OPEN

UNDER_REVIEW

RESOLVED

REJECTED
```

---

## BƯỚC 33 — Dispute Detail

Hiển thị:

```text
Description

Match Result

Replay

Bot Logs

Timeouts

Execution Errors

Audit History

Referee Notes
```

---

## BƯỚC 34 — Resolve Dispute

Admin actions:

```text
Uphold Result

Retry Match

Override Result

Disqualify Participant

Reject Dispute
```

Mọi action:

```text
reason required
audit required
```

---

# PHẦN H — REPLAY MANAGEMENT

## BƯỚC 35

Admin replay page:

```text
Match ID

Tournament

Participants

Created At

Valid

Event Count

Actions
```

---

## BƯỚC 36

Replay state:

```text
VALID

INVALID

MISSING
```

---

## BƯỚC 37

Admin có:

```text
Open Replay

Validate Replay

View Metadata
```

Không chỉnh replay thủ công.

---

# PHẦN I — AUDIT LOG

## BƯỚC 38 — Audit Page

Columns:

```text
Timestamp

Actor

Role

Action

Resource Type

Resource ID

Result

IP optional

Details
```

---

## BƯỚC 39 — Filters

```text
Actor

Action

Resource

Date Range

Success / Failure
```

---

## BƯỚC 40

Audit log phải read-only.

Không có:

```text
Delete Audit
Edit Audit
```

trên UI admin bình thường.

---

# PHẦN J — SYSTEM PAGE

## BƯỚC 41 — System Health

Trang:

```text
/admin/system.html
```

Cards:

```text
Application

PostgreSQL

Docker

Worker

Queue

WebSocket

Storage
```

Status:

```text
UP

DEGRADED

DOWN
```

---

## BƯỚC 42 — Runtime metrics

Hiển thị:

```text
Active Matches

Queued Matches

Running Tournaments

Online Users

WebSocket Sessions

Managed Docker Containers

CPU optional

Memory optional
```

---

## BƯỚC 43 — Worker status

Table:

```text
Worker ID

Status

Capacity

Running Jobs

Last Heartbeat
```

---

## BƯỚC 44 — Queue

Table:

```text
Job

Tournament

Match

Queued At

Status
```

---

# PHẦN K — ALERTS

## BƯỚC 45

Dashboard cảnh báo khi:

```text
Docker unavailable

Database degraded

Queue too long

Worker offline

High bot failure rate

Tournament stuck

Replay invalid
```

---

## BƯỚC 46

Alert severity:

```text
INFO

WARNING

CRITICAL
```

---

# PHẦN L — ADMIN NOTIFICATIONS

## BƯỚC 47

Topbar notification center:

```text
Open Dispute

Tournament Error

Worker Offline

Bot Crash Spike

Database Warning
```

Có thể chỉ lưu in-memory/websocket ở phiên bản đầu nếu backend chưa có notification persistence.

---

# PHẦN M — TABLE COMPONENT

## BƯỚC 48

Tạo generic:

```text
DataTable
```

Hỗ trợ:

```text
sorting

pagination

search

filters

empty state

loading state

error state
```

Không viết 8 bảng với logic copy-paste.

---

# PHẦN N — STATUS BADGE COMPONENT

## BƯỚC 49

Tạo component:

```text
StatusBadge
```

Dùng cho:

```text
ACTIVE

DISABLED

RUNNING

PAUSED

FAILED

READY

OPEN

RESOLVED
```

Badge phải có:

```text
text
icon
color
```

Không chỉ dựa vào màu.

---

# PHẦN O — CONFIRM DIALOG

## BƯỚC 50

Mọi destructive action phải qua:

```text
ConfirmDialog
```

Ví dụ:

```text
Disable Account

Cancel Tournament

Disqualify Participant

Override Result
```

---

## BƯỚC 51

Action nghiêm trọng yêu cầu reason:

```text
Reason *
```

---

# PHẦN P — ADMIN API CLIENT

## BƯỚC 52

Tạo:

```text
admin-api.js
```

Không gọi fetch trực tiếp rải rác.

Có:

```text
getUsers()

getUser(id)

disableUser(id, reason)

getBots()

getMatches()

getTournaments()

getDisputes()

resolveDispute()

getAudit()

getSystemHealth()
```

---

# PHẦN Q — ERROR HANDLING

## BƯỚC 53

Không hiện raw stack trace.

Các error UI:

```text
403
Bạn không có quyền thực hiện thao tác này.

404
Không tìm thấy dữ liệu.

409
Trạng thái hiện tại không cho phép thao tác.

429
Bạn thao tác quá nhanh.

500
Hệ thống gặp lỗi.
```

---

# PHẦN R — LOADING / EMPTY STATE

## BƯỚC 54

Mọi page phải có:

```text
loading

empty

error

loaded
```

Không để bảng trắng không giải thích.

---

# PHẦN S — RESPONSIVE

## BƯỚC 55

Desktop là ưu tiên:

```text
1440×900
1920×1080
```

Tablet:

```text
sidebar collapsible
```

Mobile:

```text
minimum usable
```

Admin không cần mobile-perfect như player UI.

---

# PHẦN T — ACCESSIBILITY

## BƯỚC 56

Phải có:

```text
keyboard navigation

visible focus

button labels rõ

table headers đúng

ARIA cho dialog nếu cần

contrast đủ
```

---

# PHẦN U — VISUAL DESIGN

## BƯỚC 57

Admin theme:

```text
Background:
#0f172a

Surface:
#172033

Border:
#334155

Gold accent:
#f2c35b

Primary blue:
#2f80ed

Danger:
#e74c3c

Success:
#27ae60

Text:
#f8fafc
```

Có thể điều chỉnh theo CSS variables hiện tại.

---

## BƯỚC 58

Không dùng background castle lớn trong content admin.

Có thể dùng:

```text
subtle texture
logo nhỏ
gold accent
```

để giữ nhận diện game.

---

# PHẦN V — ADMIN HOME DESIGN

Dashboard gợi ý:

```text
┌─────────────────────────────────────────────────────────────┐
│ SYSTEM OVERVIEW                                             │
├─────────┬─────────┬─────────┬─────────┬────────────────────┤
│ Users   │ Matches │ Tourney │ Bots    │ Open Disputes      │
│ 1,240   │ 18 live │ 3 live  │ 410     │ 4                  │
└─────────┴─────────┴─────────┴─────────┴────────────────────┘

┌────────────────────────────┐ ┌──────────────────────────────┐
│ LIVE TOURNAMENTS           │ │ SYSTEM HEALTH                │
│ HUST Cup      RUNNING      │ │ DB       UP                  │
│ School Cup    PAUSED       │ │ Docker   UP                  │
│                            │ │ Worker   DEGRADED            │
└────────────────────────────┘ └──────────────────────────────┘

┌────────────────────────────┐ ┌──────────────────────────────┐
│ OPEN DISPUTES              │ │ RECENT ACTIVITY              │
└────────────────────────────┘ └──────────────────────────────┘
```

---

# PHẦN W — ADMIN BACKEND ENDPOINTS

Chỉ bổ sung endpoint còn thiếu.

Tối thiểu:

```text
GET /api/admin/dashboard

GET /api/admin/users
GET /api/admin/users/{id}
POST /api/admin/users/{id}/disable
POST /api/admin/users/{id}/enable

GET /api/admin/bots
GET /api/admin/bots/{id}

GET /api/admin/matches
GET /api/admin/matches/{id}

GET /api/admin/tournaments
GET /api/admin/tournaments/{id}

GET /api/admin/disputes
GET /api/admin/disputes/{id}

GET /api/admin/audit

GET /api/admin/system
```

Không duplicate endpoint public nếu đã có endpoint đủ dùng.

---

# PHẦN X — SECURITY

## BƯỚC 59

Mọi endpoint:

```text
/api/admin/**
```

require:

```text
ROLE_ADMIN
```

---

## BƯỚC 60

Không tin role từ client.

Role lấy từ authenticated principal.

---

## BƯỚC 61

Destructive action phải audit:

```text
actor
resource
action
reason
timestamp
result
```

---

# PHẦN Y — WEBSOCKET ADMIN LIVE UPDATES

## BƯỚC 62

Nếu backend hiện có websocket infrastructure, thêm admin subscription:

```text
WATCH_ADMIN_DASHBOARD
```

hoặc sử dụng polling 5–10s nếu đơn giản hơn.

Không bắt buộc WebSocket cho mọi bảng.

---

## BƯỚC 63

Realtime phù hợp cho:

```text
active matches

running tournaments

open disputes

worker state

system alerts
```

---

# PHẦN Z — USER EXPERIENCE

## BƯỚC 64

Admin action success:

```text
toast
```

Ví dụ:

```text
Tài khoản đã được vô hiệu hóa.
```

---

## BƯỚC 65

Admin action fail:

```text
Không thể tạm dừng giải vì giải đã kết thúc.
```

Không dùng:

```text
Error 409
```

một mình.

---

# PHẦN AA — ADMIN PROFILE

## BƯỚC 66

Admin vẫn có profile account bình thường.

Admin panel không thay thế:

```text
account.html
```

---

# PHẦN AB — ADMIN BACK TO GAME

## BƯỚC 67

Sidebar cuối:

```text
← Quay lại game
```

Không khiến admin bị khóa trong dashboard.

---

# PHẦN AC — TEST BACKEND

Phải thêm:

```text
AdminAuthorizationTest

AdminDashboardControllerTest

AdminUserControllerTest

AdminBotControllerTest

AdminMatchControllerTest

AdminTournamentControllerTest

AdminDisputeControllerTest

AdminAuditControllerTest

AdminSystemControllerTest
```

---

# PHẦN AD — TEST CLIENT

Thêm:

```text
admin-auth.test.js

admin-dashboard.test.js

admin-users.test.js

admin-bots.test.js

admin-matches.test.js

admin-tournaments.test.js

admin-disputes.test.js

admin-audit.test.js

admin-system.test.js

admin-components.test.js
```

---

# PHẦN AE — END-TO-END DEMO

Chặng 7 hoàn thành khi demo được:

```text
1. Login bằng USER.

2. Truy cập /admin.

3. Bị 403 / redirect.

4. Login bằng ADMIN.

5. Mở Admin Dashboard.

6. Xem tổng quan hệ thống.

7. Vào Users.

8. Search user.

9. Mở user detail.

10. Disable user với reason.

11. Audit ghi lại action.

12. Vào Bots.

13. Xem bot lỗi.

14. Vào Matches.

15. WATCH một match live.

16. Vào Tournaments.

17. Pause một tournament.

18. Resume lại.

19. Mở Disputes.

20. Resolve dispute.

21. Mở Audit Logs.

22. Thấy đầy đủ admin actions.

23. Mở System.

24. Xem DB/Docker/Worker status.

25. Quay lại game bình thường.
```

---

# DEFINITION OF DONE — CHẶNG 7

Chặng 7 hoàn thành khi:

```text
Admin có giao diện riêng

Role guard đúng

Sidebar / Topbar hoàn chỉnh

Dashboard hoạt động

User management hoạt động

Bot management hoạt động

Match monitoring hoạt động

Tournament management hoạt động

Dispute management hoạt động

Replay access hoạt động

Audit log hoạt động

System health hoạt động

Destructive action có confirm

Action nghiêm trọng có reason

Backend authorization đúng

Frontend error/loading/empty states đầy đủ

Responsive đủ dùng

Không phá giao diện user thường

Không sửa GameEngine
```

---

# CÁCH GIAO CHO AI AGENT

Không implement toàn Chặng 7 trong một lượt.

Chia nhóm:

```text
Nhóm 1
Admin Shell + Auth Guard + Sidebar + Topbar

Nhóm 2
Dashboard

Nhóm 3
Users

Nhóm 4
Bots

Nhóm 5
Matches

Nhóm 6
Tournaments

Nhóm 7
Disputes + Replay

Nhóm 8
Audit + System Health

Nhóm 9
Responsive + Accessibility + Polish

Nhóm 10
Security + Regression + E2E
```

Sau MỖI nhóm:

```text
1. Chạy backend tests.

2. Chạy client tests.

3. Build bootJar.

4. Báo pass/fail/skipped.

5. Liệt kê file thêm/sửa.

6. Kiểm tra quyền ADMIN.

7. Kiểm tra USER không truy cập admin.

8. Kiểm tra GameEngine/RuleSet không bị sửa.

9. Không tự sang nhóm tiếp theo.

10. Chờ review.
```

---

# ƯU TIÊN TRIỂN KHAI

```text
Admin Shell
    ↓
Dashboard
    ↓
Users
    ↓
Tournaments
    ↓
Matches
    ↓
Bots
    ↓
Disputes
    ↓
Audit
    ↓
System Health
    ↓
Polish
```

Mục tiêu Chặng 7:

> Tạo một giao diện quản trị đầy đủ, rõ ràng và an toàn để ADMIN có thể vận hành RPS Battle, giám sát người dùng, bot, trận đấu, giải đấu, tranh chấp và tình trạng hệ thống mà không cần dùng API thủ công.
