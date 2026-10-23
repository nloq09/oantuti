export function modal(title, content, actions = []) {
  const dialog = document.createElement("dialog");
  const h = document.createElement("h2");
  h.textContent = title;
  h.id = 'dialog-title-' + Math.random().toString(36).slice(2);
  dialog.setAttribute('aria-labelledby',h.id);
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
  const previousFocus=document.activeElement;
  dialog.addEventListener("close", () => {dialog.remove();previousFocus?.focus();});
  dialog.addEventListener('keydown',event=>{
    if(event.key==='Escape'){event.preventDefault();dialog.close();return;}
    if(event.key!=='Tab')return;
    const controls=[...dialog.querySelectorAll('button,a[href],input,select,textarea,[tabindex="0"]')].filter(el=>!el.disabled&&!el.hidden);
    if(!controls.length){event.preventDefault();return;}
    const first=controls[0],last=controls.at(-1);
    if(event.shiftKey&&document.activeElement===first){event.preventDefault();last.focus();}
    else if(!event.shiftKey&&document.activeElement===last){event.preventDefault();first.focus();}
  });
  dialog.showModal();
  return dialog;
}
export const confirmAction = (title, text, run) =>
  modal(title, text, [
    { label: "Hủy" },
    { label: "Đồng ý", primary: true, run },
  ]);
