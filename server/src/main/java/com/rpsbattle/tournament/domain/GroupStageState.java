package com.rpsbattle.tournament.domain;

import java.util.*;

/** Frozen seeded groups and the qualification snapshot; null qualifier slots become BYEs. */
public record GroupStageState(Map<String,List<String>> groups,int rounds,boolean locked,List<String> qualifiers,int stageNumber,int startRound) {
    public GroupStageState(Map<String,List<String>> groups,int rounds,boolean locked,List<String> qualifiers){this(groups,rounds,locked,qualifiers,1,0);}
    public GroupStageState {
        if(stageNumber==0)stageNumber=1;
        var copy=new LinkedHashMap<String,List<String>>();
        groups.forEach((key,value)->copy.put(key,List.copyOf(value)));
        groups=Collections.unmodifiableMap(copy);
        qualifiers=Collections.unmodifiableList(new ArrayList<>(qualifiers));
    }
}
