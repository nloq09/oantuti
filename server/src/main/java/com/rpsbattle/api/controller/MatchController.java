package com.rpsbattle.api.controller;

import com.rpsbattle.online.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

import com.rpsbattle.bot.ownership.OwnedBotService;
import com.rpsbattle.bot.BotAgent;

@RestController
@RequestMapping("/api/matches")
public class MatchController {
    private final OnlineMatchService service;
    private final OwnedBotService uploads;
    private final com.rpsbattle.history.MatchSeatService identities;

    public MatchController(OnlineMatchService service, OwnedBotService uploads,com.rpsbattle.history.MatchSeatService identities) {
        this.service = service;
        this.uploads = uploads;
        this.identities = identities;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> create(org.springframework.security.core.Authentication auth,@RequestBody CreateRequest request) {
        BotAgent agent = request.botId() == null ? null : uploads.agent(auth,request.botId());
        var room = service.create(MatchMode.valueOf(request.mode()), request.seed());
        synchronized(room) {
            identities.bind(room,room.creator().id(),auth,request.botId());
            if (agent != null) room.attachBot(room.creator().id(), agent, uploads.require(auth,request.botId()).name());
            service.publish(room,room.prepareBots());
        }
        return credentials(room, room.creator());
    }

    @PostMapping("/{id}/join")
    public Map<String, Object> join(org.springframework.security.core.Authentication auth,@PathVariable String id, @RequestBody(required = false) JoinRequest request) {
        BotAgent agent = request == null || request.botId() == null ? null : uploads.agent(auth,request.botId());
        var room = service.find(id);
        synchronized (room) {
            var seat = room.join();
            identities.bind(room,seat.id(),auth,request==null?null:request.botId());
            if (agent != null)
                room.attachBot(seat.id(), agent, uploads.require(auth,request.botId()).name());
            var events = room.prepareBots();
            service.publish(room, events);
            return credentials(room, seat);
        }
    }

    @GetMapping("/{id}")
    public Map<String, Object> info(@PathVariable String id) {
        var room = service.find(id);
        synchronized (room) {
            return Map.of("matchId", room.id(), "mode", room.mode(), "status", room.status(), "players", room.seats().size());
        }
    }

    private Map<String, Object> credentials(OnlineMatch room, OnlineMatch.Seat seat) {
        return Map.of("matchId", room.id(), "historyId",room.archiveId(), "mode", room.mode(), "playerId", seat.id().value(), "token", seat.token(), "seed", room.seed());
    }

    public record CreateRequest(String mode, Long seed, String botId, String botToken) {
    }

    public record JoinRequest(String botId, String botToken) {
    }
}
