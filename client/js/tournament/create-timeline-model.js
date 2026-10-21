export const timelineFields=[
 ['registrationOpenAt','Mở đăng ký'],['registrationCloseAt','Đóng đăng ký'],
 ['codingOpenAt','Mở coding'],['codingCloseAt','Đóng coding'],
 ['validationStartAt','Bắt đầu kiểm thử'],['validationDeadline','Hạn kiểm thử'],
 ['checkInOpenAt','Mở check-in'],['checkInCloseAt','Đóng check-in'],
 ['tournamentStartAt','Bắt đầu giải'],['estimatedEndAt','Kết thúc dự kiến']
];
export function localStamp(value){
 if(!/^\d{4}-\d\d-\d\dT\d\d:\d\d(?::\d\d)?$/.test(value||''))return null;
 const parts=value.split(/[-T:]/).map(Number),date=new Date(value);
 const actual=[date.getFullYear(),date.getMonth()+1,date.getDate(),date.getHours(),date.getMinutes(),date.getSeconds()];
 return Number.isFinite(date.getTime())&&parts.every((p,i)=>p===actual[i])?date.toISOString():null;
}
export function validateTimeline(values){
 // Retain the nullable API field for compatibility, without offering an inactive schedule.
 const errors={},timeline={resultPublishAt:null};
 for(const [key]of timelineFields){const stamp=localStamp(values[key]);if(!stamp)errors[key]=values[key]?'Ngày giờ không hợp lệ.':'Vui lòng chọn ngày giờ.';else timeline[key]=stamp;}
 const strict=new Set(['registrationCloseAt','codingCloseAt','checkInCloseAt']);
 for(let i=1;i<timelineFields.length;i++){const [key]=timelineFields[i],[previous,label]=timelineFields[i-1];if(!timeline[key]||!timeline[previous])continue;const a=Date.parse(timeline[previous]),b=Date.parse(timeline[key]);if(b<a||strict.has(key)&&b===a)errors[key]=`Phải ${strict.has(key)?'sau':'bằng hoặc sau'} mốc ${label.toLowerCase()}.`;}
 return {errors,timeline};
}
export function timelineBotPolicy(policy,timeline){
 if(!timeline)return policy;
 return {...policy,submissionOpenAt:timeline.codingOpenAt,submissionDeadline:timeline.codingCloseAt,lockAtDeadline:true,deadlineBehavior:timeline.deadlinePolicy==='AUTO_SUBMIT_LATEST_READY'?'AUTO_SUBMIT_LATEST_READY':'NO_SUBMISSION'};
}
export function sampleTimeline(now=new Date()){
 const start=new Date(now);start.setDate(start.getDate()+1);start.setHours(8,0,0,0);
 const minutes=[0,720,1440,1560,1560,1680,1710,1730,1740,1980],result={};
 timelineFields.forEach(([key],i)=>{const d=new Date(start.getTime()+minutes[i]*60000);result[key]=`${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}T${String(d.getHours()).padStart(2,'0')}:${String(d.getMinutes()).padStart(2,'0')}`;});return result;
}
