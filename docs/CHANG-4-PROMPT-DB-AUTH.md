# PROMPT AI AGENT — CHẶNG 4
# DATABASE, LOGIN, ACCOUNT, PRODUCTION HARDENING & DEPLOYMENT

## 0. Bối cảnh hiện tại

Project:

```text
RPS Battle — Oẳn Tù Tì Chiến Thuật 9×9
```

Stack hiện tại:

```text
Java 21
Spring Boot
Gradle

HTML
CSS
JavaScript ES Modules

REST
WebSocket
Jackson

Docker cho bot upload
```

Hệ thống đã có:

```text
GameEngine
RuleEngine
ClassicRuleSet

Human vs Human
Human vs bot có sẵn

Hai phía trong phòng có thể được điều khiển bởi:
- người thật
- bot upload

Upload bot:
- Python
- C++
- Java source
- Java JAR

Docker sandbox

Placement
Ready / Unready
Move
Combat
Timer server-side
Reconnect
Surrender
Leave room
Result

Tournament
Round Robin
Tournament Execution
Ranking
Spectator
Web Replay
Tournament Result
Champion
```

Mục tiêu Chặng 4:

> Chuyển hệ thống từ bản demo/thi đấu nội bộ thành nền tảng có người dùng thật, tài khoản, dữ liệu bền vững, phân quyền, lịch sử, lưu bot theo account, đồng thời có cấu hình production, worker, monitoring, recovery và deployment rõ ràng.

---

# 1. Mục tiêu cuối Chặng 4

Sau chặng này hệ thống phải có:

```text
PostgreSQL database

User account

Register
Login
Logout

Session / Token authentication

Role USER / ADMIN

Profile

Bot ownership

Tournament ownership

Match history theo account

Ranking / statistics theo account

Persistent bots metadata

Persistent tournaments

Persistent match results

Persistent replay metadata

Authorization

Password hashing

Production configuration

Dockerized deployment

Worker isolation

Bot execution queue

Rate limiting

Health / metrics

Structured logging

Graceful shutdown

Backup / restore

Load testing

Operational documentation
```

Không bắt buộc:

```text
OAuth Google/GitHub
Redis
Kafka
Kubernetes
Microservices
```

Vẫn ưu tiên:

```text
Modular Monolith
```

---

# 2. Kiến trúc tổng thể

Luồng:

```text
Browser
   │
   ├── REST
   └── WebSocket
   │
   ▼
Spring Boot
   │
   ├── Auth
   ├── Account
   ├── Match
   ├── Bot
   ├── Tournament
   ├── Replay
   ├── Ranking
   │
   ▼
Application Services
   │
   ▼
Domain
   │
   ├── GameEngine
   ├── RuleSet
   ├── Match
   └── Tournament
```

Persistence:

```text
Application
   ↓
Repository Interface
   ↓
JPA Adapter
   ↓
PostgreSQL
```

Không để:

```text
GameEngine
↓
JPA
```

Không để:

```text
RuleSet
↓
Spring Security
```

---

# 3. Công nghệ bổ sung

Thêm:

```text
Spring Security
Spring Data JPA
PostgreSQL
Flyway
BCrypt hoặc PasswordEncoder của Spring Security
Spring Boot Actuator
```

Có thể dùng:

```text
HttpOnly Session Cookie
```

hoặc:

```text
JWT
```

Nhưng phải chọn MỘT cơ chế thống nhất.

Khuyến nghị cho project này:

```text
HttpOnly Session Cookie
```

vì client và server cùng host, đơn giản hơn JWT.

Nếu codebase hiện đã dùng token riêng cho seat/match thì token đó vẫn giữ cho quyền điều khiển seat, nhưng không thay thế authentication account.

---

# PHẦN A — DATABASE

# BƯỚC 1 — PostgreSQL

Thêm dependency:

```text
Spring Data JPA
PostgreSQL Driver
Flyway
```

