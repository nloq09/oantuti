// Configuration remains separate revision-checked server commands. Never retry creation on partial failure.
export async function createConfiguredTournament({name,settings,stages,timeline,timing,bots=[]},{create,request,add,onCreated=()=>{}}){
 let t=await create(name,settings);onCreated(t);
 if(stages){t=await request('/api/tournaments/'+encodeURIComponent(t.id)+'/stage-plan',{revision:t.revision,stages});onCreated(t);}
 if(timeline){const configured=await request('/api/tournaments/'+encodeURIComponent(t.id)+'/timeline',{revision:t.revision,timeline},'PUT');t={...t,revision:configured.revision};onCreated(t);}
 else if(timing&&Object.values(timing).some(Boolean))await request('/api/tournaments/'+encodeURIComponent(t.id)+'/timing',{revision:t.revision,...timing});
 if(!timeline)for(const bot of bots)await add(t.id,bot);
 return t;
}
