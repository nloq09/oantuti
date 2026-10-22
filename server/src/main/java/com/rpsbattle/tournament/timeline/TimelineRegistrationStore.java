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
    public record View(UUID id,String displayName,String intendedLanguage,String affiliation,String status,Instant registeredAt,String publicMessage,Instant reviewedAt,Long waitlistPosition) {}
    public record Application(UUID id,String displayName,String intendedLanguage,String affiliation,String status,Instant registeredAt,String reviewReason,Instant reviewedAt,List<EligibilityReasonCode> reasonCodes,Long waitlistPosition,String publicMessage) {}
    private String publicStatus(String status){return status.equals("PENDING")?"NEEDS_REVIEW":status;}
    private Long position(TimelineRegistration r){
        if(!r.status.equals("WAITLISTED")||r.waitlistOrder==null)return null;
        return db.createQuery("select count(r) from TimelineRegistration r where r.tournamentId=:t and r.status='WAITLISTED' and r.waitlistOrder<=:order",Long.class)
                .setParameter("t",r.tournamentId).setParameter("order",r.waitlistOrder).getSingleResult();
    }
    private Application application(TimelineRegistration r){return application(r,position(r));}
    private Application application(TimelineRegistration r,Long waitlistPosition){return new Application(r.id,r.displayName,r.intendedLanguage,r.affiliation,publicStatus(r.status),r.registeredAt,r.reviewReason,r.reviewedAt,r.eligibilityReasons.isBlank()?(r.status.equals("PENDING")?List.of(EligibilityReasonCode.MANUAL_REVIEW_REQUIRED):List.of()):Arrays.stream(r.eligibilityReasons.split(",")).map(EligibilityReasonCode::valueOf).toList(),waitlistPosition,r.publicReviewMessage);}
    @Transactional(readOnly=true) public boolean hasApplications(UUID tournament){return db.createQuery("select count(r) from TimelineRegistration r where r.tournamentId=:t",Long.class).setParameter("t",tournament).getSingleResult()>0;}
    private TimelineRegistration find(UUID tournament,UUID user){return db.createQuery("select r from TimelineRegistration r where r.tournamentId=:t and r.userId=:u",TimelineRegistration.class).setParameter("t",tournament).setParameter("u",user).getResultStream().findFirst().orElse(null);}
    private View view(TimelineRegistration r){return r==null?null:new View(r.id,r.displayName,r.intendedLanguage,r.affiliation,publicStatus(r.status),r.registeredAt,r.publicReviewMessage,r.reviewedAt,position(r));}
    @Transactional(readOnly=true) public View mine(UUID tournament,UUID user){return view(find(tournament,user));}
    @Transactional(readOnly=true) public boolean reviewComplete(UUID tournament){return db.createQuery("select count(r) from TimelineRegistration r where r.tournamentId=:t and r.status='PENDING'",Long.class).setParameter("t",tournament).getSingleResult()==0;}

    public record Control(boolean enabled,Instant closedAt,Instant lockedAt,String lockedByName,Integer lockedCount) {}
    private RegistrationControl controlRow(UUID tournament,boolean refresh){var row=db.find(RegistrationControl.class,tournament);if(row!=null&&refresh)db.refresh(row,LockModeType.PESSIMISTIC_WRITE);return row;}
    @Transactional(readOnly=true) public Control control(UUID tournament){var row=controlRow(tournament,false);return row==null?new Control(false,null,null,null,null):new Control(true,row.closedAt,row.lockedAt,row.lockedByName,row.lockedCount);}
    @Transactional(readOnly=true) public boolean codingAllowed(String tournament){var state=control(UUID.fromString(tournament));return !state.enabled()||state.lockedAt()!=null;}
    @Transactional public void enable(UUID tournament){lockCapacity(tournament);if(controlRow(tournament,true)==null){var row=new RegistrationControl();row.tournamentId=tournament;db.persist(row);db.flush();}}
    private RegistrationControl unlocked(UUID tournament){var row=controlRow(tournament,true);if(row!=null&&row.lockedAt!=null)throw new AuthException("ROSTER_LOCKED",409);return row;}
    @Transactional public void close(UUID tournament,Instant now){lockCapacity(tournament);enable(tournament);var row=unlocked(tournament);if(row.closedAt!=null)throw new AuthException("REGISTRATION_ALREADY_CLOSED",409);row.closedAt=now;db.flush();}
    @Transactional public Control lockRoster(UUID tournament,UUID actor,String name,Instant now,int minimum,Instant registrationDeadline){
        int capacity=lockCapacity(tournament);enable(tournament);var row=unlocked(tournament);
        if(row.closedAt==null&&now.isBefore(registrationDeadline))throw new AuthException("REGISTRATION_STILL_OPEN",409);
        if(!reviewComplete(tournament))throw new AuthException("ROSTER_REVIEW_REQUIRED",409);
        long approved=approvedCount(tournament);if(approved>capacity)throw new AuthException("CAPACITY_FULL",409);if(approved<minimum)throw new AuthException("NOT_ENOUGH_PARTICIPANTS",409);
        row.closedAt=row.closedAt==null?registrationDeadline:row.closedAt;row.lockedAt=now;row.lockedBy=actor;row.lockedByName=name;row.lockedCount=Math.toIntExact(approved);db.flush();return control(tournament);
    }
    @Transactional(readOnly=true) public Map<String,Long> counts(UUID tournament){var counts=new LinkedHashMap<String,Long>();for(String key:List.of("total","approved","needsReview","waitlisted","rejected","cancelled"))counts.put(key,0L);for(var row:db.createQuery("select r.status,count(r) from TimelineRegistration r where r.tournamentId=:t group by r.status",Object[].class).setParameter("t",tournament).getResultList()){String key=row[0].equals("PENDING")?"needsReview":row[0].toString().toLowerCase(Locale.ROOT);long n=(Long)row[1];counts.put(key,n);counts.merge("total",n,Long::sum);}return counts;}
    @Transactional(readOnly=true) public com.rpsbattle.tournament.TournamentPhaseResolver.Resolution phase(com.rpsbattle.tournament.domain.Tournament t,Instant now){
        var base=com.rpsbattle.tournament.TournamentPhaseResolver.resolve(t.status,t.timeline,now,null,reviewComplete(UUID.fromString(t.id)));var state=control(UUID.fromString(t.id));
        if(t.timeline!=null&&t.status==com.rpsbattle.tournament.domain.TournamentStatus.REGISTRATION&&state.enabled()&&(state.closedAt()!=null||!now.isBefore(t.timeline.registrationCloseAt()))){
            if(state.lockedAt()==null)return new com.rpsbattle.tournament.TournamentPhaseResolver.Resolution(com.rpsbattle.tournament.domain.TournamentPhase.REGISTRATION_REVIEW,t.timeline.codingOpenAt());
            if(now.isBefore(t.timeline.codingOpenAt()))return new com.rpsbattle.tournament.TournamentPhaseResolver.Resolution(com.rpsbattle.tournament.domain.TournamentPhase.WAITING_FOR_CODING,t.timeline.codingOpenAt());
        }return base;
    }
    public record Promotion(UUID applicationId,String before,String after,String reason) {}
    @Transactional public List<Promotion> promote(UUID tournament,RegistrationPolicy policy,Set<String> languages,Instant now){return promote(tournament,policy,languages,now,null);}
    @Transactional public List<Promotion> promote(UUID tournament,RegistrationPolicy policy,Set<String> languages,Instant now,UUID excluded){
        int capacity=lockCapacity(tournament);var control=controlRow(tournament,true);if(control!=null&&control.lockedAt!=null)return List.of();
        if(policy.approvalPolicy()==RegistrationApprovalPolicy.MANUAL_REVIEW||policy.requireAffiliationVerification())return List.of();
        int remaining=(int)Math.max(0,capacity-approvedCount(tournament));if(remaining==0)return List.of();
        // Bounded batches and an order cursor skip unresolved verification without looping on it.
        var result=new ArrayList<Promotion>();long cursor=0;
        while(remaining>0){var query=db.createQuery("select r from TimelineRegistration r where r.tournamentId=:t and r.status='WAITLISTED' and r.waitlistOrder>:cursor"+(excluded==null?"":" and r.id<>:excluded")+" order by r.waitlistOrder,r.id",TimelineRegistration.class).setParameter("t",tournament).setParameter("cursor",cursor).setMaxResults(100);if(excluded!=null)query.setParameter("excluded",excluded);var candidates=query.getResultList();if(candidates.isEmpty())break;
            for(var candidate:candidates){if(remaining==0)break;cursor=candidate.waitlistOrder;db.refresh(candidate,LockModeType.PESSIMISTIC_WRITE);if(!candidate.status.equals("WAITLISTED"))continue;
                Object account=db.createNativeQuery("select status from users where id=:id").setParameter("id",candidate.userId).getResultStream().findFirst().orElse(null);
                var decision=eligibility.evaluate(new RegistrationEligibilityService.Context("ACTIVE".equals(account),true,false,true,candidate.intendedLanguage,languages,candidate.affiliation,policy));
                if(decision.decision()==EligibilityDecision.Decision.REVIEW)continue;
                candidate.status=switch(decision.decision()){case APPROVE->"APPROVED";case REVIEW->"PENDING";default->"REJECTED";};candidate.publicReviewMessage=null;candidate.eligibilityReasons=String.join(",",decision.reasonCodes().stream().map(Enum::name).toList());
                result.add(new Promotion(candidate.id,"WAITLISTED",publicStatus(candidate.status),candidate.eligibilityReasons));if(candidate.status.equals("APPROVED"))remaining--;
            }db.flush();
        }return result;
    }

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
        unlocked(tournament);
        if(capacity!=currentCapacity)throw new AuthException("STALE_TOURNAMENT_REVISION",409);
        var state=control(tournament);if(state.closedAt()!=null)throw new AuthException("REGISTRATION_NOT_OPEN",409);
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
        r.reviewReason=null;r.publicReviewMessage=null;r.reviewedAt=null;r.reviewedBy=null;
        if(previous==null)db.persist(r);db.flush();return view(r);
    }
    @Transactional public ReviewChange withdraw(UUID tournament,UUID user){
        lockCapacity(tournament);unlocked(tournament);
        var r=find(tournament,user);if(r!=null)db.refresh(r,LockModeType.PESSIMISTIC_WRITE);if(r==null||!Set.of("PENDING","APPROVED","WAITLISTED").contains(r.status))throw new AuthException("INVALID_REGISTRATION_TRANSITION",409);
        String before=r.status;r.status="CANCELLED";r.publicReviewMessage=null;return new ReviewChange(before,view(r));
    }
    private record Search(String where,Map<String,Object> parameters) {}
    private Search search(UUID tournament,ApplicationFilter filter){
        var where=new StringBuilder(" where r.tournamentId=:t");var params=new LinkedHashMap<String,Object>();params.put("t",tournament);
        if(filter.status()!=null){where.append(" and r.status=:status");params.put("status",filter.status());}
        if(filter.search()!=null){where.append(" and (lower(r.displayName) like :search escape '!' or r.userId in (select u.id from UserEntity u where lower(u.username) like :search escape '!'))");params.put("search",pattern(filter.search()));}
        if(filter.affiliation()!=null){where.append(" and lower(r.affiliation) like :affiliation escape '!'");params.put("affiliation",pattern(filter.affiliation()));}
        if(filter.language()!=null){where.append(" and r.intendedLanguage=:language");params.put("language",filter.language());}
        if(filter.reason()!=null){where.append(" and (concat(',',r.eligibilityReasons,',') like :reason escape '!'");params.put("reason","%,"+filter.reason().replace("_","!_")+",%");if(filter.reason().equals("MANUAL_REVIEW_REQUIRED"))where.append(" or (r.status='PENDING' and r.eligibilityReasons='')");where.append(")");}
        if(filter.from()!=null){where.append(" and r.registeredAt>=:from");params.put("from",filter.from());}
        if(filter.until()!=null){where.append(" and r.registeredAt<:until");params.put("until",filter.until());}
        return new Search(where.toString(),params);
    }
    private String pattern(String value){return "%"+value.toLowerCase(Locale.ROOT).replace("!","!!").replace("%","!%").replace("_","!_")+"%";}
    @Transactional(readOnly=true) public long count(UUID tournament,ApplicationFilter filter){
        var search=search(tournament,filter);var query=db.createQuery("select count(r) from TimelineRegistration r"+search.where(),Long.class);search.parameters().forEach(query::setParameter);return query.getSingleResult();
    }
    @Transactional(readOnly=true) public List<Application> page(UUID tournament,int page,String status){return page(tournament,new ApplicationFilter(page,25,status,null,null,null,null,null,null));}
    @Transactional(readOnly=true) public List<Application> page(UUID tournament,ApplicationFilter filter){
        var search=search(tournament,filter);String order="WAITLISTED".equals(filter.status())?"r.waitlistOrder,r.id":"r.registeredAt,r.id";
        var query=db.createQuery("select r from TimelineRegistration r"+search.where()+" order by "+order,TimelineRegistration.class);search.parameters().forEach(query::setParameter);
        var rows=query.setFirstResult(filter.page()*filter.size()).setMaxResults(filter.size()).getResultList();
        var waiting=rows.stream().filter(r->r.status.equals("WAITLISTED")&&r.waitlistOrder!=null).map(r->r.id).toList();
        var positions=new HashMap<UUID,Long>();
        if(!waiting.isEmpty())for(var pair:db.createQuery("select r.id,(select count(w) from TimelineRegistration w where w.tournamentId=:t and w.status='WAITLISTED' and w.waitlistOrder<=r.waitlistOrder) from TimelineRegistration r where r.tournamentId=:t and r.id in :ids",Object[].class).setParameter("t",tournament).setParameter("ids",waiting).getResultList())positions.put((UUID)pair[0],(Long)pair[1]);
        return rows.stream().map(r->application(r,positions.get(r.id))).toList();
    }
    public record Details(Application application,String username,Instant rulesAcceptedAt,boolean accountActive) {}
    @Transactional(readOnly=true) public Details details(UUID tournament,UUID id){
        var r=db.createQuery("select r from TimelineRegistration r where r.tournamentId=:t and r.id=:id",TimelineRegistration.class).setParameter("t",tournament).setParameter("id",id).getResultStream().findFirst().orElseThrow(()->new AuthException("APPLICATION_NOT_FOUND",404));
        var user=db.createQuery("select u.username,u.status from UserEntity u where u.id=:u",Object[].class).setParameter("u",r.userId).getSingleResult();
        return new Details(application(r),user[0].toString(),r.rulesAcceptedAt,"ACTIVE".equals(user[1].toString()));
    }
    @Transactional public void publicMessage(UUID tournament,UUID application,String message){
        var row=db.createQuery("select r from TimelineRegistration r where r.tournamentId=:t and r.id=:id",TimelineRegistration.class).setParameter("t",tournament).setParameter("id",application).getSingleResult();row.publicReviewMessage=message;db.flush();
    }
    public record ReviewChange(String before,View after) {}
    @Transactional public ReviewChange review(UUID tournament,UUID id,String next,String reason,UUID actor,Instant now,int capacity){
        return review(tournament,id,next,reason,actor,now,capacity,null,null);
    }
    @Transactional public ReviewChange review(UUID tournament,UUID id,String next,String reason,UUID actor,Instant now,int capacity,String expectedStatus,Instant expectedRegisteredAt){
        if(!Set.of("APPROVED","REJECTED","WAITLISTED").contains(next))throw new AuthException("INVALID_REGISTRATION_TRANSITION",400);
        int currentCapacity=lockCapacity(tournament);
        unlocked(tournament);
        if(capacity!=currentCapacity)throw new AuthException("STALE_TOURNAMENT_REVISION",409);
        // Scope before acquiring the child lock: a foreign application must never be modified.
        var r=db.createQuery("select r from TimelineRegistration r where r.tournamentId=:t and r.id=:id",TimelineRegistration.class)
                .setParameter("t",tournament).setParameter("id",id).setLockMode(LockModeType.PESSIMISTIC_WRITE).getResultStream().findFirst().orElse(null);
        if(r==null)throw new AuthException("APPLICATION_NOT_FOUND",404);
        db.refresh(r,LockModeType.PESSIMISTIC_WRITE);
        if(expectedStatus!=null&&(!publicStatus(r.status).equals(expectedStatus)||!r.registeredAt.equals(expectedRegisteredAt)))throw new AuthException("APPLICATION_CHANGED",409);
        if(r.status.equals("CANCELLED")||r.status.equals(next))throw new AuthException("INVALID_REGISTRATION_TRANSITION",409);
        if(next.equals("APPROVED")&&approvedCount(tournament)>=currentCapacity)throw new AuthException("CAPACITY_FULL",409);
        if(next.equals("WAITLISTED"))r.waitlistOrder=nextWaitlistOrder(tournament);
        String before=r.status;r.status=next;r.reviewReason=reason;r.reviewedAt=now;r.reviewedBy=actor;db.flush();return new ReviewChange(before,view(r));
    }
}
