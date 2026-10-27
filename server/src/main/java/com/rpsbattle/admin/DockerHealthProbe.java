package com.rpsbattle.admin;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
@Component
public class DockerHealthProbe {
    private final String executable,scope;
    private final Supplier<Map<String,Object>> probe;
    private final ScheduledExecutorService sampler=Executors.newSingleThreadScheduledExecutor(task->{var thread=new Thread(task,"admin-docker-health");thread.setDaemon(true);return thread;});
    private volatile Map<String,Object> cached=initial();
    @Autowired public DockerHealthProbe(@Value("${rps.admin.docker-executable:docker}") String executable,@Value("${rps.bots.instance-key:local}") String scope){this.executable=executable;this.scope=scope;this.probe=this::inspect;}
    DockerHealthProbe(Supplier<Map<String,Object>> probe){this.executable="";this.scope="";this.probe=probe;}
    private static Map<String,Object> initial(){var value=new LinkedHashMap<String,Object>();value.put("checkedAt",null);value.put("managedContainers",null);value.put("status","UNKNOWN");value.put("code","DOCKER_CHECK_PENDING");return Collections.unmodifiableMap(value);}
    @PostConstruct public void start(){sampler.scheduleWithFixedDelay(this::refresh,0,15,TimeUnit.SECONDS);}
    /** Requests only read the latest sample, even while Docker is stalled. */
    public Map<String,Object> status(){return cached;}
    void refresh(){try{cached=Collections.unmodifiableMap(new LinkedHashMap<>(probe.get()));}catch(RuntimeException unavailable){var value=new LinkedHashMap<String,Object>();value.put("checkedAt",Instant.now());value.put("managedContainers",null);value.put("status","DOWN");value.put("code","DOCKER_LINUX_UNAVAILABLE");cached=Collections.unmodifiableMap(value);}}
    private Map<String,Object> inspect(){
        var result=new LinkedHashMap<String,Object>();result.put("checkedAt",Instant.now());result.put("managedContainers",null);
        try{String os=command(List.of(executable,"info","--format","{{.OSType}}"));if(!os.strip().equals("linux"))throw new IllegalStateException();
            String containers=command(List.of(executable,"ps","--filter","label=rps.managed=true","--filter","label=rps.scope="+scope,"--format","{{.ID}}"));
            result.put("status","UP");result.put("managedContainers",containers.lines().filter(line->!line.isBlank()).count());result.put("code","LINUX_DOCKER_READY");
        }catch(Exception unavailable){if(unavailable instanceof InterruptedException)Thread.currentThread().interrupt();result.put("status","DOWN");result.put("code","DOCKER_LINUX_UNAVAILABLE");}
        return result;
    }
    private String command(List<String> command)throws Exception{Process process=null;try{process=new ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.DISCARD).start();if(!process.waitFor(2,TimeUnit.SECONDS)||process.exitValue()!=0)throw new IllegalStateException();return new String(process.getInputStream().readNBytes(4096),StandardCharsets.UTF_8);}finally{if(process!=null&&process.isAlive())process.destroyForcibly();}}
    @PreDestroy public void close(){sampler.shutdownNow();}
}
