package com.rpsbattle.tournament.domain;
import java.time.Instant;
import java.util.UUID;
public record TournamentAuditEvent(UUID id,long sequence,String type,UUID actor,String target,String reason,String beforeValue,String afterValue,Instant at) {}
