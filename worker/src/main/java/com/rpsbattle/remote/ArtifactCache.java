package com.rpsbattle.remote;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.*;
import java.nio.channels.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.function.LongSupplier;

/** One private, locked cache per agent. Opaque files: no extraction or host execution. */
public final class ArtifactCache implements AutoCloseable {
    public static final int ARTIFACT_LIMIT=8*1024*1024;
    public interface Loader {InputStream open()throws IOException,InterruptedException;}
    public record Metadata(String hash,long size,String language,String runtime,String format,long lastUsedAt) {}
    public record Stats(long bytes,long hits,long misses,long evictions) {}
    private final Path root;private final long capacity;private final LongSupplier now;private final ObjectMapper json=new ObjectMapper();private final FileChannel channel;private final FileLock lock;
    private final Map<String,Metadata> entries=new HashMap<>();private final Map<String,Integer> pins=new HashMap<>();private long bytes,hits,misses,evictions;private volatile Stats stats=new Stats(0,0,0,0);private boolean closed;
    public ArtifactCache(Path root,long capacity)throws IOException {this(root,capacity,System::currentTimeMillis);}
    ArtifactCache(Path root,long capacity,LongSupplier now)throws IOException {
        if(capacity<1)throw new IllegalArgumentException("Invalid cache capacity");this.root=root.toAbsolutePath().normalize();this.capacity=capacity;this.now=now;
        for(Path p=this.root;p!=null;p=p.getParent())if(Files.isSymbolicLink(p))throw new IOException("Unsafe cache path");Files.createDirectories(this.root);IdentityStore.secure(this.root);
        Path guard=this.root.resolve("cache.lock");if(Files.isSymbolicLink(guard))throw new IOException("Unsafe cache lock");channel=FileChannel.open(guard,StandardOpenOption.CREATE,StandardOpenOption.WRITE,LinkOption.NOFOLLOW_LINKS);
        FileLock acquired=null;
        try {
            IdentityStore.secure(guard);acquired=channel.tryLock();if(acquired==null)throw new IOException("Cache already in use");
            try(var children=Files.newDirectoryStream(this.root)){for(var child:children){String name=child.getFileName().toString();if(Files.isSymbolicLink(child))throw new IOException("Unsafe cache entry");if(name.startsWith(".incoming-")){if(!Files.isRegularFile(child,LinkOption.NOFOLLOW_LINKS))throw new IOException("Unsafe temporary entry");Files.delete(child);continue;}if(!name.matches("[a-f0-9]{64}"))continue;
                safe(child);Path manifest=child.resolve("manifest.json"),file=child.resolve("artifact.bin");safe(manifest);safe(file);
                try {if(Files.size(manifest)>4096)throw new IOException();var m=json.readValue(Files.readAllBytes(manifest),Metadata.class);if(!name.equals(m.hash())||m.size()<1||m.size()>ARTIFACT_LIMIT||Files.size(file)!=m.size()||!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS)||m.lastUsedAt()<0)throw new IOException();if(!new ArtifactRef(UUID.randomUUID(),name,m.language(),m.runtime()).format().equals(m.format()))throw new IOException();IdentityStore.secure(child);IdentityStore.secure(file);IdentityStore.secure(manifest);entries.put(name,m);bytes+=m.size();}
                catch(IOException|RuntimeException invalid){removeFiles(child);}
            }}
            room(0);publish();lock=acquired;
        }catch(IOException|RuntimeException e){if(acquired!=null)acquired.release();channel.close();throw e;}
    }
    private void publish(){stats=new Stats(bytes,hits,misses,evictions);}
    public Stats stats(){return stats;}
    private void safe(Path path)throws IOException {if(!path.toAbsolutePath().normalize().startsWith(root)||Files.isSymbolicLink(path)||Files.isSymbolicLink(path.getParent()))throw new IOException("Unsafe cache entry");}
    private void removeFiles(Path dir)throws IOException {safe(dir);for(String name:List.of("artifact.bin","manifest.json","manifest.tmp")){Path file=dir.resolve(name);safe(file);Files.deleteIfExists(file);}Files.deleteIfExists(dir);}
    private void remove(String hash)throws IOException {var m=entries.get(hash);removeFiles(root.resolve(hash));entries.remove(hash);bytes-=m.size();evictions++;publish();}
    private void room(long extra)throws IOException {while(bytes+extra>capacity){var oldest=entries.values().stream().filter(m->pins.getOrDefault(m.hash(),0)==0).min(Comparator.comparingLong(Metadata::lastUsedAt).thenComparing(Metadata::hash)).orElseThrow(()->new IOException("CACHE_CAPACITY_PINNED"));remove(oldest.hash());}}
    private void manifest(Path dir,Metadata m)throws IOException {Path tmp=dir.resolve("manifest.tmp"),target=dir.resolve("manifest.json");safe(tmp);safe(target);Files.deleteIfExists(tmp);try {Files.createFile(tmp);IdentityStore.secure(tmp);byte[] data=json.writeValueAsBytes(m);try(var out=FileChannel.open(tmp,StandardOpenOption.WRITE)){var buffer=java.nio.ByteBuffer.wrap(data);while(buffer.hasRemaining())out.write(buffer);out.force(true);}Files.move(tmp,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}finally{Files.deleteIfExists(tmp);}}
    private static String digest(Path path)throws IOException {try(var input=Files.newInputStream(path,LinkOption.NOFOLLOW_LINKS)){MessageDigest digest=sha();long count=0;byte[] block=new byte[8192];for(int n;(n=input.read(block))!=-1;){count+=n;if(count>ARTIFACT_LIMIT)throw new IOException("ARTIFACT_SIZE_LIMIT");digest.update(block,0,n);}return HexFormat.of().formatHex(digest.digest());}}
    private static MessageDigest sha(){try{return MessageDigest.getInstance("SHA-256");}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
    public synchronized Handle acquire(ArtifactRef ref,Loader loader)throws IOException,InterruptedException {
        if(closed)throw new IOException("Cache closed");String hash=ref.artifactHash();var existing=entries.get(hash);Path dir=root.resolve(hash),file=dir.resolve("artifact.bin");safe(dir);safe(file);
        if(existing!=null){
            boolean good=existing.language().equals(ref.language())&&existing.runtime().equals(ref.runtime())&&existing.format().equals(ref.format())&&Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS)&&Files.size(file)==existing.size()&&digest(file).equals(hash);
            if(good){hits++;var touched=new Metadata(hash,existing.size(),ref.language(),ref.runtime(),ref.format(),now.getAsLong());manifest(dir,touched);entries.put(hash,touched);pins.merge(hash,1,Integer::sum);publish();return new Handle(hash,file);}
            if(pins.getOrDefault(hash,0)>0)throw new IOException("ARTIFACT_HASH_MISMATCH");remove(hash);
        }
        misses++;publish();Path temporary=Files.createTempFile(root,".incoming-", ".bin");
        try {
            IdentityStore.secure(temporary);long size=0;MessageDigest digest=sha();
            try(var input=loader.open();var out=FileChannel.open(temporary,StandardOpenOption.WRITE)){byte[] block=new byte[8192];for(int n;(n=input.read(block))!=-1;){size+=n;if(size>ARTIFACT_LIMIT||size>capacity)throw new IOException("ARTIFACT_SIZE_LIMIT");digest.update(block,0,n);var buffer=java.nio.ByteBuffer.wrap(block,0,n);while(buffer.hasRemaining())out.write(buffer);}out.force(true);}
            if(size==0||!HexFormat.of().formatHex(digest.digest()).equals(hash))throw new IOException("ARTIFACT_HASH_MISMATCH");room(size);Files.createDirectory(dir);IdentityStore.secure(dir);Files.move(temporary,file,StandardCopyOption.ATOMIC_MOVE);var m=new Metadata(hash,size,ref.language(),ref.runtime(),ref.format(),now.getAsLong());
            try{manifest(dir,m);}catch(IOException e){removeFiles(dir);throw e;}entries.put(hash,m);bytes+=size;pins.merge(hash,1,Integer::sum);publish();return new Handle(hash,file);
        } finally {Files.deleteIfExists(temporary);}
    }
    public synchronized List<String> hashes(){return entries.keySet().stream().sorted().limit(2048).toList();}
    public final class Handle implements AutoCloseable {
        private final String hash;private final Path path;private boolean released;
        private Handle(String hash,Path path){this.hash=hash;this.path=path;}
        public Path path(){return path;}
        public void close(){synchronized(ArtifactCache.this){if(!released){released=true;pins.computeIfPresent(hash,(k,n)->n==1?null:n-1);}}}
    }
    public synchronized void close()throws IOException {if(closed)return;if(!pins.isEmpty())throw new IOException("Cache artifacts still in use");closed=true;try{lock.release();}finally{channel.close();}}
}
