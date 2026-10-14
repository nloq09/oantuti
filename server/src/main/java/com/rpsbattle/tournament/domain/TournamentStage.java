package com.rpsbattle.tournament.domain;
/** A single stage is active today; qualification can connect stages in a later release. */
public record TournamentStage(String id,int number,TournamentFormat format,Integer qualifyTopN) {
    public TournamentStage {
        if(id==null||number<1||format==null||(qualifyTopN!=null&&qualifyTopN<1))throw new IllegalArgumentException("Invalid tournament stage");
    }
}
