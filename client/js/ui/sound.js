import { modal } from "./modal.js";
let settings;
try {
  settings = {
    sound: true,
    music: false,
    animation: true,
    ...JSON.parse(localStorage.getItem("rps-settings") || "{}"),
  };
} catch {
  settings = { sound: true, music: false, animation: true };
}
export { settings };
let context, musicTimer;
function tone(frequency, duration = 0.12, volume = 0.035) {
  if (!context) context = new AudioContext();
  context.resume().catch(() => {});
  const oscillator = context.createOscillator(),
    gain = context.createGain();
  oscillator.connect(gain);
  gain.connect(context.destination);
  oscillator.frequency.value = frequency;
  gain.gain.setValueAtTime(volume, context.currentTime);
  gain.gain.exponentialRampToValueAtTime(0.001, context.currentTime + duration);
  oscillator.start();
  oscillator.stop(context.currentTime + duration);
}
export function sound(name) {
  if (settings.sound)
    try {
      tone(
        { select: 550, move: 350, combat: 110, victory: 880, defeat: 180 }[
          name
        ] || 400,
      );
    } catch {}
}
export function unlock() {
  if (settings.music && !musicTimer) {
    let note = 0;
    musicTimer = setInterval(() => {
      try {
        tone([220, 277, 330, 277][note++ % 4], 1.4, 0.008);
      } catch {}
    }, 1800);
  }
}
export function openSettings() {
  const body = document.createElement("div");
  for (const [key, label] of [
    ["sound", "Âm thanh"],
    ["music", "Nhạc nền"],
    ["animation", "Hiệu ứng"],
  ]) {
    const row = document.createElement("label");
    row.className = "setting";
    const input = document.createElement("input");
    input.type = "checkbox";
    input.checked = settings[key];
    input.onchange = () => {
      settings[key] = input.checked;
      localStorage.setItem("rps-settings", JSON.stringify(settings));
      if (!settings.music) {
        clearInterval(musicTimer);
        musicTimer = null;
      }
      unlock();
    };
    row.append(input, document.createTextNode(label));
    body.append(row);
  }
  modal("Cài đặt", body, [{ label: "Xong", primary: true }]);
}
document.addEventListener("pointerdown", unlock, { once: true });
window.addEventListener("pagehide", () => clearInterval(musicTimer));
