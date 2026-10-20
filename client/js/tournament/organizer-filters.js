export const matchFilters={ALL:'Tất cả',FAILED:'Trận lỗi',REVIEW:'Chờ review'};
export function matchFilter(value){return Object.hasOwn(matchFilters,value)?value:'ALL';}
export function filteredMatches(t,value){const filter=matchFilter(value);return t.matches.filter(m=>filter==='ALL'||filter==='FAILED'&&m.status==='FAILED'||filter==='REVIEW'&&Boolean(t.reviews?.[m.id]));}
export function filteredRegistrations(t,value){
 const rows=t.registrations||[];if(value==='PENDING')return rows.filter(r=>r.status==='PENDING');if(value!=='MISSING')return rows;
 const roster=new Set((t.participants||[]).map(p=>p.participantId));return rows.filter(r=>!['DISQUALIFIED','WITHDRAWN'].includes(t.participantStatuses?.[r.id])&&(r.status==='NOT_CHECKED_IN'||r.status==='APPROVED'&&(!t.timeline||roster.has(r.id))));
}
