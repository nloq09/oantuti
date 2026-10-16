package com.rpsbattle.tournament.coding;

import com.rpsbattle.bot.*;
import com.rpsbattle.bot.upload.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

/** Fixed platform images, one read-only file mount, no host compilation or user shell arguments. */
@Service
public class WorkspaceSandbox {
 public static final int LOG_LIMIT=32768,ARTIFACT_LIMIT=8*1024*1024;
 private final Path root;
 private final String volume,scope,boot=UUID.randomUUID().toString();
 private final boolean enabled;
 public WorkspaceSandbox(@Value("${rps.bots.directory:data/bots}")String directory,@Value("${rps.bots.docker-volume:}")String volume,@Value("${rps.bots.instance-key:local}")String scope,@Value("${rps.bots.enabled:true}")boolean enabled){this.root=Path.of(directory).toAbsolutePath().normalize();this.volume=volume;this.scope=scope;this.enabled=enabled;}
 public record Build(boolean success,UUID artifactId,String hash,String log){}
 public static String hash(byte[] bytes){try{return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));}catch(Exception e){throw new IllegalStateException(e);}}
 public static class Failure extends RuntimeException {public final String code;public Failure(String code){super(code);this.code=code;}}
 private Path file(UUID id,BotLanguage language){return root.resolve("coding").resolve(id.toString()).resolve(language.filename);}
 private void safe(Path file){for(Path p=file;p!=null&&p.startsWith(root);p=p.getParent())if(Files.isSymbolicLink(p))throw new Failure("ARTIFACT_CHANGED");}
 private Path write(UUID id,BotLanguage language,byte[] bytes)throws IOException{Path path=file(id,language);safe(path);Files.createDirectories(path.getParent());Files.write(path,bytes,StandardOpenOption.CREATE_NEW);return path;}
 private String daemonPath(Path file)throws Exception{
  safe(file);if(volume.isBlank())return file.toString();
  if(!volume.matches("[a-zA-Z0-9][a-zA-Z0-9_.-]{0,127}"))throw new Failure("SANDBOX_UNAVAILABLE");
  var process=new ProcessBuilder("docker","volume","inspect",volume,"--format","{{.Mountpoint}}").redirectError(ProcessBuilder.Redirect.DISCARD).start();
  try{if(!process.waitFor(3,TimeUnit.SECONDS)||process.exitValue()!=0)throw new Failure("SANDBOX_UNAVAILABLE");String base=new String(process.getInputStream().readNBytes(4096),StandardCharsets.UTF_8).strip();if(!base.startsWith("/")||base.contains(",")||base.contains("..")||base.contains("\n"))throw new Failure("SANDBOX_UNAVAILABLE");return base.replaceAll("/+$","")+"/"+root.relativize(file).toString().replace('\\','/');}finally{if(process.isAlive())process.destroyForcibly();}
 }
 private List<String> command(Path file,BotLanguage language,String name,String script)throws Exception{
  if(!enabled)throw new Failure("SANDBOX_UNAVAILABLE");
  var args=new ArrayList<>(DockerBotProcess.command(daemonPath(file),language,name,Map.of("rps.managed","true","rps.scope",scope,"rps.boot",boot,"rps.kind","workspace")));
  args.set(args.size()-1,script);return args;
 }
 private static byte[] bounded(InputStream input,int limit,Process process)throws IOException{var output=new ByteArrayOutputStream();byte[] block=new byte[4096];for(int count;(count=input.read(block))!=-1;){if(output.size()+count>limit){process.destroyForcibly();throw new Failure("OUTPUT_LIMIT_EXCEEDED");}output.write(block,0,count);}return output.toByteArray();}
 public Build build(String language,String source){
  BotLanguage lang=BotLanguage.valueOf(language);String name="rps-workspace-build-"+UUID.randomUUID();Process process=null;Path input=null;
  var io=Executors.newVirtualThreadPerTaskExecutor();try{
   if(lang==BotLanguage.JAR){byte[] artifact=Base64.getDecoder().decode(source);if(artifact.length==0||artifact.length>5*1024*1024)throw new Failure("ARTIFACT_SIZE_LIMIT");UUID id=UUID.randomUUID();write(id,lang,artifact);return new Build(true,id,hash(artifact),"Imported immutable JAR; protocol and validation still required.");}
   input=write(UUID.randomUUID(),lang,source.getBytes(StandardCharsets.UTF_8));
   String script=switch(lang){
    case PYTHON->"python -I -X pycache_prefix=/tmp/cache -m py_compile /bot/main.py >&2 && base64 /bot/main.py";
    case CPP->"g++ -std=c++17 -O2 /bot/main.cpp -o /tmp/artifact >&2 && base64 /tmp/artifact";
    case JAVA->"mkdir /tmp/classes && javac -J-Xmx128m -J-XX:ActiveProcessorCount=1 -d /tmp/classes /bot/Main.java >&2 && jar --create --file /tmp/artifact --main-class Main -C /tmp/classes . >&2 && base64 /tmp/artifact";
    default->throw new Failure("UNSUPPORTED_BOT_LANGUAGE");};
   process=new ProcessBuilder(command(input,lang,name,script)).start();final Process active=process;
   var stdout=io.submit(()->bounded(active.getInputStream(),ARTIFACT_LIMIT*2,active));var stderr=io.submit(()->bounded(active.getErrorStream(),LOG_LIMIT,active));
   if(!process.waitFor(25,TimeUnit.SECONDS))throw new Failure("BUILD_TIMEOUT");
   byte[] output=stdout.get(2,TimeUnit.SECONDS),error=stderr.get(2,TimeUnit.SECONDS);String log=new String(error,StandardCharsets.UTF_8);
   if(process.exitValue()!=0)return new Build(false,null,null,process.exitValue()==125?"SANDBOX_UNAVAILABLE":log.isBlank()?"BUILD_FAILED":log);
   byte[] artifact=Base64.getMimeDecoder().decode(output);if(artifact.length==0||artifact.length>ARTIFACT_LIMIT)throw new Failure("ARTIFACT_SIZE_LIMIT");
   UUID artifactId=UUID.randomUUID();write(artifactId,lang,artifact);return new Build(true,artifactId,hash(artifact),log.isBlank()?"Build successful.":log);
  }catch(Exception error){if(error instanceof InterruptedException)Thread.currentThread().interrupt();var cause=error instanceof ExecutionException?error.getCause():error;return new Build(false,null,null,cause instanceof Failure f?f.code:"SANDBOX_UNAVAILABLE");}
  finally{if(process!=null&&process.isAlive())process.destroyForcibly();io.shutdownNow();remove(name);if(input!=null){try{safe(input);Files.deleteIfExists(input);Files.deleteIfExists(input.getParent());}catch(IOException ignored){}}}
 }
 public void verify(WorkspaceStore.Work work){try{var path=file(work.artifactId(),BotLanguage.valueOf(work.language()));safe(path);byte[] bytes;try(var input=Files.newInputStream(path)){bytes=input.readNBytes(ARTIFACT_LIMIT+1);}if(bytes.length>ARTIFACT_LIMIT||!hash(bytes).equals(work.artifactHash()))throw new Failure("ARTIFACT_CHANGED");}catch(IOException error){throw new Failure("ARTIFACT_CHANGED");}}
 public Session open(WorkspaceStore.Work work){try{
  verify(work);var language=BotLanguage.valueOf(work.language());Path path=file(work.artifactId(),language);
  String script=switch(language){case PYTHON->"exec python -I -u /bot/main.py";case CPP->"cp /bot/main.cpp /tmp/bot && chmod 500 /tmp/bot && exec /tmp/bot";case JAVA->"exec java -Xmx128m -XX:ActiveProcessorCount=1 -jar /bot/Main.java";case JAR->"exec java -Xmx128m -XX:ActiveProcessorCount=1 -jar /bot/bot.jar";};
  String name="rps-workspace-test-"+UUID.randomUUID();return new Session(name,command(path,language,name,"printf 'RPS_BOOT_READY\\n'; "+script));
 }catch(Failure failure){throw failure;}catch(Exception error){throw new Failure("SANDBOX_UNAVAILABLE");}}
 static void remove(String name){Process cleanup=null;try{cleanup=new ProcessBuilder("docker","rm","--force",name).redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start();cleanup.waitFor(3,TimeUnit.SECONDS);}catch(Exception error){if(error instanceof InterruptedException)Thread.currentThread().interrupt();}finally{if(cleanup!=null&&cleanup.isAlive())cleanup.destroyForcibly();}}
 public static final class Session implements BotProcess {
  private final String name;private Process process;private final ExecutorService io=Executors.newVirtualThreadPerTaskExecutor();private final BlockingQueue<String> lines=new ArrayBlockingQueue<>(4);private volatile String failure;private final StringBuilder debug=new StringBuilder();private boolean closed;
  Session(String name,List<String> command){this.name=name;try{process=new ProcessBuilder(command).start();io.submit(()->readOutput());io.submit(()->readDebug());String marker=lines.poll(25,TimeUnit.SECONDS);if(!"RPS_BOOT_READY".equals(marker)){close();throw new Failure(failure==null?"SANDBOX_UNAVAILABLE":failure);}}catch(IOException error){close();throw new Failure("SANDBOX_UNAVAILABLE");}catch(InterruptedException error){Thread.currentThread().interrupt();close();throw new Failure("INTERRUPTED");}}
  private void fail(String code){failure=code;if(process!=null)process.destroyForcibly();lines.offer("\0");}
  private void readOutput(){try{var input=new InputStreamReader(process.getInputStream(),StandardCharsets.UTF_8);var line=new StringBuilder();long total=0;for(int ch;(ch=input.read())!=-1;){if(++total>4*1024*1024||line.length()>=65536){fail("OUTPUT_LIMIT_EXCEEDED");return;}if(ch=='\n'){if(!lines.offer(line.toString())){fail("OUTPUT_LIMIT_EXCEEDED");return;}line.setLength(0);}else line.append((char)ch);}if(!closed)fail("PROCESS_EXITED");}catch(IOException error){if(!closed&&failure==null)fail("PROCESS_EXITED");}}
  private void readDebug(){try{byte[] block=new byte[1024];int total=0;for(int count;(count=process.getErrorStream().read(block))!=-1;){total+=count;if(total>LOG_LIMIT){fail("OUTPUT_LIMIT_EXCEEDED");return;}synchronized(debug){debug.append(new String(block,0,count,StandardCharsets.UTF_8));}}}catch(IOException ignored){}}
  public String debug(){synchronized(debug){return debug.toString();}}
  public String exchange(String request,Duration timeout){if(closed||failure!=null)throw new Failure(failure==null?"PROCESS_EXITED":failure);try{
   if(!lines.isEmpty())throw new Failure("EXTRA_PROTOCOL_OUTPUT");
   var send=io.submit(()->{try{process.getOutputStream().write((request+"\n").getBytes(StandardCharsets.UTF_8));process.getOutputStream().flush();}catch(IOException e){throw new Failure("PROCESS_EXITED");}});
   long end=System.nanoTime()+timeout.toNanos();send.get(timeout.toNanos(),TimeUnit.NANOSECONDS);String line=lines.poll(Math.max(1,end-System.nanoTime()),TimeUnit.NANOSECONDS);
   if(failure!=null)throw new Failure(failure);if(line==null){fail("TIMEOUT");throw new Failure("TIMEOUT");}return line;
  }catch(Failure error){throw error;}catch(Exception error){if(error instanceof InterruptedException)Thread.currentThread().interrupt();fail(error instanceof TimeoutException?"TIMEOUT":"PROCESS_EXITED");throw new Failure(failure);}}
  public void start(){}public boolean isAlive(){return !closed&&process!=null&&process.isAlive();}
  public void close(){if(closed)return;closed=true;if(process!=null)process.destroyForcibly();io.shutdownNow();remove(name);}
 }
}
