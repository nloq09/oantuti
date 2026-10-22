package com.rpsbattle.tournament.timeline;

import com.rpsbattle.auth.AuthException;
import com.rpsbattle.tournament.registration.EligibilityReasonCode;
import java.time.Instant;
import java.util.Set;

/** Bounded, parameterized server-side application search. Times form [from, until). */
public record ApplicationFilter(int page,int size,String status,String search,String affiliation,
        String language,String reason,Instant from,Instant until) {
    public ApplicationFilter {
        if(page<0||page>10000||!Set.of(25,50,100).contains(size))throw new AuthException("INVALID_PAGING",400);
        status=text(status,24);search=text(search,80);affiliation=text(affiliation,80);
        language=text(language,16);reason=text(reason,64);
        if("NEEDS_REVIEW".equals(status))status="PENDING";
        if(status!=null&&!Set.of("PENDING","APPROVED","WAITLISTED","REJECTED","CANCELLED").contains(status))invalid();
        if(language!=null&&!Set.of("CPP","PYTHON","JAVA").contains(language))invalid();
        if(reason!=null){try{EligibilityReasonCode.valueOf(reason);}catch(IllegalArgumentException error){invalid();}}
        if(from!=null&&until!=null&&!from.isBefore(until))invalid();
    }
    private static void invalid(){throw new AuthException("INVALID_FILTER",400);}
    private static String text(String value,int max){if(value==null||value.isBlank())return null;value=value.trim();if(value.length()>max||value.chars().anyMatch(Character::isISOControl))invalid();return value;}
    public ApplicationFilter atPage(int value){return new ApplicationFilter(value,size,status,search,affiliation,language,reason,from,until);}
}
