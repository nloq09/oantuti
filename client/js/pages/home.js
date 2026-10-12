import { modal } from "../ui/modal.js";
import { openSettings } from "../ui/sound.js";
document.querySelector("#help").onclick = () =>
  modal(
    "Cách chơi",
    "Mỗi người có 6 Búa, 6 Bao, 6 Kéo. Đặt đủ 18 quân trong nửa bàn cờ của bạn, chia bởi đường chéo A1–I9; không đặt quân trên đường chéo hoặc cứ điểm rồi bấm Sẵn sàng. Mỗi lượt đi một ô theo 8 hướng, kể cả chéo. Búa thắng Kéo, Kéo thắng Bao, Bao thắng Búa; cùng loại thì cả hai sống và giữ nguyên vị trí, vẫn tính một lượt. Cứ điểm nằm ở góc dưới bên trái của mỗi đội (P1: A9, P2: I1). Chiếm cứ điểm đối thủ sau giao chiến hoặc loại hết quân đối thủ để thắng ngay. Cả hai hết quân thì hòa. Chọn quân rồi chọn ô được tô sáng để đi. Hết giờ server tự bỏ lượt.",
    [{ label: "Đã hiểu", primary: true }],
  );

document.querySelector("#hero-settings").onclick = openSettings;
