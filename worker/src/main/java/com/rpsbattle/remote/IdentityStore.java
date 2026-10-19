package com.rpsbattle.remote;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.channels.*;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.util.*;

/** Private, atomic machine credential store. Hold the directory lock for the agent lifetime. */
public final class IdentityStore implements AutoCloseable {
    public record Identity(String central, UUID workerId, String workerCredential) {
        @Override public String toString(){return "Identity[secrets redacted]";}
    }
    private final Path directory;
    private final FileChannel channel;
    private final FileLock lock;
    private final ObjectMapper json=new ObjectMapper();
    public IdentityStore(Path directory)throws IOException {
        this.directory=directory.toAbsolutePath().normalize();
        if(Files.isSymbolicLink(this.directory)) throw new IOException("Unsafe state directory");
        Files.createDirectories(this.directory); secure(this.directory);
        Path guard=this.directory.resolve("agent.lock");
        if(Files.isSymbolicLink(guard))throw new IOException("Unsafe lock file");
        channel=FileChannel.open(guard,StandardOpenOption.CREATE,StandardOpenOption.WRITE,LinkOption.NOFOLLOW_LINKS);
        try {secure(guard);lock=channel.tryLock();if(lock==null)throw new IOException("Worker state is already in use");}
        catch(IOException|RuntimeException e){channel.close();throw e;}
    }
    public Identity load()throws IOException {
        Path file=directory.resolve("identity.json");
        if(!Files.exists(file,LinkOption.NOFOLLOW_LINKS))return null;
        if(!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS) || Files.size(file)>4096)throw new IOException("Unsafe identity file");
        secure(file);
        Identity result=json.readValue(Files.readAllBytes(file),Identity.class);
        if(result.workerId()==null || result.central()==null || result.workerCredential()==null || !result.workerCredential().matches("wrc_[A-Za-z0-9_-]{43}"))throw new IOException("Invalid identity file");
        return result;
    }
    public void save(Identity identity)throws IOException {
        Path destination=directory.resolve("identity.json");
        if(Files.exists(destination,LinkOption.NOFOLLOW_LINKS))throw new IOException("Identity already exists");
        Path temporary=Files.createTempFile(directory,"identity-",".tmp");
        try {
            secure(temporary);
            byte[] bytes=json.writeValueAsBytes(identity);
            try(var out=FileChannel.open(temporary,StandardOpenOption.WRITE)){var buffer=java.nio.ByteBuffer.wrap(bytes);while(buffer.hasRemaining())out.write(buffer);out.force(true);}
            Files.move(temporary,destination,StandardCopyOption.ATOMIC_MOVE);
        } finally {Files.deleteIfExists(temporary);}
    }
    static void secure(Path path)throws IOException {
        if(Files.getFileAttributeView(path,PosixFileAttributeView.class,LinkOption.NOFOLLOW_LINKS)!=null) {
            Files.setPosixFilePermissions(path,PosixFilePermissions.fromString(Files.isDirectory(path)?"rwx------":"rw-------")); return;
        }
        var acl=Files.getFileAttributeView(path,AclFileAttributeView.class,LinkOption.NOFOLLOW_LINKS);
        if(acl==null)throw new IOException("Filesystem must support private permissions");
        var builder=AclEntry.newBuilder().setType(AclEntryType.ALLOW).setPrincipal(acl.getOwner()).setPermissions(EnumSet.allOf(AclEntryPermission.class));
        if(Files.isDirectory(path))builder.setFlags(AclEntryFlag.DIRECTORY_INHERIT,AclEntryFlag.FILE_INHERIT);
        acl.setAcl(List.of(builder.build()));
    }
    public void close()throws IOException {try{lock.release();}finally{channel.close();}}
}
