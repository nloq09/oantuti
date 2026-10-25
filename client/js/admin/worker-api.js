import {adminRequest} from './admin-api.js';
export const listWorkers=(page=0)=>adminRequest('workers?page='+page+'&size=20');
export const issueWorkerToken=(uses,lifetimeMinutes)=>adminRequest('workers/registration-tokens',{uses,lifetimeMinutes});
export const workerTokens=()=>adminRequest('workers/registration-tokens');
export const revokeWorkerToken=id=>adminRequest('workers/registration-tokens/'+encodeURIComponent(id)+'/revoke',{});
export const reviewWorker=(id,revision,decision,trustLevel,reason)=>adminRequest('workers/'+encodeURIComponent(id)+'/review',{revision,decision,trustLevel,reason});
export const canReviewWorker=worker=>worker.approvalStatus==='PENDING_APPROVAL'&&worker.status!=='DISABLED';
export const workerPercent=value=>value==null?'—':Number(value).toFixed(1)+'%';
export const controlWorker=(id,revision,action,reason)=>adminRequest('workers/'+encodeURIComponent(id)+'/control',{revision,action,reason});
export const workerEvents=id=>adminRequest('workers/'+encodeURIComponent(id)+'/events');
export function workerControls(row){
 if(row.credentialRevoked)return [];
 if(row.approvalStatus!=='APPROVED')return [['Thu hồi quyền','REVOKE']];
 const actions=[];
 if(['DRAINING','DRAINED','DISABLED'].includes(row.status))actions.push(['Bật lại (khởi động lại agent)','RESUME']);
 else if(['ONLINE','BUSY','DEGRADED','STARTING'].includes(row.status))actions.push(['Drain · hoàn tất trận hiện tại','DRAIN']);
 if(row.status!=='DISABLED')actions.push(['Vô hiệu hóa · hủy execution hiện tại','DISABLE']);
 if(['OFFLINE','DISABLED'].includes(row.status))actions.push(['Thu hồi quyền vĩnh viễn','REVOKE']);
 return actions;
}
export const workerOperations=params=>adminRequest('workers/operations?'+new URLSearchParams(params));
export const workerOperationDetail=id=>adminRequest('workers/operations/'+encodeURIComponent(id));
export const workerCapacity=(id,params)=>adminRequest('tournaments/'+encodeURIComponent(id)+'/worker-capacity?'+new URLSearchParams(params));

export const workerMetrics=(tournamentId)=>adminRequest('workers/metrics'+(tournamentId?'?tournamentId='+encodeURIComponent(tournamentId):''));
