package com.rpsbattle.remote;

import java.lang.management.ManagementFactory;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Observations only: no pulls, no bot source, no subprocess per turn. */
public class RuntimeProbe {
    private final WorkerConfig config;
    private final Set<String> languages=new TreeSet<>();
    private final boolean docker;
    public RuntimeProbe(WorkerConfig config) {
        this(config,args->linux(args.toArray(String[]::new)));
    }
    RuntimeProbe(WorkerConfig config,java.util.function.Predicate<List<String>> command) {
        this.config=config;docker=command.test(List.of("info","--format","{{.OSType}}"));
        if(docker)for(var image:Map.of("PYTHON","python:3.12-slim","CPP","gcc:14","JAVA","eclipse-temurin:21-jdk").entrySet())if(command.test(List.of("image","inspect","--format","{{.Os}}",image.getValue())))languages.add(image.getKey());
    }
    private static boolean linux(String... arguments) {
        var command=new ArrayList<String>();command.add("docker");command.addAll(List.of(arguments));Process child=null;
        try {child=new ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.DISCARD).start();if(!child.waitFor(5,TimeUnit.SECONDS))return false;return child.exitValue()==0&&new String(child.getInputStream().readNBytes(512),java.nio.charset.StandardCharsets.UTF_8).strip().equals("linux");}
        catch(InterruptedException e){Thread.currentThread().interrupt();return false;}
        catch(Exception e){return false;}finally{if(child!=null&&child.isAlive())child.destroyForcibly();}
    }
    public Map<String,Object> capabilities() {
        var os=ManagementFactory.getOperatingSystemMXBean();long memory=Runtime.getRuntime().maxMemory();
        if(os instanceof com.sun.management.OperatingSystemMXBean extended)memory=extended.getTotalMemorySize();
        return Map.of("cpuCores",Runtime.getRuntime().availableProcessors(),"memoryMb",Math.max(1,memory/1048576),"os",System.getProperty("os.name").replaceAll("[^\\p{L}\\p{N} ._()+-]","_").substring(0,Math.min(80,System.getProperty("os.name").length())),"docker",docker,"supportedLanguages",languages,"maxConcurrentMatches",config.maxMatches(),"maxBotProcesses",config.maxBots(),"workerVersion",CentralClient.VERSION,"protocolVersion",1);
    }
    public Map<String,Object> heartbeat() {
        var result=new HashMap<String,Object>();var os=ManagementFactory.getOperatingSystemMXBean();
        if(os instanceof com.sun.management.OperatingSystemMXBean extended) {
            double cpu=extended.getCpuLoad();if(Double.isFinite(cpu)&&cpu>=0)result.put("cpuUsage",Math.min(100,cpu*100));
            long total=extended.getTotalMemorySize(),free=extended.getFreeMemorySize();if(total>0)result.put("memoryUsage",Math.max(0,Math.min(100,100.0*(total-free)/total)));
        }
        long free=0;try{free=Files.getFileStore(config.stateDirectory()).getUsableSpace()/1048576;}catch(java.io.IOException ignored){}
        result.put("activeMatches",0);result.put("activeBotProcesses",0);result.put("freeSlots",docker&&!languages.isEmpty()?config.maxMatches():0);result.put("diskFreeMb",free);result.put("cacheSizeMb",0L);return result;
    }
}
