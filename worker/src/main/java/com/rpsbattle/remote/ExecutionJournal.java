package com.rpsbattle.remote;

import com.fasterxml.jackson.databind.*;
import com.rpsbattle.replay.ReplayService;
import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.security.*;
import java.util.*;

/** Private durable outbox. A STARTING record precedes the Central start request; never run it twice. */
public final class ExecutionJournal {
    private final Path root;
    private final ObjectMapper json=new ObjectMapper();
    public ExecutionJournal(Path root)throws IOException {
        this.root=root.toAbsolutePath().normalize();for(Path p=this.root;p!=null;p=p.getParent())if(Files.isSymbolicLink(p))throw new IOException("Unsafe execution state");
        Files.createDirectories(this.root);IdentityStore.secure(this.root);
    }
    public boolean pending()throws IOException {try(var entries=Files.list(root)){return entries.findAny().isPresent();}}
    public Path begin(ExecutionJob job,JsonNode assignment)throws IOException {
        Path dir=root.resolve(job.executionId().toString());Files.createDirectory(dir);IdentityStore.secure(dir);
        write(dir.resolve("assignment.json"),assignment);write(dir.resolve("outcome.json"),Map.of("status","STARTING","executionId",job.executionId()));return dir;
    }
    public Path stage(Path dir,String seat,Path cached,ArtifactRef ref)throws IOException {
        safe(dir);if(!Set.of("A","B").contains(seat))throw new IllegalArgumentException();
        Path file=dir.resolve("bot-"+seat+".bin");safe(file);Files.copy(cached,file);IdentityStore.secure(file);
        if(Files.size(file)>ArtifactCache.ARTIFACT_LIMIT)throw new IOException("ARTIFACT_SIZE_LIMIT");
        try{if(!HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file))).equals(ref.artifactHash()))throw new IOException("ARTIFACT_HASH_MISMATCH");}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}
        // Docker UID 65534 must read the mounted file. Its host parent stays owner-only (0700).
        if(Files.getFileAttributeView(file,java.nio.file.attribute.PosixFileAttributeView.class)!=null)
            Files.setPosixFilePermissions(file,java.nio.file.attribute.PosixFilePermissions.fromString("rw-r--r--"));
        return file;
    }
    public void clearStaged(Path dir)throws IOException {safe(dir);for(String seat:List.of("A","B")){Path file=dir.resolve("bot-"+seat+".bin");safe(file);Files.deleteIfExists(file);}}
    public void finish(Path dir,ExecutionJob job,HeadlessMatch.Result result)throws IOException {
        safe(dir);Path replay=dir.resolve("replay.json");Files.createFile(replay);IdentityStore.secure(replay);
        new ReplayService().write(replay,result.replay());try(var channel=FileChannel.open(replay,StandardOpenOption.WRITE)){channel.force(true);}
        if(Files.size(replay)>32*1024*1024)throw new IOException("REPLAY_SIZE_LIMIT");
        var summary=new LinkedHashMap<String,Object>();summary.put("status","READY_FOR_UPLOAD");summary.put("executionId",job.executionId());summary.put("jobId",job.jobId());summary.put("matchId",job.match().matchId());
        summary.put("winner",result.winner());summary.put("loser",result.loser());summary.put("reason",result.reason());summary.put("turnCount",result.turnCount());summary.put("durationMs",result.durationMs());
        summary.put("artifactHashA",job.match().botA().artifactHash());summary.put("artifactHashB",job.match().botB().artifactHash());summary.put("seed",job.match().seed());
        try{summary.put("replayChecksum",HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(replay))));}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}
        write(dir.resolve("outcome.json"),summary);
    }
    public void failure(Path dir,ExecutionJob job,String code)throws IOException {write(dir.resolve("outcome.json"),Map.of("status","FAILED","executionId",job.executionId(),"reason",code));}
    private void safe(Path path)throws IOException {if(!path.toAbsolutePath().normalize().startsWith(root)||Files.isSymbolicLink(path)||Files.isSymbolicLink(path.getParent()))throw new IOException("Unsafe execution state");}
    private void write(Path file,Object value)throws IOException {
        safe(file);Path tmp=file.resolveSibling(file.getFileName()+".tmp");safe(tmp);Files.createFile(tmp);IdentityStore.secure(tmp);
        try {byte[] bytes=json.writeValueAsBytes(value);try(var out=FileChannel.open(tmp,StandardOpenOption.WRITE)){var b=java.nio.ByteBuffer.wrap(bytes);while(b.hasRemaining())out.write(b);out.force(true);}Files.move(tmp,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}finally{Files.deleteIfExists(tmp);}
    }
}
