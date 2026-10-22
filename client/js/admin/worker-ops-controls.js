import {node,errorMessage} from './admin-shell.js';
export function field(title,input){const label=node('label',title,'worker-field');label.append(input);return label;}
export function select(items,value){const input=node('select');for(const [key,title]of items){const option=node('option',title);option.value=key;input.append(option);}if(value!==undefined)input.value=value;return input;}
export function input(type,value=''){const el=node('input');el.type=type;el.value=String(value);return el;}
export function dialog(title){const previous=document.activeElement,box=node('dialog',undefined,'worker-ops-dialog'),close=node('button','Đóng'),body=node('div'),notice=node('p');close.type='button';close.onclick=()=>box.close();notice.setAttribute('role','status');box.append(node('h2',title),close,notice,body);document.body.append(box);box.addEventListener('close',()=>{box.remove();if(previous?.isConnected)previous.focus();},{once:true});box.showModal();return{box,body,notice,close};}
export async function copy(text,notice){try{await navigator.clipboard.writeText(text);notice.textContent='Đã sao chép.';}catch{notice.textContent='Không truy cập được clipboard. Bạn có thể chọn nội dung và sao chép thủ công.';}}
export function showError(error,notice){notice.textContent=errorMessage(error);}
