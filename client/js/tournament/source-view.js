import {languageText} from './player-labels.js';
import {tournamentRoute} from './tournament-routes.js';
import {requireAccount} from '../account/account-session.js';
import {sessionRequest} from '../api/auth-api.js';
import {renderTournamentShell} from './player-shell.js';
import {node,link} from './player-dom.js';
import {phaseFacts} from './phase-view.js';
import {updateHeaderTimers} from './tournament-header.js';
import {playerSurface} from './player-surface.js';
const root=document.querySelector('main'),params=new URLSearchParams(location.search),id=params.get('id'),version=params.get('version'),base='/api/tournaments/'+encodeURIComponent(id);
let sourceRead=0;
async function load(){const ticket=++sourceRead;root.replaceChildren(node('p','Đang tải source chỉ đọc…'));try{
 if(!version)throw Error('MISSING_VERSION');
 const account=await requireAccount();if(!account)return;
 const [t,source]=await Promise.all([sessionRequest(base),sessionRequest(base+'/workspace/versions/'+encodeURIComponent(version)+'/source')]);
 const flow=t.timeline?await sessionRequest(base+'/participant-flow'):null;
 if(ticket!==sourceRead)return;root.replaceChildren();renderTournamentShell(root,{tournament:t,flow});const back=link('← Quay lại giải đấu',tournamentRoute(id));back.className='workspace-back';root.append(back);
 const panel=playerSurface(root,'source-snapshot');panel.append(node('h2','Source phiên bản · Chỉ đọc'),node('p','Nội dung snapshot bất biến; trang này không lưu, build, test hoặc nộp bài.'));phaseFacts(panel,[['Phiên bản','v'+source.version],['Ngôn ngữ',languageText(source.language)]]);
 if(source.sourceAvailable){const pre=node('pre',source.sourceCode);pre.tabIndex=0;pre.setAttribute('aria-label','Source code chỉ đọc');panel.append(pre);}else panel.append(node('p','Bot được nhập từ JAR; không có source để hiển thị.'));
 root.append(panel);
 }catch{if(ticket!==sourceRead)return;root.replaceChildren(node('h1','Không tải được source phiên bản'),node('p','Phiên bản không tồn tại, chưa được phép xem hoặc không thuộc tài khoản này.'));const retry=node('button','Thử lại');retry.onclick=load;root.append(retry,link('Về chi tiết giải',tournamentRoute(id)));}}
await load();
let clock;const startClock=()=>{clearInterval(clock);if(!document.hidden){updateHeaderTimers(root);clock=setInterval(()=>updateHeaderTimers(root),1000);}};
startClock();document.addEventListener('visibilitychange',startClock);
addEventListener('pageshow',event=>{if(event.persisted){startClock();load();}});
addEventListener('pagehide',event=>{sourceRead++;clearInterval(clock);if(!event.persisted)document.removeEventListener('visibilitychange',startClock);});
