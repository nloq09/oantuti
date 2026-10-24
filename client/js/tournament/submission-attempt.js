export const attemptActive=attempt=>['QUEUED','BUILDING','TESTING','FINALIZING'].includes(attempt?.status);
const processing={QUEUED:'Đang chờ chấm',BUILDING:'Đang biên dịch',TESTING:'Đang chạy test cases',FINALIZING:'Đang ghi nhận kết quả'};
const messages={PROTOCOL_ERROR:'Sai định dạng JSON hoặc sequence trả về.',INVALID_ACTION:'Bot trả về nước đi không hợp lệ.',TIMEOUT:'Bot phản hồi quá thời gian cho phép.',TEST_TIMEOUT:'Quá thời gian kiểm thử.',OUTPUT_LIMIT_EXCEEDED:'Output vượt giới hạn.',MEMORY_LIMIT_EXCEEDED:'Vượt giới hạn bộ nhớ.',SANDBOX_UNAVAILABLE:'Máy chấm chưa sẵn sàng.',TEST_ERROR:'Bot gặp lỗi khi chạy kiểm thử.'};
const readResult=value=>{try{const result=JSON.parse(value);return result&&typeof result==='object'?result:null;}catch{return null;}};
export function submissionSummary(submission){return ['SUBMITTED','LOCKED'].includes(submission?.status)?`Bản v${submission.version} vẫn được dùng để thi đấu.`:'Chưa có bản dự thi hợp lệ.';}
/** AC is exclusively an authoritative, committed submission result, never a successful build. */
export function judgeAttempt(attempt){
 if(!attempt?.id)return null;
 if(attemptActive(attempt))return{code:'…',label:processing[attempt.status],tone:'pending',cases:[],error:''};
 const result=readResult(attempt.testResult),cases=Array.isArray(result?.cases)?result.cases.map((item,index)=>({name:'Test '+(index+1),passed:item.status==='PASS',status:item.status==='PASS'?'AC':'Không đạt',detail:messages[item.message]||item.message||'',elapsedMs:item.elapsedMs})):[];
 const hidden=readResult(attempt.hiddenResult);if(Array.isArray(hidden?.cases))for(const [index,item]of hidden.cases.entries())cases.push({name:'Test ẩn '+(index+1),passed:item.status==='PASS',status:item.status==='PASS'?'AC':'Không đạt',detail:item.status==='PASS'?'':'Không đạt kiểm thử ẩn.'});
 const firstFailure=cases.find(item=>!item.passed);
 if(attempt.status==='SUCCEEDED')return{code:'AC',label:`Accepted · v${attempt.version} · ${new Date(attempt.finishedAt).toLocaleTimeString('vi-VN',{hour:'2-digit',minute:'2-digit'})}`,tone:'success',cases,error:''};
 if(attempt.status!=='FAILED')return{code:'…',label:'Chưa xác nhận kết quả',tone:'pending',cases:[],error:''};
 const code=attempt.errorCode||'',facts=[code,attempt.errorDetail,result?.result,...(Array.isArray(result?.cases)?result.cases:[]).map(item=>item.message)].filter(Boolean).join(' ');
 let verdict='ERROR',label='Không thể chấm bài';
 if(code==='BUILD_FAILED'){verdict='CE';label='Compilation Error · Lỗi biên dịch';}
 else if(code==='TEST_FAILED'||code==='HIDDEN_TEST_FAILED'){
  if(/TIMEOUT|TIME_LIMIT/.test(facts)){verdict='TLE';label='Time Limit Exceeded · Quá thời gian';}
  else if(/MEMORY|OUT_OF_MEMORY/.test(facts)){verdict='MLE';label='Memory Limit Exceeded · Quá bộ nhớ';}
  else if(/SANDBOX_UNAVAILABLE/.test(facts)){verdict='ERROR';label='Máy chấm chưa sẵn sàng';}
  else if(/TEST_ERROR|RUNTIME_ERROR|BOT_EXITED|OUTPUT_LIMIT/.test(facts)){verdict='RE';label='Runtime Error · Lỗi khi chạy';}
  else{verdict='WA';label='Wrong Answer · Không đạt kiểm thử';}
 }
 const detail=firstFailure?`${firstFailure.name}: ${firstFailure.detail}`:attempt.errorDetail||code;
 return{code:verdict,label,tone:'error',cases,error:verdict==='CE'?(attempt.buildLog||detail):[detail,result?.debugOutput].filter(Boolean).join('\n\n')};
}
export function submitReason({online=true,writable=true,syncing=false,busy=false,attempt,submission,policy,entryMode,source,botId,pending=false}){
 if(busy)return 'Đang gửi yêu cầu. Vui lòng chờ.';
 if(attemptActive(attempt))return 'Lần nộp đang được xử lý. Bản nháp mới sẽ dành cho lần nộp tiếp theo.';
 if(!online)return 'Mất mạng. Kết nối lại để nộp bot.';
 if(pending)return '';
 if(!writable)return 'Chưa đến giờ hoặc đã hết hạn nộp; server quyết định quyền nộp.';
 if(syncing)return 'Đang đồng bộ bản nháp với server.';
 if(submission?.status==='LOCKED')return 'Bài dự thi đã khóa theo luật giải.';
 if(submission?.status==='DISQUALIFIED')return 'Bài dự thi đã bị loại. Liên hệ ban tổ chức.';
 if(submission?.versionId&&!policy.allowResubmitBeforeDeadline)return 'Luật giải không cho phép nộp lại.';
 if(entryMode==='IMPORT'&&!botId)return 'Chọn một bot có sẵn để nộp.';
 if(entryMode==='CODE'&&!source?.trim())return 'Nhập source code trước khi nộp.';
 if(entryMode==='CODE'&&new TextEncoder().encode(source).length>policy.maxSourceBytes)return `Source vượt giới hạn ${policy.maxSourceBytes} byte.`;
 if(!entryMode)return 'Chọn cách nộp bot.';
 return '';
}
