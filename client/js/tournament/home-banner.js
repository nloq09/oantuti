import {getAccount} from '../account/account-session.js';
import {readNotificationFeed,subscribeNotificationFeed} from './notification-feed.js';
import {node,link} from './player-dom.js';
let banner;
function render(feed){const cup=feed.active?.find(t=>t.status==='RUNNING')||feed.active?.[0];if(!cup){banner?.remove();banner=null;return;}if(!banner){banner=node('aside',undefined,'panel home-tournament-banner');document.querySelector('.hero .actions')?.after(banner);}banner.replaceChildren(node('strong',cup.name),link('Vào giải','/tournament-lobby.html?id='+encodeURIComponent(cup.id)));}
try{const account=await getAccount();if(account?.role==='USER'){const unsubscribe=subscribeNotificationFeed(render);addEventListener('pagehide',()=>unsubscribe(),{once:true});render(await readNotificationFeed());}}catch{}
