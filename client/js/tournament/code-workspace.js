import {mapSubmissionStatus,languageText} from './presentation.js';
import {resolvePlayerTournamentViewState} from './player-view-state.js';
import {createTournamentRefresh} from './refresh-controller.js';
import {workspacePhaseDestination} from './tournament-routes.js';
import {renderTimelineRegistration} from './timeline-registration.js';
import {temporaryDraft,readTemporary,reconcileDraft,autosaveDelay} from './draft-recovery.js';
import {renderTournamentShell} from './player-shell.js';
import {updateHeaderTimers} from './tournament-header.js';
import {CodingSession,entryOptions} from './coding-session.js';
import {renderFrozenSubmission} from './frozen-submission.js';
import {renderFinalValidation} from './final-validation-view.js';
import {refreshBots} from '../api/bot-api.js';
import {sessionRequest} from '../api/auth-api.js';
import {getAccount,loginLink} from '../account/account-session.js';
import {DraftSession,busyWorkspace} from './code-workspace-model.js';
import {node} from './player-dom.js';
import {attemptActive,submissionSummary,judgeAttempt,submitReason} from './submission-attempt.js';
import {errorText} from '../ui/toast.js';
const $=id=>document.getElementById(id),id=new URLSearchParams(location.search).get('id'),base='/api/tournaments/'+encodeURIComponent(id),editor=$('source'),session=new CodingSession();
let submissionRead=0,phaseLeaving=false,attemptRead=0,currentAttempt,pendingRequest,posting=false,saveFailed=false,attemptLoaded=false;
const attemptKey=()=>`rps-submit:${account.id}:${id}`;
function keepRequest(){try{if(pendingRequest)sessionStorage.setItem(attemptKey(),JSON.stringify(pendingRequest));else sessionStorage.removeItem(attemptKey());}catch{}}
let account,tournament,policy,workspace,draft,entryMode=null,ownSubmission,ownVersions=[],saving=false,action=false,stopped=false,timer,poll,sessionTimer,flowTimer,temporaryKey,lastWritable,needsSync=false,reconciling=false;
const status=message=>{const notice=$('notice');notice.textContent=message;notice.hidden=!message||message==='Bản nháp riêng tư của bạn.'||message.startsWith('Đã lưu bản nháp')||message==='Đã đối chiếu revision với server.';};
const writable=()=>!tournament?.timeline||session.writable();
const uploadable=()=>!stopped&&writable()&&!needsSync&&!reconciling&&navigator.onLine;
function temporary(){if(!draft||!temporaryKey||entryMode!=='CODE')return;try{if(draft.dirty)sessionStorage.setItem(temporaryKey,temporaryDraft(draft));else sessionStorage.removeItem(temporaryKey);return true;}catch{return false;}}
function followTournamentPhase(flow){
 const destination=workspacePhaseDestination(id,flow);if(!destination)return false;
 const kept=temporary();if(draft?.dirty&&kept!==true)return false;
 phaseLeaving=true;stopped=true;clearTimeout(timer);clearInterval(sessionTimer);poll?.dispose();flowTimer?.dispose();
 location.replace(destination);return true;
}
function lines(){const count=editor.value.split('\n').length;$('line-numbers').textContent=Array.from({length:count},(_,i)=>i+1).join('\n');$('line-numbers').scrollTop=editor.scrollTop;}
function download(name,text){const a=node('a'),url=URL.createObjectURL(new Blob([text],{type:'text/plain;charset=utf-8'}));a.href=url;a.download=name;a.click();setTimeout(()=>URL.revokeObjectURL(url),1000);}
function render(){
 renderFreeze();if(!policy)return;
 const code=entryMode==='CODE',busy=action||reconciling||busyWorkspace(workspace),locked=workspace?.status==='LOCKED'||ownSubmission?.status==='LOCKED';
 $('work-area').hidden=!entryMode||Boolean(tournament.timeline&&!session.writable());if(tournament.timeline&&!session.writable())$('entry-choice').hidden=true;
 $('source-pane').hidden=!code;$('import-panel').hidden=entryMode!=='IMPORT';$('language-label').hidden=!code;$('entry-mode').value=entryMode;
 editor.disabled=busy||!writable()||locked||!code||!workspace;
 const disabledReason=locked?'Bài đã khóa theo luật giải.':!writable()?'Đã hết giờ lập trình.':busy?'Đang đồng bộ bản nháp.':!workspace?'Đang tải bản nháp.':'';
 editor.title=editor.disabled?disabledReason:'';
 $('language').disabled=busy||saving||!writable()||locked||Boolean(draft?.dirty||draft?.remote);$('language').title=$('language').disabled?(draft?.dirty?'Chờ lưu bản nháp trước khi đổi ngôn ngữ.':disabledReason):'';
 $('entry-mode').disabled=busy||saving||locked||Boolean(draft?.dirty);$('entry-mode').title=$('entry-mode').disabled?'Chờ lưu bản nháp trước khi đổi cách nộp.':'';
 $('conflict').hidden=!draft?.remote||!code;
 for(const key of ['keep-local','keep-server']){$(key).disabled=busy||!writable()||locked;$(key).title=$(key).disabled?disabledReason:'';}
 $('saved').textContent=!code?'':saving?'Đang lưu…':saveFailed?'Lưu thất bại · Code vẫn được giữ trong tab':draft?.remote?'Có hai bản nháp khác nhau':!policy.allowDraftSave?'Nháp chỉ giữ trong tab':draft?.dirty?(navigator.onLine?'Chờ tự lưu…':'Mất mạng · Nháp trong tab'):workspace?'Đã lưu '+new Date(workspace.updatedAt).toLocaleTimeString('vi-VN'):'';
 drawSubmission();
}
function renderFreeze(){let shell=$('coding-shell');if(!shell){shell=node('div');shell.id='coding-shell';$('title').before(shell);$('title').hidden=true;}shell.replaceChildren();renderTournamentShell(shell,{tournament,flow:session.flow});let panel=$('frozen-summary');if(!panel){panel=node('div');panel.id='frozen-summary';$('work-area').before(panel);}const checkIn=Boolean(workspacePhaseDestination(id,session.flow)),view=resolvePlayerTournamentViewState({tournament,flow:session.flow||{}});panel.hidden=!session.flow||!checkIn&&!view.showSubmission&&!view.showValidation;if(!panel.hidden){panel.replaceChildren();if(checkIn)renderTimelineRegistration(panel,tournament,account,session.flow,()=>syncFlow());else if(view.showValidation)renderFinalValidation(panel,tournament,session.flow);else renderFrozenSubmission(panel,tournament,session.flow);}}
function adopt(next){workspace=next;const display=!policy.allowDraftSave&&next.version?{...next,sourceCode:next.version.sourceCode}:next;draft=new DraftSession(display);editor.value=draft.local;temporaryKey=`rps-code:${account.id}:${id}:${next.language}`;let local;try{local=readTemporary(sessionStorage.getItem(temporaryKey));}catch{}if(entryMode==='CODE'&&local&&local.sourceCode!==draft.local){draft.local=local.sourceCode;draft.conflict(display);editor.value=draft.local;status('Phát hiện bản tạm khác bản server. Chọn bản cần giữ.');}lines();}
async function save(){
 if(!draft?.dirty||saving||action||draft.remote||!policy.allowDraftSave||!writable()||entryMode!=='CODE')return;if(!uploadable()){temporary();return;}
 if(!navigator.onLine){temporary();status('Mất mạng · Đã giữ bản nháp tạm trên máy.');return;}clearTimeout(timer);saving=true;saveFailed=false;const sent=draft.snapshot();render();let success=false;
 try{const response=await sessionRequest(base+'/workspace/me',{language:workspace.language,...sent},'PUT');draft.saved(sent,response);workspace=response;success=true;temporary();status('Đã lưu bản nháp trên server.');}catch(error){saveFailed=true;if(error.message==='WORKSPACE_CONFLICT'){try{draft.conflict(await sessionRequest(base+'/workspace/me?language='+workspace.language));}catch{}status('Bản trên server đã thay đổi. Hãy so sánh hai bản.');}else{needsSync=true;temporary();status(errorText(error.message)+' · Nội dung đang gõ vẫn được giữ.');}}finally{saving=false;render();if(success&&draft.dirty&&!draft.remote&&writable()&&navigator.onLine)timer=setTimeout(save,autosaveDelay(temporaryKey));}
}
async function load(language){
 if(saving||action)return;if(draft?.dirty){status('Chờ tự lưu bản nháp trước khi đổi ngôn ngữ.');$('language').value=workspace.language;return;}
 action=true;render();try{let next;try{next=await sessionRequest(base+'/workspace/me?language='+language);}catch(error){if(error.message!=='WORKSPACE_NOT_FOUND')throw error;if(entryMode!=='CODE'||!writable()){workspace=null;draft=null;editor.value='';status(writable()?'Chọn bot của bạn để chụp phiên bản và kiểm thử.':'Chưa có bài làm cho ngôn ngữ này.');return;}next=await sessionRequest(base+'/workspace',{language});}adopt(next);await refreshSubmission();status(writable()?'Bản nháp riêng tư của bạn.':'Đã kết thúc giờ lập trình · Chỉ đọc, có thể tải source của bạn.');}catch(error){status(errorText(error.message));}finally{action=false;render();}
}
async function choose(mode){if(!entryOptions(policy.submissionMode).includes(mode)||saving||action||busyWorkspace(workspace))return;if(draft?.dirty){status('Chờ tự lưu bản nháp trước khi đổi cách nộp.');return;}entryMode=mode;const parameters=new URLSearchParams(location.search);parameters.set('entry',mode.toLowerCase());history.replaceState(null,'',location.pathname+'?'+parameters);render();await load($('language').value);}
function tick(){
 updateHeaderTimers(document);if(!tournament?.timeline)return;
 const seconds=session.remaining(),open=writable();
 $('coding-timer').hidden=Boolean($('coding-shell')?.querySelector('[role=timer]'));
 $('coding-timer').textContent=seconds?`Thời gian còn lại: ${Math.floor(seconds/3600)}:${String(Math.floor(seconds/60)%60).padStart(2,'0')}:${String(seconds%60).padStart(2,'0')}`:'Đã hết giờ lập trình · Chỉ đọc';
 const warning=open?session.warning():null;
 $('coding-alert').hidden=true;
 if(warning){$('coding-alert').hidden=false;$('coding-alert').textContent='Còn không quá '+Math.round(warning/60)+' phút để hoàn thành bài.';}
 if(!open){clearTimeout(timer);temporary();$('coding-alert').hidden=false;$('coding-alert').textContent='Đã đóng phần lập trình. Nội dung chưa lưu được giữ trong tab này; không gửi thay đổi mới.';}
 if(open&&session.flow.emergencyCoding===true){$('coding-alert').hidden=false;$('coding-alert').textContent='Lượt nộp khẩn cấp do người quản lý cấp. Có tối đa 3 bản build bổ sung; hết hạn mới sẽ khóa lại.';}
 const summary=$('frozen-summary'),closedSummary=!open&&summary&&!summary.hidden;
 $('policy').hidden=Boolean(closedSummary);
 if(closedSummary){
  $('coding-timer').hidden=true;$('coding-alert').hidden=!draft?.dirty;
  if(!draft?.dirty&&/^(Đã kết thúc giờ lập trình|Đã hết giờ|Chọn cách nộp bot|Bản nháp riêng tư|Đã lưu bản nháp|Đã đối chiếu revision)/.test($('notice').textContent))status('');
 }
 if(lastWritable!==open){lastWritable=open;render();}
}
async function recoverConnection(){if(stopped||reconciling||saving||action||!workspace||!draft||!navigator.onLine)return;clearTimeout(timer);needsSync=true;reconciling=true;render();const sent=performance.now();try{if(tournament.timeline){session.update(await sessionRequest(base+'/participant-flow'),sent);if(followTournamentPhase(session.flow))return;if(!session.flow.canAccessWorkspace){temporary();location.replace('/tournament-detail.html?id='+encodeURIComponent(id));return;}}let remote=await sessionRequest(base+'/workspace/me?language='+workspace.language);if(!policy.allowDraftSave&&remote.version)remote={...remote,sourceCode:remote.version.sourceCode};const result=reconcileDraft(draft,remote);workspace=remote;editor.value=draft.local;lines();needsSync=false;temporary();status(result==='conflict'?'Bản server đã thay đổi · Chọn giữ bản local, bản server hoặc tải cả hai.':!writable()?'Đã hết giờ · Bản local chỉ giữ để xem/tải, không được gửi lên server.':'Đã đối chiếu revision với server.');}catch(error){temporary();status(errorText(error.message)+' · Chưa đối chiếu được server, chưa gửi bản nháp.');}finally{reconciling=false;if(!stopped)render();if(uploadable()&&draft?.dirty&&!draft.remote&&policy.allowDraftSave)timer=setTimeout(save,autosaveDelay(temporaryKey));}}
async function syncFlow({signal,isCurrent=()=>!stopped}={}){if(needsSync&&workspace&&draft){await recoverConnection();return;}if(!tournament?.timeline||stopped)return;const sent=performance.now();try{const flow=await sessionRequest(base+'/participant-flow',undefined,undefined,{signal});if(!isCurrent())return;const previous=session.flow?.submissionStatus,previousCutoff=session.flow?.codingCloseAt;session.update(flow,sent);if(followTournamentPhase(flow))return;if(previousCutoff&&previousCutoff!==flow.codingCloseAt){needsSync=true;await recoverConnection();if(!isCurrent())return;}if(!flow.canAccessWorkspace){temporary();location.replace('/tournament-detail.html?id='+encodeURIComponent(id));return;}tick();renderFreeze();if(previous!==flow.submissionStatus)await refreshSubmission();}catch(error){if(isCurrent())status(errorText(error.message)+' · Đồng hồ cục bộ tiếp tục khóa khi hết giờ.');}}
function startWorkspacePolling(){
 poll?.dispose();flowTimer?.dispose();clearInterval(sessionTimer);
 if(tournament?.timeline){tick();sessionTimer=setInterval(tick,1000);flowTimer=createTournamentRefresh(syncFlow,{tournamentId:id,connected:()=>editor.isConnected});}
 poll=createTournamentRefresh(pollJob,{tournamentId:id,interval:1500,connected:()=>editor.isConnected});
}
async function pollJob({signal,isCurrent=()=>!stopped}={}){if(!attemptLoaded||attemptActive(currentAttempt)||pendingRequest)await refreshAttempt({signal,isCurrent});if(!isCurrent())return;if(stopped||!workspace||saving||action||draft?.remote||!busyWorkspace(workspace))return;const language=workspace.language,revision=draft.revision;try{const result=await sessionRequest(base+'/workspace/me?language='+language,undefined,undefined,{signal});if(!isCurrent()||saving||action||draft?.remote||workspace?.language!==language||draft.revision!==revision)return;workspace=result;draft.revision=workspace.revision;render();if(!busyWorkspace(workspace)){status('Công việc đã hoàn tất.');await refreshSubmission();}}catch(error){if(isCurrent())status(errorText(error.message));}}
editor.addEventListener('input',()=>{if(!draft||!writable()||editor.disabled)return;draft.local=editor.value;temporary();lines();render();clearTimeout(timer);if(policy.allowDraftSave)timer=setTimeout(save,autosaveDelay(temporaryKey));});editor.addEventListener('scroll',()=>{$('line-numbers').scrollTop=editor.scrollTop;});
editor.addEventListener('keydown',event=>{if(event.key==='Tab'&&!editor.disabled){event.preventDefault();editor.setRangeText('    ',editor.selectionStart,editor.selectionEnd,'end');editor.dispatchEvent(new Event('input'));}if((event.ctrlKey||event.metaKey)&&event.key.toLowerCase()==='s'){event.preventDefault();save();}});
$('language').onchange=()=>load($('language').value);$('entry-mode').onchange=()=>choose($('entry-mode').value);$('import-bot').onchange=render;
for(const [key,local]of [['keep-local',true],['keep-server',false]])$(key).onclick=()=>{if(!draft?.remote||!writable())return;workspace=draft.remote;draft.resolve(local);workspace.revision=draft.revision;editor.value=draft.local;temporary();lines();render();if(local)save();};
$('copy-both').onclick=()=>{if(draft?.remote)download('workspace-conflict.txt','SERVER\n'+draft.remote.sourceCode+'\n\nLOCAL\n'+draft.local);};
async function refreshSubmission(){const ticket=++submissionRead;try{const [submission,versions]=await Promise.all([sessionRequest(base+'/submission/me'),sessionRequest(base+'/submission/me/versions')]);if(stopped||ticket!==submissionRead)return;ownSubmission=submission;ownVersions=versions;$('version-history').replaceChildren(...ownVersions.map(v=>{const row=node('article',undefined,'version-row'),source=node('a','Xem source');source.href='/tournament-code.html?id='+encodeURIComponent(id)+'&view=source&version='+encodeURIComponent(v.id);row.append(node('strong',languageText(v.language)+' · v'+v.number+' · '+mapSubmissionStatus(v.status)),source);if(v.errorDetail)row.append(node('p',v.errorDetail));return row;}));drawSubmission();}catch(error){status(errorText(error.message));}}
async function refreshAttempt({signal,isCurrent=()=>!stopped}={}){if(posting)return;const ticket=++attemptRead;try{const result=await sessionRequest(base+'/submission/me/attempts/latest',undefined,undefined,{signal});if(!isCurrent()||posting||ticket!==attemptRead)return;attemptLoaded=true;const wasActive=attemptActive(currentAttempt);currentAttempt=result.id?result:null;if(pendingRequest?.requestId===currentAttempt?.id){pendingRequest=null;keepRequest();}if(wasActive&&!attemptActive(currentAttempt)){await refreshSubmission();if(draft&&!saving&&!action)try{await syncAttemptDraft();}catch{status('Chưa đồng bộ được bản nháp sau khi nộp. Nội dung đang gõ vẫn được giữ.');}}drawSubmission();}catch(error){if(isCurrent()){$('judge-result').hidden=false;$('verdict').textContent='…';$('attempt-state').textContent='Chưa tải được kết quả. Đang kết nối lại máy chấm.';}}}
async function syncAttemptDraft(){
 const remote=await sessionRequest(base+'/workspace/me?language='+workspace.language);if(stopped||saving||action)return;
 workspace=remote;if(remote.sourceCode===draft.server)draft.revision=remote.revision;else reconcileDraft(draft,remote);
 editor.value=draft.local;temporary();render();if(draft.dirty&&!draft.remote&&policy.allowDraftSave&&uploadable()){clearTimeout(timer);timer=setTimeout(save,autosaveDelay(temporaryKey));}
}
function drawSubmission(){
 if(!policy)return;
 const judged=posting?{code:'…',label:'Đang gửi bài',tone:'pending',cases:[],error:''}:pendingRequest?{code:'…',label:'Chưa xác nhận lần gửi. Bấm kiểm tra để gửi lại cùng source.',tone:'pending',cases:[],error:''}:judgeAttempt(currentAttempt);
 const panel=$('judge-result');panel.hidden=!judged;
 $('verdict').textContent=judged?.code||'';$('attempt-state').textContent=judged?.label||submissionSummary(ownSubmission);panel.dataset.tone=judged?.tone||'pending';
 const retained=$('retained-submission');retained.hidden=!(currentAttempt?.status==='FAILED'&&!posting&&!pendingRequest);retained.textContent=submissionSummary(ownSubmission);
 $('test-cases').replaceChildren(...(judged?.cases||[]).map(item=>{const row=node('span',`${item.name} · ${item.status}${Number.isFinite(item.elapsedMs)?' · '+item.elapsedMs+' ms':''}`,'test-case');row.dataset.passed=String(item.passed);row.title=item.detail;return row;}));
 $('judge-error').hidden=!judged?.error;$('judge-error').textContent=judged?.error||'';
 const reason=submitReason({online:navigator.onLine,writable:uploadable(),syncing:!attemptLoaded||needsSync||reconciling,busy:posting||action||busyWorkspace(workspace),attempt:currentAttempt,submission:ownSubmission,policy,entryMode,source:draft?.local,botId:$('import-bot').value,pending:Boolean(pendingRequest)});
 const button=$('submit-bot');button.disabled=Boolean(reason);button.title=reason||'Gửi source hiện tại để chấm';button.textContent=posting||attemptActive(currentAttempt)?'Đang chấm…':pendingRequest?'Kiểm tra lần gửi':'Submit';$('submit-reason').textContent=attemptActive(currentAttempt)?'':reason;
}
$('submit-bot').onclick=async()=>{
 if($('submit-bot').disabled||posting||attemptActive(currentAttempt))return;
 if(!pendingRequest){pendingRequest=entryMode==='IMPORT'?{requestId:crypto.randomUUID(),botId:$('import-bot').value,rulesAccepted:true}:{requestId:crypto.randomUUID(),language:workspace.language,sourceCode:draft.local,rulesAccepted:true};keepRequest();}
 const snapshot=pendingRequest;posting=true;attemptRead++;clearTimeout(timer);drawSubmission();
 try{const result=await sessionRequest(base+'/submission/me/attempts',snapshot);if(stopped)return;attemptRead++;attemptLoaded=true;currentAttempt=result;pendingRequest=null;keepRequest();await refreshSubmission();}
 catch(error){if(stopped)return;const definitive=/^[A-Z][A-Z0-9_]+$/.test(error.message)&&error.message!=='SERVER_ERROR';if(definitive){pendingRequest=null;keepRequest();status(errorText(error.message));}else status('Chưa xác nhận được yêu cầu. Kiểm tra lại cùng lần nộp để tránh gửi trùng.');}
 finally{posting=false;if(!stopped){render();if(draft?.dirty&&policy.allowDraftSave)timer=setTimeout(save,autosaveDelay(temporaryKey));}}
};
async function setupImport(){if(!entryOptions(policy.submissionMode).includes('IMPORT'))return;try{$('import-bot').append(new Option('Chọn bot của bạn',''));for(const bot of (await refreshBots()).filter(b=>['UPLOADED','READY'].includes(b.status)&&policy.allowedLanguages.includes(b.language==='JAR'?'JAVA':b.language)))$('import-bot').append(new Option(bot.name+' · '+languageText(bot.language),bot.id));}catch(error){status(errorText(error.message));}}
window.addEventListener('beforeunload',event=>{temporary();if(draft?.dirty&&!phaseLeaving){event.preventDefault();event.returnValue='';}});window.addEventListener('online',()=>{needsSync=true;recoverConnection();});window.addEventListener('offline',()=>{needsSync=true;clearTimeout(timer);const kept=temporary();status(kept?'Mất mạng · Bản nháp tạm đã giữ trong tab này.':'Mất mạng · Bản nháp còn trong bộ nhớ; lưu trữ tab không khả dụng, hãy tải bản code.');render();});window.addEventListener('pagehide',()=>{temporary();stopped=true;attemptRead++;submissionRead++;clearTimeout(timer);clearInterval(sessionTimer);});
window.addEventListener('pageshow',event=>{if(!event.persisted)return;stopped=false;phaseLeaving=false;needsSync=true;startWorkspacePolling();recoverConnection();});
async function initialize(){try{
 account=await getAccount();if(!account){location.replace(loginLink());}else{
  tournament=await sessionRequest(base);if(tournament.timeline){session.update(await sessionRequest(base+'/participant-flow'));if(followTournamentPhase(session.flow))return;if(!session.flow.canAccessWorkspace){location.replace('/tournament-detail.html?id='+encodeURIComponent(id));return;}}
  renderFreeze();policy=tournament.botPolicy;try{pendingRequest=JSON.parse(sessionStorage.getItem(attemptKey())||'null');}catch{}$('title').textContent=tournament.name+' · Bot dự thi';$('back').textContent='← Chi tiết giải';$('back').href='/tournament-detail.html?id='+encodeURIComponent(id);$('policy').textContent='Hạn nộp: '+((tournament.timeline?.codingCloseAt||policy.submissionDeadline)?new Date(tournament.timeline?.codingCloseAt||policy.submissionDeadline).toLocaleString('vi-VN'):'Chủ giải đóng đăng ký')+' · Source tối đa '+policy.maxSourceBytes+' byte · Tối đa '+policy.maxSubmissionVersions+' bản build';
  for(const language of policy.allowedLanguages)$('language').append(new Option(languageText(language),language));const parameters=new URLSearchParams(location.search),requested=parameters.get('language');if(policy.allowedLanguages.includes(requested))$('language').value=requested;
  const options=entryOptions(policy.submissionMode);$('entry-choice').hidden=options.length===1;await setupImport();await refreshSubmission();await refreshAttempt();if(!requested){const preferred=ownVersions[0]?.language||session.flow?.registration?.intendedLanguage;if(policy.allowedLanguages.includes(preferred))$('language').value=preferred;}const entry=parameters.get('entry')?.toUpperCase();await choose(options.includes(entry)?entry:options.includes('CODE')?'CODE':'IMPORT');
  startWorkspacePolling();
 }
}catch(error){status(errorText(error.message));}}
await initialize();
