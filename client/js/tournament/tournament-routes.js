const screens={detail:'/tournament-detail.html',lobby:'/tournament-lobby.html',code:'/tournament-code.html',organizer:'/tournament.html'};
const tabs=new Set(['overview','registration','coding','validation','check-in','my-match','my-matches','teams','participants','schedule','bracket','ranking','matches','replays','rules','disputes','tournament-rules','tournament-phase-content','player-submission-detail','player-validation-failure']);
export function tournamentRoute(id,screen='detail',{tab='',stay=false}={}){
 const params=new URLSearchParams({id:String(id)});if(stay)params.set('stay','1');return (screens[screen]||screens.detail)+'?'+params+(tabs.has(tab)?'#'+tab:'');
}
export const playerTournamentRoute=(t,options)=>tournamentRoute(t.id,t.status==='RUNNING'?'lobby':'detail',options);
export const ownerTournamentRoute=(t,account)=>account?.id&&t.ownerUserId===account.id?tournamentRoute(t.id,'organizer'):null;
// Workspace source access can remain read-only after coding. Follow the server's
// phase independently of that permission; a local countdown never opens check-in.
export const workspacePhaseDestination=(id,flow)=>['CHECK_IN_OPEN','READY_TO_START'].includes(flow?.phase)?tournamentRoute(id,'detail',{tab:'check-in'}):null;
export function actionDestination(action,t){
 if(action.href?.startsWith('#'))return tournamentRoute(t.id,'detail',{tab:action.href.slice(1)});
 // Cards outside a participant screen navigate to the current state; they never execute commands.
 return action.href||playerTournamentRoute(t,{tab:'tournament-phase-content'});
}
