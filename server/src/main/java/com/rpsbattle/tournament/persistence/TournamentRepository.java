package com.rpsbattle.tournament.persistence;
import java.util.*;
public interface TournamentRepository {
    void save(TournamentMetadata metadata);
    List<TournamentMetadata> all();
}
