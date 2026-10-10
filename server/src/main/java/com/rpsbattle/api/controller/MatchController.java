package com.rpsbattle.api.controller;

import com.rpsbattle.online.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/matches")
public class MatchController {
    public record CreateRequest(String mode,Long seed) {}
    private final OnlineMatchService service;
    public MatchController(OnlineMatchService service) { this.service=service; }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public Map<String,Object> create(@RequestBody CreateRequest request) {
        var room=service.create(MatchMode.valueOf(request.mode()),request.seed());
        return credentials(room,room.creator());
    }
    @PostMapping("/{id}/join")
    public Map<String,Object> join(@PathVariable String id) { return credentials(service.find(id),service.join(id)); }
    @GetMapping("/{id}")
    public Map<String,Object> info(@PathVariable String id) {
        var room=service.find(id); synchronized(room) {
            return Map.of("matchId",room.id(),"mode",room.mode(),"status",room.status(),"players",room.seats().size());
        }
    }
    private Map<String,Object> credentials(OnlineMatch room,OnlineMatch.Seat seat) {
        return Map.of("matchId",room.id(),"mode",room.mode(),"playerId",seat.id().value(),"token",seat.token(),"seed",room.seed());
    }
}