Production:

```text
PostgreSQL
```

Test:

```text
H2 hoặc Testcontainers PostgreSQL
```

Ưu tiên Testcontainers nếu môi trường cho phép.

---

# BƯỚC 2 — application config

Tạo:

```text
application.yml
application-dev.yml
application-test.yml
application-prod.yml
```

Không hard-code:

```text
DB password
DB username
JWT secret nếu có
admin password
```

Dùng environment variables:

```text
DB_URL
DB_USERNAME
DB_PASSWORD
```

---

# BƯỚC 3 — Flyway

Không dùng:

```text
ddl-auto=create
```

trong production.

Dùng Flyway migration.

Cấu trúc:

```text
db/migration/

V1__create_users.sql
V2__create_bots.sql
V3__create_matches.sql
V4__create_tournaments.sql
V5__create_replays.sql
V6__create_indexes.sql
```

---

# PHẦN B — USER ACCOUNT

# BƯỚC 4 — User domain

Tạo:

```text
User
UserId
UserRole
AccountStatus
```

User:

```text
id
username
email
passwordHash
displayName
role
status

createdAt
updatedAt
lastLoginAt
```

Role:

```text
USER
ADMIN
```

Status:

```text
ACTIVE
DISABLED
```

Không lưu password plain text.

---

# BƯỚC 5 — UserEntity

Tạo JPA entity riêng:

```text
UserEntity
```

Không biến trực tiếp domain model thành entity nếu có thể tránh.

Mapping:

```text
User
↔
UserEntity
```

---

# BƯỚC 6 — UserRepository

Tạo:

```text
UserRepository
```

Các hàm:

```text
findById
findByUsername
findByEmail
existsByUsername
existsByEmail
save
```

---

# PHẦN C — REGISTER

# BƯỚC 7 — Register API

Endpoint:

```http
POST /api/auth/register
```

Request:

```json
{
  "username": "dat123",
  "email": "dat@example.com",
  "password": "StrongPassword123",
  "displayName": "Dat"
}
```

Validate:

```text
username không trùng
email không trùng
password đủ mạnh
email hợp lệ
```

---

# BƯỚC 8 — Password hashing

Sử dụng:

```text
PasswordEncoder
```

ví dụ:

```text
BCryptPasswordEncoder
```

Không lưu:

```text
plain password
```

Không log password.

---

# PHẦN D — LOGIN / LOGOUT

# BƯỚC 9 — Spring Security

Cấu hình:

```text
SecurityConfig
```

Public:

```text
/
index.html
/css/**
/js/**
/assets/**

/api/health

/api/auth/register
/api/auth/login
```

Protected:

```text
/api/account/**
/api/bots/**
/api/tournaments/**
```

Match endpoints có thể chia public/protected tùy mode hiện tại.

---

# BƯỚC 10 — Login

Endpoint:

```http
POST /api/auth/login
```

Request:

```json
{
  "username": "dat123",
  "password": "..."
}
```

Response:

```json
{
  "id": "U001",
  "username": "dat123",
  "displayName": "Dat",
  "role": "USER"
}
```

Nếu dùng session:

```text
Set-Cookie:
HttpOnly
Secure ở production
SameSite=Lax hoặc Strict tùy flow
```

---

# BƯỚC 11 — Logout

```http
POST /api/auth/logout
```

Phải invalidate session.

---

# BƯỚC 12 — Current User

```http
GET /api/auth/me
```

Response:

```json
{
  "authenticated": true,
  "user": {
    "id": "U001",
    "username": "dat123",
    "displayName": "Dat",
    "role": "USER"
  }
}
```

---

# PHẦN E — ACCOUNT PAGE

# BƯỚC 13 — account.html

Tạo:

```text
account.html
```

Hiển thị:

```text
Avatar placeholder
Display name
Username
Email

Matches played
Wins
Draws
Losses

Bots
Tournaments
```

