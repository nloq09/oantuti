package com.rpsbattle.tournament.cleanup;
import com.rpsbattle.tournament.domain.*;
import java.time.*;
import java.util.*;
/** Evidence-based inactivity policy; names and player skill are never spam signals. */
public final class TournamentCleanupPolicy {
 public static final Duration GRACE=Duration.ofHours(72),DRAFT_IDLE=Duration.ofDays(14),CANCELLED_IDLE=Duration.ofDays(7);
 private TournamentCleanupPolicy(){}
 public static Optional<String> reason(Tournament t,Instant now){
  if(t.archived||t.ownerUserId==null||t.deferNotifications||t.startedAt!=null||!t.matches.isEmpty()||!t.participants.isEmpty()||!t.registrations.isEmpty()||!t.teams.teams().isEmpty()||!t.teams.fixtures().isEmpty())return Optional.empty();
  Duration idle=switch(t.status){case DRAFT->DRAFT_IDLE;case CANCELLED->CANCELLED_IDLE;default->null;};if(idle==null)return Optional.empty();
  var times=new ArrayList<Instant>(Arrays.asList(t.timing.registrationDeadline(),t.timing.checkInDeadline(),t.timing.startTime()));
  times.addAll(Arrays.asList(t.botPolicy.submissionOpenAt(),t.botPolicy.submissionDeadline()));
  if(t.timeline!=null)times.addAll(Arrays.asList(t.timeline.registrationOpenAt(),t.timeline.registrationCloseAt(),t.timeline.codingOpenAt(),t.timeline.codingCloseAt(),t.timeline.validationStartAt(),t.timeline.validationDeadline(),t.timeline.checkInOpenAt(),t.timeline.checkInCloseAt(),t.timeline.tournamentStartAt(),t.timeline.estimatedEndAt(),t.timeline.resultPublishAt()));
  if(times.stream().filter(Objects::nonNull).anyMatch(at->at.isAfter(now)))return Optional.empty();
  Instant last=t.createdAt;if(t.statusChangedAt!=null&&t.statusChangedAt.isAfter(last))last=t.statusChangedAt;
  for(var e:t.operations.audit())if(!e.type().startsWith("CLEANUP_")&&e.at().isAfter(last))last=e.at();
  if(last.plus(idle).isAfter(now))return Optional.empty();
  return Optional.of(t.status==TournamentStatus.DRAFT?"EMPTY_ABANDONED_DRAFT":"EMPTY_CANCELLED");
 }
}
