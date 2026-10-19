package com.rpsbattle.tournament;
import com.rpsbattle.tournament.domain.*;
import com.rpsbattle.tournament.coding.SubmissionStore;
import com.rpsbattle.auth.AuthException;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.security.core.Authentication;
@Service public class TimelineOverrideService {
 private final OwnedTournamentService owned;private final TournamentService tournaments;private final TournamentAuthorizationService authorization;private final SubmissionStore submissions;private final Clock clock;
 public TimelineOverrideService(OwnedTournamentService o,TournamentService t,TournamentAuthorizationService a,SubmissionStore s,Clock c){owned=o;tournaments=t;authorization=a;submissions=s;clock=c;}
 public record Change(Long revision,String action,Instant value,String reason){}
 public void change(Authentication auth,String id,Change request){var t=tournaments.find(id);authorization.requireManager(auth,t);synchronized(t){
  if(request.revision()==null||request.revision()!=t.revision)throw new AuthException("STALE_TOURNAMENT_REVISION",409);
  if(t.timeline==null||t.startedAt!=null||!Set.of(TournamentStatus.REGISTRATION,TournamentStatus.CHECK_IN,TournamentStatus.READY).contains(t.status))throw new AuthException("TIMELINE_OVERRIDE_CLOSED",409);
  String reason=com.rpsbattle.admin.AdminDirectoryService.reason(request.reason());if(reason.length()>400||request.value()==null||request.action()==null)throw new AuthException("INVALID_TIMELINE_OVERRIDE",400);var actor=owned.actor(auth);var old=t.timeline;var now=clock.instant();var dates=new Instant[]{old.registrationOpenAt(),old.registrationCloseAt(),old.codingOpenAt(),old.codingCloseAt(),old.validationStartAt(),old.validationDeadline(),old.checkInOpenAt(),old.checkInCloseAt(),old.tournamentStartAt(),old.estimatedEndAt(),old.resultPublishAt()};int index=switch(request.action()){case "EXTEND_REGISTRATION"->1;case "EXTEND_CODING"->3;case "START_CODING_EARLY"->2;case "EXTEND_CHECK_IN"->7;case "DELAY_START"->8;default->throw new AuthException("INVALID_TIMELINE_OVERRIDE",400);};
  if(request.action().equals("START_CODING_EARLY")){if(!now.isBefore(old.codingOpenAt())||request.value().isAfter(now)||!request.value().isBefore(old.codingOpenAt()))throw new AuthException("INVALID_TIMELINE_OVERRIDE",400);dates[2]=request.value();if(dates[1].isAfter(dates[2]))dates[1]=dates[2];}
  else{if(!request.value().isAfter(dates[index])||!request.value().isAfter(now))throw new AuthException("INVALID_TIMELINE_OVERRIDE",400);if(index==1&&!now.isBefore(old.codingOpenAt())||index==3&&(t.status!=TournamentStatus.REGISTRATION||t.registrations.stream().anyMatch(r->r.status()==TournamentRegistration.Status.CHECKED_IN)||!now.isBefore(old.codingCloseAt())&&!t.botPolicy.allowResubmitBeforeDeadline())||index==7&&t.status==TournamentStatus.READY)throw new AuthException("TIMELINE_OVERRIDE_CLOSED",409);var shift=Duration.between(dates[index],request.value());for(int i=index;i<dates.length;i++)if(dates[i]!=null)dates[i]=dates[i].plus(shift);}
  TournamentTimeline next;try{next=new TournamentTimeline(dates[0],dates[1],dates[2],dates[3],dates[4],dates[5],dates[6],dates[7],dates[8],dates[9],dates[10],old.deadlinePolicy(),old.validationFailurePolicy());}catch(IllegalArgumentException invalid){throw new AuthException("INVALID_TIMELINE_OVERRIDE",400);}
  owned.submissionMutation(t,auth,()->{
   if(index==3&&!now.isBefore(old.codingCloseAt())){submissions.reopenTimeline(t,actor);t.participants.clear();for(int i=0;i<t.registrations.size();i++){var r=t.registrations.get(i);if(r.status()!=TournamentRegistration.Status.CANCELLED)t.registrations.set(i,r.withStatus(TournamentRegistration.Status.PENDING,now));}}
   if(index==7)for(int i=0;i<t.registrations.size();i++){var r=t.registrations.get(i);if(r.status()==TournamentRegistration.Status.NOT_CHECKED_IN){submissions.requireLocked(t.id,r.id().toString());var bot=owned.timelineBot(r.botId().toString());t.registrations.set(i,r.withStatus(TournamentRegistration.Status.APPROVED,now));t.participants.add(new TournamentParticipant(r.id().toString(),r.botId().toString(),bot.token(),r.displayName(),UUID.randomUUID().getMostSignificantBits(),r.userId()).withHash(r.artifactHash()));}}
   t.timeline=next;TournamentAudit.append(t,"TIMELINE_OVERRIDE_"+request.action(),actor,t.id,reason,old.toString(),next.toString());tournaments.changed(t);
  });
 }}
}
