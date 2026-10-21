import {tournamentNextAction} from './next-action.js';
import {isKnockout} from "./bracket-renderer.js";
import{roundLabel}from "./bracket-renderer.js";
export function participantNextAction(item,t,flow){return tournamentNextAction({tournament:t,flow,me:{states:[{registration:{},...item}]},account:{id:item.registration?.userId||'participant'}}).description;}
export function podium(t){if(t.status!=="FINISHED"||!t.champion)return[];const champion=t.participants.find(p=>p.participantId===t.champion.participantId);if(!champion)return[];
 if(isKnockout(t.format)){const final=t.bracket?.slice().sort((a,b)=>b.round-a.round)[0];const runner=final&&[final.slotA,final.slotB].find(id=>id&&id!==champion.participantId);return[champion,t.participants.find(p=>p.participantId===runner)].filter(Boolean);}
 return [champion,...t.ranking.filter(row=>row.participantId!==champion.participantId&&row.played>0&&!t.participantStatuses?.[row.participantId]).map(row=>t.participants.find(p=>p.participantId===row.participantId)).filter(Boolean)].slice(0,3);
}
