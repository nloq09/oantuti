package com.rpsbattle.remote;
import java.nio.file.*;
import java.io.*;
import java.security.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
class ArtifactCacheTest {
 @TempDir Path temp;
 ArtifactRef ref(String data)throws Exception{return new ArtifactRef(UUID.randomUUID(),HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data.getBytes())),"PYTHON","python:3.12-slim");}
 ArtifactCache.Loader load(String data){return ()->new ByteArrayInputStream(data.getBytes());}
 @Test void hitRestartAndCorruptionAreVerifiedWithoutTrustingManifestAlone()throws Exception {
  var a=ref("hello");var downloads=new AtomicInteger();
  try(var cache=new ArtifactCache(temp.resolve("cache"),100)){try(var held=cache.acquire(a,()->{downloads.incrementAndGet();return load("hello").open();})){assertEquals("hello",Files.readString(held.path()));}try(var held=cache.acquire(a,()->{throw new IOException("must not download hit");})){assertEquals(1,cache.stats().hits());}assertEquals(5,cache.stats().bytes());}
  try(var cache=new ArtifactCache(temp.resolve("cache"),100)){try(var held=cache.acquire(a,()->{throw new IOException("restart should use verified cache");})){Files.writeString(held.path(),"wrong");}try(var held=cache.acquire(a,()->{downloads.incrementAndGet();return load("hello").open();})){assertEquals("hello",Files.readString(held.path()));}assertEquals(2,downloads.get());}
 }
 @Test void lruNeverEvictsPinnedFilesAndCapacityFailureDoesNotInstallNewArtifact()throws Exception {
  var clock=new AtomicLong();var a=ref("aaaa");var b=ref("bbbb");var c=ref("cccc");
  try(var cache=new ArtifactCache(temp.resolve("cache"),8,clock::incrementAndGet)) {
   try(var first=cache.acquire(a,load("aaaa"))){try(var second=cache.acquire(b,load("bbbb"))){}try(var third=cache.acquire(c,load("cccc"))){assertTrue(Files.exists(first.path()));assertEquals(Set.of(a.artifactHash(),c.artifactHash()),new HashSet<>(cache.hashes()));assertThrows(IOException.class,()->cache.acquire(b,load("bbbb")));assertEquals(8,cache.stats().bytes());}}
   try(var replacement=cache.acquire(b,load("bbbb"))){assertEquals(8,cache.stats().bytes());}
  }
 }
 @Test void wrongHashEmptyOversizedAndInterruptedDownloadLeaveNoCommittedEntry()throws Exception {
  var a=ref("hello");try(var cache=new ArtifactCache(temp.resolve("cache"),16)) {
   assertThrows(IOException.class,()->cache.acquire(a,load("wrong")));assertThrows(IOException.class,()->cache.acquire(a,load("")));assertThrows(IOException.class,()->cache.acquire(a,load("x".repeat(17))));assertThrows(InterruptedException.class,()->cache.acquire(a,()->{throw new InterruptedException();}));assertEquals(List.of(),cache.hashes());assertEquals(0,cache.stats().bytes());
   try(var children=Files.list(temp.resolve("cache"))){assertTrue(children.noneMatch(p->p.getFileName().toString().startsWith(".incoming-")));}
  }
 }
 @Test void cacheIsExclusivePrivateAndConfigurationRejectsInvalidLimits()throws Exception {
  Path root=temp.resolve("cache");try(var cache=new ArtifactCache(root,100)){assertThrows(Exception.class,()->new ArtifactCache(root,100));try(var held=cache.acquire(ref("hello"),load("hello"))){if(Files.getFileAttributeView(held.path(),java.nio.file.attribute.PosixFileAttributeView.class)!=null)assertEquals(java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"),Files.getPosixFilePermissions(held.path()));assertThrows(IOException.class,cache::close);}}
  assertThrows(IllegalArgumentException.class,()->WorkerConfig.parse(new String[]{"--central=https://example.com","--cache-mb=0"},Map.of()));assertThrows(IllegalArgumentException.class,()->new ArtifactRef(UUID.randomUUID(),"../escape","PYTHON","python:3.12-slim"));
  assertThrows(IllegalArgumentException.class,()->new ArtifactRef(UUID.randomUUID(),"a".repeat(64),"UNKNOWN",""));
 }
}
