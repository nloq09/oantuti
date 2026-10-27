import {node,link} from './player-dom.js';
import {loginLink} from '../account/account-session.js';
import {tournamentRequest} from './tournament-api.js';

export async function renderRelatedContent(panel,t,key,{account,me,refresh,isCurrent=()=>panel.isConnected}){
 if(key==='my-matches'){if(!account){panel.append(link('Đăng nhập để xem trận của tôi',loginLink()));return;}const {mountMatchMonitor}=await import('./match-monitor.js');if(isCurrent())return mountMatchMonitor(panel,t,{mine:true,flatCards:true,initial:me?.matchMonitor});return;}
 if(key==='participants'||key==='ranking'&&t.format!=='TEAM_ROUND_ROBIN'){const {mountPlayerPublicPage}=await import('./player-public-pages.js');if(isCurrent())return mountPlayerPublicPage(panel,t,key);return;}
 if(key==='teams'||key==='ranking'&&t.format==='TEAM_ROUND_ROBIN'){const [{renderTeams},extra]=await Promise.all([import('./team-view.js'),tournamentRequest('/'+encodeURIComponent(t.id)+'/player-extras/teams')]);if(isCurrent())return renderTeams(panel,{...t,...extra},me,account,refresh,{...(key==='ranking'?{section:'ranking'}:{}),flatCards:true});return;}
 if(key==='disputes'){const {renderDisputes}=await import('./disputes.js');if(isCurrent())return renderDisputes(panel,t,account,refresh);return;}
 if(key==='bracket'){const {mountPublicBracket}=await import('./public-bracket.js');if(isCurrent())return mountPublicBracket(panel,t,me);return;}
 const {renderMatchPages}=await import('./match-pages.js');if(isCurrent())return renderMatchPages(panel,t,me,key==='schedule'?'schedule':key==='replays'?'replays':'matches',{flatCards:true});
}
