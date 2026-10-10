package com.rpsbattle.online;

import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class MatchRegistry {
    private final ConcurrentHashMap<String, OnlineMatch> matches = new ConcurrentHashMap<>();

    public void register(OnlineMatch match) {
        if (matches.putIfAbsent(match.id(), match) != null) throw new DemoRequestException("DUPLICATE_MATCH");
    }

    public OnlineMatch findById(String id) {
        var match = matches.get(id);
        if (match == null) throw new DemoRequestException("MATCH_NOT_FOUND");
        return match;
    }

    public Collection<OnlineMatch> getActiveMatches() {
        return List.copyOf(matches.values());
    }

    public void remove(String id) {
        matches.remove(id);
    }
}
