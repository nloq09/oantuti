package com.rpsbattle.remote;

import com.rpsbattle.bot.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** One Docker process per match seat, bounded JSONL and discarded/bounded diagnostics. */
public final class SandboxBot implements BotProcess {
    private final String name = "rps-remote-" + UUID.randomUUID();
    private final Path artifact;
    private final ArtifactRef ref;
    private final UUID execution;
    private final long deadline;
    private final ExecutorService io = Executors.newVirtualThreadPerTaskExecutor();
    private final BlockingQueue<String> lines = new ArrayBlockingQueue<>(4);
    private final AtomicBoolean closed = new AtomicBoolean();
    private volatile Process process;
    private volatile String failure;
    private boolean started;
    public SandboxBot(Path artifact, ArtifactRef ref, UUID execution, long deadline) {
        this.artifact=artifact; this.ref=ref; this.execution=execution; this.deadline=deadline;
    }
    public static List<String> command(Path path, ArtifactRef ref, String name, UUID execution) {
        String script = switch(ref.language()) {
            case "PYTHON" -> "exec python -I -u /bot/artifact";
            case "CPP" -> "cp /bot/artifact /tmp/bot && chmod 500 /tmp/bot && exec /tmp/bot";
            case "JAVA" -> "exec java -Xmx128m -XX:ActiveProcessorCount=1 -jar /bot/artifact";
            default -> throw new IllegalArgumentException();
        };
        String source=path.toAbsolutePath().normalize().toString();
        if(source.contains(",") || source.contains("\n") || source.contains("\r"))throw new IllegalArgumentException("Invalid artifact mount");
        return List.of("docker","run","--rm","--pull=never","-i","--name",name,
                "--network=none","--read-only","--cap-drop=ALL","--security-opt=no-new-privileges:true",
                "--user=65534:65534","--pids-limit=64","--memory=384m","--memory-swap=384m","--cpus=0.5",
                "--ulimit=nofile=64:64","--tmpfs=/tmp:rw,nosuid,nodev,"+(ref.language().equals("CPP")?"exec":"noexec")+",size=64m,mode=1777",
                "--log-driver=none","--label","rps.managed=true","--label","rps.scope=remote-worker",
                "--label","rps.execution="+execution,"--mount","type=bind,source="+source+",target=/bot/artifact,readonly",
                "--workdir=/tmp","--entrypoint=/bin/sh",ref.runtime(),"-c","printf 'RPS_BOOT_READY\\n'; "+script);
    }
    private long remaining(long requested) {
        long time=Math.min(requested,deadline-System.nanoTime());
        if(time<=0)throw new BotProtocolException("MATCH_HARD_TIMEOUT");return time;
    }
    public void start() {
        try {
            synchronized(this) {
                if(closed.get())throw new BotProtocolException("PROCESS_CLOSED");
                if(started)return;
                started=true;
                process=new ProcessBuilder(command(artifact,ref,name,execution)).start();
            }
            io.submit(this::readOutput);io.submit(this::readError);
            String marker=lines.poll(remaining(TimeUnit.SECONDS.toNanos(25)),TimeUnit.NANOSECONDS);
            if(!"RPS_BOOT_READY".equals(marker))throw new BotInfrastructureException();
        } catch(InterruptedException e){Thread.currentThread().interrupt();close();throw new BotInfrastructureException();}
        catch(IOException|RuntimeException e){close();throw new BotInfrastructureException();}
    }
    private BotProtocolException protocolFailure(String code) {
        var p=process;
        if(p!=null&&!p.isAlive()&&p.exitValue()==125)throw new BotInfrastructureException();
        Process probe=null;
        try {
            probe=new ProcessBuilder("docker","info","--format","{{.OSType}}")
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start();
            if(!probe.waitFor(2,TimeUnit.SECONDS)||probe.exitValue()!=0)throw new BotInfrastructureException();
        }catch(IOException e){throw new BotInfrastructureException();}
        catch(InterruptedException e){Thread.currentThread().interrupt();throw new BotInfrastructureException();}
        finally{if(probe!=null&&probe.isAlive())probe.destroyForcibly();}
        return new BotProtocolException(code);
    }
    private void fail(String code){failure=code;var p=process;if(p!=null)p.destroyForcibly();lines.offer("\0");}
    private void readOutput() {
        try(var reader=new InputStreamReader(process.getInputStream(),StandardCharsets.UTF_8)) {
            var line=new StringBuilder();int total=0;
            for(int c;(c=reader.read())!=-1;) {
                if(++total>4*1024*1024 || line.length()>=65536){fail("OUTPUT_LIMIT_EXCEEDED");return;}
                if(c=='\n'){if(!lines.offer(line.toString())){fail("EXTRA_PROTOCOL_OUTPUT");return;}line.setLength(0);}else line.append((char)c);
            }
            if(!closed.get())fail("PROCESS_EXITED");
        } catch(IOException e){if(!closed.get())fail("PROCESS_EXITED");}
    }
    private void readError() {
        try(var input=process.getErrorStream()){byte[] block=new byte[1024];int total=0;
            for(int n;(n=input.read(block))!=-1;){total+=n;if(total>65536){fail("STDERR_LIMIT_EXCEEDED");return;}}
        }catch(IOException ignored){}
    }
    public String exchange(String request,Duration timeout) {
        if(closed.get()||failure!=null)throw protocolFailure(failure==null?"PROCESS_CLOSED":failure);
        if(request.contains("\n")||request.contains("\r")||request.length()>65536)throw new BotProtocolException("INVALID_REQUEST");
        start();
        try {
            if(!lines.isEmpty())throw new BotProtocolException("EXTRA_PROTOCOL_OUTPUT");
            long end=System.nanoTime()+remaining(timeout.toNanos());
            var send=io.submit(()->{process.getOutputStream().write((request+"\n").getBytes(StandardCharsets.UTF_8));process.getOutputStream().flush();return null;});
            send.get(remaining(timeout.toNanos()),TimeUnit.NANOSECONDS);
            String line=lines.poll(remaining(Math.max(1,end-System.nanoTime())),TimeUnit.NANOSECONDS);
            if(failure!=null||line==null)throw protocolFailure(failure==null?"BOT_TIMEOUT":failure);
            return line;
        }catch(InterruptedException e){Thread.currentThread().interrupt();close();throw new BotProtocolException("INTERRUPTED");}
        catch(ExecutionException|TimeoutException e){close();throw protocolFailure("BOT_TIMEOUT");}
    }
    public boolean isAlive(){return !closed.get()&&process!=null&&process.isAlive();}
    public void close() {
        Process p;
        synchronized(this){if(!closed.compareAndSet(false,true))return;p=process;}
        if(p!=null)p.destroyForcibly();io.shutdownNow();
        if(!started)return;
        Process cleanup=null;
        try {cleanup=new ProcessBuilder("docker","rm","--force",name).redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start();cleanup.waitFor(3,TimeUnit.SECONDS);}
        catch(InterruptedException e){Thread.currentThread().interrupt();}catch(IOException ignored){}
        finally{if(cleanup!=null&&cleanup.isAlive())cleanup.destroyForcibly();}
    }
}
