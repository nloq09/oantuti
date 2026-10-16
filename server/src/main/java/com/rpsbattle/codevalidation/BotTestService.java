package com.rpsbattle.codevalidation;
import com.rpsbattle.tournament.coding.*;

import com.rpsbattle.agent.*;
import com.rpsbattle.bot.*;
import com.rpsbattle.game.engine.*;
import com.rpsbattle.game.player.PlayerId;
import com.rpsbattle.game.state.*;
import com.rpsbattle.match.*;
import com.rpsbattle.rules.classic.*;
import org.springframework.stereotype.Service;
import java.time.Duration;
import java.util.*;

/** Public test fixtures and sample matches use the production protocol and rule engine unchanged. */
@Service
public class BotTestService {
 private final WorkspaceSandbox sandbox;
 private static final PlayerId P1=new PlayerId("P1"),P2=new PlayerId("P2");
 public BotTestService(WorkspaceSandbox sandbox){this.sandbox=sandbox;}
 public record Case(String id,String status,String message,String input,String expectedBehavior,long elapsedMs){}
 public record Result(boolean passed,String type,List<Case> cases,int turns,String result,int timeouts,int invalidActions,long elapsedMs,String debugOutput,Map<String,Object> resources){}
 private record Fixture(GameState state,ClassicRuleSet rules){}
 private Fixture fixture(){return new Fixture(ClassicGameSetup.create("00000000-0000-4000-8000-000000000009",P1,P2).withPhase(GamePhase.PLAYING),new ClassicRuleSet(P1,P2));}
 private List<GameState> states(){var f=fixture();var result=new ArrayList<GameState>();var state=f.state;var generator=new LegalActionGenerator(f.rules);var engine=new GameEngine(f.rules);var reference=new RandomBotAgent(19);for(int i=0;i<8&&!state.isFinished();i++){result.add(state);var player=state.currentPlayerId();state=engine.applyAction(state,player,reference.chooseAction(new PlayerView(state,player),generator.getLegalActions(state,player))).newState();}return result;}
 public List<Map<String,String>> publicCases(){var protocol=new BotProtocol();var rules=new ClassicRuleSet(P1,P2);var generator=new LegalActionGenerator(rules);var result=new ArrayList<Map<String,String>>();int index=0;for(var state:states())result.add(Map.of("id","legal-"+(++index),"input",protocol.encodeTurn(new PlayerView(state,state.currentPlayerId()),generator.getLegalActions(state,state.currentPlayerId())),"expectedBehavior","Return one ACTION JSON line with matching sequence and a move accepted by the current GameEngine."));return result;}
 private Map<String,Object> resources(){return Map.of("cpuLimit",0.5,"memoryLimitMiB",384,"pidsLimit",64,"tempBytes",67108864,"stdoutLineBytes",65536,"stderrBytes",32768,"turnTimeoutMs",2000,"testTimeoutMs",60000,"network","none","rootFilesystem","read-only");}
 private static String error(Throwable error){if(error instanceof WorkspaceSandbox.Failure f)return f.code;if(error instanceof InvalidActionException||error instanceof IllegalArgumentException)return "INVALID_ACTION";if(error instanceof BotProtocolException)return "PROTOCOL_ERROR";return "TEST_ERROR";}
 public Result test(WorkspaceStore.Work work){long started=System.nanoTime();var cases=new ArrayList<Case>();String debug="";boolean pass=true;int timeouts=0,invalid=0;
  try(var session=sandbox.open(work)){
   var protocol=new BotProtocol();var generator=new LegalActionGenerator(new ClassicRuleSet(P1,P2));var engine=new GameEngine(new ClassicRuleSet(P1,P2));int number=0;
   for(var state:states()){
    long begin=System.nanoTime();String input=protocol.encodeTurn(new PlayerView(state,state.currentPlayerId()),generator.getLegalActions(state,state.currentPlayerId()));
    try{if(begin-started>Duration.ofSeconds(60).toNanos())throw new WorkspaceSandbox.Failure("TEST_TIMEOUT");var response=session.exchange(input,Duration.ofMillis(2000));var action=protocol.decodeResponse(response,state.sequence(),state.currentPlayerId());engine.applyAction(state,state.currentPlayerId(),action);cases.add(new Case("legal-"+(++number),"PASS","Protocol, sequence and legal action accepted under sandbox limits.",input,"Any legal action",(System.nanoTime()-begin)/1000000));}
    catch(RuntimeException failure){String code=error(failure);cases.add(new Case("legal-"+(++number),"FAIL",code,input,"Any legal action",(System.nanoTime()-begin)/1000000));pass=false;if(code.contains("TIMEOUT"))timeouts++;if(code.equals("INVALID_ACTION"))invalid++;break;}
   }debug=session.debug();
  }catch(RuntimeException failure){pass=false;cases.add(new Case("sandbox","FAIL",error(failure),null,"Sandbox available",0));}
  return new Result(pass,"PUBLIC",List.copyOf(cases),cases.size(),pass?"PUBLIC_TESTS_PASSED":"PUBLIC_TESTS_FAILED",timeouts,invalid,(System.nanoTime()-started)/1000000,debug,resources());
 }
 public Result sample(WorkspaceStore.Work work){long started=System.nanoTime();int turns=0,timeouts=0,invalid=0;String debug="",outcome="",failure=null;
  try(var session=sandbox.open(work)){
   var f=fixture();var protocol=new BotProtocol();PlayerAgent bot=(view,legal)->{
    long remaining=Duration.ofSeconds(60).toNanos()-(System.nanoTime()-started);if(remaining<=0)throw new WorkspaceSandbox.Failure("TEST_TIMEOUT");
    return protocol.decodeResponse(session.exchange(protocol.encodeTurn(view,legal),Duration.ofNanos(Math.min(remaining,Duration.ofSeconds(2).toNanos()))),view.state().sequence(),view.playerId());};
   var match=new Match(f.state,f.rules,bot,new RandomBotAgent(2026));match.start();
   try{while(match.status()==MatchStatus.RUNNING){if(System.nanoTime()-started>Duration.ofSeconds(60).toNanos())throw new WorkspaceSandbox.Failure("TEST_TIMEOUT");match.playTurn();turns++;}outcome=match.state().result().map(Object::toString).orElse("FINISHED");}
   catch(RuntimeException error){failure=error(error);if(failure.contains("TIMEOUT"))timeouts++;if(failure.equals("INVALID_ACTION"))invalid++;}
   debug=session.debug();
  }catch(RuntimeException error){failure=error(error);}
  boolean pass=failure==null;return new Result(pass,"SAMPLE_MATCH",List.of(new Case("reference-random",pass?"PASS":"FAIL",pass?"Completed a real match; winning is not required.":failure,null,"Finish a legal match against ReferenceRandomBot",(System.nanoTime()-started)/1000000)),turns,pass?outcome:failure,timeouts,invalid,(System.nanoTime()-started)/1000000,debug,resources());
 }
}
