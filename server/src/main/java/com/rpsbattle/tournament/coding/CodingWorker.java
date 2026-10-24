package com.rpsbattle.tournament.coding;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rpsbattle.codevalidation.BotTestService;
import org.springframework.stereotype.Service;
import java.util.concurrent.*;
@Service
public class CodingWorker {
 @org.springframework.beans.factory.annotation.Value("${rps.coding.worker-auto-dispatch:true}") boolean autoDispatch=true;
 @org.springframework.beans.factory.annotation.Autowired FinalValidationWorker finals;
 @org.springframework.beans.factory.annotation.Autowired SubmissionAttemptStore attempts;
 @org.springframework.beans.factory.annotation.Autowired SubmissionAttemptService attemptService;
 private final CodingQueueStore queue;private final WorkspaceSandbox sandbox;private final BotTestService tests;private final ObjectMapper json;
 private final ExecutorService workers=Executors.newFixedThreadPool(2,Thread.ofPlatform().daemon().name("coding-job-",0).factory());private final Semaphore slots=new Semaphore(2);
 public CodingWorker(CodingQueueStore queue,WorkspaceSandbox sandbox,BotTestService tests,ObjectMapper json){this.queue=queue;this.sandbox=sandbox;this.tests=tests;this.json=json;}
 @org.springframework.scheduling.annotation.Scheduled(fixedDelay=500) public void dispatch(){if(autoDispatch)dispatchJobs();}
 public void dispatchJobs(){while(slots.tryAcquire()){CodingQueueStore.Lease lease;try{lease=queue.lease();}catch(RuntimeException unavailable){slots.release();return;}if(lease==null){slots.release();return;}try{workers.submit(()->{try{run(lease);}finally{slots.release();}});}catch(RejectedExecutionException closed){slots.release();return;}}}
 private void run(CodingQueueStore.Lease lease){try{
  if(lease.finalValidationId()!=null){finals.run(lease);return;}
  if(lease.pipelineSubmission()){submit(lease);return;}
  if(lease.kind().equals("BUILD")){queue.complete(lease,sandbox.build(lease.work().language(),lease.work().source()),null,null,null,null,null,null);return;}
  String publicResult=null,sampleResult=null,hiddenResult=null;Boolean publicPass=null,samplePass=null,hiddenPass=null;
  if(lease.kind().equals("TEST")||lease.kind().equals("VALIDATE")){var result=tests.test(lease.work());publicResult=json.writeValueAsString(result);publicPass=result.passed();}
  if(lease.kind().equals("SAMPLE")||lease.kind().equals("VALIDATE")){var result=tests.sample(lease.work());sampleResult=json.writeValueAsString(result);samplePass=result.passed();}
  if(lease.hiddenRequired()&&(lease.kind().equals("TEST")||lease.kind().equals("VALIDATE"))){var result=tests.hidden(lease.work());hiddenResult=json.writeValueAsString(result);hiddenPass=result.passed();}
  queue.complete(lease,null,publicResult,publicPass,sampleResult,samplePass,hiddenResult,hiddenPass);
 }catch(Exception failure){if(lease.pipelineSubmission()){try{attempts.fail(lease,"SUBMISSION_PROCESSING_FAILED","Máy xử lý bị gián đoạn. Bản dự thi cũ được giữ nguyên.");}catch(RuntimeException unavailable){/* Lease expiry retries safely after a database outage. */}return;}if(lease.finalValidationId()==null)queue.complete(lease,lease.kind().equals("BUILD")?new WorkspaceSandbox.Build(false,null,null,"JOB_FAILED"):null,"{\"passed\":false,\"result\":\"JOB_FAILED\"}",lease.kind().equals("BUILD")?null:false,null,null,null,null);}}
 private void submit(CodingQueueStore.Lease lease){try{
  var work=attempts.built(lease,sandbox.build(lease.work().language(),lease.work().source()));if(work==null)return;
  var result=tests.test(work);var hidden=result.passed()&&lease.hiddenRequired()?tests.hidden(work):null;
  if(attempts.tested(lease,result,hidden))attemptService.finish(lease);
 }catch(Exception error){String code=error instanceof com.rpsbattle.auth.AuthException?error.getMessage():error instanceof WorkspaceSandbox.Failure?error.getMessage():"SUBMISSION_PROCESSING_FAILED";attempts.fail(lease,code,switch(code){case "CODING_NOT_OPEN","WORKSPACE_CLOSED"->"Cửa nộp đã đóng trước khi xử lý xong; lần nộp này không được chốt.";case "RESUBMIT_DISABLED"->"Luật giải hoặc ban tổ chức chưa cho phép nộp lại.";case "SUBMISSION_LOCKED"->"Bài dự thi đã được khóa.";case "ACCOUNT_DISABLED"->"Tài khoản không còn quyền nộp bài.";default->"Không thể hoàn tất lần nộp ("+code+"). Hãy kiểm tra lại hoặc liên hệ ban tổ chức.";});}}
 @jakarta.annotation.PreDestroy public void close(){workers.shutdownNow();}
}
