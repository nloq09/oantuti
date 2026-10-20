# Kết quả, replay và biên nhận chống trùng

Worker 0.6.0 đã gửi được kết quả hoàn thành về Central. Schema V32 thêm `worker_result_receipts`; không sửa GameEngine, luật chiến đấu hoặc protocol bot.

## Cách chạy

Sau khi dừng agent đang dùng cùng state, thêm `--run-once` để claim/chạy/upload một batch. Dùng `--upload-pending` để chỉ gửi lại kết quả READY_FOR_UPLOAD, không claim hay chạy bot:

```sh
java -jar rps-worker.jar --central=https://central.example --state-dir=./worker-state --run-once
java -jar rps-worker.jar --central=https://central.example --state-dir=./worker-state --upload-pending
```

Local/LAN HTTP cần `--allow-insecure-http`. Các mode --run-once/--upload-pending/--prefetch/--once/--identity-only không dùng đồng thời. Không có mode thì agent vẫn chỉ heartbeat. Central scheduler chưa tự đưa trận giải thật vào remote queue; nhóm này xử lý job đã được giao qua queue nội bộ.

## API và xác thực

`POST /internal/workers/{workerId}/jobs/{jobId}/complete` dùng Bearer credential riêng của worker. Cookie account/admin không thay thế credential. Payload JSON gồm `result` và `replayBase64` (kết quả + replay gửi cùng request):

```json
{
  "result": {
    "executionId": "UUID",
    "matchId": "UUID",
    "winner": "P1",
    "loser": "P2",
    "reason": "ENGINE_RESULT",
    "turnCount": 84,
    "durationMs": 7310,
    "artifactHashA": "sha256",
    "artifactHashB": "sha256",
    "seed": 123456,
    "replayChecksum": "sha256"
  },
  "replayBase64": "base64 của replay JSON"
}
```

Winner/loser là seat P1/P2; draw dùng null/null. Central ánh xạ sang participant/user của giải, không tin participant ID do worker tự gửi. Reason cho phép ENGINE_RESULT hoặc BOT_FAILED; lỗi hạ tầng/hard timeout không được tạo winner giả.

Central kiểm tra execution thuộc đúng worker/job, RUNNING, job LEASED, lease còn hạn theo giờ server, approval/trust vẫn phù hợp, giải đang RUNNING/PAUSED/RECOVERY_REQUIRED và match vẫn PENDING để nhận kết quả đầu tiên. So khớp match/seed/hash A/B/số lượt/thời gian với snapshot job. Từ chối checksum sai, replay thiếu lifecycle, kết quả không khớp, seed/rules/placement sai hoặc board ban đầu khác online setup hiện tại.

Replay dùng cùng ReplayService để kiểm chứng chuỗi facts và final state; đây chưa phải re-run độc lập hai bot để chứng minh máy worker không gian lận. Kiểm toán/revalidation worker thuộc nhóm sau.

Giới hạn replay 15 MiB, request 22 MiB và tối đa hai upload xử lý đồng thời mỗi Central process. Có giới hạn số events theo maxTurns và board/piece/player đầu trận. Không ghi payload/source/credential vào error; lỗi trả mã an toàn.

## Biên nhận và cập nhật giải

1. Transaction giữ admission guard của queue, ghi biên nhận bất biến gồm canonical result hash, replay JSON/checksum và execution/job/match/worker. Unique execution/job/match chặn hai kết quả chính thức cho cùng job.
2. Trong cùng transaction: attempt DONE, job DONE, bỏ active marker/slot. Chỉ sau khi transaction commit mới trả ACK ACCEPTED kèm execution/job/match/resultHash.
3. Áp dụng biên nhận vào tournament dưới monitor của giải và transaction riêng: history + public replay + match FINISHED + winner/remaining pieces + audit MATCH_FINISHED + projection APPLIED. Ranking và bracket tiếp tục dùng cơ chế hiện tại.
4. Nếu lưu replay hoặc DB thất bại, rollback thay đổi RAM/match/revision/audit; biên nhận PENDING được giữ và thử lại mỗi 500 ms, tối đa 32/lượt. ACK xác nhận Central đã nhận bền vững, không khẳng định mọi projection đã xong ngay lúc đó.
5. Match bị admin thay đổi hoặc giải bị hủy trước projection không bị ghi đè: đánh dấu IGNORED/MATCH_STATE_CHANGED. Local scheduler/WorkerManager không launch match đã thuộc QUEUED/LEASED/DONE remote job.

Cùng execution, cùng canonical result và replay checksum trả lại cùng ACK, kể cả lease đã hết sau lần commit đầu tiên. Kết quả khác trả RESULT_CONFLICT. Request đầu tiên lease hết hạn bị chặn; chưa gia hạn hoặc hồi phục execution ở nhóm này.

## Outbox worker

Giữ READY_FOR_UPLOAD khi mất mạng, server lỗi hoặc ACK không đúng execution/job/match/hash. Worker kiểm tra lại checksum file trước mỗi upload, không chạy lại bot để tạo kết quả khác. Chỉ ghi ACKNOWLEDGED sau khi xác minh ACK; file replay và receiptHash vẫn giữ để đối chiếu sau restart.

--run-once gửi các bài hoàn thành còn chờ trước khi claim batch mới. State ACKNOWLEDGED không chặn batch tiếp theo; STARTING/FAILED vẫn chặn để tránh chạy trùng. --upload-pending không tự xử lý lease hết hạn hoặc chạy lại bài; recovery/crash/renewal thuộc nhóm 7.

## Kiểm chứng

- Kiểm thử backend H2/PostgreSQL: gửi đồng thời, retry sau lease, xung đột, cookie/wrong worker, checksum/metadata sai, lease hết hạn, trả slot, history/replay/audit đúng một lần, rollback và projection retry. Ranking phản ánh một trận; bracket advancement hiện tại nhận kết quả và giữ idempotency.
- Worker: mất ACK/503, ACK hash sai, retry cùng payload, checksum bị sửa, restart giữ ACKNOWLEDGED và bỏ qua file đã xác nhận; cùng bộ headless/cache/Docker Python/C++/Java trước đó.
- Migration từ V31 giữ worker cũ, receipt ban đầu rỗng; unique/FK giữ tính toàn vẹn. Có backup PostgreSQL + bot/replay trước khi nâng native DB lên V32.
- Native Central + PostgreSQL + JAR: trận Docker 12 lượt ENGINE_RESULT, ACK, DONE/APPLIED, slot về 0, kết quả/replay hiện qua web API, gửi lại vẫn một receipt và không có container còn chạy. Giải fixture được hủy riêng sau kiểm tra, replay/kết quả giữ nguyên.
- Sau restart Central: receipt/ACK/replay và kiểm tra duplicate vẫn PASS; E2E giải cũ giữ nguyên standings, frozen artifact, replay và audit.
- UI trực quan SKIPPED vì browser tools bị tắt trong Work settings. Trang setup admin và README đã cập nhật.

88 kiểm thử backend, 24 kiểm thử worker và 125 kiểm thử client PASS (0 fail/skip). Phần ownership được kiểm tra lại sau tối ưu đọc batch mỗi tick; không truy vấn riêng cho từng match trong lịch lớn. Tiếp theo là nhóm 7: lease, crash và mất mạng.