---

# BƯỚC 14 — Update profile

Endpoint:

```http
PATCH /api/account/profile
```

Cho sửa:

```text
displayName
```

Email có thể cho sửa sau.

Không cho sửa:

```text
role
passwordHash
id
```

---

# BƯỚC 15 — Change password

```http
POST /api/account/change-password
```

Request:

```json
{
  "currentPassword": "...",
  "newPassword": "..."
}
```

Phải xác minh current password.

---

# PHẦN F — BOT OWNERSHIP

# BƯỚC 16

Bot upload hiện tại phải gắn với:

```text
ownerUserId
```

Không còn chỉ dựa vào localStorage/token phía client.

Bot table:

```text
id
ownerUserId
name
language
sourcePath
status
createdAt
updatedAt
```

Nếu có version:

```text
Bot
BotVersion
```

tách riêng.

---

# BƯỚC 17 — Bot authorization

Chỉ owner hoặc ADMIN được:

```text
delete bot
rename bot
build/test bot
use private bot
view detailed logs
```

Không cho user A dùng bot private của user B.

---

# BƯỚC 18 — My Bots API

```http
GET /api/account/bots
```

Trả đúng bot thuộc account hiện tại.

---

# PHẦN G — MATCH PERSISTENCE

# BƯỚC 19 — MatchEntity

Lưu metadata:

```text
id
mode
status

player1UserId
player2UserId

winnerUserId
outcome

turnCount

startedAt
finishedAt
durationMs

ruleSet
```

Không persist toàn GameState bằng JPA entity.

---

# BƯỚC 20 — Match participants

Nếu bot chơi thay user:

```text
seatUserId
seatBotId
controllerType
```

Ví dụ:

```text
P1 owner = User A
P1 controller = Bot Alpha
```

Phải phân biệt:

```text
người sở hữu seat
và agent điều khiển
```

---

# BƯỚC 21 — Match History

Endpoint:

```http
GET /api/account/matches
```

Filter:

```text
all
win
loss
draw
human
bot
```

Có pagination:

```text
page
size
```

---

# PHẦN H — USER STATISTICS

# BƯỚC 22

Tạo:

```text
UserStatsService
```

Tính:

```text
matchesPlayed
wins
draws
losses
winRate
totalTurns
```

Có thể tính query từ Match table ở phiên bản đầu.

Không cần table stats riêng nếu chưa cần performance.

---

# PHẦN I — TOURNAMENT PERSISTENCE

# BƯỚC 23 — TournamentEntity

Lưu:

```text
id
ownerUserId
name
format
status
createdAt
startedAt
finishedAt
```

---

# BƯỚC 24 — TournamentParticipantEntity

```text
id
tournamentId
botId
ownerUserId
displayName
seed
```

---

# BƯỚC 25 — TournamentMatchEntity

```text
id
tournamentId
matchId
participantAId
participantBId
status
winnerParticipantId
```

---

# BƯỚC 26 — Ranking persistence

Không bắt buộc lưu ranking nếu có thể tính từ TournamentMatch.

Nếu lưu:

```text
TournamentRankingEntity
```

chỉ dùng như cache.

Source of truth vẫn là match results.

---

# PHẦN J — TOURNAMENT OWNERSHIP

# BƯỚC 27

Tournament phải có:

```text
ownerUserId
```

Owner được:

```text
start
cancel
edit participant khi DRAFT
```

User khác:

```text
view
spectate
```

nếu tournament public.

---

# BƯỚC 28 — Tournament visibility

Thêm:

```text
PUBLIC
PRIVATE
```

Private tournament chỉ:

```text
owner
participants
admin
```

được xem.

Có thể bỏ private ở bản đầu nếu muốn đơn giản.

---

# PHẦN K — REPLAY PERSISTENCE

# BƯỚC 29

Không cần lưu toàn event log trong relational columns.

