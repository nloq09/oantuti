import { modal } from "../ui/modal.js";
import { openSettings } from "../ui/sound.js";
document.querySelector("#settings").onclick = openSettings;
document.querySelector("#help").onclick = () =>
  modal(
    "Cách chơi",
    "Mỗi người có 6 Búa, 6 Bao, 6 Kéo. Đặt đủ 18 quân trong vùng của bạn rồi bấm Sẵn sàng. Búa thắng Kéo, Kéo thắng Bao, Bao thắng Búa; cùng loại loại bỏ cả hai. Chọn quân rồi chọn ô được tô sáng để đi. Hết giờ server tự bỏ lượt. Loại hết quân đối thủ để thắng.",
    [{ label: "Đã hiểu", primary: true }],
  );
