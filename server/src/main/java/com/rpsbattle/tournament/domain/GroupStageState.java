package com.rpsbattle.tournament.domain;

import java.util.*;

/** Frozen seeded groups and the qualification snapshot; null qualifier slots become BYEs. */
public record GroupStageState(Map<String,List<String>> groups,int rounds,boolean locked,List<String> qualifiers) {
    public GroupStageState {
        var copy=new LinkedHashMap<String,List<String>>();
        groups.forEach((key,value)->copy.put(key,List.copyOf(value)));
        groups=Collections.unmodifiableMap(copy);
        qualifiers=Collections.unmodifiableList(new ArrayList<>(qualifiers));
    }
}
