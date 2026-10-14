package com.rpsbattle.tournament.ranking;
public record RankingEntry(int rank,String participantId,String displayName,int played,int wins,int draws,int losses,int points,int piecesRemaining,Double averageMoveMillis) {
    public RankingEntry(int rank,String id,String name,int played,int wins,int draws,int losses,int points,int pieces) {this(rank,id,name,played,wins,draws,losses,points,pieces,null);}
}
