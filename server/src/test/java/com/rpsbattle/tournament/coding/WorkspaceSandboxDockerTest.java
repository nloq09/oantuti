package com.rpsbattle.tournament.coding;
import com.rpsbattle.codevalidation.BotTestService;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

@Testcontainers(disabledWithoutDocker=true)
class WorkspaceSandboxDockerTest {
 @TempDir Path root;
 @BeforeEach void dockerAvailable(){org.junit.jupiter.api.Assumptions.assumeTrue(org.testcontainers.DockerClientFactory.instance().isDockerAvailable());}
 WorkspaceSandbox sandbox(){return new WorkspaceSandbox(root.toString(),"","workspace-test",true);}
 WorkspaceStore.Work work(String language,WorkspaceSandbox.Build build){assertThat(build.success()).as(build.log()).isTrue();return new WorkspaceStore.Work(UUID.randomUUID(),UUID.randomUUID(),language,"","",build.artifactId(),build.hash());}
 String template(String file)throws Exception{return new org.springframework.core.io.ClassPathResource("code-templates/"+file).getContentAsString(java.nio.charset.StandardCharsets.UTF_8);}
 @Test void pythonCppAndJavaCompileAndPassRealProtocolLegalCasesAndSampleMatches()throws Exception{
  var sandbox=sandbox();var tests=new BotTestService(sandbox);
  for(var pair:List.of(new String[]{"PYTHON","main.py"},new String[]{"CPP","main.cpp"},new String[]{"JAVA","Main.java"})){
   var build=sandbox.build(pair[0],template(pair[1]));var work=work(pair[0],build);
   assertThat(tests.test(work).passed()).as(pair[0]+" public tests").isTrue();var sample=tests.sample(work);assertThat(sample.passed()).as(pair[0]+" sample: "+sample).isTrue();assertThat(sample.turns()).isGreaterThan(0);
  }
 }
 @Test void compileFailuresNeverProduceArtifactsAndRetainBoundedCompilerFeedback(){var sandbox=sandbox();for(var pair:List.of(new String[]{"PYTHON","if :"},new String[]{"CPP","int main( {"},new String[]{"JAVA","public class Main { invalid }"})){var result=sandbox.build(pair[0],pair[1]);assertThat(result.success()).isFalse();assertThat(result.artifactId()).isNull();assertThat(result.log()).isNotBlank().hasSizeLessThanOrEqualTo(WorkspaceSandbox.LOG_LIMIT);}}
 @Test void malformedJsonWrongSequenceAndIllegalActionsAreRejectedByActualProtocolAndEngine(){var sandbox=sandbox();var tests=new BotTestService(sandbox);for(var source:List.of("import sys\nfor line in sys.stdin: print('not json',flush=True)","import sys,json\nfor line in sys.stdin:\n q=json.loads(line);print(json.dumps({'type':'ACTION','sequence':-1,'action':q['legalActions'][0]}),flush=True)","import sys,json\nfor line in sys.stdin:\n q=json.loads(line);print(json.dumps({'type':'ACTION','sequence':q['sequence'],'action':{'type':'MOVE','pieceId':'enemy','from':{'row':0,'col':0},'to':{'row':99,'col':99}}}),flush=True)")){var result=tests.test(work("PYTHON",sandbox.build("PYTHON",source)));assertThat(result.passed()).isFalse();assertThat(result.cases().getFirst().message()).isIn("PROTOCOL_ERROR","INVALID_ACTION");}}
 @Test void timeoutAndStdoutAndStderrFloodsAreTerminated(){var sandbox=sandbox();var tests=new BotTestService(sandbox);for(var pair:List.of(new String[]{"import sys,time\nfor line in sys.stdin: time.sleep(10)","TIMEOUT"},new String[]{"import sys\nfor line in sys.stdin: print('x'*100000,flush=True)","OUTPUT_LIMIT_EXCEEDED"},new String[]{"import sys\nfor line in sys.stdin: sys.stderr.write('x'*100000);sys.stderr.flush()","OUTPUT_LIMIT_EXCEEDED"})){long before=System.nanoTime();var result=tests.test(work("PYTHON",sandbox.build("PYTHON",pair[0])));assertThat(result.passed()).isFalse();assertThat(result.cases().getFirst().message()).isEqualTo(pair[1]);assertThat(Duration.ofNanos(System.nanoTime()-before)).isLessThan(Duration.ofSeconds(35));}}
 @Test void sandboxDeniesNetworkHostFilesAndWritesToMountedArtifact()throws Exception{
  var sandbox=sandbox();String source="""
import sys,json,socket,os
assert os.getuid() != 0
assert not os.path.exists('/var/run/docker.sock')
assert not os.path.exists('/.env')
try:
    open('/bot/main.py','w').write('changed')
    raise AssertionError('source writable')
except OSError: pass
try:
    socket.create_connection(('1.1.1.1',443),0.2)
    raise AssertionError('network available')
except OSError: pass
for line in sys.stdin:
    q=json.loads(line)
    print(json.dumps({'type':'ACTION','sequence':q['sequence'],'action':q['legalActions'][0]}),flush=True)
""";
  var work=work("PYTHON",sandbox.build("PYTHON",source));assertThat(new BotTestService(sandbox).test(work).passed()).isTrue();
  var path=root.resolve("coding").resolve(work.artifactId().toString()).resolve("main.py");Files.writeString(path,"tampered");assertThatThrownBy(()->sandbox.open(work)).hasMessage("ARTIFACT_CHANGED");
 }
 @Test void memoryAndTemporaryStorageAreBoundedByTheRuntime(){var sandbox=sandbox();var tests=new BotTestService(sandbox);String memory="import sys\nfor line in sys.stdin: data=bytearray(700*1024*1024)";assertThat(tests.test(work("PYTHON",sandbox.build("PYTHON",memory))).passed()).isFalse();String disk="import sys\nfor line in sys.stdin:\n f=open('/tmp/fill','wb');f.write(b'x'*(70*1024*1024));f.flush()";assertThat(tests.test(work("PYTHON",sandbox.build("PYTHON",disk))).passed()).isFalse();}
 @Test void aCompilerBlockedOnStdinIsStoppedAtBuildDeadline(){var result=sandbox().build("CPP","#include \"/dev/stdin\"\nint main(){}\n");assertThat(result.success()).isFalse();assertThat(result.log()).isEqualTo("BUILD_TIMEOUT");assertThat(result.artifactId()).isNull();}
 @Test void hiddenTestsRunRealStatesButNeverReturnInputsOrBotDebug()throws Exception{var sandbox=sandbox();String source=template("main.py").replace("turn = json.loads(line)","turn = json.loads(line)\n    print(line, file=sys.stderr, flush=True)");var hidden=new BotTestService(sandbox).hidden(work("PYTHON",sandbox.build("PYTHON",source)));String exposed=new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(hidden);assertThat(exposed).contains("Hidden Test 1").doesNotContain("YOUR_TURN","legalActions","state","stderr","pieceId","elapsed");}
 @Test void preuploadedJarUsesTheSameReadOnlyRuntimeAndRequiredTests()throws Exception{var sandbox=sandbox();var compiled=sandbox.build("JAVA",template("Main.java"));assertThat(compiled.success()).isTrue();byte[] jar=Files.readAllBytes(root.resolve("coding").resolve(compiled.artifactId().toString()).resolve("Main.java"));var imported=sandbox.build("JAR",Base64.getEncoder().encodeToString(jar));var work=work("JAR",imported);var tests=new BotTestService(sandbox);assertThat(tests.test(work).passed()).isTrue();assertThat(tests.hidden(work).passed()).isTrue();}
}
