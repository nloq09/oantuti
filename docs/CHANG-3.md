# Chặng 3 — Tournament, Ranking, Spectator & Web Replay

## Sử dụng

1. Bật server và Docker Linux, chuẩn bị image theo BOT-UPLOAD.md.
2. Trong Bot của tôi, tải ít nhất 2 bot hợp lệ (mỗi bot là một upload khác nhau).
3. Home → Giải đấu → nhập tên, chọn 2–10 bot → Tạo giải đấu.
4. Bản nháp cho phép thêm/gỡ participant; Bắt đầu kiểm tra quyền bot và image rồi tạo toàn bộ lịch Round Robin.
5. Các trận có PENDING, LIVE, FINISHED hoặc FAILED; Tổng quan cập nhật tiến độ, Xếp hạng cập nhật sau từng trận.
6. Xem trực tiếp mở game.html với `spectate=<matchId>`: cùng BoardRenderer/GameController, không điều khiển quân.
7. Sau khi hoàn tất: xem vô địch, bảng xếp hạng, replay từng trận hoặc xuất JSON giải.

Hai chế độ của trang Vào trận vẫn là Hai người và Đấu với bot sẵn; mỗi bên có thể chọn bot riêng. Không thêm BOT_VS_BOT mode.

## Kiến trúc

TournamentService quản lý roster và start/cancel. TournamentRegistry dùng ConcurrentHashMap, mọi mutation/snapshot của một giải giữ monitor của giải đó. Participant dùng bot ID/token hiện có; JSON participant đánh dấu token JsonIgnore.

RoundRobinScheduler tạo n(n−1)/2 cặp, không self-pair hoặc duplicate. TournamentExecutionService là admission queue tập trung, dùng lịch Spring để thu hồi slot và bắt đầu trận kế tiếp. Bot chạy trong executor 4 worker đã có ở OnlineMatchService; không tạo thread theo từng match hay gọi HTTP/WebSocket giả tới chính server.

TournamentMatchExecutor lấy hai BotAgent từ BotUploadService, tạo phòng HUMAN_VS_HUMAN, attach hai seat, prepareBots và publish qua service hiện có. Movement/combat/victory/legal actions vẫn do Match, GameEngine và RuleSet xử lý. `game/`, `rule/`, `match/` không phụ thuộc tournament/spectator.

Bot lỗi giữ policy xử thua hiện tại; tournament lấy kết quả thật. Lỗi hạ tầng đánh FAILED và chạy tiếp; watchdog 900 giây mặc định giải phóng trận kẹt. Khi hủy, dừng running và đánh pending/running FAILED với lý do CANCELLED; kết quả trận đã xong vẫn giữ.

Kết thúc trận tournament: giữ MatchLog/final snapshot trong ReplayRegistry, dừng runner/cancel job và remove phòng khỏi MatchRegistry. Replay/spectator kết quả vẫn đọc archive; `GET /api/matches/{id}` của phòng tournament đã dọn trả 404. Không giữ active room mãi.

## Ranking

- Win 3, draw 1, loss 0; chỉ FINISHED được tính, FAILED không cộng played/điểm.
- Tiebreak: điểm giảm dần, wins giảm dần, quân còn lại cộng dồn giảm dần, participantId tăng dần.
- Ranking tính lại từ các kết quả nên không cộng điểm hai lần khi tick/reconnect.
- FINISHED có ít nhất một kết quả hợp lệ thì hạng 1 là champion. Nếu mọi trận FAILED, không công bố champion.
- Chưa ghi thời gian từng nước đi; không hiển thị số giả cho averageMoveTime.

## REST / WebSocket