Có thể:

```text
ReplayEntity
```

metadata:

```text
id
matchId
filePath
eventCount
valid
createdAt
```

Full replay:

```text
JSON file
```

hoặc:

```text
JSONB
```

Khuyến nghị:

```text
metadata DB
+
JSON file
```

để đơn giản.

---

# PHẦN L — AUTHORIZATION

# BƯỚC 30

Tạo application-level authorization.

Ví dụ:

```text
BotAuthorizationService

TournamentAuthorizationService

MatchAuthorizationService
```

Không rải:

```java
if (userId.equals(...))
```

khắp Controller.

---

# BƯỚC 31 — ADMIN

Role:

```text
ADMIN
```

Admin có thể:

```text
view all users
disable account
view bot failures
cancel tournament
inspect system
```

Không cần admin UI lớn ngay.

---

# BƯỚC 32 — Admin endpoints

```http
GET /api/admin/users
POST /api/admin/users/{id}/disable
POST /api/admin/users/{id}/enable
GET /api/admin/system
```

Tất cả require ADMIN.

---

# PHẦN M — FRONTEND AUTH

# BƯỚC 33 — Login page

Tạo:

```text
login.html
```

Giao diện:

```text
RPS BATTLE

Username
[____________]

Password
[____________]

[ LOGIN ]

Don't have an account?
[ REGISTER ]
```

---

# BƯỚC 34 — Register page

```text
register.html
```

Fields:

```text
Username
Display Name
Email
Password
Confirm Password
```

---

# BƯỚC 35 — Navbar

Sau login:

```text
PLAY
MY BOTS
TOURNAMENT
PROFILE
LOGOUT
```

Trước login:

```text
PLAY
LOGIN
REGISTER
```

Tùy chính sách có cho guest chơi hay không.

---

# BƯỚC 36 — Guest mode

Chọn một policy rõ.

Khuyến nghị:

```text
Guest:
- được xem How To Play
- có thể chơi local/demo nếu muốn

Account:
- upload bot
- tournament
- match history
- profile
```

Nếu muốn đơn giản hơn:

```text
toàn bộ online gameplay yêu cầu login
```

Chọn một policy và áp dụng nhất quán.

---

# PHẦN N — WEBSOCKET AUTH

# BƯỚC 37

WebSocket phải biết user authenticated.

Không tin:

```text
userId
```

do client tự gửi.

Lấy identity từ:

```text
HTTP session / authenticated principal
```

khi handshake.

---

# BƯỚC 38

Match seat token hiện tại vẫn dùng để xác định quyền điều khiển seat.

Authentication:

```text
ai là user
```

Seat token:

```text
user này có quyền điều khiển P1/P2 không
```

Hai khái niệm không thay thế nhau.

---

# PHẦN O — DATABASE INDEXES

# BƯỚC 39

Index cần thiết:

```text
users.username UNIQUE
users.email UNIQUE

bots.owner_user_id

matches.player1_user_id
matches.player2_user_id
matches.finished_at

tournaments.owner_user_id

tournament_matches.tournament_id

replays.match_id UNIQUE
```

Không index mọi cột.

---

# PHẦN P — TRANSACTIONS

# BƯỚC 40

Dùng transaction cho:

```text
register user

finish match + persist result

finish tournament match + update tournament state

delete bot metadata
```

Không để DB trong trạng thái nửa hoàn chỉnh.

---

# PHẦN Q — ACCOUNT DATA CONSISTENCY

# BƯỚC 41

Không xóa cứng user nếu đã có Match/Tournament history.

Dùng:

```text
DISABLED
```

hoặc soft-delete.

---

# BƯỚC 42

Nếu bot đã tham gia tournament:

không xóa artifact ngay.

Có thể:

```text
mark DELETED
```

nhưng giữ metadata/version để replay/audit.

---

# PHẦN R — PRODUCTION CONFIGURATION

