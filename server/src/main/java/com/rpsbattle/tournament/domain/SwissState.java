package com.rpsbattle.tournament.domain;
/** Round pairings themselves are immutable matches; this is only the progression cursor. */
public record SwissState(int totalRounds,int currentRound,boolean complete,String stopReason) {}
