export function modal(title, content, actions = []) {
  const dialog = document.createElement("dialog");
  const h = document.createElement("h2");
  h.textContent = title;
  dialog.append(h);
  if (typeof content === "string") {
    const p = document.createElement("p");
    p.textContent = content;
    dialog.append(p);
  } else dialog.append(content);
  const footer = document.createElement("div");
  footer.className = "actions";
  for (const action of actions) {
    const button = document.createElement("button");
    button.textContent = action.label;
    button.className = action.primary ? "primary" : "";
    button.onclick = () => {
      dialog.close();
      action.run?.();
    };
    footer.append(button);
  }
  dialog.append(footer);
  document.body.append(dialog);
  dialog.addEventListener("close", () => dialog.remove());
  dialog.showModal();
  return dialog;
}
export const confirmAction = (title, text, run) =>
  modal(title, text, [
    { label: "Hủy" },
    { label: "Đồng ý", primary: true, run },
  ]);
