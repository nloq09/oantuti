import {mountTournamentWorkers} from './tournament-worker-view.js';
import {organizerPageShell} from './organizer-shell.js';
const id=new URLSearchParams(location.search).get('id');
const view=mountTournamentWorkers(document.querySelector('#tournament-workers'),{id,onTournament:t=>organizerPageShell(t,'workers')});
addEventListener('pagehide',event=>{if(!event.persisted)view.dispose();});
