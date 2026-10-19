package com.rpsbattle.tournament;
import com.rpsbattle.tournament.domain.Tournament;
import java.util.*;
/** Rotate bounded scheduler batches so a failing tournament cannot starve later cups. */
public final class FairTournamentScan {
 private int next;
 public synchronized List<Tournament> next(Collection<Tournament> values){var list=new ArrayList<>(values);list.sort(Comparator.comparing(t->t.id));if(list.isEmpty())return list;Collections.rotate(list,-Math.floorMod(next++,list.size()));return list;}
}
