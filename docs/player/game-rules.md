# Luật chơi RPS Battle 9×9

Mục tiêu của bạn là **chiếm cứ điểm đối phương hoặc loại hết quân đối phương**, đồng thời bảo vệ cứ điểm của mình. Đây là luật bàn cờ đang dùng; luật lịch thi đấu và điểm giải được xem riêng tại **Luật giải**.

## Bàn cờ và quân

Mỗi bên có **18 quân: 6 Búa (`ROCK`), 6 Bao (`PAPER`), 6 Kéo (`SCISSORS`)**. Hai bên là P1 và P2; P1 đi trước khi trận bắt đầu.

Tọa độ chuẩn dùng cột A–I từ trái sang phải, hàng 1–9 từ trên xuống. Trong dữ liệu bot, `(row, col)` bắt đầu từ 0: A1 là `(0,0)`, I9 là `(8,8)`. Khi giao diện xoay theo người xem, tọa độ server vẫn giữ nguyên.

## Xếp đội hình

- Đường chéo **A1–I9** chia hai vùng đặt quân: P1 có `row > col`, P2 có `row < col` theo tọa độ server.
- Không đặt trên đường chéo, ra ngoài vùng của mình, vào ô đã có quân hoặc vào cứ điểm.
- Cứ điểm P1 là **A9 `(8,0)`**, cứ điểm P2 là **I1 `(0,8)`**. Đây đều là góc dưới bên trái khi nhìn từ phía đội đó.
- Đặt đủ 6 quân mỗi loại rồi bấm **Sẵn sàng**. Cả hai hoàn tất thì ván bắt đầu. **Ngẫu nhiên** cũng tuân thủ vùng đặt quân và để trống cứ điểm.

Vùng chia chéo chỉ giới hạn lúc xếp quân. Khi đang chơi, quân được vượt đường chéo và có thể vào cứ điểm của mình để phòng thủ.

## Di chuyển và giao chiến

Mỗi lượt, chọn một quân sống của mình và đi **đúng một ô theo bất kỳ hướng nào trong 8 hướng**: ngang, dọc hoặc chéo. Không được đứng yên bằng một nước MOVE, đi xa hơn một ô, ra ngoài bàn hoặc vào ô có quân cùng đội.

Đi vào ô có quân đối thủ sẽ giao chiến:

- **Búa thắng Kéo.**
- **Kéo thắng Bao.**
- **Bao thắng Búa.**

Nếu quân tấn công thắng, quân phòng thủ bị loại và quân tấn công đứng ở ô đích. Nếu quân phòng thủ thắng, quân tấn công bị loại và quân phòng thủ ở lại. **Cùng loại thì cả hai sống, giữ nguyên vị trí; lượt vẫn được tính.** Không có luật hai quân cùng loại chết cả đôi.

Ví dụ: Búa tấn công Kéo đang giữ cứ điểm thì Búa sống, đứng trên cứ điểm và có thể thắng ngay. Búa tấn công Búa giữ cứ điểm thì không chiếm được ô đó.

## Khi nào ván kết thúc?

1. Quân đối phương còn sống và đứng trên cứ điểm của bạn sau khi xử lý giao chiến: bạn thua ngay.
2. Bạn không còn quân sống: bạn thua. Nếu cả hai cùng không còn quân, kết quả là hòa.
3. **Đầu hàng:** đối thủ thắng. Trong trận online, bạn có thể đầu hàng cả khi chưa đến lượt mình.
4. Chạm giới hạn mặc định **200 lượt tổng cộng**: bên còn nhiều quân sống hơn thắng; bằng nhau thì hòa. Một lượt là một lần hành động của một bên, không phải một vòng gồm hai bên.

Một cặp đấu trong giải có thể gồm nhiều ván. Hòa một ván, tie-break của cặp đấu và điểm bảng xếp hạng theo cấu hình giải; xem [Luồng giải đấu](tournament-flow.md).

## Bỏ lượt, hết giờ và nước không hợp lệ

Người tự chơi có thể dùng **Bỏ lượt** khi đến lượt; hành động này tiêu tốn lượt. Thời gian lượt online mặc định **30 giây**, do server cấu hình; hết giờ server tự bỏ lượt cho ghế người chơi. Đó không phải xử thua ngay lập tức.

Nước không hợp lệ bị server từ chối và không áp dụng lên bàn. Chọn lại từ các ô được tô sáng sau khi trạng thái đã đồng bộ; không gửi tiếp nước dựa trên bàn cờ cũ.

Bot có cơ chế riêng: chỉ chọn hành động trong `legalActions`; lỗi phản hồi, timeout hoặc hành động không được chấp nhận có thể làm bot thua ván. [Quy tắc protocol](../bot/protocol.md) giải thích sự khác nhau giữa decoder và quyền hành động trong từng runner.

**Rời phòng** khi ván đang chạy được tính là đầu hàng. Mất mạng tạm thời không tự dừng đồng hồ; trình duyệt sẽ thử kết nối lại. [Cách chơi](how-to-play.md) có hướng dẫn vào lại phòng.

## Nguồn đối chiếu

Đối chiếu ngày 23/10/2026:

- [ClassicRuleSet](../../server/src/main/java/com/rpsbattle/rules/classic/ClassicRuleSet.java), [placement](../../server/src/main/java/com/rpsbattle/rules/classic/ClassicPlacementRule.java), [movement](../../server/src/main/java/com/rpsbattle/rules/classic/ClassicMovementRule.java), [combat](../../server/src/main/java/com/rpsbattle/rules/classic/ClassicCombatRule.java), [victory](../../server/src/main/java/com/rpsbattle/rules/classic/ClassicVictoryRule.java).
- [OnlineRuleSet](../../server/src/main/java/com/rpsbattle/rules/online/OnlineRuleSet.java), [OnlineMatch](../../server/src/main/java/com/rpsbattle/online/OnlineMatch.java), [OnlineMatchService](../../server/src/main/java/com/rpsbattle/online/OnlineMatchService.java).
- [Giao diện bàn cờ](../../client/game.html) và [controller](../../client/js/game/game-controller.js).
