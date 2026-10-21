import {registrationPolicyLabels} from './registration-policy.js';
import {node} from './player-dom.js';
import {formatLabel} from './bracket-renderer.js';
import {submissionModes} from './bot-policy.js';
import {timelineFields} from './create-timeline-model.js';
const policyNames={BOT_ONLY:'Chỉ bot',HUMAN_ONLY:'Chỉ người tự chơi',HUMAN_OR_BOT:'Người tự chơi hoặc bot'};
const labels={submissionOpenAt:'Mở nhận bài',submissionDeadline:'Hạn nộp bot',maxSubmissionVersions:'Số phiên bản tối đa',maxSourceBytes:'Source tối đa (byte)',allowDraftSave:'Lưu bản nháp',allowPublicTests:'Test công khai',allowCustomTestMatch:'Đấu thử',hiddenTestsEnabled:'Hidden tests',lockOnSubmit:'Khóa ngay khi nộp',lockAtDeadline:'Khóa tại hạn nộp',allowResubmitBeforeDeadline:'Nộp lại trước hạn'};
export function renderCreateSummary(root,config){
 const {name,settings,stages,timeline,timing,bots}=config;root.replaceChildren();
 function group(title,rows){const section=node('section'),dl=node('dl',undefined,'create-summary-grid');section.append(node('h4',title),dl);for(const [label,value]of rows){const row=node('div');row.append(node('dt',label),node('dd',value??'Chưa đặt'));dl.append(row);}root.append(section);}
 const date=value=>value?new Date(value).toLocaleString('vi-VN'):'Chủ giải quyết định';
 group('Thể thức và người tham gia',[['Tên giải',name],['Thể thức',formatLabel(settings.format)],['Số ván','BO'+settings.bestOf],['Người tham gia',policyNames[settings.participantPolicy]],['Sức chứa',settings.maxParticipants],...(stages||[]).map((s,i)=>['Vòng bảng '+(i+1),`${s.groups} bảng · Top ${s.qualifyTopN}`])]);
 if(timeline&&settings.registrationPolicy)group('Đăng ký', [['Cách duyệt',registrationPolicyLabels[settings.registrationPolicy.approvalPolicy]],['Xác minh đơn vị',settings.registrationPolicy.requireAffiliationVerification?'Bắt buộc':'Không yêu cầu']]);
 if(settings.botPolicy){const p=settings.botPolicy;group('Bot',[["Cách nộp",submissionModes[p.submissionMode]],["Ngôn ngữ",p.allowedLanguages.join(', ')],...Object.entries(labels).map(([key,label])=>[label,typeof p[key]==='boolean'?p[key]?'Có':'Không':key.endsWith('At')||key==='submissionDeadline'?date(p[key]):p[key]]),['Khi hết hạn',p.deadlineBehavior==='AUTO_SUBMIT_LATEST_READY'?'Tự nộp bản READY mới nhất':'Không tự nộp bài'],['Bot chọn trước',bots?.length?bots.map(b=>b.name).join(', '):'Không chọn']]);}
 if(timeline)group('Lịch giải',[...timelineFields.map(([key,label])=>[label,date(timeline[key])]),['Khi khóa bài',timeline.deadlinePolicy==='AUTO_SUBMIT_LATEST_READY'?'Tự nộp bản READY mới nhất':'Phải tự nộp trước hạn'],['Kiểm thử không đạt',({DISQUALIFY:'Loại bài',USE_LAST_VALID_VERSION:'Dùng bản hợp lệ trước',ALLOW_EMERGENCY_RESUBMISSION:'Chủ giải có thể cấp lượt nộp khẩn cấp'})[timeline.validationFailurePolicy]]]);
 else group('Lịch thủ công',[['Hạn đăng ký',date(timing.registrationDeadline)],['Hạn check-in',date(timing.checkInDeadline)],['Bắt đầu dự kiến',date(timing.startTime)]]);
 group('Thi đấu',[['Trận đồng thời',settings.maxConcurrentMatches],['Bắt buộc check-in',settings.checkInRequired?'Có':'Không'],['Tài nguyên bot','Theo server · 0,5 CPU, RAM 384 MiB, 2 giây/nước, build 25 giây']]);
}
