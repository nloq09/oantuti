package com.rpsbattle.tournament.coding;

import com.rpsbattle.codevalidation.BotTestService;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.*;
import org.springframework.stereotype.Service;

/** Runs inside CodingWorker's two shared slots; never recompiles or edits the frozen source. */
@Service
public class FinalValidationWorker {
 private final FinalValidationStore store;private final CodingQueueStore queue;private final WorkspaceSandbox sandbox;private final BotTestService tests;private final Clock clock;
 public FinalValidationWorker(FinalValidationStore s,CodingQueueStore q,WorkspaceSandbox b,BotTestService t,Clock c){store=s;queue=q;sandbox=b;tests=t;clock=c;}
 private static final class Stop extends RuntimeException{final String code;final boolean infrastructure;Stop(String code,boolean infrastructure){this.code=code;this.infrastructure=infrastructure;}}
 private boolean infrastructure(BotTestService.Result r){return r.cases().stream().anyMatch(c->"SANDBOX_UNAVAILABLE".equals(c.message()));}
 public void run(CodingQueueStore.Lease lease){var results=FinalValidationStore.emptySteps();String current="BUILD";boolean passed=false,infra=false;String reason=null;
  try{
   var context=store.preflight(lease);if(context.failure()!=null)throw new Stop(context.failure(),context.failure().equals("VALIDATION_DEADLINE"));
   if(!store.progress(lease,current,results))return;results.put(current,"RUNNING");
   if(!WorkspaceSandbox.hash(lease.work().source().getBytes(StandardCharsets.UTF_8)).equals(lease.work().sourceHash()))throw new Stop("SOURCE_CHANGED",false);sandbox.verify(lease.work());results.put(current,"PASS");
   current="PROTOCOL";if(!store.progress(lease,current,results))return;checkTime(context);results.put(current,"RUNNING");var publicTests=tests.test(lease.work());
   results.put(current,publicTests.passed()?"PASS":"FAIL");results.put("TIMEOUT",publicTests.timeouts()==0&&publicTests.passed()?"PASS":"FAIL");results.put("LEGAL_ACTION",publicTests.invalidActions()==0&&publicTests.passed()?"PASS":"FAIL");results.put("RESOURCE",publicTests.passed()?"PASS":"FAIL");
   if(!publicTests.passed())throw new Stop("PUBLIC_TESTS_FAILED",infrastructure(publicTests));
   current="HIDDEN_TESTS";if(!store.progress(lease,current,results))return;checkTime(context);results.put(current,"RUNNING");var hidden=tests.hidden(lease.work());results.put(current,hidden.passed()?"PASS":"FAIL");if(!hidden.passed())throw new Stop("HIDDEN_TESTS_FAILED","SANDBOX_UNAVAILABLE".equals(hidden.failure()));
   current="TOURNAMENT_TESTS";if(!store.progress(lease,current,results))return;checkTime(context);results.put(current,"RUNNING");var sample=tests.sample(lease.work());results.put(current,sample.passed()?"PASS":"FAIL");if(!sample.passed()){results.put("TIMEOUT",sample.timeouts()>0?"FAIL":results.get("TIMEOUT"));if(sample.invalidActions()>0)results.put("LEGAL_ACTION","FAIL");throw new Stop("TOURNAMENT_TESTS_FAILED",infrastructure(sample));}
   current="ARTIFACT_HASH";if(!store.progress(lease,current,results))return;checkTime(context);results.put(current,"RUNNING");sandbox.verify(lease.work());results.put(current,"PASS");passed=true;
  }catch(Stop stop){reason=stop.code;infra=stop.infrastructure;if(!results.get(current).equals("PASS"))results.put(current,"FAIL");}
  catch(WorkspaceSandbox.Failure failure){reason="ARTIFACT_CHANGED".equals(failure.code)?"ARTIFACT_CHANGED":"SANDBOX_UNAVAILABLE";infra=!reason.equals("ARTIFACT_CHANGED");results.put(current,"FAIL");}
  catch(Exception unavailable){reason="FINAL_WORKER_ERROR";infra=true;results.put(current,"FAIL");}
  queue.completeFinal(lease,passed,infra,reason,results);
 }
 private void checkTime(FinalValidationStore.Context context){if(!clock.instant().isBefore(context.deadline()))throw new Stop("VALIDATION_DEADLINE",true);}
}
