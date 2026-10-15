package com.rpsbattle.admin;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;
@Component
public class DockerHealthProbe {
    private final String executable,scope;private Map<String,Object> cached;private long expires;
    public DockerHealthProbe(@Value("${rps.admin.docker-executable:docker}") String executable,@Value("${rps.bots.instance-key:local}") String scope){this.executable=executable;this.scope=scope;}
    public synchronized Map<String,Object> status(){
        if(cached!=null&&System.nanoTime()<expires)return cached;var result=new LinkedHashMap<String,Object>();result.put("checkedAt",Instant.now());result.put("managedContainers",null);
        try{String os=command(List.of(executable,"info","--format","{{.OSType}}"));if(!os.strip().equals("linux"))throw new IllegalStateException();
            String containers=command(List.of(executable,"ps","--filter","label=rps.managed=true","--filter","label=rps.scope="+scope,"--format","{{.ID}}"));
            result.put("status","UP");result.put("managedContainers",containers.lines().filter(line->!line.isBlank()).count());result.put("code","LINUX_DOCKER_READY");
        }catch(Exception unavailable){if(unavailable instanceof InterruptedException)Thread.currentThread().interrupt();result.put("status","DOWN");result.put("code","DOCKER_LINUX_UNAVAILABLE");}
        cached=Collections.unmodifiableMap(result);expires=System.nanoTime()+TimeUnit.SECONDS.toNanos(15);return cached;
    }
    private String command(List<String> command)throws Exception{Process process=null;try{process=new ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.DISCARD).start();if(!process.waitFor(2,TimeUnit.SECONDS)||process.exitValue()!=0)throw new IllegalStateException();return new String(process.getInputStream().readNBytes(4096),StandardCharsets.UTF_8);}finally{if(process!=null&&process.isAlive())process.destroyForcibly();}}
}
