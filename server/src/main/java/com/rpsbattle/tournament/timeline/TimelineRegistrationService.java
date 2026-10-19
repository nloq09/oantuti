package com.rpsbattle.tournament.timeline;

import com.rpsbattle.account.AccountAuthorizationService;
import com.rpsbattle.auth.AuthException;
import com.rpsbattle.tournament.*;
import com.rpsbattle.tournament.domain.*;
import java.time.*;
import java.util.*;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
public class TimelineRegistrationService {
    private final AccountAuthorizationService accounts;
    private final TournamentService tournaments;
    private final OwnedTournamentService owned;
    private final TimelineRegistrationStore store;
    private final Clock clock;
    @org.springframework.beans.factory.annotation.Autowired TournamentAuthorizationService authorization;
    public TimelineRegistrationService(AccountAuthorizationService a,TournamentService t,OwnedTournamentService o,TimelineRegistrationStore s,Clock c){accounts=a;tournaments=t;owned=o;store=s;clock=c;}
    public record Signup(Boolean rulesAccepted,String intendedLanguage,String affiliation,String botId,String sourceCode,String versionId) {}
    public record Flow(TournamentPhase phase,String registrationStatus,TimelineRegistrationStore.View registration,String nextAction,Instant serverNow,Instant phaseEndsAt,Instant codingOpenAt,Set<String> allowedLanguages,long revision) {}
    private TournamentPhaseResolver.Resolution resolve(Tournament t,Instant now){if(t.timeline==null)throw new AuthException("TIMELINE_REQUIRED",409);return TournamentPhaseResolver.resolve(t.status,t.timeline,now,null,store.reviewComplete(UUID.fromString(t.id)));}
    private void registrationOpen(Tournament t){if(resolve(t,clock.instant()).phase()!=TournamentPhase.REGISTRATION_OPEN)throw new AuthException("REGISTRATION_NOT_OPEN",409);}
    public Flow read(Authentication auth,String id){var user=accounts.requireUser(auth).id().value();var t=tournaments.find(id);synchronized(t){return flow(t,user);}}
    private Flow flow(Tournament t,UUID user){var now=clock.instant();var phase=resolve(t,now);var registration=store.mine(UUID.fromString(t.id),user);String status=registration==null?"NOT_REGISTERED":registration.status();String action=phase.phase()==TournamentPhase.REGISTRATION_OPEN&&(registration==null||status.equals("CANCELLED"))?"REGISTER":switch(status){case "PENDING"->"WAIT_FOR_APPROVAL";case "APPROVED"->phase.phase()==TournamentPhase.CODING_OPEN?"OPEN_CODE_WORKSPACE":"WAIT_FOR_CODING";case "REJECTED"->"REGISTRATION_REJECTED";case "WAITLISTED"->"WAITLISTED";default->"VIEW_REGISTRATION_STATUS";};return new Flow(phase.phase(),status,registration,action,now,phase.phaseEndsAt(),t.timeline.codingOpenAt(),t.botPolicy.allowedLanguages(),t.revision);}
    private boolean reviewOpen(Tournament t){var phase=resolve(t,clock.instant()).phase();return Set.of(TournamentPhase.REGISTRATION_OPEN,TournamentPhase.REGISTRATION_REVIEW,TournamentPhase.WAITING_FOR_CODING).contains(phase);}
    public record Applications(List<TimelineRegistrationStore.View> items,int page,int size,long revision,boolean reviewOpen,Instant codingOpenAt) {}
    public Applications list(Authentication auth,String id,int page,String status){var t=tournaments.find(id);authorization.requireManager(auth,t);if(page<0||page>10000)throw new AuthException("INVALID_PAGING",400);if(status!=null&&!Set.of("PENDING","APPROVED","WAITLISTED","REJECTED","CANCELLED").contains(status))throw new AuthException("INVALID_FILTER",400);synchronized(t){return new Applications(store.page(UUID.fromString(id),page,status),page,25,t.revision,reviewOpen(t),t.timeline.codingOpenAt());}}
    public record Review(Long revision,String decision,String reason) {}
    public void review(Authentication auth,String id,UUID application,Review request){var actor=accounts.requireUser(auth);var t=tournaments.find(id);authorization.requireManager(auth,t);synchronized(t){
        if(request.revision()==null||request.revision()!=t.revision)throw new AuthException("STALE_TOURNAMENT_REVISION",409);
        if(!reviewOpen(t))throw new AuthException("APPLICATION_REVIEW_CLOSED",409);
        if(request.decision()==null||!Set.of("APPROVED","REJECTED","WAITLISTED").contains(request.decision()))throw new AuthException("INVALID_REGISTRATION_TRANSITION",400);
        String reason=com.rpsbattle.admin.AdminDirectoryService.reason(request.reason());
        owned.submissionMutation(t,auth,()->{var change=store.review(UUID.fromString(id),application,request.decision(),reason,actor.id().value(),clock.instant(),t.maxParticipants);TournamentAudit.append(t,"PARTICIPANT_APPLICATION_REVIEWED",actor.id().value(),application.toString(),reason,change.before(),request.decision());tournaments.changed(t);});
    }}
    public Flow register(Authentication auth,String id,Signup request){var actor=accounts.requireUser(auth);var t=tournaments.find(id);synchronized(t){registrationOpen(t);
        if(!Boolean.TRUE.equals(request.rulesAccepted()))throw new AuthException("RULES_ACCEPTANCE_REQUIRED",400);
        if(request.botId()!=null||request.sourceCode()!=null||request.versionId()!=null)throw new AuthException("REGISTRATION_PARTICIPANT_ONLY",400);
        if(request.intendedLanguage()==null||!t.botPolicy.allowedLanguages().contains(request.intendedLanguage()))throw new AuthException("BOT_LANGUAGE_NOT_ALLOWED",400);
        String affiliation=request.affiliation()==null?null:request.affiliation().trim();if(affiliation!=null&&(affiliation.length()>80||affiliation.codePoints().anyMatch(Character::isISOControl)))throw new AuthException("INVALID_AFFILIATION",400);
        owned.submissionMutation(t,auth,()->{var entry=store.register(UUID.fromString(id),actor.id().value(),actor.displayName(),request.intendedLanguage(),affiliation,t.maxParticipants,clock.instant());TournamentAudit.append(t,"PARTICIPANT_APPLICATION_SUBMITTED",actor.id().value(),entry.id().toString(),null,null,"PENDING");tournaments.changed(t);});return flow(t,actor.id().value());
    }}
    public Flow withdraw(Authentication auth,String id){var user=accounts.requireUser(auth).id().value();var t=tournaments.find(id);synchronized(t){registrationOpen(t);owned.submissionMutation(t,auth,()->{store.withdraw(UUID.fromString(id),user);TournamentAudit.append(t,"PARTICIPANT_APPLICATION_WITHDRAWN",user,id,null,null,"CANCELLED");tournaments.changed(t);});return flow(t,user);}}
}
