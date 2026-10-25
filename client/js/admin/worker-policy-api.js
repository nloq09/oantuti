import {adminRequest} from './admin-api.js';
export const workerPolicy=id=>adminRequest('tournaments/'+encodeURIComponent(id)+'/worker-policy');
export const saveWorkerPolicy=(id,policy)=>adminRequest('tournaments/'+encodeURIComponent(id)+'/worker-policy',policy);
export const capacityLimit=(id,revision,maxMatches,reason)=>adminRequest('workers/'+encodeURIComponent(id)+'/capacity',{revision,maxMatches,reason});
