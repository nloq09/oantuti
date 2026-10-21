package com.rpsbattle.tournament.domain;
import java.time.Instant;
public record TournamentTiming(Instant registrationDeadline,Instant checkInDeadline,Instant startTime){
 public static TournamentTiming manual(){return new TournamentTiming(null,null,null);}
 public void validate(){if(registrationDeadline!=null&&checkInDeadline!=null&&!registrationDeadline.isBefore(checkInDeadline)||checkInDeadline!=null&&startTime!=null&&checkInDeadline.isAfter(startTime)||registrationDeadline!=null&&startTime!=null&&registrationDeadline.isAfter(startTime))throw new com.rpsbattle.online.DemoRequestException("INVALID_TOURNAMENT_TIMING");}
}
