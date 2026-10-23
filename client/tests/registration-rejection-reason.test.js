import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {setup,descendants} from './helpers/tournament-dom.js';
import {node,link} from '../js/tournament/player-dom.js';
const source=await readFile(new URL('../js/tournament/player-detail.js',import.meta.url),'utf8');
const start=source.indexOf('function myStatus('),end=source.indexOf('\nfunction readyCard(',start);
assert.ok(start>=0&&end>start);
function render(reason){
 const state={me:{states:[{status:'REJECTED',rejectionReason:reason,registration:{id:'r',displayName:'An',controllerType:'HUMAN'}}]}};
 const deps={node,link,account:{id:'u'},flow:null,load:()=>{},state,participantLabels:{REJECTED:'Đăng ký bị từ chối'},primaryAction:null,tournamentNextAction:()=>({description:'Xem trạng thái đăng ký'}),readyCard:()=>{},withdrawalControl:()=>{},registrationForm:()=>{}};
 const myStatus=new Function(...Object.keys(deps),source.slice(start,end)+';return myStatus;')(...Object.values(deps));
 const root=setup();myStatus(root,{id:'cup'});return descendants(root).map(n=>n.textContent).join(' ');
}
test('rejection card renders personal reason without access to organizer audit',()=>{
 assert.match(render('Thiếu thông tin trường học'),/Thiếu thông tin trường học/);
 assert.doesNotMatch(render('Thiếu thông tin trường học'),/chưa cung cấp lý do/);
 assert.match(render(null),/Chủ giải chưa cung cấp lý do từ chối/);
});
