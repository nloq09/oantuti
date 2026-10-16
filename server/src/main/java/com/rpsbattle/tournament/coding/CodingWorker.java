package com.rpsbattle.tournament.coding;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rpsbattle.codevalidation.BotTestService;
import org.springframework.stereotype.Service;
import java.util.concurrent.*;
@Service
public class CodingWorker {
 private final CodingQueueStore queue;private final WorkspaceSandbox sandbox;private final BotTestService tests;private final ObjectMapper json;
 private final ExecutorService workers=Executors.newFixedThreadPool(2,Thread.ofPlatform().daemon().name("coding-job-",0).factory());private final Semaphore slots=new Semaphore(2);
 public CodingWorker(CodingQueueStore queue,WorkspaceSandbox sandbox,BotTestService tests,ObjectMapper json){this.queue=queue;this.sandbox=sandbox;this.tests=tests;this.json=json;}
 @org.springframework.scheduling.annotation.Scheduled(fixedDelay=500) public void dispatch(){while(slots.tryAcquire()){CodingQueueStore.Lease lease;try{lease=queue.lease();}catch(RuntimeException unavailable){slots.release();return;}if(lease==null){slots.release();return;}try{workers.submit(()->{try{run(lease);}finally{slots.release();}});}catch(RejectedExecutionException closed){slots.release();return;}}}
 private void run(CodingQueueStore.Lease lease){try{
  if(lease.kind().equals("BUILD")){queue.complete(lease,sandbox.build(lease.work().language(),lease.work().source()),null,null,null,null,null,null);return;}
  String publicResult=null,sampleResult=null,hiddenResult=null;Boolean publicPass=null,samplePass=null,hiddenPass=null;
  if(lease.kind().equals("TEST")||lease.kind().equals("VALIDATE")){var result=tests.test(lease.work());publicResult=json.writeValueAsString(result);publicPass=result.passed();}
  if(lease.kind().equals("SAMPLE")||lease.kind().equals("VALIDATE")){var result=tests.sample(lease.work());sampleResult=json.writeValueAsString(result);samplePass=result.passed();}
  if(lease.hiddenRequired()&&(lease.kind().equals("TEST")||lease.kind().equals("VALIDATE"))){var result=tests.hidden(lease.work());hiddenResult=json.writeValueAsString(result);hiddenPass=result.passed();}
  queue.complete(lease,null,publicResult,publicPass,sampleResult,samplePass,hiddenResult,hiddenPass);
 }catch(Exception failure){queue.complete(lease,lease.kind().equals("BUILD")?new WorkspaceSandbox.Build(false,null,null,"JOB_FAILED"):null,"{\"passed\":false,\"result\":\"JOB_FAILED\"}",lease.kind().equals("BUILD")?null:false,null,null,null,null);}}
 @jakarta.annotation.PreDestroy public void close(){workers.shutdownNow();}
}
