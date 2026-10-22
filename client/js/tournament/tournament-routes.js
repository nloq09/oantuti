const screens={detail:'/tournament-detail.html',lobby:'/tournament-lobby.html',code:'/tournament-code.html',organizer:'/tournament.html'};
const tabs=new Set(['overview','my-match','my-matches','teams','participants','schedule','bracket','ranking','matches','replays','rules','disputes','tournament-rules','tournament-phase-content','player-submission-detail']);
export function tournamentRoute(id,screen='detail',{tab='',stay=false}={}){
 const params=new URLSearchParams({id:String(id)});if(stay)params.set('stay','1');return (screens[screen]||screens.detail)+'?'+params+(tabs.has(tab)?'#'+tab:'');
}
export const playerTournamentRoute=(t,options)=>tournamentRoute(t.id,t.status==='RUNNING'?'lobby':'detail',options);
export function actionDestination(action,t){
 if(action.href?.startsWith('#'))return tournamentRoute(t.id,'detail',{tab:action.href.slice(1)});
 // Cards outside a participant screen navigate to the current state; they never execute commands.
 return action.href||playerTournamentRoute(t,{tab:'tournament-phase-content'});
}
