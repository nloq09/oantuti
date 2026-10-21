export function canApply(flow){return flow.phase==='REGISTRATION_OPEN'&&['NOT_REGISTERED','CANCELLED'].includes(flow.registrationStatus);}
export function applicationPayload(language,affiliation,accepted){
 if(!accepted)throw new Error('RULES_ACCEPTANCE_REQUIRED');
 if(!['CPP','PYTHON','JAVA'].includes(language))throw new Error('BOT_LANGUAGE_NOT_ALLOWED');
 const school=(affiliation||'').trim();if(school.length>80||/[\u0000-\u001f\u007f]/.test(school))throw new Error('INVALID_AFFILIATION');
 return {intendedLanguage:language,affiliation:school,rulesAccepted:true};
}
export const applicationLabels={NEEDS_REVIEW:'Đang chờ ban tổ chức xác nhận',PENDING:'Đã gửi đăng ký · Đang chờ duyệt',APPROVED:'Bạn đã được chấp nhận',REJECTED:'Đăng ký bị từ chối',CANCELLED:'Bạn đã hủy đăng ký',WAITLISTED:'Bạn đang trong danh sách chờ'};
export const registrationPhases=new Set(['DRAFT','UPCOMING','REGISTRATION_OPEN','REGISTRATION_REVIEW','WAITING_FOR_CODING']);
