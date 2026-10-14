package com.rpsbattle.tournament.domain;
import java.util.*;
/** Immutable administrative state; null in older structure snapshots means the defaults. */
public record TournamentOperations(boolean autoAdvanceRounds,int releasedRound,
        Map<String,ParticipantStatus> participants,Map<String,String> reviews,
        Map<String,Object> rulesSnapshot,List<TournamentAuditEvent> audit) {
    public TournamentOperations {participants=Map.copyOf(participants);reviews=Map.copyOf(reviews);rulesSnapshot=Map.copyOf(rulesSnapshot);audit=List.copyOf(audit);}
    public static TournamentOperations defaults(){return new TournamentOperations(true,1,Map.of(),Map.of(),Map.of(),List.of());}
    public boolean active(String id){return participants.getOrDefault(id,ParticipantStatus.ACTIVE)==ParticipantStatus.ACTIVE;}
    public TournamentOperations participant(String id,ParticipantStatus status){var map=new HashMap<>(participants);map.put(id,status);return new TournamentOperations(autoAdvanceRounds,releasedRound,map,reviews,rulesSnapshot,audit);}
    public TournamentOperations review(String id,String reason){var map=new HashMap<>(reviews);if(reason==null)map.remove(id);else map.put(id,reason);return new TournamentOperations(autoAdvanceRounds,releasedRound,participants,map,rulesSnapshot,audit);}
    public TournamentOperations rounds(boolean auto,int released){return new TournamentOperations(auto,released,participants,reviews,rulesSnapshot,audit);}
    public TournamentOperations rules(Map<String,Object> rules){return new TournamentOperations(autoAdvanceRounds,releasedRound,participants,reviews,rules,audit);}
    public TournamentOperations append(TournamentAuditEvent event){var events=new ArrayList<>(audit);events.add(event);return new TournamentOperations(autoAdvanceRounds,releasedRound,participants,reviews,rulesSnapshot,events);}
}
