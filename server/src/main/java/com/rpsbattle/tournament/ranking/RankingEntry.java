package com.rpsbattle.tournament.ranking;
public record RankingEntry(int rank,String participantId,String displayName,int played,int wins,int draws,int losses,int points,int piecesRemaining) {}