# BƯỚC 43

Tạo:

```text
application-dev.yml
application-test.yml
application-prod.yml
```

Prod:

```text
PostgreSQL
Secure cookie
Strict bot limits
Reduced debug logging
```

---

# PHẦN S — DOCKER COMPOSE

# BƯỚC 44

docker-compose:

```text
rps-server
postgres
reverse-proxy
```

Volumes:

```text
postgres-data
bot-data
replay-data
```

---

# BƯỚC 45

Environment:

```text
POSTGRES_DB
POSTGRES_USER
POSTGRES_PASSWORD

DB_URL
DB_USERNAME
DB_PASSWORD
```

Không commit secrets.

---

# PHẦN T — DATABASE BACKUP

# BƯỚC 46

Tạo docs/script:

```text
pg_dump
pg_restore
```

Backup thêm:

```text
bot artifacts
replay files
```

---

# PHẦN U — WORKER & QUEUE

# BƯỚC 47 — MatchWorker

Tạo abstraction:

```java
public interface MatchWorker {
    MatchExecutionResult execute(
        MatchExecutionRequest request
    );
}
```

Implementation:

```text
LocalMatchWorker
```

---

# BƯỚC 48 — Execution Queue

Tournament match dài không chạy trực tiếp trên HTTP thread.

Flow:

```text
Tournament
↓
ExecutionQueue
↓
WorkerManager
↓
MatchWorker
```

---

# BƯỚC 49 — Capacity

Config:

```text
maxConcurrentMatches
maxPendingMatches
```

Nếu quá tải:

```text
SERVER_BUSY
```

---

# PHẦN V — RATE LIMIT

# BƯỚC 50

Rate limit:

```text
login
register

bot upload

create match

join match

create tournament

WebSocket ACTION
STATE_REQUEST
PING
```

---

# BƯỚC 51

Login brute-force protection:

```text
limit failed login attempts
```

Không cần Redis ở single-instance.

---

# PHẦN W — SECURITY BASICS

# BƯỚC 52

Phải có:

```text
CSRF strategy phù hợp

Secure Cookie
HttpOnly
SameSite

CORS strict

password hash

request size limit

upload validation

path traversal protection
```

Nếu dùng session cookie + same-origin client:

ưu tiên bật CSRF đúng cách thay vì disable toàn bộ vô lý.

---

# PHẦN X — STRUCTURED LOGGING

# BƯỚC 53

Log context:

```text
userId
matchId
tournamentId
botId
requestId
```

Không log:

```text
password
session id
botToken
secret
```

---

# PHẦN Y — ACTUATOR / METRICS

# BƯỚC 54

Thêm:

```text
Spring Boot Actuator
```

Metrics:

```text
active_users
active_matches
running_tournaments
uploaded_bots
bot_timeout_total
login_failure_total
websocket_sessions
```

---

# PHẦN Z — GRACEFUL SHUTDOWN

# BƯỚC 55

Khi shutdown:

```text
stop accepting new jobs
↓
worker DRAINING
↓
finish/persist active state
↓
close bot processes
↓
close DB pool
```

---

# PHẦN AA — RECOVERY

# BƯỚC 56

Sau restart:

DB khôi phục:

```text
users
bots
matches
tournaments
rankings
replays metadata
```

Tournament đang RUNNING khi crash:

```text
PAUSED
```

hoặc:

```text
RECOVERY_REQUIRED
```

Chọn policy rõ ràng.

---

# BƯỚC 57 — Recovery API

```http
POST /api/tournaments/{id}/resume
```

hoặc:

```http
POST /api/tournaments/{id}/cancel
```

---

# PHẦN AB — BOT SANDBOX HARDENING

# BƯỚC 58

Tiếp tục bảo vệ:

```text
no network
non-root
read-only root filesystem
CPU limit
RAM limit
PID limit
timeout
output limit
tmp limit
```

---

