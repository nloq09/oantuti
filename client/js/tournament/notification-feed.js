import {sessionRequest} from '../api/auth-api.js';
let pending,latest,receivedAt=0;
const listeners=new Set();
export function subscribeNotificationFeed(listener){listeners.add(listener);return()=>listeners.delete(listener);}
export function readNotificationFeed({force=false,signal}={}){
 if(signal?.aborted)return Promise.reject(new DOMException('Aborted','AbortError'));
 if(!force&&latest&&performance.now()-receivedAt<1000)return Promise.resolve(latest);
 if(!pending)pending=sessionRequest('/api/account/tournament-notifications').then(feed=>{latest=feed;receivedAt=performance.now();for(const listener of listeners)listener(feed);return feed;}).catch(error=>{latest=null;throw error;}).finally(()=>{pending=null;});
 if(!signal)return pending;
 // One reader leaving must not abort another reader's shared request.
 const request=pending;return new Promise((resolve,reject)=>{const abort=()=>reject(new DOMException('Aborted','AbortError'));signal.addEventListener('abort',abort,{once:true});request.then(resolve,reject).finally(()=>signal.removeEventListener('abort',abort));});
}
