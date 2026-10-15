package com.rpsbattle.tournament.domain;
import java.time.Instant;import java.util.*;
public record MatchReadyWindow(String id,Instant deadline,Set<String> confirmed){
 public MatchReadyWindow{confirmed=Set.copyOf(confirmed);}
 public MatchReadyWindow confirm(String participant){var copy=new HashSet<>(confirmed);copy.add(participant);return new MatchReadyWindow(id,deadline,copy);}
}
