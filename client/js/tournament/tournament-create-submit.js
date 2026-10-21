import {createConfiguredTournament} from './create-configured-tournament.js';
export function createTournamentSubmission({configuration,create,request,add,navigate,onBusy=()=>{},onError=()=>{},canCreate=true}){
 let busy=false,draft=null;
 return async()=>{if(!canCreate||busy||draft)return;busy=true;onBusy(true);try{const result=await createConfiguredTournament(configuration(),{create,request,add,onCreated:value=>draft=value});navigate('/tournament.html?id='+encodeURIComponent(result.id));}
  catch(error){onError(error,draft);if(!draft){busy=false;onBusy(false);}}};
}
