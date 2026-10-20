import {adminRequest} from './admin-api.js';
export const groupList=()=>adminRequest('worker-groups');
export const createGroup=(name,reason)=>adminRequest('worker-groups',{name,reason});
export const changeGroup=(id,revision,enabled,workerId,member,reason)=>adminRequest('worker-groups/'+encodeURIComponent(id),{revision,enabled,workerId,member,reason});
export const workerPolicy=id=>adminRequest('tournaments/'+encodeURIComponent(id)+'/worker-policy');
export const saveWorkerPolicy=(id,policy)=>adminRequest('tournaments/'+encodeURIComponent(id)+'/worker-policy',policy);
export const capacityLimit=(id,revision,maxMatches,reason)=>adminRequest('workers/'+encodeURIComponent(id)+'/capacity',{revision,maxMatches,reason});