# BƯỚC 59

Cleanup orphan container bằng Docker label:

```text
rps.managed=true
matchId
botId
```

---

# PHẦN AC — CLIENT ACCOUNT EXPERIENCE

# BƯỚC 60

Home sau login:

```text
Welcome, Dat

PLAY GAME
MY BOTS
TOURNAMENT
MATCH HISTORY
PROFILE
```

---

# BƯỚC 61 — Match History UI

Tạo:

```text
history.html
```

List:

```text
vs AlphaBot

WIN

42 turns

2026-10-12

[ VIEW ]
[ REPLAY ]
```

---

# BƯỚC 62 — Profile stats

Hiển thị:

```text
Matches
Wins
Losses
Draws
Win Rate

Bots
Tournament Wins
```

Chỉ hiện Tournament Wins nếu đã có dữ liệu hợp lệ.

---

# PHẦN AD — TESTS

Phải bổ sung:

```text
UserRepositoryTest

RegistrationServiceTest

LoginTest

PasswordHashTest

AuthorizationTest

BotOwnershipTest

TournamentOwnershipTest

MatchPersistenceTest

MatchHistoryTest

UserStatsTest

FlywayMigrationTest

WebSocketAuthenticationTest

AdminAuthorizationTest

DatabaseRecoveryTest

WorkerManagerTest

ExecutionQueueTest

RateLimitTest

GracefulShutdownTest
```

Client:

```text
login.test.js
register.test.js
account.test.js
history.test.js
auth-session.test.js
```

---

# PHẦN AE — MIGRATION TEST

Test migration từ:

```text
empty database
```

đến latest schema.

Không để startup production phụ thuộc `hibernate ddl-auto`.

---

# PHẦN AF — REGRESSION

Toàn bộ feature cũ phải tiếp tục hoạt động:

```text
Human vs Human

Human vs built-in bot

Human vs uploaded bot

Two uploaded bots controlling two seats

Placement
Random placement

Ready
Unready

Move
Combat

Timer
Reconnect

Surrender
Leave
Rematch

Bot upload:
Python
C++
Java
JAR

Docker execution

Tournament
Ranking
Spectator
Replay
```

---

# PHẦN AG — DATA MIGRATION CHO BOT HIỆN TẠI

Hiện bot cũ có thể đang nằm ở:

```text
server/data/bots
```

và metadata/token ở localStorage.

Không được làm mất bot cũ một cách âm thầm.

## BƯỚC 63

Thiết kế migration/import tool:

```text
LegacyBotImportService
```

hoặc CLI/script.

Cho phép:

```text
import bot artifact cũ
→ gán owner account
→ ghi BotEntity
```

Nếu không thể tự động vì thiếu ownership:

ghi rõ trong docs.

---

# PHẦN AH — ACCOUNT SECURITY RULES

# BƯỚC 64

Username:

```text
unique
case policy rõ
```

Email:

```text
unique
normalized
```

Password policy:

```text
min length
max length
```

Không cần password complexity quá mức.

---

# BƯỚC 65

Không expose:

```text
passwordHash
internal token
filesystem path
Docker command
```

trong DTO.

---

# PHẦN AI — ADMIN UI TỐI THIỂU

Nếu còn thời gian:

```text
admin.html
```

Hiển thị:

```text
Users
Active Matches
Running Tournaments
Bot Failures
System Health
```

Không bắt buộc làm đẹp.

---

# PHẦN AJ — DEPLOYMENT DOCUMENTATION

# BƯỚC 66

Tạo:

```text
docs/DEPLOYMENT.md
```

Bao gồm:

```text
PostgreSQL setup

Environment variables

Flyway

Build

Docker Compose

Reverse proxy

HTTPS

Bot Docker requirements

Backup

Restore

Upgrade
```

---

# BƯỚC 67 — ACCOUNT DOCS

Tạo:

```text
docs/AUTH.md
```

