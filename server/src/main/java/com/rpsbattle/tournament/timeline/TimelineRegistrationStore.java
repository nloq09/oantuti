package com.rpsbattle.tournament.timeline;

import com.rpsbattle.auth.AuthException;
import com.rpsbattle.tournament.registration.*;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class TimelineRegistrationStore {
    @PersistenceContext EntityManager db;
    public record View(UUID id,String displayName,String intendedLanguage,String affiliation,String status,Instant registeredAt,String reviewReason,Instant reviewedAt) {}
    public record Application(UUID id,String displayName,String intendedLanguage,String affiliation,String status,Instant registeredAt,String reviewReason,Instant reviewedAt,List<EligibilityReasonCode> reasonCodes) {}
    private String publicStatus(String status){return status.equals("PENDING")?"NEEDS_REVIEW":status;}
    private Application application(TimelineRegistration r){return new Application(r.id,r.displayName,r.intendedLanguage,r.affiliation,publicStatus(r.status),r.registeredAt,r.reviewReason,r.reviewedAt,r.eligibilityReasons.isBlank()?(r.status.equals("PENDING")?List.of(EligibilityReasonCode.MANUAL_REVIEW_REQUIRED):List.of()):Arrays.stream(r.eligibilityReasons.split(",")).map(EligibilityReasonCode::valueOf).toList());}
    @Transactional(readOnly=true) public boolean hasApplications(UUID tournament){return db.createQuery("select count(r) from TimelineRegistration r where r.tournamentId=:t",Long.class).setParameter("t",tournament).getSingleResult()>0;}
    private TimelineRegistration find(UUID tournament,UUID user){return db.createQuery("select r from TimelineRegistration r where r.tournamentId=:t and r.userId=:u",TimelineRegistration.class).setParameter("t",tournament).setParameter("u",user).getResultStream().findFirst().orElse(null);}
    private View view(TimelineRegistration r){return r==null?null:new View(r.id,r.displayName,r.intendedLanguage,r.affiliation,publicStatus(r.status),r.registeredAt,r.reviewReason,r.reviewedAt);}
    @Transactional(readOnly=true) public View mine(UUID tournament,UUID user){return view(find(tournament,user));}
    @Transactional(readOnly=true) public boolean reviewComplete(UUID tournament){return db.createQuery("select count(r) from TimelineRegistration r where r.tournamentId=:t and r.status='PENDING'",Long.class).setParameter("t",tournament).getSingleResult()==0;}
    @Transactional public View register(UUID tournament,UUID user,String name,String language,String affiliation,int capacity,Instant now){return register(tournament,user,name,language,affiliation,capacity,now,new EligibilityDecision(EligibilityDecision.Decision.REVIEW,List.of(EligibilityReasonCode.MANUAL_REVIEW_REQUIRED)));}
    @Transactional public View register(UUID tournament,UUID user,String name,String language,String affiliation,int capacity,Instant now,EligibilityDecision decision){
        if(!Set.of(EligibilityDecision.Decision.APPROVE,EligibilityDecision.Decision.REVIEW).contains(decision.decision()))throw new IllegalArgumentException("Unsupported admission decision");
        var previous=find(tournament,user);
        if(previous!=null&&!previous.status.equals("CANCELLED"))throw new AuthException("DUPLICATE_PARTICIPANT",409);
        long count=db.createQuery("select count(r) from TimelineRegistration r where r.tournamentId=:t and r.status in ('PENDING','APPROVED','WAITLISTED')",Long.class).setParameter("t",tournament).getSingleResult();
        if(count>=capacity)throw new AuthException("PARTICIPANT_LIMIT",409);
        var r=previous==null?new TimelineRegistration():previous;
        if(previous==null){r.id=UUID.randomUUID();r.tournamentId=tournament;r.userId=user;}
        r.displayName=name;r.intendedLanguage=language;r.affiliation=affiliation;r.status=decision.decision()==EligibilityDecision.Decision.APPROVE?"APPROVED":"PENDING";r.eligibilityReasons=String.join(",",decision.reasonCodes().stream().map(Enum::name).toList());r.registeredAt=now;r.rulesAcceptedAt=now;
        r.reviewReason=null;r.reviewedAt=null;r.reviewedBy=null;
        if(previous==null)db.persist(r);db.flush();return view(r);
    }
    @Transactional public void withdraw(UUID tournament,UUID user){var r=find(tournament,user);if(r==null||!Set.of("PENDING","APPROVED","WAITLISTED").contains(r.status))throw new AuthException("INVALID_REGISTRATION_TRANSITION",409);r.status="CANCELLED";}
    @Transactional(readOnly=true) public List<Application> page(UUID tournament,int page,String status){
        String filter=status==null?"":" and r.status=:status";
        var query=db.createQuery("select r from TimelineRegistration r where r.tournamentId=:t"+filter+" order by r.registeredAt,r.id",TimelineRegistration.class).setParameter("t",tournament);
        if(status!=null)query.setParameter("status",status.equals("NEEDS_REVIEW")?"PENDING":status);
        return query.setFirstResult(page*25).setMaxResults(25).getResultStream().map(this::application).toList();
    }
    public record ReviewChange(String before,View after) {}
    @Transactional public ReviewChange review(UUID tournament,UUID id,String next,String reason,UUID actor,Instant now,int capacity){
        var r=db.find(TimelineRegistration.class,id,LockModeType.PESSIMISTIC_WRITE);
        if(r==null||!r.tournamentId.equals(tournament))throw new AuthException("APPLICATION_NOT_FOUND",404);
        if(r.status.equals("CANCELLED")||r.status.equals(next))throw new AuthException("INVALID_REGISTRATION_TRANSITION",409);
        if(r.status.equals("REJECTED")&&!next.equals("REJECTED")){
            long count=db.createQuery("select count(r) from TimelineRegistration r where r.tournamentId=:t and r.status in ('PENDING','APPROVED','WAITLISTED')",Long.class).setParameter("t",tournament).getSingleResult();
            if(count>=capacity)throw new AuthException("PARTICIPANT_LIMIT",409);
        }
        String before=r.status;r.status=next;r.reviewReason=reason;r.reviewedAt=now;r.reviewedBy=actor;db.flush();return new ReviewChange(before,view(r));
    }
}
