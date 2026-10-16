package com.rpsbattle.tournament.domain;
import java.util.*;
/** Account-owned teams and immutable fixture lineup snapshots, stored with the tournament transaction. */
public record TeamState(List<Team> teams,List<Fixture> fixtures) {
 public TeamState {teams=List.copyOf(teams);fixtures=List.copyOf(fixtures);}
 public static TeamState empty(){return new TeamState(List.of(),List.of());}
 public record Team(String id,String name,String school,UUID captain,List<String> roster){public Team{roster=List.copyOf(roster);}}
 public record Fixture(String id,int round,String teamA,String teamB,List<String> lineupA,List<String> lineupB,boolean locked,List<String> seriesIds){public Fixture{lineupA=List.copyOf(lineupA);lineupB=List.copyOf(lineupB);seriesIds=List.copyOf(seriesIds);}}
}
