import {registrationPolicyEditor} from './registration-policy.js';
import {botPolicyEditor} from './bot-policy.js';
import {stagePlanEditor} from './stage-plan.js';
import {timelineEditor} from './create-timeline-editor.js';
import {timelineBotPolicy} from './create-timeline-model.js';
import {createTournamentSubmission} from './tournament-create-submit.js';
import {participantLimitForFormat} from './capacity.js';
import {validateCreateStep} from './tournament-create-validation.js';
import {mountCreateWizard} from './tournament-create-wizard.js';
import {renderCreateSummary} from './tournament-create-summary.js';
import {node,link} from './player-dom.js';
import {loginLink} from '../account/account-session.js';

export function mountTournamentCreate(root,{account,bots=[],create,request,add,navigate=url=>location.assign(url)}){
 const find=id=>root.querySelector('#'+id),form=find('create-tournament'),options=find('bot-options'),errorBox=find('create-error'),panels=[...form.querySelectorAll('[data-wizard-step]')];
 if(!account)find('tournament-account-notice').append(link('Đăng nhập để tạo giải đấu',loginLink('/tournaments.html?tab=create')));
 for(const bot of bots){const label=node('label',undefined,'bot-option'),input=node('input');input.type='checkbox';input.value=bot.id;label.append(input,document.createTextNode(`${bot.name} · ${bot.language}`));options.append(label);}find('bot-empty').hidden=bots.length>0;
 const stageBox=node('fieldset');stageBox.append(node('legend','Các giai đoạn vòng bảng'));find('create-stages').append(stageBox);const readStages=stagePlanEditor(stageBox),policyEditor=botPolicyEditor(find('create-bot-policy')),timeline=timelineEditor(find('create-timeline')),fullTimeline=find('use-full-timeline');
 const registrationEditor=registrationPolicyEditor(find('create-timeline'));
 let manualCheckIn=false,previousTimeline=true;
 function syncMode(){const policy=find('participant-policy').value,humanOnly=policy==='HUMAN_ONLY',supportsTimeline=policy==='BOT_ONLY';if(!supportsTimeline)fullTimeline.checked=false;fullTimeline.disabled=!account||!supportsTimeline;const enabled=fullTimeline.checked&&supportsTimeline;
  policyEditor.box.hidden=humanOnly;policyEditor.box.disabled=humanOnly||!account;find('create-human-help').hidden=!humanOnly;policyEditor.setTimeline(enabled);timeline.setEnabled(enabled);registrationEditor.setEnabled(enabled&&Boolean(account));if(!account)for(const control of find('create-timeline').querySelectorAll('input,select,button'))control.disabled=true;
  find('manual-timing').hidden=enabled;for(const input of find('manual-timing').querySelectorAll('input'))input.disabled=enabled||!account;
  const checkIn=find('check-in-required');if(enabled&&!previousTimeline)manualCheckIn=checkIn.checked;if(!enabled&&previousTimeline)checkIn.checked=manualCheckIn;if(enabled)checkIn.checked=true;checkIn.disabled=enabled||!account;previousTimeline=enabled;
  find('create-existing-bots').hidden=enabled||humanOnly;for(const input of options.querySelectorAll('input')){input.disabled=enabled||humanOnly||!account;if(enabled||humanOnly)input.checked=false;}
  find('timeline-mode-help').textContent=supportsTimeline?'Người chơi đăng ký trước, chọn hoặc viết bot trong giờ coding; server khóa bài theo lịch sau khi chủ giải mở đăng ký. Bỏ chọn để quản lý thủ công.':'Giải có người tự chơi dùng thời gian thủ công, không có giai đoạn coding.';
 }
 function syncFormat(){const format=find('tournament-format').value,capacity=find('max-participants');stageBox.hidden=format!=='GROUP_KNOCKOUT';stageBox.disabled=stageBox.hidden||!account;find('create-team-help').hidden=format!=='TEAM_ROUND_ROBIN';capacity.max=participantLimitForFormat(format);capacity.min=format==='TEAM_ROUND_ROBIN'?6:format==='GROUP_KNOCKOUT'?4:2;capacity.value=Math.max(Number(capacity.min),Math.min(Number(capacity.max),Number(capacity.value)||10));
  find('format-help').textContent=({ROUND_ROBIN:'Mỗi cặp gặp nhau một lần; thắng 3, hòa 1, thua 0 điểm.',SINGLE_ELIMINATION:'Thua một cặp đấu sẽ dừng bước; người thắng đi tiếp theo nhánh.',GROUP_KNOCKOUT:'Chạy các giai đoạn vòng bảng, sau đó loại trực tiếp. Sức chứa phải đủ cho cấu hình bảng.',SWISS:'Ghép theo điểm qua nhiều vòng, không gặp lại. BYE được 3 điểm và không tạo phòng đấu.',TEAM_ROUND_ROBIN:'Mỗi cặp đội có 3 vị trí. Người chơi lập đội và đội trưởng nộp đội hình sau khi được duyệt.'})[format];
 }
 const chosen=()=>[...options.querySelectorAll('input:checked')].map(input=>bots.find(bot=>bot.id===input.value));
 const base=()=>({name:find('tournament-name').value.trim(),format:find('tournament-format').value,bestOf:Number(find('tournament-best-of').value),maxParticipants:Number(find('max-participants').value),participantPolicy:find('participant-policy').value,maxConcurrentMatches:Number(find('match-concurrency').value),stages:readStages(),bots:chosen(),fullTimeline:fullTimeline.checked,timing:{registrationDeadline:find('registration-deadline').value,checkInDeadline:find('check-in-deadline').value,startTime:find('scheduled-start').value}});
 function report(error){errorBox.hidden=false;errorBox.textContent=error.message==='TOURNAMENT_LIMIT'?'Server đã đạt giới hạn số giải đấu. Vui lòng liên hệ quản trị viên.':error.message==='BOT_LANGUAGE_NOT_ALLOWED'?'Chọn ít nhất một ngôn ngữ cho bot.':error.message==='INVALID_TOURNAMENT_TIMELINE'?'Kiểm tra các mốc giờ được đánh dấu tại Timeline.':error.message;}
 function validate(index){errorBox.hidden=true;try{const data=base();if(index===2&&data.participantPolicy!=='HUMAN_ONLY')data.botPolicy=policyEditor.read();if(index===3&&data.fullTimeline)timeline.read();validateCreateStep(index,data);
   for(const input of panels[index].querySelectorAll('input,select'))if(!input.matches(':disabled')&&!input.closest('[hidden]')&&!input.checkValidity()){for(let parent=input.parentElement;parent&&parent!==form;parent=parent.parentElement)if(parent.tagName==='DETAILS')parent.open=true;input.reportValidity();return false;}return true;
  }catch(error){report(error);return false;}}
 function configuration(){const data=base(),schedule=data.fullTimeline?timeline.read():null,policy=data.participantPolicy==='HUMAN_ONLY'?null:timelineBotPolicy(policyEditor.read(),schedule);return{name:data.name,settings:{registrationPolicy:schedule?registrationEditor.read():undefined,botPolicy:policy,bestOf:data.bestOf,participantPolicy:data.participantPolicy,maxParticipants:data.maxParticipants,format:data.format,maxConcurrentMatches:data.maxConcurrentMatches,checkInRequired:find('check-in-required').checked},stages:data.format==='GROUP_KNOCKOUT'?data.stages:null,timeline:schedule,timing:schedule?null:Object.fromEntries(Object.entries(data.timing).map(([key,value])=>[key,value?new Date(value).toISOString():null])),bots:data.bots};}
 const submit=createTournamentSubmission({configuration,create,request,add,navigate,canCreate:!!account,onBusy:value=>wizard.setBusy(value),onError:(error,draft)=>{if(draft){errorBox.hidden=false;errorBox.textContent='Đã tạo bản nháp nhưng chưa hoàn tất cấu hình. Giải chưa mở đăng ký. Mở bản nháp để kiểm tra, không tạo lại.';find('created-draft').replaceChildren(link('Mở bản nháp đã tạo','/tournament.html?id='+encodeURIComponent(draft.id)));find('created-draft').hidden=false;}else report(error);}});
 fullTimeline.addEventListener('change',syncMode);find('participant-policy').addEventListener('change',syncMode);find('tournament-format').addEventListener('change',syncFormat);syncMode();syncFormat();
 const wizard=mountCreateWizard({form,panels,stepper:find('create-steps'),progress:find('create-progress'),back:find('create-back'),next:find('create-next'),submit:find('create'),validateStep:validate,review:()=>renderCreateSummary(find('create-summary'),configuration()),onSubmit:submit,canCreate:!!account});
 if(!account)for(const control of form.querySelectorAll('input,select,button,fieldset'))control.disabled=true;return wizard;
}
