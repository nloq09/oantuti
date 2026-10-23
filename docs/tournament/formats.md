# Các thể thức giải đấu

Source hỗ trợ đúng năm giá trị: `ROUND_ROBIN`, `SINGLE_ELIMINATION`, `GROUP_KNOCKOUT`, `SWISS`, `TEAM_ROUND_ROBIN`. Chế độ ai điều khiển (`BOT_ONLY`, `HUMAN_ONLY`, `HUMAN_OR_BOT`) và cách nộp bot là cấu hình riêng, không phải thể thức ghép trận.

## BO1, BO3, BO5

Mỗi cặp đấu có tối đa 1, 3 hoặc 5 ván. Ván hòa không cộng một ván thắng cho bên nào. Cặp kết thúc sớm khi một bên đạt số ván thắng cần thiết (1/2/3), hoặc khi đã chơi đủ tối đa; lúc đó so số ván thắng. Vì có ván hòa, BO3 có thể kết thúc 1–0 sau ba ván. Không tự kéo dài vô hạn để tìm người thắng.

Tỷ số cặp đấu dùng cho điểm/đi tiếp, không cộng điểm bảng riêng cho từng ván. Mỗi ván có trận và replay riêng. Người tự chơi có ready check mới khi được cấp ván tiếp theo. Forfeit có thể quyết định cả cặp đấu, không chỉ một ván.

## Vòng tròn — ROUND_ROBIN

- Mỗi cặp gặp nhau một lần; với n người có n×(n−1)/2 cặp đấu trước khi tách thành ván BO.
- Chia lịch theo vòng. Nếu số người lẻ, một người nghỉ (BYE) trong vòng đó; không tạo phòng giả hay cộng điểm cho lượt nghỉ vòng tròn.
- Thắng cặp được 3 điểm, hòa 1, thua 0. Xếp hạng theo thứ tự tie-break đã cấu hình.
- Hòa cặp được giữ là hòa; không dùng hạt giống để biến thành một trận thắng như vòng loại trực tiếp.

## Loại trực tiếp — SINGLE_ELIMINATION

- Ghép theo hạt giống vào cây có kích thước lũy thừa 2; vị trí thiếu tạo BYE để người còn lại đi tiếp.
- Thua cặp thì dừng bước. Không có nhánh thua/double elimination trong enum hiện tại.
- Nếu cặp hòa, so tổng quân còn lại của các ván; vẫn bằng nhau thì hạt giống tốt hơn (số nhỏ hơn) đi tiếp.
- Người đi tiếp do bracket server xác định. Trận lỗi hạ tầng hoặc đang review không được tự gán người thắng để lấp nhánh.

## Vòng bảng rồi loại trực tiếp — GROUP_KNOCKOUT

- Có một hoặc nhiều giai đoạn vòng bảng theo kế hoạch, sau đó một giai đoạn loại trực tiếp.
- Mặc định hai bảng, lấy top 2 mỗi bảng. Có thể cấu hình số bảng và top N; source kiểm tra sức chứa hợp lệ và tối đa 8 giai đoạn vòng bảng.
- Mỗi bảng thi đấu vòng tròn và xếp điểm/tie-break riêng. Người đủ điều kiện được đưa sang giai đoạn tiếp theo bằng qualification snapshot.
- BYE trong phần vòng bảng là lượt nghỉ; phần knockout áp dụng BYE/luật hòa của loại trực tiếp.
- Đây không phải trình tạo đồ thị tùy ý trộn mọi thể thức; không tự suy diễn có Swiss trong giữa kế hoạch này.

## Swiss — SWISS

- Tất cả không bị loại chỉ vì thua một cặp; các vòng sau ưu tiên ghép đối thủ gần điểm và tránh cặp đã gặp.
- Số vòng ban đầu theo `ceil(log2(n))`. Giải có thể kết thúc sớm nếu không còn đủ người hợp lệ hoặc không có ghép cặp mới khả thi.
- Khi lẻ người, thuật toán tìm BYE trong nhóm cuối bảng chưa từng nhận BYE, đồng thời phải bảo đảm phần còn lại ghép được. BYE được **3 điểm**, không có phòng/replay giả.
- Cặp đấu bình thường: thắng 3, hòa 1, thua 0. Có Buchholz là tổng điểm của các đối thủ đã gặp; lượt BYE không tạo một đối thủ giả để tính tổng này.
- Xem bảng điểm/ghép cặp theo vòng thay vì bracket loại trực tiếp. BO1/BO3/BO5 vẫn áp dụng cho từng cặp.

## Đồng đội / trường — TEAM_ROUND_ROBIN

- Có ít nhất hai đội hợp lệ; mỗi đội có ít nhất ba người đã có suất, không trùng thành viên giữa các đội. Các đội gặp nhau theo lịch vòng tròn.
- Mỗi cuộc gặp hai đội có ba vị trí. Đội trưởng nộp đội hình ba người; trước khóa, đội hình chỉ hiển thị theo quyền tương ứng.
- Cửa sổ đội hình hiện tại 300 giây; hết hạn mà chưa đủ, server chọn từ roster theo quy tắc active trước. Không coi việc này là quyền thay roster sau khóa.
- Mỗi vị trí là một cặp BO. Thắng vị trí được 2 điểm bàn, hòa 1, thua 0; so tổng ba vị trí để quyết định cuộc gặp đội.
- Thắng cuộc gặp đội được 3 điểm xếp hạng đội, hòa 1, thua 0. Tie-break đội dùng hiệu số điểm bàn rồi khóa ổn định; không dùng bảng điểm cá nhân để quyết định vô địch đội.
- Khi số đội lẻ có lượt nghỉ theo lịch vòng tròn. Không có bracket knockout đồng đội riêng trong thể thức này.

Đọc tiếp: [Bracket và ranking](bracket-ranking.md), [check-in](check-in.md), [luồng người chơi](../player/tournament-flow.md).

## Nguồn đối chiếu

- [TournamentFormat](../../server/src/main/java/com/rpsbattle/tournament/domain/TournamentFormat.java), [TournamentSeries](../../server/src/main/java/com/rpsbattle/tournament/domain/TournamentSeries.java).
- [RoundRobinScheduler](../../server/src/main/java/com/rpsbattle/tournament/scheduler/RoundRobinScheduler.java), [SingleEliminationScheduler](../../server/src/main/java/com/rpsbattle/tournament/scheduler/SingleEliminationScheduler.java), [GroupKnockoutScheduler](../../server/src/main/java/com/rpsbattle/tournament/scheduler/GroupKnockoutScheduler.java), [GroupRoundPlan](../../server/src/main/java/com/rpsbattle/tournament/domain/GroupRoundPlan.java).
- [SwissScheduler](../../server/src/main/java/com/rpsbattle/tournament/scheduler/SwissScheduler.java), [TeamScheduler](../../server/src/main/java/com/rpsbattle/tournament/scheduler/TeamScheduler.java), [ClassicScoringRule](../../server/src/main/java/com/rpsbattle/tournament/ranking/ClassicScoringRule.java).
