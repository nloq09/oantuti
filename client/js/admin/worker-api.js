import {adminRequest} from './admin-api.js';
export const listWorkers=(page=0)=>adminRequest('workers?page='+page+'&size=20');
export const issueWorkerToken=(uses,lifetimeMinutes)=>adminRequest('workers/registration-tokens',{uses,lifetimeMinutes});
export const workerTokens=()=>adminRequest('workers/registration-tokens');
export const revokeWorkerToken=id=>adminRequest('workers/registration-tokens/'+encodeURIComponent(id)+'/revoke',{});
export const reviewWorker=(id,revision,decision,trustLevel,reason)=>adminRequest('workers/'+encodeURIComponent(id)+'/review',{revision,decision,trustLevel,reason});
export const canReviewWorker=worker=>worker.approvalStatus==='PENDING_APPROVAL'&&worker.status!=='DISABLED';
export const workerPercent=value=>value==null?'—':Number(value).toFixed(1)+'%';
