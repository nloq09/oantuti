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
    private final RegistrationEligibilityService eligibility;
    public TimelineRegistrationStore(RegistrationEligibilityService eligibility){this.eligibility=eligibility;}
    public record View(UUID id,String displayName,String intendedLanguage,String affiliation,String status,Instant registeredAt,String reviewReason,Instant reviewedAt,Long waitlistPosition) {}
    public record Application(UUID id,String displayName,String intendedLanguage,String affiliation,String status,Instant registeredAt,String reviewReason,Instant reviewedAt,List<EligibilityReasonCode> reasonCodes,Long waitlistPosition) {}
    private String publicStatus(String status){return status.equals("PENDING")?"NEEDS_REVIEW":status;}
    private Long position(TimelineRegistration r){
        if(!r.status.equals("WAITLISTED")||r.waitlistOrder==null)return null;
        return db.createQuery("select count(r) from TimelineRegistration r where r.tournamentId=:t and r.status='WAITLISTED' and r.waitlistOrder<=:order",Long.class)
                .setParameter("t",r.tournamentId).setParameter("order",r.waitlistOrder).getSingleResult();
    }
    private Application application(TimelineRegistration r){return new Application(r.id,r.displayName,r.intendedLanguage,r.affiliation,publicStatus(r.status),r.registeredAt,r.reviewReason,r.reviewedAt,r.eligibilityReasons.isBlank()?(r.status.equals("PENDING")?List.of(EligibilityReasonCode.MANUAL_REVIEW_REQUIRED):List.of()):Arrays.stream(r.eligibilityReasons.split(",")).map(EligibilityReasonCode::valueOf).toList(),position(r));}
    @Transactional(readOnly=true) public boolean hasApplications(UUID tournament){return db.createQuery("select count(r) from TimelineRegistration r where r.tournamentId=:t",Long.class).setParameter("t",tournament).getSingleResult()>0;}
    private TimelineRegistration find(UUID tournament,UUID user){return db.createQuery("select r from TimelineRegistration r where r.tournamentId=:t and r.userId=:u",TimelineRegistration.class).setParameter("t",tournament).setParameter("u",user).getResultStream().findFirst().orElse(null);}
    private View view(TimelineRegistration r){return r==null?null:new View(r.id,r.displayName,r.intendedLanguage,r.affiliation,publicStatus(r.status),r.registeredAt,r.reviewReason,r.reviewedAt,position(r));}
    @Transactional(readOnly=true) public View mine(UUID tournament,UUID user){return view(find(tournament,user));}
    @Transactional(readOnly=true) public boolean reviewComplete(UUID tournament){return db.createQuery("select count(r) from TimelineRegistration r where r.tournamentId=:t and r.status='PENDING'",Long.class).setParameter("t",tournament).getSingleResult()==0;}

    // All slot mutations take this parent lock first, then inspect/lock applications.
    // It is held until the surrounding submissionMutation transaction commits or rolls back.
    private int lockCapacity(UUID tournament){
        var rows=db.createNativeQuery("select max_participants from tournaments where id=:id for update").setParameter("id",tournament).getResultList();
        if(rows.isEmpty())throw new AuthException("TOURNAMENT_NOT_FOUND",404);
        return ((Number)rows.getFirst()).intValue();
    }
    private long approvedCount(UUID tournament){return db.createQuery("select count(r) from TimelineRegistration r where r.tournamentId=:t and r.status='APPROVED'",Long.class).setParameter("t",tournament).getSingleResult();}
    private long nextWaitlistOrder(UUID tournament){return Math.addExact(db.createQuery("select coalesce(max(r.waitlistOrder),0) from TimelineRegistration r where r.tournamentId=:t",Long.class).setParameter("t",tournament).getSingleResult(),1L);}

    @Transactional public View register(UUID tournament,UUID user,String name,String language,String affiliation,int capacity,Instant now){
        return register(tournament,user,name,language,affiliation,capacity,now,new EligibilityDecision(EligibilityDecision.Decision.REVIEW,List.of(EligibilityReasonCode.MANUAL_REVIEW_REQUIRED)));
    }
    @Transactional public View register(UUID tournament,UUID user,String name,String language,String affiliation,int capacity,Instant now,EligibilityDecision decision){
        if(!Set.of(EligibilityDecision.Decision.APPROVE,EligibilityDecision.Decision.REVIEW).contains(decision.decision()))throw new IllegalArgumentException("Unsupported admission decision");
        int currentCapacity=lockCapacity(tournament);
        if(capacity!=currentCapacity)throw new AuthException("STALE_TOURNAMENT_REVISION",409);
        var previous=find(tournament,user);
        if(previous!=null)db.refresh(previous,LockModeType.PESSIMISTIC_WRITE);
        if(previous!=null&&!previous.status.equals("CANCELLED"))throw new AuthException("DUPLICATE_REGISTRATION",409);
        var allocated=eligibility.allocate(decision,approvedCount(tournament),currentCapacity);
        var r=previous==null?new TimelineRegistration():previous;
        if(previous==null){r.id=UUID.randomUUID();r.tournamentId=tournament;r.userId=user;}
        if(allocated.decision()==EligibilityDecision.Decision.WAITLIST)r.waitlistOrder=nextWaitlistOrder(tournament);
        r.displayName=name;r.intendedLanguage=language;r.affiliation=affiliation;
        r.status=switch(allocated.decision()){case APPROVE->"APPROVED";case REVIEW->"PENDING";case WAITLIST->"WAITLISTED";default->throw new IllegalStateException("Invalid allocation");};
        r.eligibilityReasons=String.join(",",allocated.reasonCodes().stream().map(Enum::name).toList());r.registeredAt=now;r.rulesAcceptedAt=now;
        r.reviewReason=null;r.reviewedAt=null;r.reviewedBy=null;
        if(previous==null)db.persist(r);db.flush();return view(r);
    }
    @Transactional public void withdraw(UUID tournament,UUID user){
        lockCapacity(tournament);
        var r=find(tournament,user);if(r!=null)db.refresh(r,LockModeType.PESSIMISTIC_WRITE);if(r==null||!Set.of("PENDING","APPROVED","WAITLISTED").contains(r.status))throw new AuthException("INVALID_REGISTRATION_TRANSITION",409);
        r.status="CANCELLED";
    }
    @Transactional(readOnly=true) public List<Application> page(UUID tournament,int page,String status){
        String filter=status==null?"":" and r.status=:status";
        String order="WAITLISTED".equals(status)?"r.waitlistOrder,r.id":"r.registeredAt,r.id";
        var query=db.createQuery("select r from TimelineRegistration r where r.tournamentId=:t"+filter+" order by "+order,TimelineRegistration.class).setParameter("t",tournament);
        if(status!=null)query.setParameter("status",status.equals("NEEDS_REVIEW")?"PENDING":status);
        return query.setFirstResult(page*25).setMaxResults(25).getResultList().stream().map(this::application).toList();
    }
    public record ReviewChange(String before,View after) {}
    @Transactional public ReviewChange review(UUID tournament,UUID id,String next,String reason,UUID actor,Instant now,int capacity){
        if(!Set.of("APPROVED","REJECTED","WAITLISTED").contains(next))throw new AuthException("INVALID_REGISTRATION_TRANSITION",400);
        int currentCapacity=lockCapacity(tournament);
        if(capacity!=currentCapacity)throw new AuthException("STALE_TOURNAMENT_REVISION",409);
        // Scope before acquiring the child lock: a foreign application must never be modified.
        var r=db.createQuery("select r from TimelineRegistration r where r.tournamentId=:t and r.id=:id",TimelineRegistration.class)
                .setParameter("t",tournament).setParameter("id",id).setLockMode(LockModeType.PESSIMISTIC_WRITE).getResultStream().findFirst().orElse(null);
        if(r==null)throw new AuthException("APPLICATION_NOT_FOUND",404);
        db.refresh(r,LockModeType.PESSIMISTIC_WRITE);
        if(r.status.equals("CANCELLED")||r.status.equals(next))throw new AuthException("INVALID_REGISTRATION_TRANSITION",409);
        if(next.equals("APPROVED")&&approvedCount(tournament)>=currentCapacity)throw new AuthException("CAPACITY_FULL",409);
        if(next.equals("WAITLISTED"))r.waitlistOrder=nextWaitlistOrder(tournament);
        String before=r.status;r.status=next;r.reviewReason=reason;r.reviewedAt=now;r.reviewedBy=actor;db.flush();return new ReviewChange(before,view(r));
    }
}
