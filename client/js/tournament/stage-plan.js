import {node} from './player-dom.js';
export const stagePlanText=plans=>(plans||[]).map((plan,index)=>`Giai đoạn ${index+1}: ${plan.groups} bảng, top ${plan.qualifyTopN} mỗi bảng`).concat('Loại trực tiếp').join(' → ');
export function validateStagePlan(stages,entrants){
 if(!Array.isArray(stages)||!stages.length||stages.length>8)throw Error('Chọn từ 1 đến 8 giai đoạn vòng bảng.');
 for(const stage of stages){const {groups,qualifyTopN}=stage;if(!Number.isInteger(groups)||!Number.isInteger(qualifyTopN)||groups<1||groups>16||qualifyTopN<1||qualifyTopN>16||Math.floor(entrants/groups)<Math.max(2,qualifyTopN)||groups*qualifyTopN<2)throw Error('Mỗi bảng cần ít nhất 2 người và đủ suất top N; cần ít nhất 2 người vào giai đoạn sau.');entrants=groups*qualifyTopN;}
 return stages;
}
export function stagePlanEditor(root,initial=[{groups:2,qualifyTopN:2}]){
 const rows=node('div'),add=node('button','Thêm giai đoạn vòng bảng');add.type='button';root.append(node('p','Các giai đoạn vòng bảng chạy theo thứ tự, sau cùng là loại trực tiếp. Danh sách thực tế khi khóa giải phải đủ cho cấu hình này.'),rows,add);
 const items=[];
 function row(value){const box=node('div',undefined,'actions'),groups=node('input'),top=node('input'),remove=node('button','Bỏ giai đoạn');for(const input of [groups,top]){input.type='number';input.min=1;input.max=16;input.required=true;}groups.value=value.groups;top.value=value.qualifyTopN;const gl=node('label','Số bảng'),tl=node('label','Top N mỗi bảng');gl.append(groups);tl.append(top);remove.type='button';const item={box,groups,top};items.push(item);remove.onclick=()=>{if(items.length===1)return;items.splice(items.indexOf(item),1);box.remove();add.disabled=false;};box.append(gl,tl,remove);rows.append(box);add.disabled=items.length>=8;}
 initial.forEach(row);add.onclick=()=>row({groups:1,qualifyTopN:2});return()=>items.map(item=>({groups:Number(item.groups.value),qualifyTopN:Number(item.top.value)}));
}