Mô tả:

```text
Register
Login
Session
Logout
Roles
Authorization
Seat token vs account session
```

---

# PHẦN AK — OPERATIONS

# BƯỚC 68

Tạo:

```text
docs/OPERATIONS.md
```

Bao gồm:

```text
Start
Stop

DB backup
DB restore

Check health

Check logs

Inspect Docker bot containers

Recover tournament

Disable user

Cleanup orphan container
```

---

# DEFINITION OF DONE — CHẶNG 4

Chặng 4 chỉ hoàn thành khi chạy được scenario:

```text
1. Start PostgreSQL.

2. Start server.

3. Register User A.

4. Register User B.

5. Login User A.

6. Upload bot AlphaBot.

7. Bot được lưu với owner User A.

8. Logout.

9. Login User B.

10. User B không thể sửa/xóa bot của User A.

11. User A tạo match.

12. User B join.

13. Match chạy bình thường.

14. Match kết thúc.

15. Result được ghi DB.

16. User A mở Match History và thấy trận.

17. User B cũng thấy trận trong history của mình.

18. User A tạo Tournament.

19. Tournament và participants được persist.

20. Tournament chạy.

21. Ranking cập nhật.

22. Server restart.

23. User account vẫn còn.

24. Bot metadata vẫn còn.

25. Match history vẫn còn.

26. Tournament/replay metadata vẫn còn.

27. Login lại thành công.

28. Replay cũ vẫn mở được.
```

Phải chứng minh thêm:

```text
password không lưu plaintext

user khác không truy cập private resource

ADMIN endpoint không mở cho USER

WebSocket không tin userId client tự khai

Flyway chạy thành công từ DB rỗng

rate limit hoạt động

bot crash không làm server chết

DB lỗi được xử lý rõ

graceful shutdown hoạt động
```

---

# CÁCH GIAO CHO AI AGENT

Không implement toàn Chặng 4 trong một lượt.

Chia thành:

```text
Nhóm 1
PostgreSQL + JPA + Flyway

Nhóm 2
User domain + Register + Login + Logout

Nhóm 3
Account/Profile + Security + Authorization

Nhóm 4
Bot ownership + bot persistence

Nhóm 5
Match persistence + Match History + Stats

Nhóm 6
Tournament persistence + ownership

Nhóm 7
Replay persistence

Nhóm 8
Frontend Login/Register/Profile/History

Nhóm 9
Worker + Queue + Rate Limit + Monitoring

Nhóm 10
Docker Compose + Backup + Recovery + Production Docs
```

Sau MỖI nhóm phải:

```text
1. Chạy toàn bộ backend tests.

2. Chạy toàn bộ client tests.

3. Chạy Flyway migration test nếu nhóm liên quan DB.

4. Build bootJar.

5. Nếu có Docker-related changes:
   chạy Docker integration tests nếu Docker khả dụng.

6. Báo pass / fail / skipped.

7. Liệt kê file thêm/sửa.

8. Kiểm tra GameEngine/RuleSet có bị sửa không.

9. Nếu sửa core:
   giải thích lý do.

10. Không tự sang nhóm tiếp theo.

11. Chờ tôi review.
```

---

# ƯU TIÊN TRIỂN KHAI

Thứ tự ưu tiên:

```text
PostgreSQL
    ↓
Register/Login
    ↓
Account
    ↓
Bot Ownership
    ↓
Match Persistence
    ↓
Tournament Persistence
    ↓
History / Stats
    ↓
Authorization
    ↓
Production Hardening
    ↓
Deployment
```

Mục tiêu Chặng 4:

> RPS Battle có tài khoản thật, dữ liệu không mất khi restart server, bot thuộc đúng người dùng, lịch sử trận và tournament được lưu bền vững, quyền truy cập được kiểm soát, đồng thời hệ thống đủ ổn định để triển khai trên một server thực tế.
