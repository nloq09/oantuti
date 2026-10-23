export function canApply(flow){return flow.phase==='REGISTRATION_OPEN'&&['NOT_REGISTERED','CANCELLED'].includes(flow.registrationStatus);}
export function applicationPayload(language,affiliation,accepted){
 if(!accepted)throw new Error('RULES_ACCEPTANCE_REQUIRED');
 if(!['CPP','PYTHON','JAVA'].includes(language))throw new Error('BOT_LANGUAGE_NOT_ALLOWED');
 const school=(affiliation||'').trim();if(school.length>80||/[\u0000-\u001f\u007f]/.test(school))throw new Error('INVALID_AFFILIATION');
 return {intendedLanguage:language,affiliation:school,rulesAccepted:true};
}
export const applicationLabels={NEEDS_REVIEW:'Đang chờ ban tổ chức xác nhận',PENDING:'Đang chờ ban tổ chức xác nhận',APPROVED:'Đăng ký đã được chấp nhận',REJECTED:'Đăng ký không được chấp nhận',CANCELLED:'Bạn đã hủy đăng ký',WAITLISTED:'Bạn đang trong danh sách chờ'};
export function canWithdrawRegistration(flow){
 if(typeof flow.canWithdraw==='boolean')return flow.canWithdraw;
 if(!['PENDING','NEEDS_REVIEW','APPROVED','WAITLISTED'].includes(flow.registrationStatus)||flow.registrationControl?.lockedAt)return false;
 const beforeCoding=!flow.serverNow||!flow.codingOpenAt||Date.parse(flow.serverNow)<Date.parse(flow.codingOpenAt);
 return beforeCoding&&(flow.phase==='REGISTRATION_OPEN'||flow.registrationControl?.enabled&&flow.phase==='REGISTRATION_REVIEW');
}
export function registrationPresentation(flow){
 const status=flow.registrationStatus==='PENDING'?'NEEDS_REVIEW':flow.registrationStatus;
 const closed=Boolean(flow.registrationControl?.lockedAt)||Boolean(flow.serverNow&&flow.codingOpenAt&&Date.parse(flow.serverNow)>=Date.parse(flow.codingOpenAt));
 let description=({NEEDS_REVIEW:closed?'Thời gian xét duyệt đã kết thúc; bạn chưa có suất thi đấu. Liên hệ ban tổ chức nếu cần hỗ trợ.':'Đơn đã được gửi. Ban tổ chức sẽ xác nhận điều kiện tham gia; bạn không cần gửi lại.',APPROVED:'Bạn đã có suất tham gia. Phần lập trình mở theo lịch server; theo dõi bước tiếp theo của bạn.',WAITLISTED:closed?'Danh sách xét duyệt đã kết thúc; bạn chưa có suất thi đấu chính thức.':'Bạn chưa có suất chính thức. Nếu có chỗ trước khi chốt danh sách và còn trong hạn xét duyệt, hệ thống hoặc ban tổ chức sẽ xét chuyển bạn vào.',REJECTED:'Bạn chưa được tham gia giải. Liên hệ ban tổ chức nếu cần làm rõ điều kiện đăng ký.',CANCELLED:'Đơn đăng ký đã hủy. Bạn có thể đăng ký lại nếu cửa đăng ký còn mở.'})[status]||'Đăng ký người chơi trước, chọn hoặc viết bot khi đến phần lập trình.';
 return {status,title:applicationLabels[status]||'Đăng ký tham gia',description,publicMessage:flow.registration?.publicMessage||null};
}
export const registrationPhases=new Set(['DRAFT','UPCOMING','REGISTRATION_OPEN','REGISTRATION_REVIEW','WAITING_FOR_CODING']);
