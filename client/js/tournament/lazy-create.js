import {node} from './player-dom.js';
async function loadCreate(){
 const [{getAccount},form,api,{sessionRequest},bots]=await Promise.all([import('../account/account-session.js'),import('./tournament-create-form.js'),import('./tournament-api.js'),import('../api/auth-api.js'),import('../api/bot-api.js')]);
 const account=await getAccount();if(account)await bots.refreshBots();
 return root=>form.mountTournamentCreate(root,{account,bots:bots.savedBots(),create:api.createTournament,request:sessionRequest,add:api.addParticipant});
}
export function lazyTournamentCreate(root,{load=loadCreate}={}){
 let pending,ready=false,disposed=false;
 const status=node('p');status.setAttribute('role','status');status.hidden=true;root.prepend(status);
 function ensure(){
  if(disposed||ready)return Promise.resolve();if(pending)return pending;
  status.hidden=false;status.textContent='Đang chuẩn bị form tạo giải…';root.querySelector('form')?.setAttribute('inert','');root.setAttribute('aria-busy','true');
  pending=Promise.resolve().then(load).then(mount=>{if(disposed||!root.isConnected)return;mount(root);ready=true;status.hidden=true;root.querySelector('form')?.removeAttribute('inert');}).catch(()=>{if(disposed)return;const retry=node('button','Thử lại');retry.type='button';retry.onclick=ensure;status.replaceChildren(node('span','Chưa tải được form tạo giải. Dữ liệu đã nhập được giữ. '),retry);}).finally(()=>{pending=null;root.removeAttribute('aria-busy');});
  return pending;
 }
 return{ensure,dispose(){disposed=true;}};
}
