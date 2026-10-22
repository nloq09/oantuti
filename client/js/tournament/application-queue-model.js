export const pageSizes=[25,50,100];
export const filterKeys=['search','affiliation','language','reason','from','until'];
export function queueFilters(search){
 const params=new URLSearchParams(search),result={};
 for(const key of filterKeys)if(params.get(key))result[key]=params.get(key);
 result.size=pageSizes.includes(Number(params.get('size')))?Number(params.get('size')):25;
 return result;
}
export function applicationQuery(page,status,filters={}){
 const params=new URLSearchParams({page:String(page)});
 if(status)params.set('status',status);
 if(filters.size&&filters.size!==25)params.set('size',String(filters.size));
 for(const key of filterKeys)if(filters[key])params.set(key,filters[key]);
 return params.toString();
}
export function bulkReviewPayload(decision,reason,revision,entries){
 const explanation=(reason||'').trim();
 if(!['APPROVED','WAITLISTED','REJECTED'].includes(decision))throw Error('INVALID_REGISTRATION_TRANSITION');
 if(!explanation||explanation.length>500)throw Error('REASON_REQUIRED');
 if(!entries.length||entries.length>100||new Set(entries.map(entry=>entry.id)).size!==entries.length)throw Error('INVALID_BULK_REVIEW');
 return {decision,reason:explanation,revision,items:entries.map(({id,status,registeredAt})=>({id,status,registeredAt}))};
}
export function localDateTime(iso){
 if(!iso||!Number.isFinite(Date.parse(iso)))return '';
 const date=new Date(iso),pad=value=>String(value).padStart(2,'0');
 return `${date.getFullYear()}-${pad(date.getMonth()+1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
}
