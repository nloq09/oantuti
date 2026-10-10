# Chặng 2B — Hoàn thiện client

Triển khai toàn bộ 20 mục của đặc tả `CHANG-2B-CLIENT-SPEC.md`. Client HTML/CSS/JS
thuần, ES modules; không cần npm install, Live Server, React hoặc frontend build.
Server giữ luật và trạng thái, client dùng legalActions/placementCells để hiển thị.

## Chạy trong VS Code

Mở thư mục BT3, mở Terminal → New Terminal rồi chạy:

```powershell
cd server
$env:JAVA_HOME = 'C:\Users\hi\.jdks\openjdk-24.0.2+12-54'
.\gradlew.bat bootRun --console=plain
```

Đường dẫn JAVA_HOME trên là JDK đã có ở máy hiện tại. Máy khác dùng JDK 21
hoặc JDK 24 tương ứng. Gradle wrapper 8.14.3 không chạy với JDK 25.
Mở `http://localhost:8080`. Dừng server bằng Ctrl+C.
Nếu port 8080 đang được server cũ sử dụng, dừng terminal cũ trước hoặc chạy:

```powershell
.\gradlew.bat bootRun --console=plain --args="--server.port=8081"
```

Trên Windows, build nằm ngoài OneDrive tại
`GRADLE_USER_HOME/rps-battle-builds/<checkout-key>`. In đường dẫn bằng
`.\gradlew.bat -q printBuildDir`. Không cần xóa các thư mục build cũ để chạy.

## Luồng chơi

1. Home → Bắt đầu chơi → chọn Hai người hoặc Đấu với bot.
2. Tạo phòng. Hai người: gửi mã 8 ký tự cho người còn lại, nhập mã ở lobby.
3. Chọn loại quân rồi chọn ô trong vùng được server cho phép. Chọn quân đã đặt
   và ô trống để đổi vị trí. Có Ngẫu nhiên và Đặt lại.
4. Sẵn sàng bật khi đủ 6 quân mỗi loại. Có thể Hủy sẵn sàng trước lúc bắt đầu.
5. P1/Xanh đi trước. Chọn quân, chọn ô xanh để đi hoặc ô đỏ để tấn công.
   Quân của người đang xem luôn ở phía dưới: P2 nhìn bàn cờ xoay 180°.
   Nhãn ô giữ tọa độ chung của server để hai người trao đổi cùng một nước đi.
6. Server xử lý bot, combat và timeout; client diễn hoạt lần lượt và cập nhật board.
7. Đầu hàng hoặc Rời phòng khi đang chơi đều kết thúc ván. Cần xác nhận.
8. Popup kết quả → Chi tiết xem số lượt, thời lượng và quân còn lại.
   Bot có Chơi lại ngay; hai người chọn Chơi ván mới để trở về lobby và tạo phòng.

Timer mặc định 30 giây/lượt. Override bằng `--rps.turn-seconds=60` hoặc biến môi
trường `RPS_TURN_SECONDS=60`. Hết giờ server gửi PASS qua Match/GameEngine.
Mất kết nối: board khóa, modal thông báo, thử lại sau 1/2/3/5 giây. Kết nối lại
dùng token ghế, RESUME_MATCH và full snapshot; không tự gửi lại nước đi cũ.
Reload tab khôi phục ghế qua sessionStorage. Snapshot JOIN/RESUME chứa phần cuối
event history để khôi phục log; client không chạy lại animation lịch sử.
Cài đặt sound/music/animation dùng
localStorage. Âm thanh/nhạc được tạo bằng Web Audio và chỉ bật sau tương tác.

## API và ranh giới kiến trúc

- POST `/api/matches`: `{ "mode": "HUMAN_VS_HUMAN" }` hoặc `HUMAN_VS_BOT`;
  trả matchId, mode, playerId, token, seed. Seed tùy chọn giúp kiểm tra tái lập.
- POST `/api/matches/{id}/join`: cấp ghế trống và token.
- GET `/api/matches/{id}`: mode, status, số ghế đã tham gia.
- WS `/ws/game`: JOIN_MATCH/RESUME_MATCH, PLACE, RANDOM_PLACEMENT,
  RESET_PLACEMENT, READY/UNREADY, ACTION, LEAVE, STATE_REQUEST, PING.
