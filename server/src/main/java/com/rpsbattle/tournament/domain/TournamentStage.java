package com.rpsbattle.tournament.domain;
/** Persisted stage definition; qualifyTopN applies independently to each group. */
public record TournamentStage(String id,int number,TournamentFormat format,Integer qualifyTopN) {
    public TournamentStage {
        if(id==null||number<1||format==null||(qualifyTopN!=null&&qualifyTopN<1))throw new IllegalArgumentException("Invalid tournament stage");
    }
}
