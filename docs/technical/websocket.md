# WebSocket: phòng chơi, spectator và cập nhật giải

[Mục lục](../README.md) · [REST API](api.md) · [Giao thức bot stdin/stdout](../bot/protocol.md)

Endpoint duy nhất là **`/ws/game`**, dùng WebSocket JSON text thuần, không STOMP/SockJS. Cùng origin với UI; HTTPS dùng WSS. Message của browser khác message bot: bot stdin/stdout **không có** JOIN_MATCH, READY hoặc GAME_OVER.

## Kết nối một ghế online

REST create/join phòng trả matchId, historyId, playerId, token và seed. Giữ token riêng của ghế; account session xác định ownership khi dùng bot riêng, còn capability ghế cấp quyền socket.

```javascript
const socket = new WebSocket(`${location.protocol === 'https:' ? 'wss:' : 'ws:'}//${location.host}/ws/game`);
// credentials là response REST tạo/join của chính người dùng; không ghi token vào log.
socket.addEventListener('open', () => socket.send(JSON.stringify({
  type: 'JOIN_MATCH', matchId: credentials.matchId,
  playerId: credentials.playerId, token: credentials.token
})));
```

`OnlineMessage` có type, matchId, playerId, token, sequence, payload. Không gửi field ngoài schema; JSON parser từ chối trailing token, unknown property và số thực ở trường integer.

| Client type | Dữ liệu và tác dụng |
| --- | --- |
| JOIN_MATCH | matchId/playerId/token hợp lệ; bind socket vào ghế. |
| RESUME_MATCH | Cùng capability; có thể thay socket cũ của chính ghế, nhận snapshot mới. |
| PLACE | payload.type = ROCK/PAPER/SCISSORS; payload.position theo PositionDto; pieceId tùy chọn. |
| RANDOM_PLACEMENT / RESET_PLACEMENT | Sắp xếp/xóa placement của ghế khi được phép. |
| READY / UNREADY | Sẵn sàng placement trước trận. Bot upload do server điều khiển bước này. |
| ACTION | sequence hiện tại, payload theo ActionDto; server kiểm tra lượt và legality. |
| STATE_REQUEST | Yêu cầu snapshot hiện tại sau khi đã join. |
| LEAVE | Rời phòng; tác động kết quả theo phase, trả LEFT_ROOM. |
| PING | Nhận PONG; không thay đổi game state. |

Server trả envelope `{ "type": "…", "payload": { … } }`:

| Server type | Payload |
| --- | --- |
| GAME_STATE | snapshot gồm state của phòng/ghế được phép xem. |
| GAME_EVENT | events của cập nhật vừa xảy ra; không phải toàn bộ replay bất biến. |
| YOUR_TURN | sequence của lượt để browser thao tác. |
| ACTION_ACCEPTED | sequence sau khi action được nhận. |
| ACTION_REJECTED | error code như JOIN_REQUIRED, PLAYER_MISMATCH, INVALID_SEQUENCE, BOT_CONTROLLED, INVALID_MESSAGE. |
| GAME_OVER | snapshot phòng đã FINISHED. |
| LEFT_ROOM / PONG | Xác nhận rời hoặc ping, payload rỗng. |

## Đồng bộ và reconnect

Snapshot server là chuẩn. Khi INVALID_SEQUENCE, gateway gửi lại state; không tự tăng sequence ở client rồi gửi action cũ. Khi reconnect, dùng RESUME_MATCH với đúng seat token và dựng UI lại từ snapshot. Không replay mù các mutation chưa rõ đã được chấp nhận hay chưa.

JOIN khi ghế còn socket khác có thể trả SEAT_TAKEN; RESUME chủ động thay socket cũ. Socket đóng không tự tạo account khác hoặc xoay token ghế. Nếu Central restart mất phòng RAM, reconnect không khôi phục engine; mở lịch sử/replay khi có archive và xử lý theo [recovery](backup-recovery.md).

## Spectator và theo dõi giải

`SPECTATE_MATCH` với matchId đọc snapshot spectator của trận còn khả dụng. `WATCH_TOURNAMENT` với tournamentId nhận TOURNAMENT_STATE và các invalidation. `updatesOnly: true` nhận state tối giản id/revision để client đọc lại REST view theo quyền.

Các event cập nhật giải có TOURNAMENT_UPDATED, REGISTRATION_UPDATED, ROUND_STARTED, MATCH_STARTED, MATCH_FINISHED, TOURNAMENT_FINISHED, STANDINGS_UPDATED, BRACKET_UPDATED. Đây là thông báo đọc lại dữ liệu, không phải quyền quản lý hay đủ dữ liệu để tái dựng mọi private field. Spectator gửi mutation bị SPECTATOR_READ_ONLY.

Demo cũ dùng `JOIN`/`ACTION` với DemoMatchService.MATCH_ID và nhận `STATE`; không dùng envelope demo để viết client online mới. Remote headless execution chưa có stream từng nước trực tiếp.

## Giới hạn và proxy

Giới hạn text đầu vào 16 KiB. Bucket mặc định theo IP: tổng 60 message/giây, PING 2/giây, STATE_REQUEST 5/giây và nhóm action 30/giây; vượt bị RATE_LIMITED. Cùng NAT có thể chung quota. Không dùng polling STATE_REQUEST dồn thay invalidation.

Nginx/Caddy phải forward Upgrade/Connection, Host, scheme và port đúng; xem [deployment](deployment.md). WSS 403 cần kiểm tra Origin/Host/trusted proxy, không mở wildcard origins. Không đặt seat token trong query URL.

## Nguồn đối chiếu

- [WebSocketConfig](../../server/src/main/java/com/rpsbattle/websocket/config/WebSocketConfig.java), [handler và limits](../../server/src/main/java/com/rpsbattle/websocket/handler/GameWebSocketHandler.java), [online gateway](../../server/src/main/java/com/rpsbattle/websocket/handler/OnlineGameGateway.java).
- [OnlineMessage](../../server/src/main/java/com/rpsbattle/websocket/message/OnlineMessage.java), [ActionDto](../../server/src/main/java/com/rpsbattle/api/dto/ActionDto.java), [PositionDto](../../server/src/main/java/com/rpsbattle/api/dto/PositionDto.java), [spectator](../../server/src/main/java/com/rpsbattle/spectator/SpectatorService.java).
