package com.rpsbattle.remote;

import java.net.URI;
import java.nio.file.Path;
import java.util.*;

public record WorkerConfig(URI central, Path stateDirectory, String name, String registrationToken, boolean insecureHttp) {
    public static WorkerConfig parse(String[] args, Map<String,String> env) {
        var options=new HashMap<String,String>();
        for(String arg:args) {
            if(arg.equals("--allow-insecure-http")) { if(options.put("allow-insecure-http","true")!=null)throw new IllegalArgumentException(); continue; }
            if(!arg.startsWith("--") || !arg.contains("=")) throw new IllegalArgumentException();
            String[] parts=arg.substring(2).split("=",2);
            if(!Set.of("central","state-dir","name","register-token").contains(parts[0]) || options.put(parts[0],parts[1])!=null) throw new IllegalArgumentException();
        }
        var url=URI.create(options.getOrDefault("central",env.getOrDefault("WORKER_CENTRAL_URL","")));
        boolean insecure=options.containsKey("allow-insecure-http");
        validateCentral(url,insecure);
        var name=options.getOrDefault("name",env.getOrDefault("WORKER_NAME","RPS-WORKER"));
        if(!name.matches("[\\p{L}\\p{N} _.-]{1,80}") || name.isBlank()) throw new IllegalArgumentException();
        return new WorkerConfig(URI.create(url.toString().replaceAll("/$","")),Path.of(options.getOrDefault("state-dir",env.getOrDefault("WORKER_STATE_DIR","./worker-state"))),name,
            options.getOrDefault("register-token",env.get("WORKER_REGISTRATION_TOKEN")),insecure);
    }
    static void validateCentral(URI uri,boolean insecure) {
        if(uri.getHost()==null || uri.getRawUserInfo()!=null || uri.getRawQuery()!=null || uri.getRawFragment()!=null || !(uri.getPath().isEmpty() || uri.getPath().equals("/")) || uri.getPort()==0 || uri.getPort() < -1 || uri.getPort()>65535) throw new IllegalArgumentException();
        if("https".equals(uri.getScheme())) return;
        if(!"http".equals(uri.getScheme()) || !insecure || !privateHost(uri.getHost())) throw new IllegalArgumentException();
    }
    private static boolean privateHost(String host) {
        if(Set.of("localhost","[::1]","::1").contains(host)) return true;
        String[] parts=host.split("\\."); if(parts.length!=4) return false;
        int[] n=new int[4];
        for(int i=0;i<4;i++){if(!parts[i].matches("[0-9]{1,3}"))return false;n[i]=Integer.parseInt(parts[i]);if(n[i]>255)return false;}
        return n[0]==127 || n[0]==10 || n[0]==192&&n[1]==168 || n[0]==172&&n[1]>=16&&n[1]<=31;
    }
    @Override public String toString(){return "WorkerConfig[secrets redacted]";}
}
