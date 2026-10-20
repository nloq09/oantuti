package com.rpsbattle.remote;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Reconcile durable state with Central. Never resumes or replays an interrupted bot match. */
public final class RecoveryAgent {
    @FunctionalInterface public interface Cleanup {void remove(UUID execution)throws IOException,InterruptedException;}
    private final CentralClient central;private final IdentityStore.Identity identity;private final ExecutionJournal journal;private final Cleanup cleanup;
    public RecoveryAgent(CentralClient central,IdentityStore.Identity identity,ExecutionJournal journal){this(central,identity,journal,RecoveryAgent::removeContainers);}
    RecoveryAgent(CentralClient central,IdentityStore.Identity identity,ExecutionJournal journal,Cleanup cleanup){this.central=central;this.identity=identity;this.journal=journal;this.cleanup=cleanup;}
    public int reconcile(boolean interrupted)throws IOException,InterruptedException {
        int count=0;IOException failure=null;var uploader=new ResultUploader(central,identity,journal);
        for(Path dir:journal.entries())try{
            String status;try{status=journal.outcome(dir).path("status").asText();}catch(IOException e){status="STARTING";}
            if(Set.of("ACKNOWLEDGED","RECOVERED","SUPERSEDED").contains(status))continue;
            UUID execution=uuid(dir.getFileName().toString());
            if(status.equals("READY_FOR_UPLOAD")){
                try{if(uploader.upload(dir))count++;continue;}
                catch(CentralClient.CentralFailure e){if(e.status!=409)throw e;}
                var grant=recover(execution,"RESULT_READY");String decision=grant.path("status").asText();
                if(Set.of("DONE","UPLOAD_ALLOWED").contains(decision)){if(uploader.upload(dir))count++;}
                else if(decision.equals("SUPERSEDED")){cleanup.remove(execution);journal.clearStaged(dir);journal.terminal(dir,execution,"SUPERSEDED");count++;}
                else throw new IOException("Completed result lease not recoverable yet");
            }else if(interrupted){
                var grant=recover(execution,"ABANDON");if(!Set.of("ABANDONED","SUPERSEDED","DONE").contains(grant.path("status").asText()))throw new IOException("Invalid recovery decision");
                cleanup.remove(execution);journal.clearStaged(dir);journal.terminal(dir,execution,"RECOVERED");count++;
            }
        }catch(IOException e){failure=e;}
        if(interrupted){
            var assigned=central.assigned(identity).path("jobs");if(!assigned.isArray()||assigned.size()>256)throw new IOException("Invalid recovery assignments");
            for(var node:assigned){if(!identity.workerId().toString().equals(node.path("workerId").asText()))throw new IOException("Invalid recovery ownership");UUID execution=uuid(node.path("executionId").asText());
                if(journal.contains(execution))continue;
                Path dir=journal.orphan(execution,node);var grant=recover(execution,"ABANDON");if(!Set.of("ABANDONED","SUPERSEDED","DONE").contains(grant.path("status").asText()))throw new IOException("Invalid recovery decision");
                cleanup.remove(execution);journal.terminal(dir,execution,"RECOVERED");count++;
            }
        }
        if(failure!=null)throw new IOException("Recovery incomplete; local state retained",failure);return count;
    }
    private JsonNode recover(UUID execution,String operation)throws IOException,InterruptedException {
        var grant=central.post(identity,"jobs/recover",Map.of("executionId",execution,"operation",operation));
        if(!execution.toString().equals(grant.path("executionId").asText()))throw new IOException("Invalid recovery acknowledgement");uuid(grant.path("jobId").asText());return grant;
    }
    private static UUID uuid(String value)throws IOException{try{return UUID.fromString(value);}catch(Exception e){throw new IOException("Invalid recovery identity");}}
    static void removeContainers(UUID execution)throws IOException,InterruptedException {
        var list=new ProcessBuilder("docker","ps","-aq","--filter","label=rps.scope=remote-worker","--filter","label=rps.execution="+execution).redirectError(ProcessBuilder.Redirect.DISCARD).start();
        String output;
        try{if(!list.waitFor(5,TimeUnit.SECONDS)||list.exitValue()!=0)throw new IOException("Docker recovery unavailable");byte[] bytes=list.getInputStream().readNBytes(8193);if(bytes.length>8192)throw new IOException("Recovery container limit");output=new String(bytes,java.nio.charset.StandardCharsets.UTF_8).strip();}finally{if(list.isAlive())list.destroyForcibly();}
        if(output.isEmpty())return;var args=new ArrayList<>(List.of("docker","rm","--force"));for(String id:output.split("\\s+")){if(!id.matches("[a-f0-9]{12,64}"))throw new IOException("Invalid container identity");args.add(id);}if(args.size()>131)throw new IOException("Recovery container limit");
        var remove=new ProcessBuilder(args).redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start();try{if(!remove.waitFor(5,TimeUnit.SECONDS)||remove.exitValue()!=0)throw new IOException("Container cleanup incomplete");}finally{if(remove.isAlive())remove.destroyForcibly();}
    }
}
