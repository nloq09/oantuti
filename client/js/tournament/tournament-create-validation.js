import {participantLimitForFormat} from './capacity.js';
import {localStamp} from './create-timeline-model.js';
import {validateStagePlan} from './stage-plan.js';
export const createFormats=['ROUND_ROBIN','SWISS','TEAM_ROUND_ROBIN','GROUP_KNOCKOUT','SINGLE_ELIMINATION'];
export function validateCreateStep(step,data){
 const fail=message=>{throw Error(message);};
 if(step===0){if(!data.name?.trim()||data.name.trim().length>80)fail('Nhập tên giải từ 1 đến 80 ký tự.');if(!createFormats.includes(data.format)||![1,3,5].includes(data.bestOf))fail('Chọn thể thức và số ván được hỗ trợ.');}
 if(step===1){const min=data.format==='TEAM_ROUND_ROBIN'?6:data.format==='GROUP_KNOCKOUT'?4:2,max=participantLimitForFormat(data.format);if(!Number.isInteger(data.maxParticipants)||data.maxParticipants<min||data.maxParticipants>max)fail(`Số người/bot phải từ ${min} đến ${max}.`);if(!['BOT_ONLY','HUMAN_ONLY','HUMAN_OR_BOT'].includes(data.participantPolicy))fail('Chọn chính sách người tham gia hợp lệ.');if(data.format==='GROUP_KNOCKOUT')validateStagePlan(data.stages,data.maxParticipants);}
 if(step===2&&data.participantPolicy!=='HUMAN_ONLY'){
  const policy=data.botPolicy;if(!policy||!['PRE_UPLOADED_ONLY','CODE_IN_PLATFORM_ONLY','BOTH'].includes(policy.submissionMode)||!policy.allowedLanguages?.length||policy.allowedLanguages.some(l=>!['PYTHON','CPP','JAVA'].includes(l)))fail('Chọn cách nộp bot và ít nhất một ngôn ngữ hợp lệ.');
  for(const [key,max,label]of [['maxSubmissionVersions',100,'Số phiên bản'],['maxSourceBytes',524288,'Kích thước source']])if(!Number.isInteger(policy[key])||policy[key]<1||policy[key]>max)fail(`${label} phải từ 1 đến ${max}.`);
  if(policy.submissionOpenAt&&policy.submissionDeadline&&Date.parse(policy.submissionOpenAt)>=Date.parse(policy.submissionDeadline))fail('Hạn nộp bot phải sau giờ mở nhận bài.');
  const bots=data.bots||[];if(bots.length>data.maxParticipants)fail('Số bot chọn trước vượt sức chứa giải.');if(bots.length&&(policy.submissionMode==='CODE_IN_PLATFORM_ONLY'||bots.some(b=>!policy.allowedLanguages.includes(b.language==='JAR'?'JAVA':b.language))))fail('Bot đã chọn không phù hợp với cách nộp hoặc ngôn ngữ cho phép.');
 }
 if(step===3&&!data.fullTimeline){
  const stamps={};for(const [key,label]of [['registrationDeadline','hạn đăng ký'],['checkInDeadline','hạn check-in'],['startTime','giờ bắt đầu']]){const value=data.timing?.[key];if(!value)continue;const stamp=localStamp(value);if(!stamp)fail('Ngày giờ không hợp lệ: '+label+'.');stamps[key]=Date.parse(stamp);}
  const {registrationDeadline:r,checkInDeadline:c,startTime:s}=stamps;
  if(r!=null&&c!=null&&r>=c)fail('Hạn check-in phải sau hạn đăng ký.');
  if(c!=null&&s!=null&&c>s||r!=null&&s!=null&&r>s)fail('Các mốc đăng ký, check-in và bắt đầu phải theo thứ tự thời gian.');
 }
 if(step===4&&(!Number.isInteger(data.maxConcurrentMatches)||data.maxConcurrentMatches<1||data.maxConcurrentMatches>4))fail('Số trận đồng thời phải từ 1 đến 4.');
 return true;
}
