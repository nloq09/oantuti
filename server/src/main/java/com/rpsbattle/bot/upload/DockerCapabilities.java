package com.rpsbattle.bot.upload;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.*;

/** One bounded probe flight per instance; failures are cached too. Concurrent readers never wait. */
final class DockerCapabilities {
 private record Snapshot(Map<BotLanguage,Boolean> values,long expires){}
 private final BiPredicate<List<String>,String> probe;
 private final LongSupplier ticker;
 private final AtomicBoolean checking=new AtomicBoolean();
 private volatile Snapshot snapshot;
 DockerCapabilities(BiPredicate<List<String>,String> probe,LongSupplier ticker){this.probe=probe;this.ticker=ticker;}
 boolean available(BotLanguage language){
  var known=snapshot;if(known!=null&&ticker.getAsLong()-known.expires()<0)return known.values().getOrDefault(language,false);
  if(!checking.compareAndSet(false,true))return false;
  try{
   known=snapshot;if(known!=null&&ticker.getAsLong()-known.expires()<0)return known.values().getOrDefault(language,false);
   boolean linux=probe.test(List.of("docker","info","--format","{{.OSType}}"),"linux");
   var images=new HashMap<String,Boolean>();var values=new EnumMap<BotLanguage,Boolean>(BotLanguage.class);
   for(var kind:BotLanguage.values())values.put(kind,linux&&images.computeIfAbsent(kind.image,image->probe.test(List.of("docker","image","inspect",image,"--format","{{.Id}}"),"sha256:")));
   snapshot=new Snapshot(Map.copyOf(values),ticker.getAsLong()+TimeUnit.SECONDS.toNanos(5));return values.get(language);
  }finally{checking.set(false);}
 }
}
