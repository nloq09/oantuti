package com.rpsbattle.tournament.timeline;

import com.rpsbattle.auth.AuthException;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class TimelineRegistrationStore {
    @PersistenceContext EntityManager db;
    public record View(UUID id,String displayName,String intendedLanguage,String affiliation,String status,Instant registeredAt) {}
    private TimelineRegistration find(UUID tournament,UUID user){return db.createQuery("select r from TimelineRegistration r where r.tournamentId=:t and r.userId=:u",TimelineRegistration.class).setParameter("t",tournament).setParameter("u",user).getResultStream().findFirst().orElse(null);}
    private View view(TimelineRegistration r){return r==null?null:new View(r.id,r.displayName,r.intendedLanguage,r.affiliation,r.status,r.registeredAt);}
    @Transactional(readOnly=true) public View mine(UUID tournament,UUID user){return view(find(tournament,user));}
    @Transactional(readOnly=true) public boolean reviewComplete(UUID tournament){return db.createQuery("select count(r) from TimelineRegistration r where r.tournamentId=:t and r.status='PENDING'",Long.class).setParameter("t",tournament).getSingleResult()==0;}
    @Transactional public View register(UUID tournament,UUID user,String name,String language,String affiliation,int capacity,Instant now){
        var previous=find(tournament,user);
        if(previous!=null&&!previous.status.equals("CANCELLED"))throw new AuthException("DUPLICATE_PARTICIPANT",409);
        long count=db.createQuery("select count(r) from TimelineRegistration r where r.tournamentId=:t and r.status in ('PENDING','APPROVED','WAITLISTED')",Long.class).setParameter("t",tournament).getSingleResult();
        if(count>=capacity)throw new AuthException("PARTICIPANT_LIMIT",409);
        var r=previous==null?new TimelineRegistration():previous;
        if(previous==null){r.id=UUID.randomUUID();r.tournamentId=tournament;r.userId=user;}
        r.displayName=name;r.intendedLanguage=language;r.affiliation=affiliation;r.status="PENDING";r.registeredAt=now;r.rulesAcceptedAt=now;
        if(previous==null)db.persist(r);db.flush();return view(r);
    }
    @Transactional public void withdraw(UUID tournament,UUID user){var r=find(tournament,user);if(r==null||!Set.of("PENDING","APPROVED","WAITLISTED").contains(r.status))throw new AuthException("INVALID_REGISTRATION_TRANSITION",409);r.status="CANCELLED";}
}
