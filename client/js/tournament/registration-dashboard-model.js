import {applicationQuery,filterKeys} from './application-queue-model.js';
export const applicationPageSize=25;
export const registrationMetrics=[['total','Tổng đơn',''],['approved','Đã duyệt','APPROVED'],['needsReview','Cần xử lý','NEEDS_REVIEW'],['waitlisted','Danh sách chờ','WAITLISTED'],['rejected','Từ chối','REJECTED']];
export function initialApplicationFilter(search,counts){
 const requested=new URLSearchParams(search).get('status');
 if(requested==='PENDING')return 'NEEDS_REVIEW';
 if(requested==='ALL'||requested==='')return '';
 if(['NEEDS_REVIEW','APPROVED','WAITLISTED','REJECTED','CANCELLED'].includes(requested))return requested;
 return counts.needsReview>0?'NEEDS_REVIEW':'';
}
export function applicationCount(counts,status){return counts[({'':'total',APPROVED:'approved',NEEDS_REVIEW:'needsReview',WAITLISTED:'waitlisted',REJECTED:'rejected',CANCELLED:'cancelled'})[status]]??0;}
export function registrationDashboardState(t,summary){
 if(t.status==='CANCELLED')return {label:'Giải đã hủy'};
 if(t.status==='FINISHED')return {label:'Giải đã kết thúc'};
 if(summary.control.lockedAt)return {label:'Đã đóng đăng ký'};
 if(t.status==='DRAFT')return {label:'Chưa mở đăng ký · Server tự mở theo lịch',deadline:t.timeline.registrationOpenAt};
 if(Date.parse(summary.serverNow)<Date.parse(t.timeline.registrationOpenAt))return {label:'Sắp mở đăng ký',deadline:t.timeline.registrationOpenAt};
 if(summary.registrationOpen)return {label:'Đang nhận đăng ký',deadline:summary.registrationCloseAt};
 return {label:summary.canReview?'Đã đóng nhận đơn · Còn thời gian xét duyệt':'Đã kết thúc xét duyệt'};
}

/** Summary and one server page only. Reject mixed revisions before exposing mutation controls. */
export async function readRegistrationDashboard(base,{request,search='',status=null,page=0,filters={},signal,isCurrent=()=>true}){
 for(let attempt=0;attempt<2;attempt++){
  const [tournament,summary]=await Promise.all([request(base,undefined,undefined,{signal}),request(base+'/registration-summary',undefined,undefined,{signal})]);
  if(!isCurrent())return null;
  if(tournament.revision!==summary.revision)continue;
  const selected=status===null?initialApplicationFilter(search,summary.counts):status;
  const size=filters.size||applicationPageSize,filtered=filterKeys.some(key=>filters[key]);
  const currentPage=filtered?Math.max(0,page):Math.min(Math.max(0,page),Math.max(0,Math.ceil(applicationCount(summary.counts,selected)/size)-1));
  const data=await request(base+'/participant-applications?'+applicationQuery(currentPage,selected,filters),undefined,undefined,{signal});
  if(!isCurrent())return null;
  if(data.revision===summary.revision)return {tournament,summary,data,status:selected,page:data.page??currentPage};
 }
 throw new Error('REGISTRATION_SNAPSHOT_CHANGED');
}
