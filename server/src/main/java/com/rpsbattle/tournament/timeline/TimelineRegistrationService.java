package com.rpsbattle.tournament.timeline;

import com.rpsbattle.account.AccountAuthorizationService;
import com.rpsbattle.auth.AuthException;
import com.rpsbattle.tournament.*;
import com.rpsbattle.tournament.registration.*;
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
    @org.springframework.beans.factory.annotation.Autowired RegistrationEligibilityService eligibility;
    @org.springframework.beans.factory.annotation.Autowired TournamentAuthorizationService authorization;
    @org.springframework.beans.factory.annotation.Autowired com.rpsbattle.tournament.coding.SubmissionStore submissions;
    @org.springframework.beans.factory.annotation.Autowired com.rpsbattle.tournament.coding.FinalValidationStore finals;
    public TimelineRegistrationService(AccountAuthorizationService a,TournamentService t,OwnedTournamentService o,TimelineRegistrationStore s,Clock c){accounts=a;tournaments=t;owned=o;store=s;clock=c;}
    public record Signup(Boolean rulesAccepted,String intendedLanguage,String affiliation,String botId,String sourceCode,String versionId) {}
    public record CheckIn(String status,UUID registrationId,Instant checkedInAt,boolean canCheckIn,Instant opensAt,Instant closesAt,Instant startsAt,long checkedInCount) {}
    public record Flow(TournamentPhase phase,String registrationStatus,TimelineRegistrationStore.View registration,String nextAction,Instant serverNow,Instant phaseEndsAt,Instant codingOpenAt,Set<String> allowedLanguages,long revision,Instant codingCloseAt,boolean canAccessWorkspace,boolean canModifyWorkspace,String submissionStatus,com.rpsbattle.tournament.coding.CodingFreeze.View frozenSubmission,com.rpsbattle.tournament.coding.FinalValidationStore.View finalValidation,CheckIn checkIn,String participantStatus,boolean emergencyCoding,TimelineRegistrationStore.Control registrationControl,boolean canWithdraw) {}
    private TournamentPhaseResolver.Resolution resolve(Tournament t,Instant now){if(t.timeline==null)throw new AuthException("TIMELINE_REQUIRED",409);return store.phase(t,now);}
    private void registrationOpen(Tournament t){if(resolve(t,clock.instant()).phase()!=TournamentPhase.REGISTRATION_OPEN)throw new AuthException("REGISTRATION_NOT_OPEN",409);}
    public Flow read(Authentication auth,String id){var user=accounts.requireUser(auth).id().value();var t=tournaments.find(id);synchronized(t){return flow(t,user);}}
    private Flow flow(Tournament t,UUID user){
        var now=clock.instant();var phase=resolve(t,now);var registration=store.mine(UUID.fromString(t.id),user);String status=registration==null?"NOT_REGISTERED":registration.status();
        var emergency=status.equals("APPROVED")?finals.emergencyCutoff(t.id,user):null;
        var cutoff=emergency==null?t.timeline.codingCloseAt():emergency;
        boolean emergencyOpen=store.codingAllowed(t.id)&&emergency!=null&&now.isBefore(emergency)&&t.status==TournamentStatus.REGISTRATION&&t.startedAt==null;
        if(emergencyOpen)phase=new TournamentPhaseResolver.Resolution(TournamentPhase.CODING_OPEN,emergency);
        boolean approved=status.equals("APPROVED"),closed=store.codingAllowed(t.id)&&!now.isBefore(cutoff);
        var frozen=approved&&closed?submissions.frozenMine(t.id,user,cutoff):null;
        var validation=approved&&closed?finals.mine(t.id,user,cutoff):null;
        var submission=approved&&!closed?submissions.mine(t.id,user):null;
        String submissionStatus=frozen!=null?(frozen.decision().equals("MISSING")?"SUBMISSION_MISSING":"SUBMISSION_FROZEN"):approved&&closed?"FREEZE_PENDING":submission==null?"NO_SUBMISSION":submission.status();
        if(validation!=null)submissionStatus=validation.status().equals("PASSED")&&validation.applied()?"BOT_READY":validation.status().equals("FAILED")?"VALIDATION_FAILED":validation.status().equals("INFRA_ERROR")?"VALIDATION_INFRA_ERROR":"VALIDATING";
        String action=phase.phase()==TournamentPhase.REGISTRATION_OPEN&&(registration==null||status.equals("CANCELLED"))?"REGISTER":switch(status){case "PENDING","NEEDS_REVIEW"->"WAIT_FOR_APPROVAL";case "APPROVED"->closed?"VIEW_FROZEN_SUBMISSION":phase.phase()==TournamentPhase.CODING_OPEN?"OPEN_CODE_WORKSPACE":"WAIT_FOR_CODING";case "REJECTED"->"REGISTRATION_REJECTED";case "WAITLISTED"->"WAITLISTED";default->"VIEW_REGISTRATION_STATUS";};
        if(validation!=null)action="VIEW_FINAL_VALIDATION";
        var entry=t.registrations.stream().filter(r->r.userId().equals(user)&&(r.active()||r.status()==TournamentRegistration.Status.NOT_CHECKED_IN)).findFirst().orElse(null);
        boolean checked=entry!=null&&entry.status()==TournamentRegistration.Status.CHECKED_IN;
        boolean eligible=approved&&submissionStatus.equals("BOT_READY")&&entry!=null&&Set.of(TournamentRegistration.Status.APPROVED,TournamentRegistration.Status.CHECKED_IN).contains(entry.status());
        boolean open=Set.of(TournamentStatus.REGISTRATION,TournamentStatus.CHECK_IN).contains(t.status)&&t.startedAt==null&&!now.isBefore(t.timeline.checkInOpenAt())&&now.isBefore(t.timeline.checkInCloseAt());
        String checkStatus=checked?"CHECKED_IN":approved&&submissionStatus.equals("BOT_READY")&&entry!=null&&!now.isBefore(t.timeline.checkInCloseAt())?"MISSED":eligible?"BOT_READY":"NOT_ELIGIBLE";
        var checkIn=new CheckIn(checkStatus,entry==null?null:entry.id(),entry==null?null:entry.checkedInAt(),eligible&&open&&!checked,t.timeline.checkInOpenAt(),t.timeline.checkInCloseAt(),t.timeline.tournamentStartAt(),t.registrations.stream().filter(r->r.status()==TournamentRegistration.Status.CHECKED_IN).count());
        if(checked)action="OPEN_WAITING_ROOM";else if(checkIn.canCheckIn())action="CHECK_IN";else if(approved&&checkStatus.equals("MISSED"))action="CHECK_IN_MISSED";
        return new Flow(phase.phase(),status,registration,action,now,phase.phaseEndsAt(),t.timeline.codingOpenAt(),t.botPolicy.allowedLanguages(),t.revision,cutoff,approved&&store.codingAllowed(t.id)&&t.status!=TournamentStatus.DRAFT&&!now.isBefore(t.timeline.codingOpenAt()),approved&&phase.phase()==TournamentPhase.CODING_OPEN,submissionStatus,frozen,validation,checkIn,participantStatus(t,user,phase.phase(),status,submissionStatus,checkIn),emergencyOpen,store.control(UUID.fromString(t.id)),Set.of("NEEDS_REVIEW","PENDING","APPROVED","WAITLISTED").contains(status)&&(store.control(UUID.fromString(t.id)).enabled()?reviewOpen(t):phase.phase()==TournamentPhase.REGISTRATION_OPEN));
    }
    private String participantStatus(Tournament t,UUID user,TournamentPhase phase,String registration,String submission,CheckIn check){
        if(phase==TournamentPhase.DRAFT||phase==TournamentPhase.UPCOMING)return "REGISTER_NOT_OPEN";
        if(registration.equals("NOT_REGISTERED")||registration.equals("CANCELLED"))return "NOT_REGISTERED";
        if(!registration.equals("APPROVED"))return switch(registration){case "PENDING","NEEDS_REVIEW"->"REGISTRATION_NEEDS_REVIEW";case "WAITLISTED"->"WAITLISTED";default->"REGISTRATION_REJECTED";};
        if(phase==TournamentPhase.CANCELLED)return "TOURNAMENT_CANCELLED";
        if(submission.equals("SUBMISSION_MISSING"))return "SUBMISSION_MISSING";
        if(submission.equals("VALIDATION_FAILED"))return "VALIDATION_FAILED";
        if(submission.equals("VALIDATING")||submission.equals("VALIDATION_INFRA_ERROR"))return "VALIDATING";
        if(check.status().equals("MISSED"))return "MISSED_CHECK_IN";
        if(Set.of(TournamentPhase.RUNNING,TournamentPhase.PAUSED,TournamentPhase.RECOVERY_REQUIRED,TournamentPhase.FINISHED).contains(phase)){
            var mine=t.participants.stream().filter(p->user.equals(p.ownerUserId())&&t.operations.active(p.participantId())).map(TournamentParticipant::participantId).collect(java.util.stream.Collectors.toSet());
            if(mine.isEmpty())return "ELIMINATED";
            if(phase==TournamentPhase.FINISHED){if(t.format==TournamentFormat.TEAM_ROUND_ROBIN){String teamWinner=com.rpsbattle.tournament.scheduler.TeamScheduler.winner(t);return t.teams.teams().stream().anyMatch(team->team.id().equals(teamWinner)&&team.roster().stream().anyMatch(mine::contains))?"CHAMPION":"FINISHED";}var champion=(com.rpsbattle.tournament.ranking.RankingEntry)tournaments.summary(t).get("champion");return champion!=null&&mine.contains(champion.participantId())?"CHAMPION":"FINISHED";}
            if(t.bracket.stream().anyMatch(n->n.resolution()!=null&&(mine.contains(n.slotA())||mine.contains(n.slotB()))&&!mine.contains(n.winnerParticipantId())))return "ELIMINATED";
            if(t.groupHistory.stream().anyMatch(stage->stage.qualifiers().stream().noneMatch(mine::contains))||t.groupStage!=null&&t.groupStage.locked()&&t.groupStage.qualifiers().stream().noneMatch(mine::contains))return "ELIMINATED";
            return "COMPETING";
        }
        if(check.status().equals("CHECKED_IN"))return phase==TournamentPhase.READY_TO_START?"WAITING_FOR_START":"CHECKED_IN";
        if(check.canCheckIn())return "CHECK_IN_REQUIRED";
        if(submission.equals("BOT_READY"))return "BOT_READY";
        if(phase==TournamentPhase.CODING_OPEN)return Set.of("SUBMITTED","LOCKED").contains(submission)?"SUBMISSION_READY":"CODING";
        if(phase==TournamentPhase.SUBMISSION_FROZEN||submission.equals("FREEZE_PENDING")||submission.equals("SUBMISSION_FROZEN"))return "SUBMISSION_FROZEN";
        if(phase==TournamentPhase.WAITING_FOR_CODING||phase==TournamentPhase.REGISTRATION_REVIEW)return "WAITING_FOR_CODING";
        return "REGISTRATION_APPROVED";
    }
    private boolean reviewOpen(Tournament t){if(store.control(UUID.fromString(t.id)).lockedAt()!=null||!clock.instant().isBefore(t.timeline.codingOpenAt()))return false;var phase=resolve(t,clock.instant()).phase();return Set.of(TournamentPhase.REGISTRATION_OPEN,TournamentPhase.REGISTRATION_REVIEW,TournamentPhase.WAITING_FOR_CODING).contains(phase);}
    public record Applications(List<TimelineRegistrationStore.Application> items,int page,int size,long revision,boolean reviewOpen,Instant codingOpenAt,TimelineRegistrationStore.Control registrationControl,long total) {}
    public Applications list(Authentication auth,String id,int page,String status){return list(auth,id,new ApplicationFilter(page,25,status,null,null,null,null,null,null));}
    public Applications list(Authentication auth,String id,ApplicationFilter filter){
        var t=tournaments.find(id);authorization.requireManager(auth,t);
        synchronized(t){
            if(t.timeline==null)throw new AuthException("TIMELINE_REQUIRED",409);
            UUID key=UUID.fromString(id);long total=store.count(key,filter);
            int page=(int)Math.min(filter.page(),Math.max(0,(total-1)/filter.size()));var bounded=filter.atPage(page);
            return new Applications(store.page(key,bounded),page,filter.size(),t.revision,reviewOpen(t),t.timeline.codingOpenAt(),store.control(key),total);
        }
    }
    public record ApplicationDetails(TimelineRegistrationStore.Details details,RegistrationPolicy policy,boolean languageAllowed,
            List<TournamentAuditEvent> history,boolean moreHistory,long revision,boolean canReview) {}
    public ApplicationDetails details(Authentication auth,String id,UUID application){
        var t=tournaments.find(id);authorization.requireManager(auth,t);
        synchronized(t){
            if(t.timeline==null)throw new AuthException("TIMELINE_REQUIRED",409);
            var facts=store.details(UUID.fromString(id),application);
            var history=t.operations.audit().stream().filter(e->application.toString().equals(e.target())&&Set.of("PARTICIPANT_APPLICATION_SUBMITTED","PARTICIPANT_APPLICATION_WITHDRAWN","PARTICIPANT_APPLICATION_MESSAGE_UPDATED","PARTICIPANT_APPLICATION_REVIEWED","WAITLIST_PROMOTED","WAITLIST_REJECTED").contains(e.type())).sorted(Comparator.comparingLong(TournamentAuditEvent::sequence).reversed()).limit(101).toList();
            return new ApplicationDetails(facts,t.registrationPolicy,t.botPolicy.allowedLanguages().contains(facts.application().intendedLanguage()),history.stream().limit(100).toList(),history.size()>100,t.revision,reviewOpen(t));
        }
    }
    public record BulkEntry(UUID id,String status,Instant registeredAt) {}
    public record BulkReview(Long revision,String decision,String reason,List<BulkEntry> items,String publicMessage) {}
    public record BulkItem(UUID id,boolean success,String error) {}
    public record BulkResult(List<BulkItem> items,long revision,int succeeded,int failed) {}
    public BulkResult bulkReview(Authentication auth,String id,BulkReview request){
        accounts.requireUser(auth);var t=tournaments.find(id);authorization.requireManager(auth,t);
        synchronized(t){
            if(request.revision()==null||request.revision()!=t.revision)throw new AuthException("STALE_TOURNAMENT_REVISION",409);
            if(t.timeline==null)throw new AuthException("TIMELINE_REQUIRED",409);
            if(!reviewOpen(t))throw new AuthException("APPLICATION_REVIEW_CLOSED",409);
            if(request.decision()==null||!Set.of("APPROVED","REJECTED","WAITLISTED").contains(request.decision()))throw new AuthException("INVALID_REGISTRATION_TRANSITION",400);
            String reason=com.rpsbattle.admin.AdminDirectoryService.reason(request.reason());
            String message=publicMessage(request.publicMessage());
            if(request.items()==null||request.items().isEmpty()||request.items().size()>100)throw new AuthException("INVALID_BULK_REVIEW",400);
            var ids=new HashSet<UUID>();
            for(var entry:request.items())if(entry==null||entry.id()==null||entry.status()==null||!Set.of("NEEDS_REVIEW","APPROVED","WAITLISTED","REJECTED","CANCELLED").contains(entry.status())||entry.registeredAt()==null||!ids.add(entry.id()))throw new AuthException("INVALID_BULK_REVIEW",400);
            var results=new ArrayList<BulkItem>();
            for(var entry:request.items()){
                try{review(auth,id,entry.id(),new Review(t.revision,request.decision(),reason,message),entry.status(),entry.registeredAt());results.add(new BulkItem(entry.id(),true,null));}
                catch(AuthException error){results.add(new BulkItem(entry.id(),false,error.code()));}
            }
            int succeeded=(int)results.stream().filter(BulkItem::success).count();
            return new BulkResult(List.copyOf(results),t.revision,succeeded,results.size()-succeeded);
        }
    }
    public record Review(Long revision,String decision,String reason,String publicMessage) {
        public Review(Long revision,String decision,String reason){this(revision,decision,reason,null);}
    }
    private static String publicMessage(String value){
        if(value==null||value.isBlank())return null;
        value=value.trim();if(value.length()>500||value.chars().anyMatch(c->Character.isISOControl(c)&&c!='\n'&&c!='\t'))throw new AuthException("INVALID_PUBLIC_MESSAGE",400);return value;
    }
    public void review(Authentication auth,String id,UUID application,Review request){review(auth,id,application,request,null,null);}
    private void review(Authentication auth,String id,UUID application,Review request,String expectedStatus,Instant expectedRegisteredAt){var actor=accounts.requireUser(auth);var t=tournaments.find(id);authorization.requireManager(auth,t);synchronized(t){
        if(t.timeline==null)throw new AuthException("TIMELINE_REQUIRED",409);
        if(request.revision()==null||request.revision()!=t.revision)throw new AuthException("STALE_TOURNAMENT_REVISION",409);
        if(!reviewOpen(t))throw new AuthException("APPLICATION_REVIEW_CLOSED",409);
        if(request.decision()==null||!Set.of("APPROVED","REJECTED","WAITLISTED").contains(request.decision()))throw new AuthException("INVALID_REGISTRATION_TRANSITION",400);
        String reason=com.rpsbattle.admin.AdminDirectoryService.reason(request.reason());
        String message=publicMessage(request.publicMessage());
        owned.submissionMutation(t,auth,()->{
            if(request.decision().equals("APPROVED")){
                var facts=store.details(UUID.fromString(id),application);
                if(!facts.accountActive())throw new AuthException("ACCOUNT_NOT_ELIGIBLE",409);
                if(!t.botPolicy.allowedLanguages().contains(facts.application().intendedLanguage()))throw new AuthException("LANGUAGE_NOT_ALLOWED",409);
            }
            var change=store.review(UUID.fromString(id),application,request.decision(),reason,actor.id().value(),clock.instant(),t.maxParticipants,expectedStatus,expectedRegisteredAt);store.publicMessage(UUID.fromString(id),application,message);if(!Objects.equals(change.after().publicMessage(),message))TournamentAudit.append(t,"PARTICIPANT_APPLICATION_MESSAGE_UPDATED",actor.id().value(),application.toString(),reason,change.after().publicMessage(),message);TournamentAudit.append(t,"PARTICIPANT_APPLICATION_REVIEWED",actor.id().value(),application.toString(),reason,change.before(),request.decision());promote(t,application);tournaments.changed(t);});
    }}
    public record PolicyUpdate(Long revision,RegistrationPolicy policy,String reason) {}
    public void configurePolicy(Authentication auth,String id,PolicyUpdate request){
        var actor=accounts.requireUser(auth);var t=tournaments.find(id);authorization.requireManager(auth,t);
        synchronized(t){
            if(request.revision()==null||request.revision()!=t.revision)throw new AuthException("STALE_TOURNAMENT_REVISION",409);
            if(request.policy()==null)throw new AuthException("INVALID_REGISTRATION_POLICY",400);
            if(t.status!=TournamentStatus.DRAFT||store.hasApplications(UUID.fromString(id)))throw new AuthException("REGISTRATION_POLICY_LOCKED",409);
            String reason=com.rpsbattle.admin.AdminDirectoryService.reason(request.reason());
            owned.submissionMutation(t,auth,()->{var before=t.registrationPolicy;t.registrationPolicy=request.policy();store.enable(UUID.fromString(id));TournamentAudit.append(t,"REGISTRATION_POLICY_UPDATED",actor.id().value(),id,reason,before.toString(),request.policy().toString());tournaments.changed(t);});
        }
    }
    public Flow register(Authentication auth,String id,Signup request){var actor=accounts.requireUser(auth);var t=tournaments.find(id);synchronized(t){registrationOpen(t);
        if(!Boolean.TRUE.equals(request.rulesAccepted()))throw new AuthException("RULES_ACCEPTANCE_REQUIRED",400);
        if(request.botId()!=null||request.sourceCode()!=null||request.versionId()!=null)throw new AuthException("REGISTRATION_PARTICIPANT_ONLY",400);
        if(request.intendedLanguage()==null||!t.botPolicy.allowedLanguages().contains(request.intendedLanguage()))throw new AuthException("BOT_LANGUAGE_NOT_ALLOWED",400);
        String affiliation=request.affiliation()==null?null:request.affiliation().trim();if(affiliation!=null&&(affiliation.length()>80||affiliation.codePoints().anyMatch(Character::isISOControl)))throw new AuthException("INVALID_AFFILIATION",400);
        var previous=store.mine(UUID.fromString(id),actor.id().value());
        var decision=eligibility.evaluate(new RegistrationEligibilityService.Context(actor.status()==com.rpsbattle.account.domain.AccountStatus.ACTIVE,true,previous!=null&&!previous.status().equals("CANCELLED"),Boolean.TRUE.equals(request.rulesAccepted()),request.intendedLanguage(),t.botPolicy.allowedLanguages(),affiliation,t.registrationPolicy));
        if(decision.decision()==EligibilityDecision.Decision.REJECT){var code=decision.reasonCodes().getFirst();throw new AuthException(code.name(),code==EligibilityReasonCode.DUPLICATE_REGISTRATION?409:400);}
        owned.submissionMutation(t,auth,()->{promote(t);var entry=store.register(UUID.fromString(id),actor.id().value(),actor.displayName(),request.intendedLanguage(),affiliation,t.maxParticipants,clock.instant(),decision);TournamentAudit.append(t,"PARTICIPANT_APPLICATION_SUBMITTED",actor.id().value(),entry.id().toString(),entry.status().equals("WAITLISTED")?EligibilityReasonCode.CAPACITY_FULL.name():String.join(",",decision.reasonCodes().stream().map(Enum::name).toList()),null,entry.status());tournaments.changed(t);});return flow(t,actor.id().value());
    }}
    private void promote(Tournament t){promote(t,null);}
    private void promote(Tournament t,UUID excluded){
        if(t.startedAt!=null||t.status!=TournamentStatus.REGISTRATION||!clock.instant().isBefore(t.timeline.codingOpenAt()))return;
        for(var change:store.promote(UUID.fromString(t.id),t.registrationPolicy,t.botPolicy.allowedLanguages(),clock.instant(),excluded)){
            String type=change.after().equals("APPROVED")?"WAITLIST_PROMOTED":change.after().equals("NEEDS_REVIEW")?"WAITLIST_REVIEW_REQUIRED":"WAITLIST_REJECTED";
            TournamentAudit.append(t,type,null,change.applicationId().toString(),change.reason(),change.before(),change.after());
        }
    }
    public record LifecycleAction(Long revision,String reason) {}
    public record Summary(TimelineRegistrationStore.Control control,Map<String,Long> counts,
            boolean registrationOpen,boolean canClose,boolean canLock,Instant registrationCloseAt,
            Instant codingOpenAt,long revision,Instant serverNow,int capacity,long remainingSlots,
            RegistrationPolicy policy,boolean canEditPolicy,boolean canReview) {}
    public Summary summary(Authentication auth,String id){
        var t=tournaments.find(id);authorization.requireManager(auth,t);
        synchronized(t){
            if(t.timeline==null)throw new AuthException("TIMELINE_REQUIRED",409);
            var now=clock.instant();var state=store.control(UUID.fromString(id));var counts=store.counts(UUID.fromString(id));
            boolean beforeCoding=now.isBefore(t.timeline.codingOpenAt()),closed=state.closedAt()!=null||!now.isBefore(t.timeline.registrationCloseAt());
            boolean active=t.status==TournamentStatus.REGISTRATION&&t.startedAt==null;
            boolean admission=active&&!now.isBefore(t.timeline.registrationOpenAt())&&!closed&&state.lockedAt()==null;
            int minimum=t.format==TournamentFormat.GROUP_KNOCKOUT?4:t.format==TournamentFormat.TEAM_ROUND_ROBIN?6:2;
            return new Summary(state,Map.copyOf(counts),admission,admission&&beforeCoding,
                    active&&closed&&beforeCoding&&state.lockedAt()==null&&counts.get("needsReview")==0&&counts.get("approved")>=minimum,
                    t.timeline.registrationCloseAt(),t.timeline.codingOpenAt(),t.revision,now,t.maxParticipants,
                    Math.max(0L,t.maxParticipants-counts.get("approved")),t.registrationPolicy,
                    t.status==TournamentStatus.DRAFT&&counts.get("total")==0&&state.lockedAt()==null,reviewOpen(t));
        }
    }
    public void lifecycle(Authentication auth,String id,LifecycleAction request,boolean lock){
        var actor=accounts.requireUser(auth);var t=tournaments.find(id);authorization.requireManager(auth,t);synchronized(t){
            if(t.timeline==null)throw new AuthException("TIMELINE_REQUIRED",409);
            if(request.revision()==null||request.revision()!=t.revision)throw new AuthException("STALE_TOURNAMENT_REVISION",409);
            if(t.status!=TournamentStatus.REGISTRATION||t.startedAt!=null)throw new AuthException("APPLICATION_REVIEW_CLOSED",409);
            var now=clock.instant();if(!now.isBefore(t.timeline.codingOpenAt()))throw new AuthException("ROSTER_LOCK_DEADLINE",409);
            String reason=com.rpsbattle.admin.AdminDirectoryService.reason(request.reason());
            owned.submissionMutation(t,auth,()->{
                if(lock){promote(t);int minimum=t.format==TournamentFormat.GROUP_KNOCKOUT?4:t.format==TournamentFormat.TEAM_ROUND_ROBIN?6:2;var state=store.lockRoster(UUID.fromString(id),actor.id().value(),actor.displayName(),now,minimum,t.timeline.registrationCloseAt());TournamentAudit.append(t,"REGISTRATION_ROSTER_LOCKED",actor.id().value(),id,reason,null,state.lockedCount().toString());}
                else{if(now.isBefore(t.timeline.registrationOpenAt()))throw new AuthException("REGISTRATION_NOT_OPEN",409);store.close(UUID.fromString(id),now);promote(t);TournamentAudit.append(t,"REGISTRATION_APPLICATIONS_CLOSED",actor.id().value(),id,reason,null,now.toString());}
                tournaments.changed(t);
            });
        }
    }
    public Flow withdraw(Authentication auth,String id){var user=accounts.requireUser(auth).id().value();var t=tournaments.find(id);synchronized(t){if(!store.control(UUID.fromString(id)).enabled())registrationOpen(t);else if(!reviewOpen(t))throw new AuthException("APPLICATION_REVIEW_CLOSED",409);owned.submissionMutation(t,auth,()->{var change=store.withdraw(UUID.fromString(id),user);TournamentAudit.append(t,"PARTICIPANT_APPLICATION_WITHDRAWN",user,change.after().id().toString(),null,change.before(),"CANCELLED");promote(t);tournaments.changed(t);});return flow(t,user);}}
}