- Phản hồi: GAME_STATE, GAME_EVENT, YOUR_TURN, ACTION_ACCEPTED/REJECTED,
  GAME_OVER, LEFT_ROOM, PONG. ACTION có sequence; stale action bị reject và
  gửi trạng thái mới. Token là capability của ghế, không phải account/login.
- OnlineMatch giữ lifecycle phòng; khi cả hai Ready tạo Match với 36 quân đã
  được GameEngine validate. Match.submitAction dùng cùng engine/log với bot.
- OnlineRuleSet dùng luật Classic, chỉ cho phép surrender ngoài lượt để rời ván.
  GameEngine không sửa, không phụ thuộc Spring/HTTP/WebSocket/client.
- In-memory: restart server mất phòng; không có resume sau restart hay lịch sử
  lâu dài. Bot online dùng TacticalBotAgent: đánh giá ăn quân, phản công một lượt
  và khoảng cách tới mục tiêu; hạn chế lặp bàn cờ. RandomBotAgent vẫn giữ cho
  CLI/kiểm thử. Không có mutual-rematch protocol cho hai người;
  dùng Play Again → lobby như phương án cho phép trong đặc tả.
- Demo 2A giữ nguyên tại `/demo/` và `demo-match`.

## Kiểm thử

```powershell
cd server
.\gradlew.bat test bootJar --no-daemon --console=plain
# Tùy chọn, Node chỉ cần để chạy test transport client:
cd ..\client
npm test
```

Backend: 270 tests pass (260 cũ + 10 mới), không failures/errors/skipped.
Test mới kiểm tra zone/quota/reposition/reset, Ready 36 quân, token/ghế,
sequence, surrender ngoài lượt, timeout bằng Clock giả, bot và kết nối WebSocket
thật. Hai client nhận board đồng nhất sau move, RESUME nhận board mới nhất,
REST trả lỗi cho phòng không tồn tại/full/mode không hợp lệ.
Client transport test kiểm tra JOIN/RESUME, backoff 1/2/3/5 giây, chỉ báo connected
sau full snapshot, không gửi khi offline và hủy retry khi đóng trang.

Browser kiểm tra Home/How to Play, create/join/waiting, đặt đủ 18 quân bằng chuột
ở mỗi tab, đổi vị trí/reset/random, Ready/Unready, selected/legal/attack highlights,
move/combat đồng bộ, bot trả lời và combat cùng loại loại cả hai, surrender,
thắng/thua, trang kết quả, bot rematch tạo phòng mới, Settings giữ sau reload,
reload snapshot, Leave về Home. Desktop 1366×768: board và controls vừa chiều cao;
mobile 390×844: board đủ 9×9, không tràn ngang. Event log giới hạn 20 dòng.

Combat dễ tái lập: P1 đặt Búa A7–F7, Bao A8–F8, Kéo A9–F9.
P2 đặt Búa A1–F1, Bao A2–F2, Kéo A3–F3. Cả hai Ready.
P1 A7→A6, P2 A3→A4, P1 A6→A5, P2 Bỏ lượt, P1 A5→A4.
P2 còn 17 quân; hai tab cùng thấy Búa P1 tại A4 và event tấn công thắng.

## Nâng cấp bot chiến thuật

Bot chỉ chọn trong legalActions được cung cấp. Đánh giá dùng CombatRule và
MovementRule được inject, không chép ma trận Búa/Bao/Kéo hoặc sửa GameEngine.
Ưu tiên thắng ván/ăn quân, xét nguy cơ phản công ngay sau nước đi, áp sát quân
có thể thắng và phạt các trạng thái đã lặp gần đây. Seed quyết định khi hòa điểm.
Đây là đánh giá chiến thuật một lượt phản công, chưa có tìm kiếm sâu/minimax.

Sau nâng cấp: 277 backend tests và 3 client tests pass. Có 6 tình huống bot mới
và một ván đầy đủ chống nước đi ngẫu nhiên hợp lệ, gồm combat và kết thúc trận.
