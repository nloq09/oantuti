/** Read-only invalidation queue. Never retries a mutation or applies WebSocket payloads as page state. */
export function createTournamentRefresh(read,{tournamentId=null,interval=5000,connected=()=>true,onError=()=>{},immediate=true}={},runtime={}){
 const events=runtime.events||globalThis,doc=runtime.document||globalThis.document,set=runtime.setTimeout||((fn,ms)=>setTimeout(fn,ms)),clear=runtime.clearTimeout||(id=>clearTimeout(id));
 let disposed=false,paused=false,timer,flight=null,pending=null,epoch=0;
 const defer=()=>{let resolve;const promise=new Promise(done=>resolve=done);return{promise,resolve,force:false,reason:'poll'};};
 function schedule(){clear(timer);if(!disposed&&!paused&&!doc.hidden)timer=set(()=>request(false,'poll'),interval);}
 function drain(){
  if(disposed||paused||flight||!pending)return;if(!connected()){dispose();return;}if(doc.hidden)return;
  const batch=pending;pending=null;clear(timer);const generation=++epoch,abort=new AbortController();flight={batch,abort,generation};
  const current=()=>!disposed&&!paused&&epoch===generation&&!abort.signal.aborted&&connected();
  Promise.resolve().then(()=>current()?read({force:batch.force,reason:batch.reason,signal:abort.signal,isCurrent:current}):undefined).catch(error=>{if(current()&&error.name!=='AbortError')onError(error);}).finally(()=>{
   batch.resolve();if(flight?.generation!==generation)return;flight=null;if(pending)drain();else schedule();
  });
 }
 function request(force=false,reason='manual',{replace=false}={}){
  if(disposed)return Promise.resolve();if(replace&&flight){epoch++;const old=flight;flight=null;old.abort.abort();old.batch.resolve();}clear(timer);if(!pending)pending=defer();pending.force||=force;pending.reason=reason;const promise=pending.promise;drain();return promise;
 }
 function suspend(){paused=true;clear(timer);epoch++;const old=flight;flight=null;old?.abort.abort();old?.batch.resolve();pending?.resolve();pending=null;}
 function resume(){if(disposed)return;paused=false;request(true,'resume');}
 function invalidate(event){if(tournamentId&&event.detail?.id!==tournamentId)return;request(false,'invalidation');}
 function visibility(){if(doc.hidden){clear(timer);return;}request(true,'visible',{replace:true});}
 function online(){request(true,'online',{replace:true});}
 function hide(event){if(event.persisted)suspend();else dispose();}
 function show(event){if(event.persisted)resume();}
 function dispose(){if(disposed)return;suspend();disposed=true;for(const [key,fn]of bindings)events.removeEventListener(key,fn);doc.removeEventListener('visibilitychange',visibility);}
 const bindings=[['online',online],['rps-tournament-update',invalidate],['pagehide',hide],['pageshow',show]];
 for(const [key,fn]of bindings)events.addEventListener(key,fn);doc.addEventListener('visibilitychange',visibility);if(immediate)request(false,'initial');
 return{request,invalidate:()=>request(false,'invalidation'),dispose};
}
