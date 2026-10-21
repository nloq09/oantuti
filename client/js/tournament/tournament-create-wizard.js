import {node} from './player-dom.js';
export const createSteps=['Thể thức','Người tham gia','Bot','Timeline','Thi đấu','Xác nhận'];
export function mountCreateWizard({form,panels,stepper,progress,back,next,submit,validateStep,review=()=>{},onSubmit,canCreate=true}){
 let current=0,reached=0,busy=false;const buttons=[];
 for(const [index,label]of createSteps.entries()){const li=node('li'),button=node('button');button.type='button';button.onclick=()=>{if(!busy&&index<=reached)show(index,true);};li.append(button);stepper.append(li);buttons.push(button);}
 function show(index,focus=false){current=index;reached=Math.max(reached,index);panels.forEach((panel,i)=>panel.hidden=i!==current);buttons.forEach((button,i)=>{button.textContent=(i<reached&&i!==current?'✓ ':i===current?'● ':'○ ')+createSteps[i];button.dataset.state=i===current?'CURRENT':i<reached?'COMPLETED':i<=reached?'AVAILABLE':'LOCKED';button.disabled=busy||i>reached;button.setAttribute('aria-current',i===current?'step':'false');});back.disabled=busy||current===0;next.hidden=current===5;next.disabled=busy||!canCreate;submit.hidden=current!==5;submit.disabled=busy||!canCreate;progress.textContent=`Bước ${current+1} / 6 · ${createSteps[current]}`;if(index===5)review();if(focus)panels[index].querySelector('h3')?.focus();}
 function check(index){panels.forEach((panel,i)=>panel.hidden=i!==index);const valid=validateStep(index)!==false;if(!valid){reached=index;show(index,true);return false;}return true;}
 function advance(){if(busy||!canCreate||current===5)return false;if(!check(current))return false;show(current+1,true);return true;}
 back.onclick=()=>{if(!busy&&current>0)show(current-1,true);};next.onclick=advance;
 form.addEventListener('input',()=>{if(!busy){reached=current;buttons.forEach((button,i)=>button.disabled=i>reached);}});
 form.addEventListener('change',()=>{if(!busy){reached=current;buttons.forEach((button,i)=>button.disabled=i>reached);}});
 form.onsubmit=async event=>{event.preventDefault();if(busy||!canCreate)return;if(current!==5){advance();return;}for(let i=0;i<5;i++)if(!check(i))return;show(5);await onSubmit();};
 show(0);return{get step(){return current;},advance,show,setBusy(value){busy=value;show(current);}};
}