- POST/GET `/api/tournaments`; GET `/api/tournaments/{id}`.
- POST `/{id}/participants` với botId, botToken, displayName, seed tùy chọn; DELETE `/{id}/participants/{participantId}` chỉ ở DRAFT.
- POST `/{id}/start`, POST `/{id}/cancel`.
- GET `/{id}/ranking`, GET `/{id}/export` (JSON download, không token).
- GET `/api/matches/{id}/replay` chỉ sau kết thúc: initialState, events, result, finalState, players. Replay dùng model MatchLog hiện có và mapper DTO.
- WS `/ws/game`: WATCH_TOURNAMENT/tournamentId → TOURNAMENT_STATE sau mỗi thay đổi; SPECTATE_MATCH/matchId → GAME_STATE/GAME_OVER.
- Observer không chiếm seat và không gửi player credentials. Mọi PLACE/READY/UNREADY/ACTION/LEAVE-as-player/JOIN_MATCH từ observer bị từ chối SPECTATOR_READ_ONLY. Client cũng khóa input.
- WS reconnect lấy snapshot mới nhất; không đưa bot token vào link xem hoặc replay.

## Replay

Client dựng lại trạng thái đầu cộng event facts, không gọi GameEngine hay tính lại combat. Các sự kiện cùng sequence là một bước nguyên tử để không hiện nửa giao chiến. Tua reset từ initialState; không cần reverse events. Có đầu/cuối, bước trước/sau, phát/dừng, ×1/×2/×4, slider và nhập số lượt.

ReplayRegistry tối đa 1000 bản lưu RAM; restart server làm mất toàn bộ replay và tournament. TournamentRegistry giới hạn 100 giải, mỗi giải 10 bot. Chưa có login/database, import giải, xếp hạng liên giải hoặc worker riêng.

## Kiểm thử

Gradle: TournamentRegistryTest, RoundRobinSchedulerTest (2/3/4/5/10 bot), TournamentServiceTest, TournamentMatchExecutorTest, TournamentExecutionServiceTest, TournamentConcurrencyTest, RankingServiceTest, ScoringRuleTest, TournamentControllerTest, TournamentWebSocketTest, SpectatorTest, ReplayWebApiTest và regression/architecture tests.

Node: tournament-api.test.js, tournament-ranking.test.js, replay-controller.test.js, spectator-controller.test.js cùng test client cũ.

`tools/verify-tournaments.mjs` dùng Docker thật và API server riêng: 4 bot/6 trận, 10 bot/45 trận, crash/invalid/timeout, max 4 running, ranking, spectator read-only, full replay khớp final board, không còn active tournament room và không leak container. Lỗi Docker/hạ tầng được kiểm tra bằng fault injection trong TournamentExecutionServiceTest; không dừng Docker của người dùng để thử.

Để chạy nhanh trên server kiểm thử: `--server.port=8081 --rps.bots.directory=build/stage3-verification --rps.bot-tick-millis=10 --rps.tournament.bot-delay-millis=0`. Server thông thường giữ pacing 600 ms và tick 200 ms để dễ xem LIVE. Chạy `node tools/verify-tournaments.mjs` ở root; script cần Node có fetch/File/WebSocket và lệnh docker.

### Kết quả kiểm chứng ngày 12/10/2026

- 315 test server đạt, 0 failures/errors/skipped; gồm Docker Python/C++/Java/JAR và regression chức năng cũ.
- 10 test client đạt.
- Docker thật: giải 4 bot hoàn tất 6 trận, giải 10 bot hoàn tất 45 trận, tối đa 4 trận đồng thời; không lỗi bot ở hai giải hợp lệ.
- Giải fault injection crash/invalid output/timeout hoàn tất 6 trận; bot lỗi bị xử thua, server health vẫn UP. Hạ tầng/Docker launch failure có test riêng chứng minh các trận khác vẫn hoàn tất.
- Replay facts dựng đúng toàn bộ final board, winner và số lượt; phòng tournament hoàn tất đã được remove; không container mới còn sót lại.
- Kiểm tra stack JVM sau stress không còn LocalProcessRunner/DockerBotProcess/TournamentMatchExecutor đang chạy.
- Browser: tải 4 bot Python/C++/Java, tạo giải, đủ 6 trận và champion/ranking; xem LIVE chỉ đọc, chuyển sang replay khi trận kết thúc; đầu/cuối, phát ×4, tua chính xác lượt 80 và replay điện thoại không tràn ngang.
